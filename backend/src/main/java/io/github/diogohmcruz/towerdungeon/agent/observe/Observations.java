package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import io.github.diogohmcruz.towerdungeon.agent.mcp.AgentHttpProperties;
import io.github.diogohmcruz.towerdungeon.agent.tools.Args;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver.Stop;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Everything the read surface says about a Campaign, as plain data. Tools and resources both call
 * it, so they cannot drift apart. It finds the Campaign a caller means (a Sandbox the caller holds,
 * or the one human Campaign the developer configured), reads it under its lock, and never hands the
 * live state to anyone.
 */
@AgentEnabled
@Component
public class Observations {
  /** A Campaign a caller may read: its id and whether it is a Sandbox. */
  public record Target(String id, boolean sandbox) {}

  private static final long POLL_MS = 250;
  private static final int MAX_LEDGER_LINES = 50;
  private static final int MAX_FALLEN_LISTED = 10;

  private final GameService service;
  private final SandboxRegistry sandboxes;
  private final AgentHttpProperties http;
  private final GameProperties config;
  private final Map<String, AtomicBoolean> waiting = new ConcurrentHashMap<>();

  public Observations(
      GameService service,
      SandboxRegistry sandboxes,
      AgentHttpProperties http,
      GameProperties config) {
    this.service = service;
    this.sandboxes = sandboxes;
    this.http = http;
    this.config = config;
  }

  /**
   * The Campaign a call means. A named one must be a Sandbox the caller holds; "not yours" and
   * "does not exist" read the same. With no name: the caller's only Sandbox, else the human
   * Campaign configured for the developer token.
   */
  public Target resolve(Principal principal, String named) {
    if (named != null) {
      if (!sandboxes.holds(principal.grantId(), named)) {
        throw new ToolFailure("NO_CAMPAIGN", "There is no such Campaign for this grant.");
      }
      return new Target(named, true);
    }
    var held = sandboxes.heldBy(principal.grantId());
    if (held.size() == 1) {
      return new Target(held.getFirst(), true);
    }
    if (held.size() > 1) {
      throw new ToolFailure(
          "CAMPAIGN_REQUIRED",
          "More than one Sandbox is open. Name one with the 'campaign' argument.");
    }
    var human = http.getDevCampaign();
    if (principal.has(Scope.SANDBOX) && !human.isBlank() && service.hasSlot(human)) {
      return new Target(human, false);
    }
    throw new ToolFailure("NO_CAMPAIGN", "There is no Campaign to read. Open a Sandbox first.");
  }

  private <T> T read(Target target, GameService.View<T> view) {
    return service
        .view(target.id(), view)
        .orElseThrow(() -> new ToolFailure("NO_CAMPAIGN", "The Campaign is no longer live."));
  }

  public Map<String, Object> state(Principal principal, String named, String view, Long sinceSeq) {
    var target = resolve(principal, named);
    return read(
        target,
        (state, meta) -> {
          if (sinceSeq != null && sinceSeq == meta.seq()) {
            var same = new LinkedHashMap<String, Object>();
            same.put("seq", meta.seq());
            same.put("unchanged", true);
            return same;
          }
          return "full".equals(view) ? StateViews.full(state, meta) : StateViews.brief(state, meta);
        });
  }

  public Map<String, Object> actions(Principal principal, String named) {
    return read(resolve(principal, named), (state, meta) -> ActionViews.of(state, meta));
  }

  public Map<String, Object> tower(Principal principal, String named) {
    return read(resolve(principal, named), (state, meta) -> TowerViews.summary(state, meta));
  }

  public Map<String, Object> floor(Principal principal, String named, int floor) {
    return read(
        resolve(principal, named),
        (state, meta) ->
            TowerViews.floor(state, meta, floor)
                .orElseThrow(
                    () -> new ToolFailure("NO_SUCH_FLOOR", "That Floor has not been reached.")));
  }

  /** The rules index, or one section. The Enemy Type table needs the Sandbox scope. */
  public Map<String, Object> rules(Principal principal, String named, String section) {
    if (section == null) {
      return RuleViews.index();
    }
    var allowEnemies = principal.has(Scope.SANDBOX);
    var result =
        named == null
            ? RuleViews.section(section, config, allowEnemies)
            : read(
                resolve(principal, named),
                (state, meta) -> RuleViews.section(section, state.getConfig(), allowEnemies));
    if (result == null) {
      throw new ToolFailure("UNKNOWN_SECTION", "There is no such rules section for this caller.");
    }
    return result;
  }

  public Map<String, Object> ledger(Principal principal, String named, int lines, int expeditions) {
    var target = resolve(principal, named);
    var summaries =
        service
            .expeditionSummaries(target.id())
            .orElseThrow(() -> new ToolFailure("NO_CAMPAIGN", "The Campaign is no longer live."));
    return read(
        target,
        (state, meta) -> {
          var ledger = state.getLedger();
          var from = Math.max(0, ledger.size() - Math.min(lines, MAX_LEDGER_LINES));
          var out = new LinkedHashMap<String, Object>();
          out.put("seq", meta.seq());
          out.put(
              "lines",
              ledger.subList(from, ledger.size()).stream()
                  .map(l -> Map.<String, Object>of("text", l.text(), "loss", l.loss()))
                  .toList());
          var recent =
              summaries.subList(Math.max(0, summaries.size() - expeditions), summaries.size());
          out.put("expedition_count", summaries.size());
          out.put("expeditions", recent.stream().map(Observations::summary).toList());
          return out;
        });
  }

  /** One Expedition Summary: the numbers, and the first few of the fallen by name. */
  static Map<String, Object> summary(RunSummary s) {
    var out = new LinkedHashMap<String, Object>();
    out.put("outcome", s.outcome());
    out.put("start_floor", s.startFloor());
    out.put("highest_floor", s.highestFloor());
    out.put("floors_cleared", s.floorsCleared());
    out.put("enemies_defeated", s.enemiesDefeated());
    out.put("credits", s.creditsGained());
    out.put("materials", s.materialsGained());
    out.put("relics", s.relicsGained());
    out.put("units_lost", s.unitsLost());
    out.put("survivors", s.survivors());
    out.put("fallen_total", s.fallen().size());
    out.put(
        "fallen",
        s.fallen().stream()
            .limit(MAX_FALLEN_LISTED)
            .map(
                f -> Map.<String, Object>of("name", f.name(), "type", f.type(), "floor", f.floor()))
            .toList());
    return out;
  }

  public Map<String, Object> events(Principal principal, String named, long since, int limit) {
    var target = resolve(principal, named);
    var all =
        service
            .eventsSince(target.id(), since)
            .orElseThrow(() -> new ToolFailure("NO_CAMPAIGN", "The Campaign is no longer live."));
    var shown = all.stream().limit(limit).toList();
    var out = new LinkedHashMap<String, Object>();
    out.put("events", shown.stream().map(Observations::event).toList());
    out.put("last_id", shown.isEmpty() ? since : shown.getLast().id());
    out.put("more", all.size() > shown.size());
    return out;
  }

  private static Map<String, Object> event(EventLog.Event e) {
    var out = new LinkedHashMap<String, Object>();
    out.put("id", e.id());
    out.put("seq", e.stateSeq());
    out.put("type", e.type());
    out.put("data", e.data());
    return out;
  }

  /**
   * Waits for a condition. A Sandbox advances itself, tick by tick, until a condition holds or a
   * cap is reached; a human's Campaign is watched, never advanced. One wait at a time per grant.
   */
  public Mono<Map<String, Object>> await(Principal principal, Args args) {
    var target =
        resolve(principal, args.has("campaign") ? args.string("campaign", ID, null) : null);
    var raw = args.objects("conditions", 1, 6);
    var timeoutMs = (int) args.integer("timeout_ms", 100, 20_000, 15_000);
    var maxTicks = (int) args.integer("max_ticks", 1, 3600, 60);

    var guard = waiting.computeIfAbsent(principal.grantId(), _ -> new AtomicBoolean());
    if (!guard.compareAndSet(false, true)) {
      return Mono.error(
          new ToolFailure("WAIT_IN_FLIGHT", "A wait is already running for this grant."));
    }
    var started = System.nanoTime();
    Mono<Map<String, Object>> done;
    try {
      var first = read(target, (state, meta) -> WaitConditions.begin(raw, state, meta.seq()));
      var firstEventId =
          service
              .eventsSince(target.id(), 0)
              .map(e -> e.isEmpty() ? 0L : e.getLast().id())
              .orElse(0L);
      done =
          target.sandbox()
              ? Mono.fromSupplier(
                  () -> advance(principal, target, first, maxTicks, firstEventId, started))
              : watch(target, first, timeoutMs, firstEventId, started);
    } catch (RuntimeException e) {
      guard.set(false);
      throw e;
    }
    return done.doFinally(_ -> guard.set(false));
  }

  private Map<String, Object> advance(
      Principal principal,
      Target target,
      WaitConditions conditions,
      int maxTicks,
      long firstEventId,
      long started) {
    var wanted = Math.min(maxTicks, conditions.tickLimit(maxTicks));
    var met = new String[1];
    Predicate<GameState> until =
        state -> {
          var seq = service.stateSeq(target.id()).orElse(0L);
          var hit = conditions.met(state, seq, 0);
          hit.ifPresent(type -> met[0] = type);
          return hit.isPresent();
        };
    var advance = sandboxes.advance(principal.grantId(), target.id(), wanted, until);
    if (advance.stop() == Stop.NOT_FOUND) {
      throw new ToolFailure("NO_CAMPAIGN", "The Campaign is no longer live.");
    }
    var out = result(target, met[0] == null ? "timeout" : met[0], started, firstEventId);
    out.put("ticks", advance.ticksRun());
    out.put("stop", advance.stop().name());
    out.put("simulated_seconds", advance.simulatedSeconds());
    out.put("simulated_limit", advance.simulatedLimit());
    out.put("note", advance.note());
    return out;
  }

  private Mono<Map<String, Object>> watch(
      Target target, WaitConditions conditions, int timeoutMs, long firstEventId, long started) {
    Supplier<String> check =
        () ->
            service
                .view(target.id(), (state, meta) -> conditions.met(state, meta.seq(), 0))
                .orElse(Optional.of("campaign_closed"))
                .orElse(null);
    return Flux.interval(Duration.ZERO, Duration.ofMillis(POLL_MS), Schedulers.boundedElastic())
        .map(_ -> Optional.ofNullable(check.get()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .next()
        .timeout(Duration.ofMillis(timeoutMs), Mono.just("timeout"))
        .map(type -> result(target, type, started, firstEventId));
  }

  private Map<String, Object> result(Target target, String met, long started, long firstEventId) {
    var out = new LinkedHashMap<String, Object>();
    out.put("met", met);
    out.put("waited_ms", Duration.ofNanos(System.nanoTime() - started).toMillis());
    var events = service.eventsSince(target.id(), firstEventId).orElse(List.of());
    var shown = new ArrayList<Map<String, Object>>();
    events.stream().limit(50).forEach(e -> shown.add(event(e)));
    out.put("events", shown);
    out.put("events_dropped", Math.max(0, events.size() - shown.size()));
    out.put("state", service.view(target.id(), StateViews::brief).orElse(Map.of()));
    return out;
  }

  /** The pattern a Campaign handle must match. */
  public static final java.util.regex.Pattern ID =
      java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
}

package io.github.diogohmcruz.towerdungeon.agent.tools;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import io.github.diogohmcruz.towerdungeon.agent.auth.TokenHasher;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.observe.RuleViews;
import io.github.diogohmcruz.towerdungeon.agent.observe.WaitConditions;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent's tools, defined once as data, and the one place a call to any of them is checked,
 * limited, audited and shielded. The MCP binding asks this class for the list and for every call;
 * nothing in a tool depends on the library that carries it.
 *
 * <p>A call is refused before its handler runs when the grant lacks the scope, an argument is
 * unknown or missing, or the rate limit is spent. A handler that fails never reaches the caller as
 * more than a fixed code and sentence: an unexpected exception becomes {@code INTERNAL_ERROR}.
 */
@AgentEnabled
@Component
@Slf4j
public class ToolCatalogue {
  /** The largest result, in characters (about 6,000 tokens): bigger is a bug in a view. */
  public static final int MAX_RESULT_CHARS = 24_000;

  private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
  private static final Pattern SANDBOX_HANDLE = Pattern.compile("^sandbox-[0-9a-f-]{36}$");
  private static final List<String> VIEWS = List.of("brief", "full");

  private static final JsonMapper COMPACT = JsonMapper.builder().build();
  private static final JsonMapper CANONICAL =
      JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  private final Observations observations;
  private final SandboxRegistry sandboxes;
  private final RateLimiter limiter;
  private final AuditLog audit;
  private final List<ToolDef> tools;
  private final Map<String, ToolDef> byName = new LinkedHashMap<>();

  public ToolCatalogue(
      Observations observations, SandboxRegistry sandboxes, RateLimiter limiter, AuditLog audit) {
    this.observations = observations;
    this.sandboxes = sandboxes;
    this.limiter = limiter;
    this.audit = audit;
    this.tools = definitions().stream().sorted(Comparator.comparing(ToolDef::name)).toList();
    tools.forEach(tool -> byName.put(tool.name(), tool));
  }

  /** The tools, in a deterministic order (by name). */
  public List<ToolDef> tools() {
    return tools;
  }

  // ---------------------------------------------------------------- the manifest

  /**
   * The canonical manifest: what a {@code tools/list} would carry, in one deterministic form.
   * Reviewed by hand whenever it changes (a golden file pins it).
   */
  public String manifestJson() {
    var list = new java.util.ArrayList<Map<String, Object>>();
    for (var tool : tools) {
      var entry = new LinkedHashMap<String, Object>();
      entry.put("name", tool.name());
      entry.put("description", tool.description());
      entry.put("inputSchema", tool.inputSchema());
      var annotations = new TreeMap<String, Object>();
      annotations.put("readOnlyHint", tool.readOnly());
      annotations.put("destructiveHint", tool.destructive());
      annotations.put("idempotentHint", tool.idempotent());
      annotations.put("openWorldHint", false);
      entry.put("annotations", annotations);
      list.add(entry);
    }
    return CANONICAL.writeValueAsString(Map.of("tools", list)) + "\n";
  }

  /** SHA-256 of {@link #manifestJson()}: it changes when any tool, schema or description does. */
  public String manifestHash() {
    return TokenHasher.sha256Hex(manifestJson());
  }

  // ---------------------------------------------------------------- one call

  /** The outcome of one call, for the binding and the audit. */
  public Mono<McpSchema.CallToolResult> call(
      Principal principal, String name, Map<String, Object> arguments) {
    var started = System.nanoTime();
    var tool = name == null ? null : byName.get(name);
    var toolName = tool == null ? "unknown" : tool.name();
    var scope = tool == null ? "-" : tool.scope().label();
    var grant = principal == null ? "-" : principal.grantId();
    return Mono.defer(() -> run(principal, tool, arguments))
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::ok)
        .onErrorResume(e -> Mono.just(failed(toolName, e)))
        .doOnNext(
            result ->
                audit.record(
                    new AuditLog.Entry(
                        grant,
                        toolName,
                        scope,
                        Boolean.TRUE.equals(result.isError()) ? "rejected" : "ok",
                        codeOf(result),
                        seqOf(result),
                        (System.nanoTime() - started) / 1_000_000)));
  }

  private Mono<Map<String, Object>> run(
      Principal principal, ToolDef tool, Map<String, Object> arguments) {
    if (principal == null) {
      return Mono.error(new ToolFailure("UNAUTHENTICATED", "No caller was identified."));
    }
    if (tool == null) {
      return Mono.error(new ToolFailure("UNKNOWN_TOOL", "There is no such tool."));
    }
    if (!principal.has(tool.scope())) {
      return Mono.error(new ToolFailure("FORBIDDEN", "This grant may not use that tool."));
    }
    var given = arguments == null ? Map.<String, Object>of() : arguments;
    var known = Set.copyOf(tool.propertyNames());
    if (!known.containsAll(given.keySet())) {
      return Mono.error(
          new ToolFailure("INVALID_ARGUMENT", "An argument is not known to this tool."));
    }
    for (var required : tool.required()) {
      if (given.get(required) == null) {
        return Mono.error(new ToolFailure("INVALID_ARGUMENT", "A required argument is missing."));
      }
    }
    var verdict =
        limiter.tryAcquire(
            tool.write() ? RateLimiter.Kind.WRITE : RateLimiter.Kind.READ, principal.grantId());
    if (!verdict.allowed()) {
      return Mono.error(
          new ToolFailure(
              "RATE_LIMITED",
              "Too many calls. Wait before the next one.",
              Map.of("retry_after_ms", verdict.retryAfterMs())));
    }
    return tool.handler().handle(new ToolDef.Call(principal, new Args(given)));
  }

  private McpSchema.CallToolResult ok(Map<String, Object> data) {
    var json = COMPACT.writeValueAsString(data);
    if (json.length() > MAX_RESULT_CHARS) {
      log.error("A tool result of {} characters was refused", json.length());
      return failed(
          "result", new ToolFailure("RESULT_TOO_LARGE", "The result is too large to return."));
    }
    return McpSchema.CallToolResult.builder()
        .structuredContent(data)
        .addTextContent(json)
        .isError(false)
        .build();
  }

  private McpSchema.CallToolResult failed(String toolName, Throwable error) {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("status", "rejected");
    if (error instanceof ToolFailure failure) {
      envelope.put("code", failure.code());
      envelope.put("message", failure.getMessage());
      envelope.putAll(failure.extra());
    } else {
      log.error("Tool {} failed unexpectedly", toolName, error);
      envelope.put("code", "INTERNAL_ERROR");
      envelope.put("message", "The tool failed. Nothing was changed by this call.");
    }
    return McpSchema.CallToolResult.builder()
        .structuredContent(envelope)
        .addTextContent(COMPACT.writeValueAsString(envelope))
        .isError(true)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static String codeOf(McpSchema.CallToolResult result) {
    return result.structuredContent() instanceof Map<?, ?> map
            && map.get("code") instanceof String c
        ? c
        : "OK";
  }

  private static Long seqOf(McpSchema.CallToolResult result) {
    return result.structuredContent() instanceof Map<?, ?> map && map.get("seq") instanceof Number n
        ? n.longValue()
        : null;
  }

  // ---------------------------------------------------------------- the definitions

  private List<ToolDef> definitions() {
    return List.of(
        read(
            "td_get_state",
            "Read the Campaign's current state: Vault, Pantry, Villagers, Reserves, and the Party,"
                + " Supplies, Loot and Garrison while an Expedition is out. 'brief' is a few"
                + " hundred tokens; 'full' adds the Roster, Upgrades, Milestones, Shortcuts and"
                + " Trades. Units are counted by type, never listed one by one. Pass since_seq to"
                + " skip an unchanged state.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "view", enumOf(VIEWS, "How much detail. Default brief."),
                    "since_seq",
                        Map.of(
                            "type", "integer",
                            "minimum", 0,
                            "description",
                                "Skip the state if it has not moved past this sequence number.")),
                List.of()),
            call ->
                Mono.just(
                    observations.state(
                        call.principal(),
                        handle(call),
                        call.args().oneOf("view", VIEWS, "brief"),
                        call.args().has("since_seq")
                            ? call.args().integer("since_seq", 0, Long.MAX_VALUE, 0)
                            : null))),
        read(
            "td_get_actions",
            "List what the Campaign would accept now: which Unit Types can be recruited and how"
                + " many, Villager and sale options, affordable Upgrades, and whether an Expedition"
                + " can set out, a Reinforcement Wave can follow or the Party can Extract. Each"
                + " refusal carries a reason code. Reads only.",
            schema(Map.of("campaign", campaign()), List.of()),
            call -> Mono.just(observations.actions(call.principal(), handle(call)))),
        read(
            "td_get_tower",
            "Read the Tower as far as the Party has climbed: highest Floor, Guardian and Surge"
                + " Floors met, Guardians slain, Shortcuts, Milestones, and the Garrison of the"
                + " current Floor during an Expedition. Floors not yet reached are not shown.",
            schema(Map.of("campaign", campaign()), List.of()),
            call -> Mono.just(observations.tower(call.principal(), handle(call)))),
        read(
            "td_get_rules",
            "Read the fixed rules in sections: units, upgrades, shortcuts, milestones, tick. Call"
                + " with no section for the list of sections. A Sandbox caller may also ask for"
                + " 'enemies'. These are the values the player sees; nothing in them is Campaign"
                + " state.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "section",
                        enumOf(
                            List.of(
                                "units",
                                "upgrades",
                                "shortcuts",
                                "milestones",
                                "tick",
                                RuleViews.ENEMIES),
                            "The section to read. Omit for the list.")),
                List.of()),
            call -> {
              var section =
                  call.args()
                      .oneOf(
                          "section",
                          List.of(
                              "units",
                              "upgrades",
                              "shortcuts",
                              "milestones",
                              "tick",
                              RuleViews.ENEMIES),
                          null);
              var rules = observations.rules(call.principal(), handle(call), section);
              if (section == null) {
                rules = new LinkedHashMap<>(rules);
                rules.put("manifest_hash", manifestHash());
              }
              return Mono.just(rules);
            }),
        read(
            "td_get_ledger",
            "Read the Village's Ledger: its latest plain lines, and the latest Expedition Summaries"
                + " with the fallen named. Losses are written as plainly as gains.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "lines", bounded("How many Ledger lines, newest last. Default 20.", 1, 50),
                    "expeditions", bounded("How many Expedition Summaries. Default 5.", 1, 20)),
                List.of()),
            call ->
                Mono.just(
                    observations.ledger(
                        call.principal(),
                        handle(call),
                        (int) call.args().integer("lines", 1, 50, 20),
                        (int) call.args().integer("expeditions", 1, 20, 5)))),
        read(
            "td_get_events",
            "Read the event feed of the Campaign after an event id: recruits, sales, Floors"
                + " reached, Enemies slain, Expeditions ended. Oldest first. Use last_id as the"
                + " next 'since'.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "since", bounded("Only events after this id. Default 0.", 0, Long.MAX_VALUE),
                    "limit", bounded("The most events in one answer. Default 100.", 1, 200)),
                List.of()),
            call ->
                Mono.just(
                    observations.events(
                        call.principal(),
                        handle(call),
                        call.args().integer("since", 0, Long.MAX_VALUE, 0),
                        (int) call.args().integer("limit", 1, 200, 100)))),
        new ToolDef(
            "td_wait",
            "Wait for something to change. In a Sandbox this advances time, one simulated second"
                + " per tick, until a condition holds or max_ticks pass. On a live Campaign it only"
                + " watches, up to timeout_ms. Conditions: floor_changed, unit_lost,"
                + " expedition_ended, supplies_below, party_health_fraction_below, ticks. One wait"
                + " at a time.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "conditions",
                        Map.of(
                            "type",
                            "array",
                            "minItems",
                            1,
                            "maxItems",
                            6,
                            "items",
                            Map.of(
                                "type",
                                "object",
                                "additionalProperties",
                                false,
                                "required",
                                List.of("type"),
                                "properties",
                                Map.of(
                                    "type", Map.of("enum", WaitConditions.TYPES),
                                    "value", Map.of("type", "number")))),
                    "timeout_ms", bounded("Live Campaign only. Default 15000.", 100, 20_000),
                    "max_ticks", bounded("Sandbox only. Default 60.", 1, 3600)),
                List.of("conditions")),
            false,
            false,
            false,
            Scope.OBSERVE,
            false,
            call -> observations.await(call.principal(), call.args())),
        new ToolDef(
            "td_sandbox_new",
            "Open a Sandbox Campaign: a headless Campaign on a manual clock that advances only"
                + " through td_wait, with its own copy of the rules. Pass a seed to repeat an"
                + " earlier one; the seed in use is returned. The number open per grant and in all"
                + " is capped, and so is its simulated time. Returns its handle for the 'campaign'"
                + " argument.",
            schema(
                Map.of(
                    "seed",
                    Map.of(
                        "type", "integer",
                        "description", "The Sandbox's seed. Omit for a fresh one."),
                    "max_simulated_seconds",
                    bounded(
                        "A lower ceiling on its simulated time. Default the server's.", 1, 21_600)),
                List.of()),
            false,
            false,
            false,
            Scope.SANDBOX,
            true,
            call -> Mono.just(openSandbox(call))),
        new ToolDef(
            "td_sandbox_close",
            "Close a Sandbox Campaign you opened and discard it. This cannot be undone.",
            schema(
                Map.of(
                    "campaign",
                    Map.of(
                        "type", "string",
                        "pattern", SANDBOX_HANDLE.pattern(),
                        "description", "The Sandbox handle returned by td_sandbox_new.")),
                List.of("campaign")),
            false,
            true,
            true,
            Scope.SANDBOX,
            true,
            call -> Mono.just(closeSandbox(call))));
  }

  private Map<String, Object> openSandbox(ToolDef.Call call) {
    var seconds =
        call.args().has("max_simulated_seconds")
            ? call.args().integer("max_simulated_seconds", 1, 21_600, 21_600)
            : null;
    var seed =
        call.args().has("seed")
            ? call.args().integer("seed", Long.MIN_VALUE, Long.MAX_VALUE, 0)
            : null;
    var opened = sandboxes.open(call.principal().grantId(), null, seconds, seed);
    if (opened.status() != Status.OPENED) {
      throw new ToolFailure(opened.status().name(), opened.note());
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("campaign", opened.sandboxId());
    out.put("clock", "manual");
    // A Sandbox's seed is public: it is how a run is repeated. A human Campaign's never is.
    out.put("seed", opened.seed());
    out.put("max_simulated_seconds", opened.maxSimulatedSeconds());
    out.put("note", opened.note());
    return out;
  }

  private Map<String, Object> closeSandbox(ToolDef.Call call) {
    var id = call.args().string("campaign", SANDBOX_HANDLE, null);
    if (!sandboxes.close(call.principal().grantId(), id)) {
      throw new ToolFailure("NO_CAMPAIGN", "There is no such Sandbox for this grant.");
    }
    return Map.of("closed", true);
  }

  // ---------------------------------------------------------------- small builders

  private static ToolDef read(
      String name, String description, Map<String, Object> schema, ToolDef.Handler handler) {
    return new ToolDef(name, description, schema, true, false, true, Scope.OBSERVE, false, handler);
  }

  private static String handle(ToolDef.Call call) {
    return call.args().has("campaign") ? call.args().string("campaign", HANDLE, null) : null;
  }

  private static Map<String, Object> campaign() {
    return Map.of(
        "type", "string",
        "pattern", HANDLE.pattern(),
        "description", "A Sandbox handle. Omit to use your only open Sandbox.");
  }

  private static Map<String, Object> enumOf(List<String> values, String description) {
    return Map.of("type", "string", "enum", values, "description", description);
  }

  private static Map<String, Object> bounded(String description, long min, long max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max, "description", description);
  }

  private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    return schema;
  }
}

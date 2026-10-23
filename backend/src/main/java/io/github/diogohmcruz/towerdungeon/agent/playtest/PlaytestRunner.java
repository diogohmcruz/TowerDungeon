package io.github.diogohmcruz.towerdungeon.agent.playtest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.act.Actions;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import io.github.diogohmcruz.towerdungeon.agent.telemetry.RowRecorder;
import io.github.diogohmcruz.towerdungeon.agent.telemetry.TelemetrySink;
import io.github.diogohmcruz.towerdungeon.agent.tools.Args;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver.Stop;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;

/**
 * Runs {@link Policy scripted players} through the same action tools an agent calls ({@link
 * Actions}: one {@code request_id} per call, the same envelope, the same refusals), in the same
 * process and without MCP, on Sandbox Campaigns the manual driver ticks. Each run is seeded, so the
 * same seed and policy make the same rows (design 5.7).
 *
 * <p>What it reads of the game it reads through {@link GameService#view}, as the observation tools
 * do, and shows a policy only a {@link Sight}. The Sandbox caps still bind it: the simulated-time
 * ceiling and the per-grant and global counts. It is one grant, one Sandbox at a time.
 */
@AgentEnabled
@Component
public class PlaytestRunner {
  /** The grant the runner acts as. */
  public static final String GRANT = "playtest";

  private static final int MAX_MOVES = 400_000;
  private static final int MAX_FRUITLESS = 40;

  /** How one Campaign went, for the console and for tests; the rows are the evidence. */
  public record CampaignResult(
      int index,
      long seed,
      int expeditions,
      int highestFloor,
      long simulatedSeconds,
      String outcome,
      String stop,
      int moves,
      int refused) {}

  /** A finished batch. */
  public record BatchResult(String batchId, String policy, List<CampaignResult> campaigns) {}

  private final Actions actions;
  private final SandboxRegistry registry;
  private final GameService service;
  private final TelemetrySink sink;
  private final java.util.concurrent.atomic.AtomicLong runs =
      new java.util.concurrent.atomic.AtomicLong();

  public PlaytestRunner(
      Actions actions, SandboxRegistry registry, GameService service, TelemetrySink sink) {
    this.actions = actions;
    this.registry = registry;
    this.service = service;
    this.sink = sink;
  }

  /**
   * Plays {@code campaigns} Campaigns of one policy into a new telemetry batch. Campaign {@code i}
   * runs on {@link #seedFor seedFor(baseSeed, i)}. {@code workers} Campaigns run at once, each
   * under a grant of its own, and their rows are written in Campaign order whatever the order they
   * finish in, so the file is the same for any number of workers.
   */
  public BatchResult runBatch(
      Policy policy, int campaigns, long baseSeed, int maxExpeditions, int workers) {
    var results = new CampaignResult[campaigns];
    var pending = new java.util.TreeMap<Integer, List<Map<String, Object>>>();
    var next = new int[1];
    try (var batch = sink.open();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(Math.max(1, workers))) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      var claim = new java.util.concurrent.atomic.AtomicInteger();
      for (var worker = 0; worker < Math.max(1, workers); worker++) {
        var grant = workers <= 1 ? GRANT : GRANT + "-" + worker;
        futures.add(
            pool.submit(
                () -> {
                  for (var index = claim.getAndIncrement();
                      index < campaigns;
                      index = claim.getAndIncrement()) {
                    var rows = new ArrayList<Map<String, Object>>();
                    var result =
                        runCampaign(
                            grant,
                            policy,
                            index,
                            seedFor(baseSeed, index),
                            maxExpeditions,
                            rows::add);
                    synchronized (pending) {
                      results[index] = result;
                      pending.put(index, rows);
                      while (pending.containsKey(next[0])) {
                        pending.remove(next[0]).forEach(batch::append);
                        next[0]++;
                      }
                    }
                  }
                }));
      }
      for (var future : futures) {
        try {
          future.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
          throw new IllegalStateException("A playtest worker failed", e.getCause());
        }
      }
      batch.flush();
      return new BatchResult(batch.id(), policy.name(), List.of(results));
    }
  }

  public BatchResult runBatch(Policy policy, int campaigns, long baseSeed, int maxExpeditions) {
    return runBatch(policy, campaigns, baseSeed, maxExpeditions, 1);
  }

  /** The seed of the {@code index}th Campaign of a batch: a mix, so near seeds are far apart. */
  public static long seedFor(long baseSeed, int index) {
    var z = baseSeed + (index + 1) * 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  /** Plays one Campaign on {@code seed}, sending its rows to {@code rows}. */
  public CampaignResult runCampaign(
      Policy policy, int index, long seed, int maxExpeditions, Consumer<Map<String, Object>> rows) {
    return runCampaign(GRANT, policy, index, seed, maxExpeditions, rows);
  }

  /** As above, as the named grant, so Campaigns on several threads do not share one. */
  public CampaignResult runCampaign(
      String grant,
      Policy policy,
      int index,
      long seed,
      int maxExpeditions,
      Consumer<Map<String, Object>> rows) {
    var who = new Principal(grant, EnumSet.allOf(Scope.class));
    // A request id is never reused, so a repeat of a seeded run acts again instead of replaying.
    var run = runs.incrementAndGet();
    var opened = registry.open(grant, null, null, seed);
    if (opened.status() != Status.OPENED) {
      throw new IllegalStateException("No Sandbox for the playtest: " + opened.status());
    }
    var id = opened.sandboxId();
    var recorder = new RowRecorder(rows, policy.name(), index);
    registry.watch(grant, id, recorder);
    var dice = new SplittableRandom(seed ^ 0x5DEECE66DL);
    var expeditions = 0;
    var moves = 0;
    var refused = 0;
    var fruitless = 0;
    var suppliesAtDeparture = 0d;
    var homeTicks = 0;
    var stop = "MAX_MOVES";
    try {
      while (moves < MAX_MOVES) {
        final var departure = suppliesAtDeparture;
        final var home = homeTicks;
        final var started = expeditions;
        var sight =
            service
                .view(id, (state, meta) -> Sight.of(state, departure, started, home))
                .orElseThrow(() -> new IllegalStateException("The Sandbox is gone"));
        if (sight.ended()) {
          stop = "ENDED";
          break;
        }
        if (!sight.expedition() && expeditions >= maxExpeditions) {
          stop = "MAX_EXPEDITIONS";
          break;
        }
        var move = policy.next(sight, dice);
        moves++;
        var progressed = true;
        switch (move) {
          case Move.Wait wait -> {
            var advance = registry.advance(grant, id, wait.ticks(), null);
            if (!sight.expedition()) {
              homeTicks += advance.ticksRun();
            }
            progressed = advance.ticksRun() > 0;
            if (advance.stop() == Stop.SIMULATED_TIME_CAP) {
              stop = "SIMULATED_TIME_CAP";
            }
          }
          case Move.StartExpedition start -> {
            progressed =
                applied(
                    call(
                        who,
                        run,
                        id,
                        index,
                        moves,
                        "td_start_expedition",
                        Map.of("party", "all_reserves", "start_floor", start.startFloor())));
            if (progressed) {
              expeditions++;
              homeTicks = 0;
              suppliesAtDeparture =
                  service.view(id, (state, meta) -> state.getSupplies()).orElse(0d);
            }
          }
          case Move.BuyUpgrade buy ->
              progressed =
                  applied(
                      call(
                          who,
                          run,
                          id,
                          index,
                          moves,
                          "td_buy_upgrade",
                          Map.of("upgrade_id", buy.id())));
          case Move.Extract ignored ->
              progressed = applied(call(who, run, id, index, moves, "td_extract", Map.of()));
          case Move.Recruit recruit ->
              progressed =
                  applied(
                      call(
                          who,
                          run,
                          id,
                          index,
                          moves,
                          "td_recruit",
                          Map.of(
                              "unit_type", recruit.type().name(), "quantity", recruit.quantity())));
          case Move.SellFood sell ->
              progressed =
                  applied(
                      call(
                          who,
                          run,
                          id,
                          index,
                          moves,
                          "td_sell",
                          Map.of("good", "FOOD", "amount", sell.amount())));
        }
        if ("SIMULATED_TIME_CAP".equals(stop)) {
          break;
        }
        if (progressed) {
          fruitless = 0;
        } else {
          refused++;
          if (++fruitless >= MAX_FRUITLESS) {
            stop = "STALLED";
            break;
          }
        }
      }
      final var why = stop;
      var highest = new int[1];
      var outcome = new String[1];
      service.view(
          id,
          (state, meta) -> {
            recorder.finish(state, why);
            highest[0] = state.getHighestFloor();
            outcome[0] = state.getGameOutcome().name();
            return Boolean.TRUE;
          });
      var seconds = registry.advance(grant, id, 0, null).simulatedSeconds();
      return new CampaignResult(
          index, seed, expeditions, highest[0], seconds, outcome[0], stop, moves, refused);
    } finally {
      registry.watch(grant, id, null);
      registry.close(grant, id);
    }
  }

  private Map<String, Object> call(
      Principal who,
      long run,
      String campaign,
      int index,
      int move,
      String tool,
      Map<String, Object> own) {
    var args = new HashMap<String, Object>(own);
    args.put("request_id", "pt-%d-%d-%d".formatted(run, index, move));
    args.put("campaign", campaign);
    var in = new Args(args);
    return switch (tool) {
      case "td_recruit" -> actions.recruit(who, in);
      case "td_sell" -> actions.sell(who, in);
      case "td_buy_upgrade" -> actions.buyUpgrade(who, in);
      case "td_start_expedition" -> actions.startExpedition(who, in);
      case "td_extract" -> actions.extract(who, in);
      default -> throw new IllegalArgumentException(tool);
    };
  }

  private static boolean applied(Map<String, Object> answer) {
    var status = answer.get("status");
    return "applied".equals(status) || "partial".equals(status);
  }
}

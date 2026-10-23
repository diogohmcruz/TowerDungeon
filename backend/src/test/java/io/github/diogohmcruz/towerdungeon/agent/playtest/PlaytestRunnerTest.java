package io.github.diogohmcruz.towerdungeon.agent.playtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.agent.telemetry.RowRecorder;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;
import io.github.diogohmcruz.towerdungeon.domain.trial.ConfigHash;

/**
 * The scripted bots and the rows they leave: the same seed makes the same rows, the three policies
 * run by the thousand inside the Sandbox caps, village time never reaches a Floor row, and a row
 * adds up with the Expedition it belongs to.
 */
class PlaytestRunnerTest {
  private PlaytestFixture fx;

  @BeforeEach
  void setUp() throws IOException {
    fx = new PlaytestFixture(Files.createTempDirectory("td-playtest"));
  }

  private List<Map<String, Object>> play(Policy policy, int index, long seed, int expeditions) {
    var rows = new ArrayList<Map<String, Object>>();
    fx.runner.runCampaign(policy, index, seed, expeditions, rows::add);
    return rows;
  }

  private static List<Map<String, Object>> of(List<Map<String, Object>> rows, String kind) {
    return rows.stream().filter(r -> kind.equals(r.get("row"))).toList();
  }

  private static double number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).doubleValue();
  }

  // ---------------------------------------------------------------- the rows

  @Test
  void everyRowCarriesTheEngineTheConfigHashTheSeedAndThePolicy() {
    var rows = play(Policies.GREEDY, 3, 4242L, 8);

    assertThat(rows).isNotEmpty();
    var hash = ConfigHash.of(fx.config);
    for (var row : rows) {
      assertThat(row).containsEntry("engine", "td-1");
      assertThat(row).containsEntry("config_hash", hash);
      assertThat(row).containsEntry("seed", 4242L);
      assertThat(row).containsEntry("policy", "greedy");
      assertThat(row).containsEntry("campaign_index", 3);
      assertThat(row).containsKey("tick_model");
    }
    assertThat(of(rows, "campaign")).hasSize(1);
    assertThat(of(rows, "expedition")).hasSize(8);
    assertThat(of(rows, "floor")).isNotEmpty();
  }

  @Test
  void theSameSeedAndPolicyMakeIdenticalRowsInTheSameFixtureAndInAFreshOne() throws IOException {
    for (var policy : Policies.all().values()) {
      var first = play(policy, 0, 99L, 12);
      var again = play(policy, 0, 99L, 12);
      var fresh = new PlaytestFixture(Files.createTempDirectory("td-playtest-2"));
      var elsewhere = new ArrayList<Map<String, Object>>();
      fresh.runner.runCampaign(policy, 0, 99L, 12, elsewhere::add);

      assertThat(again).as(policy.name()).isEqualTo(first);
      assertThat(elsewhere).as(policy.name() + " in a fresh service").isEqualTo(first);
    }
  }

  @Test
  void differentSeedsMakeDifferentCampaigns() {
    var a = play(Policies.GREEDY, 0, 1L, 12);
    var b = play(Policies.GREEDY, 0, 2L, 12);

    assertThat(of(a, "floor")).isNotEqualTo(of(b, "floor"));
  }

  @Test
  void aFloorRowAddsUpWithItsExpedition() {
    for (var policy : Policies.all().values()) {
      var rows = play(policy, 0, 7L, 40);
      var floors = of(rows, "floor");
      for (var expedition : of(rows, "expedition")) {
        var index = expedition.get("expedition_index");
        var mine = floors.stream().filter(r -> index.equals(r.get("expedition_index"))).toList();
        var onFloors = mine.stream().mapToDouble(r -> number(r, "ticks_on_floor")).sum();
        var lost = mine.stream().mapToInt(r -> ((List<?>) r.get("units_lost")).size()).sum();

        // Floor 0 is the way to the foot of the Tower, and has its row: nothing is left out.
        assertThat(onFloors)
            .as("%s expedition %s".formatted(policy.name(), index))
            .isEqualTo(number(expedition, "total_ticks"));
        assertThat(lost)
            .as("units lost, %s expedition %s".formatted(policy.name(), index))
            .isEqualTo(((Number) expedition.get("units_lost")).intValue());
      }
    }
  }

  @Test
  void aPartysHealthFallsByWhatItTookLessWhatItRecoveredWhenNobodyDied() {
    var checked = 0;
    for (var seed = 1L; seed <= 6; seed++) {
      for (var row : of(play(Policies.GREEDY, 0, seed, 30), "floor")) {
        if (!((List<?>) row.get("units_lost")).isEmpty()) {
          continue;
        }
        var taken =
            number(row, "damage_taken_combat")
                + number(row, "damage_taken_attrition")
                + number(row, "damage_taken_starvation");
        var fell = number(row, "party_health_start") - number(row, "party_health_end");
        var recovered = number(row, "party_health_recovered");

        assertThat(recovered).isGreaterThanOrEqualTo(0);
        assertThat(fell)
            .as("floor %s".formatted(row.get("floor")))
            .isCloseTo(taken - recovered, within(0.01));
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(20);
  }

  @Test
  void theUnitDepthColumnsExistOnEveryFloorRow() {
    for (var row : of(play(Policies.GREEDY, 0, 5L, 10), "floor")) {
      assertThat(row).containsKeys("parts_lost", "items_found", "surplus_items", "sanity_end");
      assertThat(number(row, "sanity_end")).isBetween(0.0, 100.0);
    }
  }

  // ---------------------------------------------------------------- idle farming

  @Test
  void timeAtHomeIsVillageTimeAndNeverReachesAFloorRow() {
    var rows = new ArrayList<Map<String, Object>>();
    var opened = fx.registry.open(PlaytestRunner.GRANT, null, null, 11L);
    assertThat(opened.status()).isEqualTo(Status.OPENED);
    var id = opened.sandboxId();
    var recorder = new RowRecorder(rows::add, "idle", 0);
    fx.registry.watch(PlaytestRunner.GRANT, id, recorder);

    fx.registry.advance(PlaytestRunner.GRANT, id, 600, null);
    fx.service.view(
        id,
        (state, meta) -> {
          recorder.finish(state, "TEST");
          return true;
        });

    assertThat(of(rows, "floor")).isEmpty();
    var campaign = of(rows, "campaign").getFirst();
    assertThat(campaign.get("village_ticks")).isEqualTo(600);
    assertThat(campaign.get("expedition_ticks")).isEqualTo(0);
    assertThat(number(campaign, "village_food_grown")).isPositive();
    assertThat(number(campaign, "village_food_per_tick")).isPositive();
  }

  @Test
  void everyTickIsEitherVillageOrExpeditionAndTheTwoAddUp() {
    for (var policy : Policies.all().values()) {
      var rows = play(policy, 0, 21L, 25);
      var campaign = of(rows, "campaign").getFirst();
      var expeditionTicks =
          of(rows, "expedition").stream().mapToDouble(r -> number(r, "total_ticks")).sum();

      assertThat(number(campaign, "village_ticks") + number(campaign, "expedition_ticks"))
          .as(policy.name())
          .isEqualTo(number(campaign, "simulated_seconds"));
      assertThat(expeditionTicks).as(policy.name()).isEqualTo(number(campaign, "expedition_ticks"));
    }
  }

  // ---------------------------------------------------------------- watching changes nothing

  @Test
  void aWatchedSandboxRunsExactlyAsAnUnwatchedOne() {
    var watched = fx.registry.open(PlaytestRunner.GRANT, null, null, 31L).sandboxId();
    var plain = fx.registry.open(PlaytestRunner.GRANT, null, null, 31L).sandboxId();
    fx.registry.watch(PlaytestRunner.GRANT, watched, new RowRecorder(r -> {}, "watch", 0));

    for (var id : List.of(watched, plain)) {
      fx.service.execute(id, new Command.StartExpedition(new EnumMap<>(UnitStats.class), 0));
      fx.registry.advance(PlaytestRunner.GRANT, id, 60, null);
    }

    assertThat(fx.service.playerState(watched).toSnapshot())
        .isEqualTo(fx.service.playerState(plain).toSnapshot());
  }

  // ---------------------------------------------------------------- volume, caps, workers

  @Test
  void thousandsOfCampaignsRunOnTheManualDriverInsideTheSandboxCaps() {
    var cap = fx.config.getAgent().getSandbox().getMaxSimulatedSeconds();
    var seen = 0;
    for (var policy : Policies.all().values()) {
      var batch = fx.runner.runBatch(policy, 1000, 5L, 2);
      assertThat(batch.campaigns()).hasSize(1000);
      for (var campaign : batch.campaigns()) {
        assertThat(campaign.simulatedSeconds()).isLessThanOrEqualTo(cap);
        assertThat(campaign.stop())
            .isIn("MAX_EXPEDITIONS", "ENDED", "SIMULATED_TIME_CAP", "STALLED");
        seen++;
      }
    }
    assertThat(seen).isEqualTo(1000 * Policies.all().size());
    assertThat(fx.registry.live()).as("every Sandbox was closed").isZero();
  }

  @Test
  void theNumberOfWorkersChangesNothingInTheFile() {
    var one = fx.runner.runBatch(Policies.RANDOM, 24, 3L, 10, 1);
    var three = fx.runner.runBatch(Policies.RANDOM, 24, 3L, 10, 3);

    assertThat(fx.sink.readAll(three.batchId(), null))
        .isEqualTo(fx.sink.readAll(one.batchId(), null));
    assertThat(three.campaigns()).isEqualTo(one.campaigns());
  }

  @Test
  void aBatchIdIsTheServersAndTheFileHoldsOnlyThatBatch() {
    var batch = fx.runner.runBatch(Policies.CAUTIOUS, 3, 1L, 4);

    assertThat(batch.batchId()).matches("^[a-z0-9-]{8,40}$");
    assertThat(fx.sink.readAll(batch.batchId(), "campaign")).hasSize(3);
    assertThat(fx.sink.readAll(batch.batchId(), "expedition")).hasSize(12);
  }
}

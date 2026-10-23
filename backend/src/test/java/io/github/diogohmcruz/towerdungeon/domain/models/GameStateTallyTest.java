package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.prestige.Tally;

/** The Tally counters and the Village Tiers they open. */
class GameStateTallyTest {
  private static GameState state() {
    return new GameState(config());
  }

  private static GameProperties config() {
    var config = new GameProperties();
    config.getVillage().setStartingFood(100);
    config.getVillage().setStartingVillagers(20);
    return config;
  }

  private static List<String> lines(GameState state) {
    return state.getLedger().stream().map(GameState.LedgerLine::text).toList();
  }

  private static Tower towerAtFloor(int floor) {
    var config = new GameProperties();
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    RandomGenerator random = new SplittableRandom(1);
    var tower = new Tower(100, config.getBoss(), config.getEnemies(), random, plan);
    tower.startNewRunAt(floor);
    return tower;
  }

  @Test
  void aNewCampaignHasAnEmptyTallyAtTierOne() {
    var state = state();

    assertThat(state.getTally().getFoodGrown()).isZero();
    assertThat(state.getTally().getPeakVillagers()).isEqualTo(20);
    assertThat(state.getVillageTier()).isEqualTo(VillageTier.HUDDLED);
    assertThat(state.getLedger()).isEmpty();
  }

  @Test
  void foodGrownCountsWhatTheTillersGrowEachTickAndSpendingNeverLowersIt() {
    var state = state();
    state.setFoodDistributionStrategy((_, _, _) -> 0d);

    state.triggerLifecycle();
    state.triggerLifecycle();

    assertThat(state.getTally().getFoodGrown()).isEqualTo(2 * 20 * 0.5);
    state.getVillage().takeFood(1000);
    assertThat(state.getTally().getFoodGrown()).isEqualTo(20);
  }

  @Test
  void sellingFoodNeverRaisesCreditsBanked() {
    var state = state();
    state.getVillage().addFood(1000);

    state.sellFood();

    assertThat(state.getTally().getCreditsBanked()).isZero();
  }

  @Test
  void lootCountsOnlyAtExtractionAndAWipeAddsNothing() {
    var state = state();
    state.gatherLoot(ResourceType.MATERIALS, 40d);
    state.gatherLoot(ResourceType.RELICS, 3d);
    state.gatherCredits(25d);
    state.forfeitLoot();
    assertThat(state.getTally().getMaterialsBanked()).isZero();
    assertThat(state.getTally().getRelicsBanked()).isZero();
    assertThat(state.getTally().getCreditsBanked()).isZero();

    state.gatherLoot(ResourceType.MATERIALS, 40d);
    state.gatherLoot(ResourceType.RELICS, 3d);
    state.gatherCredits(25d);
    state.bankLoot();

    assertThat(state.getTally().getMaterialsBanked()).isEqualTo(40);
    assertThat(state.getTally().getRelicsBanked()).isEqualTo(3);
    assertThat(state.getTally().getCreditsBanked()).isEqualTo(25);
  }

  @Test
  void spendingBankedLootNeverLowersTheTally() {
    var state = state();
    state.gatherLoot(ResourceType.MATERIALS, 40d);
    state.bankLoot();

    state.sellAllMaterials();

    assertThat(state.getTally().getMaterialsBanked()).isEqualTo(40);
  }

  @Test
  void theQuarryHeapCarriedHomeCountsAsMaterialsBanked() {
    var state = state();
    state.getVillage().restoreTrades(5, 0, java.util.Map.of(), 0, 100, false, 0, 0);
    state.setLastRunSummary(new RunSummary(RunSummary.EXTRACTED, 0, 3, 3, 0, 0, 0, 0, 0, 1));

    state.deliverQuarryHeap();

    assertThat(state.getTally().getMaterialsBanked()).isEqualTo(15);
  }

  @Test
  void floorsClearedCountAtExtractionButNotOnAWipe() {
    var state = state();
    state.setRunStartFloor(0);
    state.setTower(towerAtFloor(4));
    state.buildRunSummary(RunSummary.WIPED);
    assertThat(state.getTally().getFloorsCleared()).isZero();

    state.buildRunSummary(RunSummary.EXTRACTED);
    assertThat(state.getTally().getFloorsCleared()).isEqualTo(4);
  }

  @Test
  void peakVillagersKeepsTheHighWaterMarkThroughStarvation() {
    var state = state();
    state.setCredit(25d);
    state.buyVillager();
    var peak = state.getVillage().getVillagersCount();
    assertThat(peak).isGreaterThan(20);
    assertThat(state.getTally().getPeakVillagers()).isEqualTo(peak);

    state.getVillage().restore(0, 3);
    state.triggerLifecycle();

    assertThat(state.getTally().getPeakVillagers()).isEqualTo(peak);
  }

  @Test
  void tierTwoNeedsFoodGrownAndHighestFloorAndWritesOneLine() {
    var state = state();
    state.getTally().addFoodGrown(1000);
    state.recordFloorReached(4);
    assertThat(state.getVillageTier()).isEqualTo(VillageTier.HUDDLED);

    state.recordFloorReached(5);
    state.recordFloorReached(6);
    state.recordFloorReached(7);

    assertThat(state.getVillageTier()).isEqualTo(VillageTier.SHELTERED);
    assertThat(lines(state))
        .containsExactly(
            "Village Tier II, Sheltered. The Parliament lets the Salt Pans be worked.");
  }

  @Test
  void tierThreeNeedsFloorMaterialsAndSixExpeditions() {
    var state = state();
    state.getTally().addFoodGrown(1000);
    state.recordFloorReached(10);
    state.getTally().addMaterialsBanked(1000);
    for (var i = 0; i < 5; i++) {
      state.completeExpedition();
    }
    assertThat(state.getVillageTier()).isEqualTo(VillageTier.SHELTERED);

    state.completeExpedition();

    assertThat(state.getVillageTier()).isEqualTo(VillageTier.FORTIFIED);
    assertThat(lines(state))
        .containsExactly(
            "Village Tier II, Sheltered. The Parliament lets the Salt Pans be worked.",
            "Village Tier III, Fortified. The cellar below the Base is opened.");
  }

  @Test
  void tierFourNeedsFloorTwentyFiveAndSixtyRelicsAndOpensNothingYet() {
    var state = state();
    state.getTally().addFoodGrown(1000);
    state.getTally().addMaterialsBanked(1000);
    state.getTally().addRelicsBanked(60);
    state.recordFloorReached(25);
    for (var i = 0; i < 6; i++) {
      state.completeExpedition();
    }

    assertThat(state.getVillageTier()).isEqualTo(VillageTier.ENTRENCHED);
    assertThat(lines(state)).hasSize(3);
    assertThat(lines(state).getLast())
        .isEqualTo("Village Tier IV, Entrenched. The Alliance trusts you with the old stairs.");
  }

  @Test
  void thresholdsComeFromConfig() {
    var config = config();
    config.getTiers().getSheltered().setFoodGrown(10);
    config.getTiers().getSheltered().setHighestFloor(1);
    var state = new GameState(config);
    state.getTally().addFoodGrown(10);

    state.recordFloorReached(1);

    assertThat(state.getVillageTier()).isEqualTo(VillageTier.SHELTERED);
  }

  @Test
  void aTierIsNeverLostOnceReached() {
    var config = config();
    var state = new GameState(config);
    state.getTally().addFoodGrown(1000);
    state.recordFloorReached(5);
    config.getTiers().getSheltered().setHighestFloor(50);

    state.recordFloorReached(6);

    assertThat(state.getVillageTier()).isEqualTo(VillageTier.SHELTERED);
  }

  @Test
  void theTollTallyIsBuiltFromTheLiveCounters() {
    var state = state();
    state.getTally().addFoodGrown(7);
    state.getTally().addMaterialsBanked(8);
    state.getTally().addCreditsBanked(9);
    state.getTally().addRelicsBanked(10);
    state.recordFloorReached(12);
    state.completeExpedition();
    state.completeExpedition();

    var tally = state.tollTally(3);

    assertThat(tally).isEqualTo(new Tally(7, 8, 9, 10, 2, 12, 3));
  }
}

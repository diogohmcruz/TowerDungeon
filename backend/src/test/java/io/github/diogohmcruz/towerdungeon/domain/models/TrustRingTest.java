package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import tools.jackson.databind.json.JsonMapper;

/** Trust earned on Extraction, the Thought-Binding Ring it buys and Survivors' Notes. */
class TrustRingTest {
  private final GameProperties config = new GameProperties();
  private final RandomGenerator random = new SplittableRandom(3);

  private GameState state() {
    config.getVillage().setStartingFood(1000);
    return new GameState(config);
  }

  private Tower towerAtFloor(int floor) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var tower = new Tower(100, config.getBoss(), config.getEnemies(), new RandomSource(3), plan);
    tower.startNewRunAt(floor);
    return tower;
  }

  /** A state whose Party of {@code size} Warriors stands on {@code floor}, set out from floor 0. */
  private GameState expeditionOnFloor(int floor, int size) {
    var state = state();
    state.setTower(towerAtFloor(floor));
    state.setRunStartFloor(0);
    state.addUnits(
        UnitStats.WARRIOR,
        java.util.stream.IntStream.range(0, size)
            .mapToObj(_ -> state.recruit(UnitStats.WARRIOR, random))
            .toList());
    for (var i = 0; i < size; i++) {
      state.enlistRandom(UnitStats.WARRIOR);
    }
    return state;
  }

  @Test
  void aWholePartyExtractingEarnsOneTrustPerFloorCleared() {
    var state = expeditionOnFloor(10, 4);

    state.buildRunSummary(RunSummary.EXTRACTED);

    assertThat(state.getVillage().getTrust()).isEqualTo(10);
  }

  @Test
  void eachDeathInThePartyLowersTheTrustEarned() {
    var state = expeditionOnFloor(10, 3);
    state.setRunUnitsLost(1);

    state.buildRunSummary(RunSummary.EXTRACTED);

    assertThat(state.getVillage().getTrust()).isCloseTo(10d * 3 / 4, within(1e-9));
  }

  @Test
  void aWipeEarnsNoTrustAndTakesNoneAway() {
    var state = expeditionOnFloor(10, 4);
    state.getVillage().restoreTrust(12d, 2);

    state.buildRunSummary(RunSummary.WIPED);

    assertThat(state.getVillage().getTrust()).isEqualTo(12d);
    assertThat(state.getVillage().getRingLevel()).isEqualTo(2);
  }

  @Test
  void trustIsNotSellable() {
    var state = state();
    state.getVillage().restoreTrust(50d, 0);

    state.sellAllMaterials();
    state.sellAllRelics();

    assertThat(state.getVillage().getTrust()).isEqualTo(50d);
  }

  @Test
  void theRingCostsTenTrustAndGrowsByHalfEachLevelUpToFive() {
    var village = new Village(new GameProperties.Village(), new GameProperties.Trades());
    var costs = new java.util.ArrayList<Long>();
    for (var level = 0; level < 5; level++) {
      village.restoreTrust(0d, level);
      costs.add(village.ringCost());
    }

    assertThat(costs).containsExactly(10L, 15L, 23L, 34L, 51L);
    assertThat(costs.stream().mapToLong(Long::longValue).sum()).isBetween(130L, 135L);
  }

  @Test
  void buyingARingLevelSpendsTrustAndTheRingStopsAtLevelFive() {
    var state = state();
    state.getVillage().restoreTrust(500d, 0);

    for (var level = 1; level <= 5; level++) {
      assertThat(state.buyRing()).isEmpty();
      assertThat(state.getVillage().getRingLevel()).isEqualTo(level);
    }

    assertThat(state.getVillage().getTrust()).isEqualTo(500d - 133);
    assertThat(state.buyRing()).contains("The Ring holds all it can.");
    assertThat(state.getVillage().getRingLevel()).isEqualTo(5);
    assertThat(state.getRing().maxed()).isTrue();
  }

  @Test
  void aRingLevelYouCannotAffordIsRefusedWithTheShortfall() {
    var state = state();
    state.getVillage().restoreTrust(7.9d, 0);

    assertThat(state.buyRing()).contains("Ring 1 costs 10. Trust 7.");
    assertThat(state.getVillage().getRingLevel()).isZero();
    assertThat(state.getVillage().getTrust()).isEqualTo(7.9d);
    assertThat(state.getRing().blocked()).isEqualTo("Ring 1 costs 10. Trust 7.");
  }

  @Test
  void theRingQuickensWavesAndWidensTheirCarryingCapacity() {
    var state = state();
    state.getVillage().restoreTrust(0d, 4);
    state.addUnits(UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR, random)));
    var baseCapacity = config.getSupply().getBaseMax();

    state.sendReinforcement(Map.of(UnitStats.WARRIOR, 1));

    var wave = state.getReinforcements().getFirst();
    assertThat(wave.getMaxSupplies()).isEqualTo(baseCapacity + 20);
    state.setTower(towerAtFloor(50));
    state.advanceReinforcements();
    assertThat(wave.getCurrentFloor()).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void survivorsNotesAddTenCapacityPerLevelToWavesOnly() {
    var state = state();
    state.getVillage().restoreUpgrades(Map.of(Tome.SURVIVORS_NOTES, 2), Map.of());
    state.addUnits(UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR, random)));
    var baseCapacity = config.getSupply().getBaseMax();

    state.sendReinforcement(Map.of(UnitStats.WARRIOR, 1));

    assertThat(state.getReinforcements().getFirst().getMaxSupplies()).isEqualTo(baseCapacity + 20);
    assertThat(state.getLoadout().carryingCapacity()).isEqualTo(baseCapacity);
  }

  @Test
  void survivorsNotesIsTheThirdTomeAndShowsItsFlatBonus() {
    var state = state();

    assertThat(state.getTomeViews())
        .extracting(GameState.TomeView::tome)
        .containsExactly("TILLERS_RECORD", "SEAM_CHARTS", "SURVIVORS_NOTES");
    assertThat(state.getTomeViews().get(2).bonusFlat()).isEqualTo(10);
    assertThat(state.getTomeViews().get(2).bonusPercent()).isZero();
  }

  @Test
  void theLoadoutStatesTheTrustHeld() {
    var state = state();
    state.getVillage().restoreTrust(12.6d, 1);

    assertThat(state.getLoadout().trustHeld()).isEqualTo(12);
  }

  @Test
  void trustAndTheRingSurviveASave() throws Exception {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var state = state();
    state.getVillage().restoreTrust(21.5d, 3);

    var mapper = JsonMapper.builder().build();
    var back = mapper.readValue(mapper.writeValueAsString(state.toSnapshot()), GameSnapshot.class);
    var restored = GameState.restore(back, config, new RandomSource(5), plan);

    assertThat(restored.getVillage().getTrust()).isEqualTo(21.5d);
    assertThat(restored.getVillage().getRingLevel()).isEqualTo(3);
    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
  }
}

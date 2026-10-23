package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import tools.jackson.databind.json.JsonMapper;

/** Trades at the level of the Campaign: named Starvation, The Count, Emergency Tilling, loads. */
class GameStateTradesTest {
  private static GameState stateWith(double food, int villagers, int warriors) {
    var config = new GameProperties();
    config.getVillage().setStartingFood(food);
    config.getVillage().setStartingVillagers(villagers);
    var state = new GameState(config);
    for (var i = 0; i < warriors; i++) {
      state.addUnits(UnitStats.WARRIOR, List.of(new Unit(UnitStats.WARRIOR, "Tomas")));
    }
    return state;
  }

  /** Always draws the same value, capped to the bound. */
  private static RandomGenerator draws(int value) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        return value;
      }

      @Override
      public int nextInt(int bound) {
        return Math.min(value, bound - 1);
      }
    };
  }

  @Test
  void starvationStillCostsOneVillagerAndOneReserveAndEachIsNamed() {
    var state = stateWith(0, 2, 1);
    state.setFoodDistributionStrategy((_, _, _) -> 0d);

    state.triggerLifecycle(draws(0));

    assertThat(state.getVillage().getVillagersCount()).isEqualTo(1);
    assertThat(state.getHomeUnitCount()).isZero();
    assertThat(state.getLedger())
        .containsExactly(
            new GameState.LedgerLine("Starvation. 1 Tiller lost. Tomas, Warrior, lost.", true));
    assertThat(state.getTheCount()).isEqualTo(new GameState.TheCount(1, 1));
  }

  @Test
  void starvationNamesTheTradeOfTheVillagerLost() {
    var state = stateWith(0, 10, 3);
    for (var i = 0; i < 4; i++) {
      state.getVillage().assign(Trade.BREAKER, 1);
    }
    state.setFoodDistributionStrategy((_, _, _) -> 0d);

    state.triggerLifecycle(draws(9));

    assertThat(state.getLedger())
        .containsExactly(
            new GameState.LedgerLine("Starvation. 1 Breaker lost. Tomas, Warrior, lost.", true));
    assertThat(state.getTheCount()).isEqualTo(new GameState.TheCount(1, 1));
  }

  @Test
  void theCountKeepsAddingUpAcrossStarvations() {
    var state = stateWith(0, 5, 5);
    state.setFoodDistributionStrategy((_, _, _) -> 0d);

    for (var i = 0; i < 3; i++) {
      state.triggerLifecycle(draws(0));
    }

    assertThat(state.getTheCount()).isEqualTo(new GameState.TheCount(3, 3));
    assertThat(state.getLedger())
        .filteredOn(line -> line.text().startsWith("Starvation"))
        .hasSize(3);
  }

  @Test
  void nothingIsWrittenWhileTheVillageIsFed() {
    var state = stateWith(50, 20, 1);

    state.triggerLifecycle(draws(0));

    assertThat(state.getLedger()).isEmpty();
  }

  @Test
  void emergencyTillingWritesOneLedgerLineWhenItStartsNamingTheTradeMoved() {
    var state = stateWith(100, 20, 12);
    for (var i = 0; i < 5; i++) {
      state.getVillage().assign(Trade.BREAKER, 1);
    }
    for (var i = 0; i < 10; i++) {
      state.getVillage().triggerLifecycle(0, 0);
    }
    state.getVillage().restore(19, 20);
    state.setFoodDistributionStrategy((_, _, _) -> 0d);

    state.triggerLifecycle(draws(0));
    state.triggerLifecycle(draws(0));

    assertThat(state.getLedger())
        .containsExactly(
            new GameState.LedgerLine(
                "Emergency Tilling. Pantry below 20. Breakers moved to the Fields.", false));
    assertThat(state.getVillage().hands(Trade.BREAKER)).isEqualTo(3);
  }

  @Test
  void quarryAndStudyProduceIntoTheirStoresEachTick() {
    var state = stateWith(1000, 20, 0);
    state.setHighestFloor(10);
    state.getVillage().assign(Trade.BREAKER, 1);
    state.getVillage().assign(Trade.SCRIBE, 1);

    state.triggerLifecycle(draws(0));

    assertThat(state.getVillage().getQuarryHeap()).isEqualTo(0.25 * 1.2);
    assertThat(state.getVillage().getKnowledge()).isEqualTo(0.05 * 1.2);
    assertThat(state.getQuarryHeapCap()).isEqualTo(100.0);
    assertThat(state.getStockroomCap()).isEqualTo(100.0);
  }

  @Test
  void theTradesRoundTripThroughTheSnapshot() throws Exception {
    var config = new GameProperties();
    var random = new SplittableRandom(5);
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var state = stateWith(100, 20, 0);
    state.getVillage().assign(Trade.BREAKER, 2);
    state.getVillage().assign(Trade.SCRIBE, 1);
    state.setHighestFloor(6);
    state.triggerLifecycle(draws(0));
    state
        .getVillage()
        .restoreTrades(2, 1, Map.of(Trade.BREAKER, List.of(4, 7)), 12.5, 30.25, true, 3, 2);

    var mapper = JsonMapper.builder().build();
    var json = mapper.writeValueAsString(state.toSnapshot());
    var back =
        mapper.readValue(
            json, io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot.class);
    var restored = GameState.restore(back, config, random, plan);

    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
    assertThat(restored.getVillage().hands(Trade.BREAKER)).isEqualTo(2);
    assertThat(restored.getVillage().hands(Trade.SCRIBE)).isEqualTo(1);
    assertThat(restored.getVillage().hands(Trade.TILLER)).isEqualTo(17);
    assertThat(restored.getVillage().settlingSeconds(Trade.BREAKER)).isEqualTo(4);
    assertThat(restored.getVillage().isEmergencyTilling()).isTrue();
    assertThat(restored.getVillage().getKnowledge()).isEqualTo(12.5);
    assertThat(restored.getVillage().getQuarryHeap()).isEqualTo(30.25);
    assertThat(restored.getTheCount()).isEqualTo(new GameState.TheCount(3, 2));
    assertThat(new ArrayList<>(restored.getLedger())).isEmpty();
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** Pins the order of a lifecycle tick: production and upkeep, starvation, then recovery. */
class GameStateLifecycleTest {
  private static GameState stateWith(double food, int villagers, int units) {
    var config = new GameProperties();
    config.getVillage().setStartingFood(food);
    config.getVillage().setStartingVillagers(villagers);
    var state = new GameState(config);
    for (var i = 0; i < units; i++) {
      state
          .getUnits()
          .computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>())
          .add(new Unit(UnitStats.WARRIOR));
    }
    return state;
  }

  /** Records the home party handed to recovery, in place of the real round-robin healing. */
  private static List<List<Unit>> recordRecovery(GameState state) {
    var seen = new ArrayList<List<Unit>>();
    state.setFoodDistributionStrategy(
        (homeUnits, _, _) -> {
          seen.add(List.copyOf(homeUnits));
          return 0d;
        });
    return seen;
  }

  @Test
  void recoveryRunsAfterProductionAndUpkeepHaveSettledTheStock() {
    var state = stateWith(10, 4, 2);
    var foodSeenByRecovery = new ArrayList<Double>();
    state.setFoodDistributionStrategy(
        (_, village, _) -> {
          foodSeenByRecovery.add(village.getFood());
          return 0d;
        });

    state.triggerLifecycle();

    assertThat(foodSeenByRecovery).containsExactly(10 + 4 * 0.5 - 2 * 1.0);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(4);
  }

  @Test
  void starvationCostsAVillagerAndAUnitBeforeRecoveryLooksAtTheParty() {
    var state = stateWith(0, 2, 1);
    var recovery = recordRecovery(state);

    state.triggerLifecycle();

    assertThat(state.getVillage().getVillagersCount()).isEqualTo(1);
    assertThat(state.getHomeUnitCount()).isZero();
    assertThat(recovery).hasSize(1);
    assertThat(recovery.getFirst()).isEmpty();
  }

  @Test
  void nothingIsLostWhileTheVillageIsFed() {
    var state = stateWith(50, 2, 1);
    var recovery = recordRecovery(state);

    state.triggerLifecycle();

    assertThat(state.getHomeUnitCount()).isEqualTo(1);
    assertThat(recovery.getFirst()).hasSize(1);
  }

  @Test
  void recoverySpendIsExposedForTheTickThatSpentIt() {
    var state = stateWith(100, 20, 1);
    var unit = state.getUnits().get(UnitStats.WARRIOR).getFirst();
    unit.setCurrentHealth(unit.getStats().getHealth() - 20);

    state.triggerLifecycle();

    assertThat(state.getRecoverySpend()).isEqualTo(5.0);
  }

  @Test
  void recoverySpendIsZeroWhenNobodyIsWounded() {
    var state = stateWith(100, 20, 1);

    state.triggerLifecycle();

    assertThat(state.getRecoverySpend()).isZero();
  }

  @Test
  void pantryHoldIsComputedOnTheWorstCaseRecoveryCeilingWhileAnyoneIsWounded() {
    var state = stateWith(100, 20, 14);
    var unit = state.getUnits().get(UnitStats.WARRIOR).getFirst();
    unit.setCurrentHealth(unit.getStats().getHealth() - 20);

    assertThat(state.getPantryHoldSeconds()).isEqualTo(10);
  }

  @Test
  void pantryHoldIgnoresRecoveryWhenNobodyIsWounded() {
    var state = stateWith(100, 20, 14);

    assertThat(state.getPantryHoldSeconds()).isEqualTo(24);
  }

  @Test
  void pantryHoldIsAbsentWhileFieldsCoverUpkeep() {
    var state = stateWith(100, 20, 8);

    assertThat(state.getPantryHoldSeconds()).isNull();
  }

  @Test
  void sellingFoodPaysTheConfiguredPrice() {
    var state = stateWith(4.5, 1, 0);
    state.getConfig().getReward().setFoodSellPrice(3.0);
    var before = state.getCredit();

    state.sellFood();

    assertThat(state.getCredit()).isEqualTo(before + 4 * 3.0);
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** The Villager pool: Trades, Settling, the Tiller minimum, Emergency Tilling and the stores. */
class VillageTradesTest {
  private static Village village(double food, int villagers) {
    var config = new GameProperties.Village();
    config.setStartingFood(food);
    config.setStartingVillagers(villagers);
    return new Village(config, new GameProperties.Trades());
  }

  private static void settle(Village village) {
    for (var i = 0; i < 10; i++) {
      village.triggerLifecycle(0, 0);
    }
  }

  @Test
  void everyVillagerStartsAsATiller() {
    var village = village(100, 20);

    assertThat(village.hands(Trade.TILLER)).isEqualTo(20);
    assertThat(village.hands(Trade.BREAKER)).isZero();
    assertThat(village.hands(Trade.SCRIBE)).isZero();
    assertThat(village.getProductionRate()).isEqualTo(10.0);
  }

  @Test
  void movingAHandToTheQuarryCostsFoodAndMakesMaterialsAtTheHighestFloorYield() {
    var village = village(100, 20);

    assertThat(village.assign(Trade.BREAKER, 1)).isEmpty();

    assertThat(village.hands(Trade.BREAKER)).isEqualTo(1);
    assertThat(village.getProductionRate()).isEqualTo(9.5);
    assertThat(village.materialsPerTick(0)).isCloseTo(0.25, within(1e-9));
    assertThat(village.materialsPerTick(10)).isCloseTo(0.25 * 1.2, within(1e-9));
  }

  @Test
  void scribesWriteKnowledgeAtTheHighestFloorYield() {
    var village = village(100, 20);
    village.assign(Trade.SCRIBE, 1);

    assertThat(village.knowledgePerTick(0)).isCloseTo(0.05, within(1e-9));
    assertThat(village.knowledgePerTick(25)).isCloseTo(0.05 * 1.5, within(1e-9));
  }

  @Test
  void aMovedVillagerSettlesForTenTicksBeforeItCanBeMovedAgain() {
    var village = village(1000, 20);
    village.assign(Trade.BREAKER, 1);

    assertThat(village.assign(Trade.BREAKER, -1)).contains("Settling 10 s.");
    for (var i = 0; i < 9; i++) {
      village.triggerLifecycle(0, 0);
    }
    assertThat(village.assign(Trade.BREAKER, -1)).contains("Settling 1 s.");
    village.triggerLifecycle(0, 0);

    assertThat(village.assign(Trade.BREAKER, -1)).isEmpty();
    assertThat(village.hands(Trade.BREAKER)).isZero();
  }

  @Test
  void aSettlingHandStillWorks() {
    var village = village(1000, 20);
    village.assign(Trade.BREAKER, 1);

    village.triggerLifecycle(0, 10);

    assertThat(village.getQuarryHeap()).isCloseTo(0.25 * 1.2, within(1e-9));
  }

  @Test
  void fiveTillersMustStayButOnlyAsAnAssignmentLimit() {
    var village = village(100, 7);

    assertThat(village.assign(Trade.BREAKER, 1)).isEmpty();
    assertThat(village.assign(Trade.BREAKER, 1)).isEmpty();
    assertThat(village.assign(Trade.BREAKER, 1)).contains("Cannot assign. 5 Tillers must stay.");
    assertThat(village.hands(Trade.TILLER)).isEqualTo(5);
  }

  @Test
  void aWorkplaceStopsAtItsPosts() {
    var village = village(1000, 40);
    for (var i = 0; i < 10; i++) {
      assertThat(village.assign(Trade.SCRIBE, 1)).isEmpty();
    }

    assertThat(village.assign(Trade.SCRIBE, 1)).contains("Scribe's Study: 10 of 10 Posts.");
    assertThat(village.hands(Trade.SCRIBE)).isEqualTo(10);
  }

  @Test
  void aHandCannotBeTakenFromATradeThatHoldsNone() {
    var village = village(100, 20);

    assertThat(village.assign(Trade.BREAKER, -1)).contains("No Breakers to move.");
  }

  @Test
  void emergencyTillingStartsBelowTwentyFoodWhileUpkeepOutrunsTheFields() {
    for (var food : new double[] {19.0, 20.0}) {
      var village = village(food, 20);
      for (var i = 0; i < 5; i++) {
        village.assign(Trade.BREAKER, 1);
      }
      settle(village);
      village.restore(food, 20);

      var result = village.emergencyTilling(10);

      assertThat(result.active()).as("food %s", food).isEqualTo(food < 20);
    }
  }

  @Test
  void emergencyTillingMovesOneVillagerPerTickFromTheTradeWithMostHands() {
    var village = village(10, 20);
    for (var i = 0; i < 3; i++) {
      village.assign(Trade.BREAKER, 1);
    }
    village.assign(Trade.SCRIBE, 1);
    settle(village);
    village.restore(10, 20);

    var first = village.emergencyTilling(12);

    assertThat(first.started()).isTrue();
    assertThat(first.moved()).isEqualTo(Trade.BREAKER);
    assertThat(village.hands(Trade.BREAKER)).isEqualTo(2);
    assertThat(village.hands(Trade.TILLER)).isEqualTo(17);
  }

  @Test
  void emergencyTillingDoesNotPullAVillagerStillSettling() {
    var village = village(10, 20);
    village.assign(Trade.BREAKER, 1);
    village.restore(10, 20);

    var result = village.emergencyTilling(12);

    assertThat(result.started()).isTrue();
    assertThat(result.moved()).isNull();
    assertThat(village.hands(Trade.BREAKER)).isEqualTo(1);
  }

  @Test
  void emergencyTillingHoldsUntilThePantryPassesSixtyThenEnds() {
    var village = village(10, 20);
    village.assign(Trade.BREAKER, 1);
    settle(village);
    village.restore(10, 20);
    assertThat(village.emergencyTilling(12).active()).isTrue();

    village.restore(60, 19);
    assertThat(village.emergencyTilling(12).active()).as("at 60").isTrue();

    village.restore(61, 19);
    assertThat(village.emergencyTilling(12).active()).as("above 60").isFalse();
  }

  @Test
  void emergencyTillingNeverStartsWhileTheFieldsCoverUpkeep() {
    var village = village(5, 20);
    village.assign(Trade.BREAKER, 1);
    settle(village);
    village.restore(5, 20);

    assertThat(village.emergencyTilling(9).active()).isFalse();
  }

  @Test
  void duringEmergencyTillingTheFieldsCannotBeLeft() {
    var village = village(10, 20);
    village.assign(Trade.BREAKER, 1);
    settle(village);
    village.restore(10, 20);
    village.emergencyTilling(12);

    assertThat(village.assign(Trade.SCRIBE, 1))
        .contains("Held at the Fields until the Pantry passes 60.");
  }

  @Test
  void starvationTakesOneVillagerWeightedByHeadcountAndNamesTheTrade() {
    var village = village(0, 10);
    for (var i = 0; i < 4; i++) {
      village.assign(Trade.BREAKER, 1);
    }

    // 10 Villagers: Tillers hold 6 (draws 0..5), Breakers 4 (draws 6..9).
    assertThat(village.starve(new FixedRandom(7))).contains(Trade.BREAKER);
    assertThat(village.hands(Trade.BREAKER)).isEqualTo(3);
    assertThat(village.getVillagersCount()).isEqualTo(9);
    assertThat(village.getVillagersLost()).isEqualTo(1);

    assertThat(village.starve(new FixedRandom(0))).contains(Trade.TILLER);
    assertThat(village.hands(Trade.TILLER)).isEqualTo(5);
  }

  @Test
  void starvingWithNoVillagerLeftLosesNobody() {
    var village = village(0, 0);

    assertThat(village.starve(new FixedRandom(0))).isEmpty();
    assertThat(village.getVillagersLost()).isZero();
  }

  @Test
  void theStockroomHoldsSixtyPlusFourPerHighestFloorAndWastesTheRest() {
    var village = village(1000, 20);
    for (var i = 0; i < 10; i++) {
      village.assign(Trade.SCRIBE, 1);
    }

    assertThat(village.stockroomCap(10)).isEqualTo(100.0);
    for (var i = 0; i < 2000; i++) {
      village.triggerLifecycle(0, 10);
    }
    assertThat(village.getKnowledge()).isEqualTo(100.0);
  }

  @Test
  void theHeapHoldsTenPerHighestFloor() {
    var village = village(1000, 20);
    for (var i = 0; i < 10; i++) {
      village.assign(Trade.BREAKER, 1);
    }

    assertThat(village.heapCap(5)).isEqualTo(50.0);
    for (var i = 0; i < 200; i++) {
      village.triggerLifecycle(0, 5);
    }
    assertThat(village.getQuarryHeap()).isEqualTo(50.0);
  }

  @Test
  void theHeapDeliversFivePerFloorClearedAndKeepsTheRest() {
    var village = village(1000, 20);
    for (var i = 0; i < 10; i++) {
      village.assign(Trade.BREAKER, 1);
    }
    for (var i = 0; i < 200; i++) {
      village.triggerLifecycle(0, 10);
    }
    var before = village.getQuarryHeap();

    var delivered = village.deliverHeap(3);

    assertThat(delivered).isEqualTo(15.0);
    assertThat(village.getQuarryHeap()).isEqualTo(before - 15.0);
    assertThat(village.deliverHeap(1000)).isEqualTo(before - 15.0);
    assertThat(village.getQuarryHeap()).isZero();
  }

  @Test
  void aBoughtVillagerJoinsTheFields() {
    var village = village(100, 20);
    village.assign(Trade.BREAKER, 1);

    village.buyVillager(100.0);

    assertThat(village.hands(Trade.TILLER)).isEqualTo(20);
    assertThat(village.getVillagersCount()).isEqualTo(21);
  }

  @Test
  void theMarginPreviewStatesTheFieldsUpkeepAndWhatThePantryHolds() {
    var village = village(100, 20);

    var preview = village.preview(Trade.BREAKER, 1, 8, 0.0);

    assertThat(preview.fields()).isEqualTo(9.5);
    assertThat(preview.upkeep()).isEqualTo(8.0);
    assertThat(preview.fieldsCoverUpkeep()).isTrue();
    assertThat(preview.pantryHoldSeconds()).isNull();
    assertThat(preview.blocked()).isNull();
  }

  @Test
  void thePreviewWarnsButStillShowsTheMarginWhenTheFieldsWouldNotCoverUpkeep() {
    var village = village(100, 20);

    var preview = village.preview(Trade.BREAKER, 1, 14, 0.0);

    assertThat(preview.fieldsCoverUpkeep()).isFalse();
    // (100 - 4.5) / 4.5 = 21.2, rounded down.
    assertThat(preview.pantryHoldSeconds()).isEqualTo(21);
  }

  private record FixedRandom(int value) implements RandomGenerator {
    @Override
    public long nextLong() {
      return value;
    }

    @Override
    public int nextInt(int bound) {
      return Math.min(value, bound - 1);
    }
  }
}

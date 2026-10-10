package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** Characterises the village economy as configured today (see {@code game.village}). */
class VillageTest {
  private static final double STARTING_FOOD = 100.0;
  private static final int STARTING_VILLAGERS = 20;
  private static final double PRODUCTION_PER_VILLAGER = 0.5;
  private static final double UPKEEP_PER_UNIT = 1.0;

  private static Village village(double food, int villagers) {
    var config = new GameProperties.Village();
    config.setStartingFood(food);
    config.setStartingVillagers(villagers);
    return new Village(config);
  }

  @Test
  void defaultConfigStartsWithTheDocumentedStock() {
    var village = new Village(new GameProperties.Village());

    assertThat(village.getFood()).isEqualTo(STARTING_FOOD);
    assertThat(village.getVillagersCount()).isEqualTo(STARTING_VILLAGERS);
    assertThat(village.getProductionRate()).isEqualTo(STARTING_VILLAGERS * PRODUCTION_PER_VILLAGER);
  }

  @Test
  void lifecycleAddsProductionThenSubtractsUpkeep() {
    var village = village(50, 20);

    var starving = village.triggerLifecycle(4);

    assertThat(village.getFood()).isCloseTo(50 + 10 - 4 * UPKEEP_PER_UNIT, within(1e-9));
    assertThat(starving).isFalse();
  }

  @Test
  void upkeepBeyondStockFloorsFoodAtZeroAndStarves() {
    var village = village(3, 2);

    var starving = village.triggerLifecycle(10);

    assertThat(village.getFood()).isZero();
    assertThat(starving).isTrue();
  }

  @Test
  void foodExactlyZeroCountsAsStarving() {
    var village = village(0, 2);

    assertThat(village.triggerLifecycle(1)).isTrue();
  }

  @Test
  void starveCostsOneVillagerAndNeverGoesNegative() {
    var village = village(0, 1);

    village.starve();
    village.starve();

    assertThat(village.getVillagersCount()).isZero();
  }

  @Test
  void buyingAVillagerCostsTheNewHeadcount() {
    var village = village(0, 20);

    var creditsLeft = village.buyVillager(100.0);

    assertThat(village.getVillagersCount()).isEqualTo(21);
    assertThat(creditsLeft).isEqualTo(79.0);
  }

  @Test
  void sellFoodSellsOneUnitAtATimeAndKeepsTheLast() {
    var village = village(2.5, 1);

    assertThat(village.sellFood()).isTrue();
    assertThat(village.getFood()).isEqualTo(1.5);
    assertThat(village.sellFood()).isTrue();
    assertThat(village.getFood()).isEqualTo(0.5);
    assertThat(village.sellFood()).isFalse();
    assertThat(village.getFood()).isEqualTo(0.5);
  }

  @Test
  void sellFoodRefusesExactlyOneUnit() {
    assertThat(village(1, 1).sellFood()).isFalse();
  }

  @Test
  void takeFoodIsCappedByTheStockAndIgnoresNegativeRequests() {
    var village = village(10, 1);

    assertThat(village.takeFood(-5)).isZero();
    assertThat(village.takeFood(25)).isEqualTo(10);
    assertThat(village.getFood()).isZero();
  }
}

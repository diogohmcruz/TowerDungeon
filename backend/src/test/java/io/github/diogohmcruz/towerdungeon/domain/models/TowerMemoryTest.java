package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

class TowerMemoryTest {
  private static Tower tower(GameProperties config) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return new Tower(
        100, config.getBoss().copy(), config.getEnemies().copy(), new RandomSource(5), plan);
  }

  private static GameProperties inGeneration(int generation) {
    return new GameProperties().copy().withTowerMemory(generation);
  }

  private static double guardianHealth(Tower tower, int floor) {
    tower.startNewRunAt(floor);
    return tower.getCurrentTowerFloor().getEnemies().getFirst().getMaxHealth();
  }

  @Test
  void generationOneHasNoMemory() {
    var base = tower(new GameProperties());
    var first = tower(inGeneration(1));

    assertThat(first.easedBaseBudget(44)).isEqualTo(base.easedBaseBudget(44));
    assertThat(guardianHealth(first, 30)).isEqualTo(guardianHealth(base, 30));
  }

  @Test
  void memoryHardensGarrisonsFivePercentAndGuardiansEightPercentPerStep() {
    var base = tower(new GameProperties());
    var third = tower(inGeneration(4));

    assertThat(third.easedBaseBudget(44)).isCloseTo(base.easedBaseBudget(44) * 1.15, within(1e-9));
    assertThat(guardianHealth(third, 30)).isCloseTo(guardianHealth(base, 30) * 1.24, within(1e-6));
  }

  @Test
  void memoryStopsAtFourSteps() {
    var capped = tower(inGeneration(5));
    var later = tower(inGeneration(12));

    assertThat(later.easedBaseBudget(44)).isEqualTo(capped.easedBaseBudget(44));
    assertThat(guardianHealth(later, 30)).isEqualTo(guardianHealth(capped, 30));
  }

  @Test
  void guardiansAboveTheUpperFloorTakeAtMostTheConfiguredBonus() {
    var base = tower(new GameProperties());
    var late = tower(inGeneration(5));

    assertThat(guardianHealth(late, 100)).isCloseTo(guardianHealth(base, 100) * 1.16, within(1e-6));
    assertThat(guardianHealth(late, 50)).isCloseTo(guardianHealth(base, 50) * 1.32, within(1e-6));
  }

  @Test
  void theUpperCapIsConfigAndTurnsOff() {
    var config = new GameProperties();
    config.getPrestige().getMemory().setUpperGuardianFloor(0);
    var late = tower(config.copy().withTowerMemory(5));
    var base = tower(new GameProperties());

    assertThat(guardianHealth(late, 100)).isCloseTo(guardianHealth(base, 100) * 1.32, within(1e-6));
  }

  @Test
  void oneCampaignsMemoryNeverLeaksIntoAnother() {
    var shared = new GameProperties();
    var hardened = shared.copy().withTowerMemory(5);
    var other = shared.copy().withTowerMemory(1);

    assertThat(hardened.getEnemies().getMemoryBudget()).isGreaterThan(1.0);
    assertThat(other.getEnemies().getMemoryBudget()).isEqualTo(1.0);
    assertThat(shared.getEnemies().getMemoryBudget()).isEqualTo(1.0);
    assertThat(shared.getBoss().getMemoryHealth()).isEqualTo(1.0);
  }
}

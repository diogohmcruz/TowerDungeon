package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.Architecture;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;

class TowerTest {
  private static final int FLOORS = 30;

  private static Tower tower(RandomGenerator random) {
    var config = new GameProperties();
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return new Tower(100, config.getBoss(), config.getEnemies(), random, plan);
  }

  private static List<List<String>> climb(RandomGenerator random) {
    var tower = tower(random);
    return IntStream.range(0, FLOORS)
        .<List<String>>mapToObj(
            _ -> {
              tower.moveToNextFloor();
              return tower.getCurrentTowerFloor().getEnemies().stream()
                  .map(enemy -> enemy.getStats().name())
                  .toList();
            })
        .toList();
  }

  @Test
  void sameSeedBuildsTheSameGarrisons() {
    assertThat(climb(new SplittableRandom(42))).isEqualTo(climb(new SplittableRandom(42)));
  }

  @Test
  void differentSeedsBuildDifferentGarrisons() {
    assertThat(climb(new SplittableRandom(1))).isNotEqualTo(climb(new SplittableRandom(2)));
  }

  @Test
  void everyDiscoveredFloorCarriesItsArchitectureAndVariant() {
    var tower = tower(new SplittableRandom(1));

    tower.moveToNextFloor();
    tower.moveToNextFloor();
    tower.moveToNextFloor();

    assertThat(tower.getFloors().get(1).getArchitecture()).isEqualTo(Architecture.COLONNADE);
    assertThat(tower.getFloors().get(3).getArchitecture()).isEqualTo(Architecture.STAIR_SHAFT);
    assertThat(tower.getFloors().get(3).getVariant()).isBetween(0, 3);
  }

  @Test
  void undiscoveredFloorsAreNeverPartOfTheTower() {
    var tower = tower(new SplittableRandom(1));

    tower.moveToNextFloor();

    assertThat(tower.getFloors()).containsOnlyKeys(1);
  }

  @Test
  void layoutVersionBumpsWhenAFloorIsAddedButNotWhenItIsRepopulated() {
    var tower = tower(new SplittableRandom(1));
    var initial = tower.getLayoutVersion();

    tower.moveToNextFloor();
    var afterFirstFloor = tower.getLayoutVersion();
    tower.startNewRun();
    tower.moveToNextFloor();

    assertThat(afterFirstFloor).isGreaterThan(initial);
    assertThat(tower.getLayoutVersion()).isEqualTo(afterFirstFloor);
  }

  @Test
  void layoutVersionBumpsWhenAGuardianIsSlainOnceOnly() {
    var tower = tower(new SplittableRandom(1));
    var initial = tower.getLayoutVersion();

    tower.markBossCleared(10);
    var afterSlaying = tower.getLayoutVersion();
    tower.markBossCleared(10);

    assertThat(afterSlaying).isGreaterThan(initial);
    assertThat(tower.getLayoutVersion()).isEqualTo(afterSlaying);
  }

  @Test
  void slainGuardiansAreExposedInAscendingOrder() {
    var tower = tower(new SplittableRandom(1));

    tower.markBossCleared(30);
    tower.markBossCleared(10);

    assertThat(tower.getSlainGuardians()).containsExactly(10, 30);
  }

  @Test
  void startingAtAShortcutFloorAddsItToTheTower() {
    var tower = tower(new SplittableRandom(1));

    tower.startNewRunAt(15);

    assertThat(tower.getFloors().get(15).getArchitecture()).isEqualTo(Architecture.WATERWAY);
  }
}

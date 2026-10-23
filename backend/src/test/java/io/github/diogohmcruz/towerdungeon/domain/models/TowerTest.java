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
    return tower(random, new GameProperties());
  }

  private static Tower tower(RandomGenerator random, GameProperties config) {
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

  private static GameProperties noVariance() {
    var config = new GameProperties();
    config.getEnemies().setVariance(0);
    return config;
  }

  /** Slimes weigh 1, so on a slime-only floor the headcount is the budget rounded up. */
  private static int slimesOnFloor(Tower tower, int floor) {
    return tower.getFloors().get(floor).getEnemies().size();
  }

  private static void climbTo(Tower tower, int floor) {
    while (tower.getCurrentFloor() < floor) {
      tower.moveToNextFloor();
    }
  }

  private static double undiscountedBudget(int floor) {
    return 3.0 + 0.8 * floor + 0.04 * floor * floor;
  }

  @Test
  void firstReachFloorsAreDiscountedAndFloorTenOnwardIsNot() {
    var tower = tower(new SplittableRandom(1), noVariance());
    climbTo(tower, 9);
    tower.moveToNextFloor();
    tower.moveToNextFloor();

    var floorOne = (int) Math.ceil(undiscountedBudget(1) * 0.5);
    var floorNine = (int) Math.ceil(undiscountedBudget(9) * (0.5 + 0.5 * 8 / 9) * 1.0);
    assertThat(slimesOnFloor(tower, 1)).isEqualTo(floorOne);
    assertThat(floorOne).isLessThan((int) Math.ceil(undiscountedBudget(1)));
    // Floor 5 is a surge floor: the discount applies before the surge multiplier.
    assertThat(slimesOnFloor(tower, 5))
        .isEqualTo((int) Math.ceil(undiscountedBudget(5) * (0.5 + 0.5 * 4 / 9) * 1.5));
    assertThat(slimesOnFloor(tower, 9)).isEqualTo(floorNine);
    assertThat(slimesOnFloor(tower, 11)).isEqualTo((int) Math.ceil(undiscountedBudget(11)));
  }

  @Test
  void aSlainGuardianBelowAFloorRaisesItsBudgetOneStepEach() {
    var plain = tower(new SplittableRandom(1), noVariance());
    plain.startNewRunAt(11);
    var oneStep = tower(new SplittableRandom(1), noVariance());
    oneStep.markBossCleared(10);
    oneStep.startNewRunAt(11);
    var twoSteps = tower(new SplittableRandom(1), noVariance());
    twoSteps.markBossCleared(5);
    twoSteps.markBossCleared(10);
    twoSteps.startNewRunAt(11);

    var base = undiscountedBudget(11);
    assertThat(slimesOnFloor(plain, 11)).isEqualTo((int) Math.ceil(base));
    assertThat(slimesOnFloor(oneStep, 11)).isEqualTo((int) Math.ceil(base * 1.15));
    assertThat(slimesOnFloor(twoSteps, 11)).isEqualTo((int) Math.ceil(base * 1.15 * 1.15));
  }

  @Test
  void aSlainGuardianAboveAFloorEasesItInsteadOfSteppingItUp() {
    var tower = tower(new SplittableRandom(1), noVariance());
    tower.markBossCleared(20);
    tower.startNewRunAt(11);

    assertThat(slimesOnFloor(tower, 11)).isEqualTo((int) Math.ceil(undiscountedBudget(11) * 0.85));
  }
}

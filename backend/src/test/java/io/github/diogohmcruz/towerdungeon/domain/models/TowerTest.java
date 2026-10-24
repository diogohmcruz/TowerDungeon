package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.FloorCurve;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.Architecture;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

class TowerTest {
  private static final int FLOORS = 30;

  private static Tower tower(RandomSource random) {
    return tower(random, new GameProperties());
  }

  private static Tower tower(RandomSource random, GameProperties config) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return new Tower(100, config.getBoss(), config.getEnemies(), random, plan);
  }

  private static List<List<String>> climb(RandomSource random) {
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
    assertThat(climb(new RandomSource(42))).isEqualTo(climb(new RandomSource(42)));
  }

  @Test
  void differentSeedsBuildDifferentGarrisons() {
    assertThat(climb(new RandomSource(1))).isNotEqualTo(climb(new RandomSource(2)));
  }

  @Test
  void everyDiscoveredFloorCarriesItsArchitectureAndVariant() {
    var tower = tower(new RandomSource(1));

    tower.moveToNextFloor();
    tower.moveToNextFloor();
    tower.moveToNextFloor();

    assertThat(tower.getFloors().get(1).getArchitecture()).isEqualTo(Architecture.COLONNADE);
    assertThat(tower.getFloors().get(3).getArchitecture()).isEqualTo(Architecture.STAIR_SHAFT);
    assertThat(tower.getFloors().get(3).getVariant()).isBetween(0, 3);
  }

  @Test
  void garrisonsFollowThePartRangesAndGuardiansAlwaysHaveSixParts() {
    var config = new GameProperties();
    var tower = tower(new RandomSource(11), config);
    var seenGuardians = 0;

    for (var i = 0; i < 100; i++) {
      tower.moveToNextFloor();
      for (var enemy : tower.getCurrentTowerFloor().getEnemies()) {
        var present = enemy.getParts().stream().filter(part -> !part.isLost()).toList();
        var range = config.getEnemies().partsOf(enemy.getStats());
        if (enemy.isBoss()) {
          seenGuardians++;
          assertThat(present).hasSize(6);
        } else {
          assertThat(present.size()).isBetween(range.getMin(), range.getMax());
        }
        assertThat(enemy.getMaxHealth())
            .isCloseTo(
                enemy.getStats().getHealth() * enemy.healthMultiplier(),
                org.assertj.core.api.Assertions.within(1e-6));
      }
    }

    assertThat(seenGuardians).isEqualTo(10);
  }

  @Test
  void theSameSeedBuildsTheSameBodies() {
    List<Integer> first = bodyLayoutsOfAClimb(5);
    assertThat(first).isEqualTo(bodyLayoutsOfAClimb(5));
    assertThat(first).isNotEqualTo(bodyLayoutsOfAClimb(6));
  }

  private static List<Integer> bodyLayoutsOfAClimb(long seed) {
    var tower = tower(new RandomSource(seed));
    return IntStream.range(0, FLOORS)
        .boxed()
        .flatMap(
            _ -> {
              tower.moveToNextFloor();
              return tower.getCurrentTowerFloor().getEnemies().stream()
                  .map(enemy -> enemy.getParts().hashCode());
            })
        .toList();
  }

  @Test
  void undiscoveredFloorsAreNeverPartOfTheTower() {
    var tower = tower(new RandomSource(1));

    tower.moveToNextFloor();

    assertThat(tower.getFloors()).containsOnlyKeys(1);
  }

  @Test
  void layoutVersionBumpsWhenAFloorIsAddedButNotWhenItIsRepopulated() {
    var tower = tower(new RandomSource(1));
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
    var tower = tower(new RandomSource(1));
    var initial = tower.getLayoutVersion();

    tower.markBossCleared(10);
    var afterSlaying = tower.getLayoutVersion();
    tower.markBossCleared(10);

    assertThat(afterSlaying).isGreaterThan(initial);
    assertThat(tower.getLayoutVersion()).isEqualTo(afterSlaying);
  }

  @Test
  void slainGuardiansAreExposedInAscendingOrder() {
    var tower = tower(new RandomSource(1));

    tower.markBossCleared(30);
    tower.markBossCleared(10);

    assertThat(tower.getSlainGuardians()).containsExactly(10, 30);
  }

  @Test
  void startingAtAShortcutFloorAddsItToTheTower() {
    var tower = tower(new RandomSource(1));

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

  /** The formula that sets Floors 1 to 5 as measured. */
  private static double undiscountedBudget(int floor) {
    return 3.0 + 0.8 * floor + 0.04 * floor * floor;
  }

  /** The curve that sets Floors 6 to 100: no discount, the anchors read between. */
  private static double curvedBudget(int floor) {
    return FloorCurve.at(new GameProperties().getEnemies().getBudgetCurve(), floor);
  }

  @Test
  void firstReachFloorsAreDiscountedAndFloorTenOnwardIsNot() {
    var tower = tower(new RandomSource(1), noVariance());
    climbTo(tower, 9);
    tower.moveToNextFloor();
    tower.moveToNextFloor();

    var floorOne = (int) Math.ceil(undiscountedBudget(1) * 0.5);
    assertThat(slimesOnFloor(tower, 1)).isEqualTo(floorOne);
    assertThat(floorOne).isLessThan((int) Math.ceil(undiscountedBudget(1)));
    // Floor 5 is a surge floor: the discount applies before the surge multiplier.
    assertThat(slimesOnFloor(tower, 5))
        .isEqualTo((int) Math.ceil(undiscountedBudget(5) * (0.5 + 0.5 * 4 / 9) * 1.5));
    // From Floor 6 the curve sets the budget outright, with no first-reach discount left to fade.
    assertThat(slimesOnFloor(tower, 9)).isEqualTo((int) Math.ceil(curvedBudget(9)));
    assertThat(slimesOnFloor(tower, 11)).isEqualTo((int) Math.ceil(curvedBudget(11)));
  }

  @Test
  void aSlainGuardianBelowAFloorRaisesItsBudgetOneStepEach() {
    var plain = tower(new RandomSource(1), noVariance());
    plain.startNewRunAt(11);
    var oneStep = tower(new RandomSource(1), noVariance());
    oneStep.markBossCleared(10);
    oneStep.startNewRunAt(11);
    var twoSteps = tower(new RandomSource(1), noVariance());
    twoSteps.markBossCleared(5);
    twoSteps.markBossCleared(10);
    twoSteps.startNewRunAt(11);

    var base = curvedBudget(11);
    assertThat(slimesOnFloor(plain, 11)).isEqualTo((int) Math.ceil(base));
    assertThat(slimesOnFloor(oneStep, 11)).isEqualTo((int) Math.ceil(base * 1.15));
    assertThat(slimesOnFloor(twoSteps, 11)).isEqualTo((int) Math.ceil(base * 1.15 * 1.15));
  }

  @Test
  void aSlainGuardianAboveAFloorEasesItInsteadOfSteppingItUp() {
    var tower = tower(new RandomSource(1), noVariance());
    tower.markBossCleared(20);
    tower.startNewRunAt(11);

    assertThat(slimesOnFloor(tower, 11)).isEqualTo((int) Math.ceil(curvedBudget(11) * 0.75));
  }

  @Test
  void threeSlainGuardiansThinAFloorToItsFloorAndNoFurther() {
    var tower = tower(new RandomSource(1), noVariance());
    tower.markBossCleared(20);
    tower.markBossCleared(30);
    tower.markBossCleared(40);
    var base = tower(new RandomSource(1), noVariance()).easedBaseBudget(11);

    // 0.75^3 is 0.42, below the floor of half the base, so the floor holds.
    assertThat(tower.easedBaseBudget(11)).isCloseTo(base * 0.5, within(1e-9));
  }

  @Test
  void theCapStopsTheThinningWhenItIsStricterThanTheFloor() {
    var config = new GameProperties().copy();
    config.getEnemies().setEaseBudgetFloor(0.0);
    config.getEnemies().setEaseBudgetCap(0.6);
    var tower = tower(new RandomSource(1), config);
    var base = tower(new RandomSource(1), config);
    for (var boss : new int[] {20, 30, 40, 50}) {
      tower.markBossCleared(boss);
    }
    assertThat(tower.easedBaseBudget(11)).isCloseTo(base.easedBaseBudget(11) * 0.4, within(1e-9));
  }

  @Test
  void tenSealsBrokenDeepensTheEasingButCannotBreakTheFloor() {
    var config = new GameProperties().copy();
    config.getEnemies().setEaseMultiplier(1.25);
    var tower = tower(new RandomSource(1), config);
    var base = tower(new RandomSource(1), config);
    tower.markBossCleared(20);
    // One Guardian: 0.25 x 1.25 thins by 31.25%, still above the floor.
    assertThat(tower.easedBaseBudget(11))
        .isCloseTo(base.easedBaseBudget(11) * 0.6875, within(1e-9));
    tower.markBossCleared(30);
    tower.markBossCleared(40);
    assertThat(tower.easedBaseBudget(11)).isCloseTo(base.easedBaseBudget(11) * 0.5, within(1e-9));
  }
}

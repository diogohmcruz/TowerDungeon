package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.FloorCurve;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/**
 * The curves of Floors 6 to 100 ({@code docs/research/curve-floors-6-to-100.md}): the anchors read
 * smoothly, Floors 1 to 5 stay on the formula as measured, and the garrison budget, the tier
 * window, a Guardian's health, the Supplies drain and the Carrying Capacity follow the config.
 */
class ScalingCurveTest {
  private final GameProperties config = new GameProperties();

  private Tower tower(GameProperties props) {
    var plan =
        ArchitecturePlan.generate(
            props.getArchitecture().getSeed(),
            100,
            props.getBoss().getInterval(),
            props.getEnemies().getSurgeInterval());
    return new Tower(100, props.getBoss(), props.getEnemies(), new RandomSource(3), plan);
  }

  @Test
  void aCurveHoldsItsAnchorsAndMovesGeometricallyBetweenThem() {
    var anchors = Map.of(10, 4.0, 20, 16.0);

    assertThat(FloorCurve.at(anchors, 10)).isEqualTo(4.0);
    assertThat(FloorCurve.at(anchors, 20)).isEqualTo(16.0);
    assertThat(FloorCurve.at(anchors, 15)).isCloseTo(8.0, within(1e-9));
    assertThat(FloorCurve.at(anchors, 1)).isEqualTo(4.0);
    assertThat(FloorCurve.at(anchors, 99)).isEqualTo(16.0);
    assertThat(FloorCurve.at(Map.of(), 5)).isZero();
  }

  @Test
  void aCurveThroughZeroMovesLinearly() {
    assertThat(FloorCurve.at(Map.of(10, 0.0, 20, 4.0), 15)).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void floorsOneToFiveStayOnTheMeasuredFormula() {
    var tower = tower(config);
    for (var floor = 1; floor <= 5; floor++) {
      var formula = 3.0 + 0.8 * floor + 0.04 * floor * floor;
      var gentle = 0.45 + 0.55 * (floor - 1) / 9;
      var surge = floor == 5 ? 1.5 : 1.0;
      assertThat(tower.easedBaseBudget(floor))
          .as("floor %d", floor)
          .isCloseTo(formula * gentle * surge, within(1e-9));
      assertThat(config.getEnemies().curveGoverns(floor)).isFalse();
      assertThat(config.getLoop().supplyDrainAt(floor)).isEqualTo(1.0);
    }
  }

  @Test
  void theBudgetCurveRisesFromFloorSixToOneHundredAndHoldsItsAnchors() {
    var enemies = config.getEnemies();
    var tower = tower(config);
    var steady =
        IntStream.rangeClosed(6, 100)
            .filter(floor -> floor % enemies.getSurgeInterval() != 0)
            .boxed()
            .toList();
    for (var i = 1; i < steady.size(); i++) {
      assertThat(tower.easedBaseBudget(steady.get(i)))
          .as("floor %d", steady.get(i))
          .isGreaterThanOrEqualTo(tower.easedBaseBudget(steady.get(i - 1)));
    }
    // Floor 59 is a pinned anchor and no surge floor, so its budget is the anchor itself.
    assertThat(tower.easedBaseBudget(59)).isCloseTo(enemies.getBudgetCurve().get(59), within(1e-9));
    assertThat(enemies.getBudgetCurve().keySet()).contains(50, 59, 60, 80, 100);
  }

  @Test
  void aSurgeFloorBeyondTheFifthIsDenserByTheUpperSurgeMultiplier() {
    var tower = tower(config);
    var enemies = config.getEnemies();

    var steady = FloorCurve.at(enemies.getBudgetCurve(), 55);

    assertThat(tower.easedBaseBudget(55))
        .isCloseTo(steady * enemies.getSurgeMultiplierUpper(), within(1e-9));
  }

  @Test
  void aDeepGarrisonDrawsOnlyFromItsTierWindowAndHasNoSlimes() {
    var tower = tower(config);
    tower.startNewRunAt(75);

    var types = tower.getCurrentTowerFloor().getEnemies().stream().map(Enemy::getStats).toList();

    assertThat(types).isNotEmpty();
    assertThat(types)
        .allMatch(
            type -> type == EnemyStats.CHIMERA || type == EnemyStats.CHYMUS_INSECT,
            "Chimeras and Chymus Insects, the window of the deep Floors");
  }

  @Test
  void anEarlyGarrisonKeepsItsWholeRangeOfTiers() {
    var tower = tower(config);
    tower.startNewRunAt(35);

    var types = tower.getCurrentTowerFloor().getEnemies().stream().map(Enemy::getStats).toList();

    assertThat(types).contains(EnemyStats.SLIME);
  }

  @Test
  void aGuardianHasTheTotalHealthOfTheCurveAndKeepsItsArchetype() {
    var tower = tower(config);
    var curve = config.getBoss().getHealthCurve();

    for (var floor : new int[] {10, 30, 80, 100}) {
      tower.startNewRunAt(floor);
      var guardian = tower.getCurrentTowerFloor().getEnemies().getFirst();
      assertThat(guardian.getMaxHealth())
          .as("floor %d", floor)
          .isCloseTo(curve.get(floor), within(1e-6));
    }
    tower.startNewRunAt(80);
    assertThat(tower.getCurrentTowerFloor().getEnemies().getFirst().getStats())
        .isEqualTo(EnemyStats.DRAGON);
  }

  @Test
  void theDragonOnFloorEightyIsTheHardestFightOfTheSizedGuardians() {
    var health = new TreeMap<>(config.getBoss().getHealthCurve());
    var dragons = health.tailMap(80, true);

    assertThat(dragons.get(80)).isGreaterThanOrEqualTo(dragons.get(90));
    assertThat(dragons.get(90)).isGreaterThanOrEqualTo(dragons.get(100));
  }

  @Test
  void theDrainCurveLetsADeepPartyEatLessPerTickAndNeverMoreThanTheMeasuredRate() {
    var loop = config.getLoop();
    var previous = 1.0;
    for (var floor = 1; floor <= 100; floor++) {
      assertThat(loop.supplyDrainAt(floor)).isLessThanOrEqualTo(previous).isPositive();
      previous = loop.supplyDrainAt(floor);
    }
    assertThat(loop.supplyDrainAt(100)).isLessThan(0.2);
  }

  @Test
  void porterAndSupplyLinesCapacityAreMultipliedByTheCarryMultiplier() {
    var state = new GameState(config);
    var multiplier = config.getSupply().getCarryMultiplier();
    state.grant(ResourceType.MATERIALS, 1e9);
    var before = state.getSupplyCapacityBonus();

    state.applyUpgrade(
        io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType.SUPPLY_LINES);

    assertThat(state.getSupplyCapacityBonus() - before)
        .isCloseTo(
            io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType.SUPPLY_LINES
                    .getCapacityPerLevel()
                * multiplier,
            within(1e-9));
  }
}

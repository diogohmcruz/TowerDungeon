package io.github.diogohmcruz.towerdungeon.agent.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;

class FloorDuelTest {
  /** The Party the baseline's bots set out with on Floor 1: about eight Warriors and Archers. */
  private static final PartyPlan START =
      PartyPlan.of(Map.of(UnitStats.WARRIOR, 4, UnitStats.ARCHER, 4));

  private final FloorDuel duel = FloorDuel.standalone(new GameProperties());

  @BeforeAll
  static void quietTheGame() {
    // A duel plays thousands of Floors: the game's own INFO lines would bury the test output.
    ((Logger) LoggerFactory.getLogger("io.github.diogohmcruz.towerdungeon.domain"))
        .setLevel(Level.ERROR);
  }

  @Test
  void theSamePlanFloorAndSeedGiveTheSameReport() {
    var plan = START.withTowerMemoryStep(2).withEasing(Set.of(10));
    var first = duel.duel(plan, 4, 40, 7);
    var again = FloorDuel.standalone(new GameProperties()).duel(plan, 4, 40, 7);

    assertThat(again).isEqualTo(first);
    assertThat(duel.duel(plan, 4, 40, 8)).isNotEqualTo(first);
  }

  @Test
  void aRunIsFixedByItsSeedAlone() {
    var runs = duel.fight(START, 3, 5, 11);
    var alone = duel.fight(START, 3, 1, 11);

    assertThat(alone.getFirst()).isEqualTo(runs.getFirst());
  }

  /**
   * The greedy bot's first Expedition in the baseline (seed 1, 400 Campaigns, Risen out of the way
   * as it is on Floors nobody has been Wiped on): four Warriors and two Archers.
   */
  @Test
  void floorOneAndTwoMatchTheBaselineForTheSamePlan() {
    var party = PartyPlan.of(Map.of(UnitStats.WARRIOR, 4, UnitStats.ARCHER, 2)).withFounders(true);
    var one = duel.duel(party, 1, 400, 1);
    var two = duel.duel(party, 2, 400, 1);

    assertThat(one.garrisonHealth()).isCloseTo(17.5, within(0.03 * 17.5));
    assertThat(two.garrisonHealth()).isCloseTo(23.3, within(0.03 * 23.3));
    assertThat(one.winRate()).isGreaterThan(0.95);
    assertThat(one.ticksMean()).isCloseTo(5.49, within(0.10 * 5.49));
    assertThat(one.damagePerUnitTick()).isCloseTo(0.567, within(0.10 * 0.567));
    assertThat(one.unitsLost()).isCloseTo(0.63, within(0.25));
  }

  /**
   * Garrison health on Floors 1 to 5 against the baseline's per-Floor rows. The baseline mixes
   * Expeditions that met a Floor clean with ones that met the Risen a Wipe left there (about 15
   * health each), so each of its rows lies between the duel with no Risen and the duel with one.
   */
  @Test
  void floorsOneToFiveHaveTheBaselinesShape() {
    var baseline = Map.of(1, 17.9, 2, 30.8, 3, 29.9, 4, 39.7, 5, 71.6);
    for (var floor = 1; floor <= 5; floor++) {
      var clean = duel.duel(START, floor, 200, 1).garrisonHealth();
      var haunted = duel.duel(START.withRisen(1), floor, 200, 1).garrisonHealth();
      assertThat(baseline.get(floor))
          .as("Floor %d: baseline between clean %.1f and haunted %.1f", floor, clean, haunted)
          .isBetween(clean * 0.97, haunted * 1.03);
    }
    // Floor 5 is a Surge Floor, and no starting Party clears it, as in the baseline.
    var surge = duel.duel(START, 5, 100, 1);
    assertThat(surge.surge()).isTrue();
    assertThat(surge.winRate()).isLessThan(0.05);
    // The Floors grow harder one after the other.
    assertThat(duel.duel(START, 1, 100, 1).winRate())
        .isGreaterThan(duel.duel(START, 3, 100, 1).winRate());
  }

  @Test
  void anyFloorFromSixToOneHundredRunsGuardiansAndSurgesIncluded() {
    var army = PartyPlan.of(Map.of(UnitStats.ROGUE, 60, UnitStats.WARRIOR, 60)).withSupplies(1e6);
    var started = System.nanoTime();
    var reports = new ArrayList<DuelReport>();
    for (var floor = 6; floor <= 100; floor++) {
      reports.add(duel.duel(army.withCarryingCapacity(1e6), floor, 10, 3));
    }
    var seconds = (System.nanoTime() - started) / 1e9;

    assertThat(reports).hasSize(95);
    assertThat(reports.stream().filter(DuelReport::guardian).map(DuelReport::floor))
        .containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
    assertThat(reports.stream().filter(DuelReport::surge).map(DuelReport::floor))
        .contains(15, 25, 95)
        .doesNotContain(10, 20, 100);
    assertThat(reports).allSatisfy(r -> assertThat(r.timeouts()).isZero());
    assertThat(seconds).as("95 Floors of ten runs, in seconds").isLessThan(60d);
  }

  @Test
  void bothSidesOfTheSupplyDrainShowInTheReport() {
    var starved = START.withCarryingCapacity(10d);
    var fed = START.withCarryingCapacity(400d);

    var thin = duel.duel(starved, 3, 60, 5);
    var full = duel.duel(fed, 3, 60, 5);

    assertThat(thin.suppliesSpent()).isLessThanOrEqualTo(10.0 + 1e-9);
    assertThat(thin.takenStarvation()).isGreaterThan(full.takenStarvation());
    assertThat(thin.winRate()).isLessThan(full.winRate());
  }

  @Test
  void upgradesAndPartySizeMoveTheOutcome() {
    var sharper = START.withUpgrades(Map.of(UpgradeType.SHARPENED_ARMS, 4));
    var bigger = PartyPlan.of(Map.of(UnitStats.WARRIOR, 8, UnitStats.ARCHER, 8));

    var base = duel.duel(START, 3, 120, 9);

    assertThat(duel.duel(sharper, 3, 120, 9).winRate()).isGreaterThan(base.winRate());
    assertThat(duel.duel(bigger, 3, 120, 9).winRate()).isGreaterThan(base.winRate());
  }

  @Test
  void towerMemoryHardensAndEasingThinsTheGarrison() {
    var plain = duel.duel(START, 8, 60, 2);
    var remembered = duel.duel(START.withTowerMemoryStep(4), 8, 60, 2);
    var eased = duel.duel(START.withEasing(Set.of(10)), 8, 60, 2);

    assertThat(remembered.garrisonHealth()).isGreaterThan(plain.garrisonHealth());
    assertThat(eased.garrisonHealth()).isLessThan(plain.garrisonHealth());
  }

  @Test
  void aimOfAUnitTypeCanBeReplaced() {
    var head = Map.of(io.github.diogohmcruz.towerdungeon.domain.models.Aim.BODY, 1.0);
    var aimed = START.withAim(Map.of(UnitStats.WARRIOR, head, UnitStats.ARCHER, head));

    assertThat(duel.duel(aimed, 2, 60, 4)).isNotEqualTo(duel.duel(START, 2, 60, 4));
  }

  @Test
  void aPlanThatCannotBeFoughtIsRefused() {
    assertThatThrownBy(() -> duel.duel(START, 0, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> duel.duel(START, 101, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> duel.duel(START, 1, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PartyPlan.of(UnitStats.WARRIOR, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> START.withTowerMemoryStep(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theCommandLineReadsAPlan() {
    var settings =
        Map.of(
            "duel.units", "warrior:4, ARCHER:2",
            "duel.upgrades", "SHARPENED_ARMS:3",
            "duel.capacity", "150",
            "duel.memory", "2",
            "duel.easing", "10,20-21");
    var plan = FloorDuelCli.plan(settings::get);

    assertThat(plan.units()).containsEntry(UnitStats.WARRIOR, 4).containsEntry(UnitStats.ARCHER, 2);
    assertThat(plan.upgrades()).containsEntry(UpgradeType.SHARPENED_ARMS, 3);
    assertThat(plan.carryingCapacity()).isEqualTo(150d);
    assertThat(plan.towerMemoryStep()).isEqualTo(2);
    assertThat(plan.easing()).containsExactly(10, 20, 21);
    assertThat(FloorDuelCli.floors("1-3,10")).containsExactly(1, 2, 3, 10);
  }
}

package io.github.diogohmcruz.towerdungeon.domain.prestige;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** Golden tests for the Toll formula (docs/brainstorm/prestige-and-persistence.md, 3.4). */
class TollCalculatorTest {
  private static final double TOLERANCE = 0.01;
  private static final GameProperties.Prestige CONFIG = new GameProperties.Prestige();

  private static Tally tally(
      double food,
      double materials,
      double credits,
      double relics,
      int expeditions,
      int highestFloor,
      int guardians) {
    return new Tally(food, materials, credits, relics, expeditions, highestFloor, guardians);
  }

  private static Tally food(double food) {
    return tally(food, 0, 0, 0, 0, 0, 0);
  }

  private static Toll calc(Tally tally) {
    return TollCalculator.calculate(tally, TollContext.NONE, CONFIG);
  }

  private static Toll calc(Tally tally, TollContext context) {
    return TollCalculator.calculate(tally, context, CONFIG);
  }

  @Test
  void firstExpeditionOfFloorsOneToTenIsAboutEightMarks() {
    var result = calc(tally(0, 375, 150, 3, 1, 10, 1));

    assertThat(result.materialsMarks()).isCloseTo(0.89, within(TOLERANCE));
    assertThat(result.creditsMarks()).isCloseTo(0.45, within(TOLERANCE));
    assertThat(result.relicsMarks()).isCloseTo(0.39, within(TOLERANCE));
    assertThat(result.expeditionsMarks()).isCloseTo(0.73, within(TOLERANCE));
    assertThat(result.ascentMarks()).isCloseTo(4.0, within(TOLERANCE));
    assertThat(result.guardianMarks()).isCloseTo(1.5, within(TOLERANCE));
    assertThat(result.marks()).isCloseTo(8.0, within(0.1));
    assertThat(result.legacy()).isEqualTo(1); // floor(8.0 / 9) is 0, plus New Ground 1
  }

  @Test
  void idleFoodCheck() {
    assertThat(calc(food(54_000)).foodMarks()).isCloseTo(3 * Math.sqrt(0.9), within(1e-9));
    assertThat(calc(food(54_000)).foodMarks()).isCloseTo(2.85, within(TOLERANCE));
    assertThat(calc(food(288_000)).foodMarks()).isCloseTo(6.6, within(0.05));
  }

  @Test
  void doublingASquareRootedCounterMultipliesItsMarksBySqrtTwo() {
    var one = calc(tally(10_000, 5_000, 2_000, 100, 5, 0, 0));
    var two = calc(tally(20_000, 10_000, 4_000, 200, 10, 0, 0));

    assertThat(two.foodMarks() / one.foodMarks()).isCloseTo(1.414, within(0.001));
    assertThat(two.materialsMarks() / one.materialsMarks()).isCloseTo(1.414, within(0.001));
    assertThat(two.creditsMarks() / one.creditsMarks()).isCloseTo(1.414, within(0.001));
    assertThat(two.relicsMarks() / one.relicsMarks()).isCloseTo(1.414, within(0.001));
    assertThat(two.expeditionsMarks() / one.expeditionsMarks()).isCloseTo(1.414, within(0.001));
  }

  @Test
  void ascentAndGuardiansStayLinear() {
    var one = calc(tally(0, 0, 0, 0, 0, 10, 2));
    var two = calc(tally(0, 0, 0, 0, 0, 20, 4));

    assertThat(two.ascentMarks()).isCloseTo(2 * one.ascentMarks(), within(1e-9));
    assertThat(two.guardianMarks()).isCloseTo(2 * one.guardianMarks(), within(1e-9));
  }

  @Test
  void highestFloorAboveTheDoorPaysNoMoreThanTheDoor() {
    var atDoor = calc(tally(0, 0, 0, 0, 0, 99, 0));
    var beyond = calc(tally(0, 0, 0, 0, 0, 100, 0));

    assertThat(atDoor.ascentMarks()).isCloseTo(0.4 * 99, within(1e-9));
    assertThat(beyond.ascentMarks()).isEqualTo(atDoor.ascentMarks());
  }

  @Test
  void highestFloorAtOrBelowTheBaselinePaysNoAscent() {
    var context = new TollContext(0, 0, 0.0, 0, 30);

    assertThat(calc(tally(0, 0, 0, 0, 0, 30, 0), context).ascentMarks()).isZero();
    assertThat(calc(tally(0, 0, 0, 0, 0, 12, 0), context).ascentMarks()).isZero();
    assertThat(calc(tally(0, 0, 0, 0, 0, 40, 0), context).ascentMarks())
        .isCloseTo(0.4 * 10, within(1e-9));
  }

  @Test
  void loadScalesMarksAndIsCapped() {
    var tally = tally(0, 0, 0, 0, 0, 50, 0); // S = 20
    var none = calc(tally);
    var three = calc(tally, new TollContext(3, 0, 0.0, 0, 0));
    var atCap = calc(tally, new TollContext(8, 0, 0.0, 0, 0));
    var overCap = calc(tally, new TollContext(20, 0, 0.0, 0, 0));

    assertThat(none.marks()).isCloseTo(20.0, within(1e-9));
    assertThat(three.marks()).isCloseTo(20.0, within(1e-9));
    assertThat(three.toll()).isCloseTo(20.0 * 1.15, within(1e-9));
    assertThat(atCap.toll()).isCloseTo(20.0 * 1.4, within(1e-9));
    assertThat(overCap.toll()).isEqualTo(atCap.toll());
  }

  @Test
  void residueIsAddedToTheTollAndTheRemainderCarriesOver() {
    var result = calc(tally(0, 0, 0, 0, 0, 50, 0), new TollContext(0, 0, 2.5, 0, 0)); // 20 + 2.5

    assertThat(result.toll()).isCloseTo(22.5, within(1e-9));
    assertThat(result.legacy()).isEqualTo(2);
    assertThat(result.residueNext()).isCloseTo(4.5, within(1e-9));
  }

  @Test
  void anExactMultipleOfThePointsDivisorLeavesNoResidue() {
    var result = calc(tally(0, 0, 0, 0, 0, 0, 0), new TollContext(0, 0, 18.0, 0, 0));

    assertThat(result.toll()).isEqualTo(18.0);
    assertThat(result.legacy()).isEqualTo(2);
    assertThat(result.residueNext()).isZero();
  }

  @Test
  void newGroundCountsGuardiansBeyondTheLifetimeBestAndIsIncludedInLegacy() {
    var tally = tally(0, 0, 0, 0, 0, 0, 4); // S = 6.0, floor(6 / 9) = 0

    assertThat(calc(tally, new TollContext(0, 0, 0.0, 1, 0)).newGround()).isEqualTo(3);
    assertThat(calc(tally, new TollContext(0, 0, 0.0, 1, 0)).legacy()).isEqualTo(3);
    assertThat(calc(tally, new TollContext(0, 0, 0.0, 4, 0)).newGround()).isZero();
    assertThat(calc(tally, new TollContext(0, 0, 0.0, 7, 0)).newGround()).isZero();
  }

  @Test
  void offeringIsAddedToLegacy() {
    var result = calc(tally(0, 0, 0, 0, 0, 0, 0), new TollContext(0, 3, 0.0, 0, 0));

    assertThat(result.legacy()).isEqualTo(3);
  }

  @Test
  void zeroCountersGiveTheResidueAsTollAndOnlyNewGroundAndOfferingAsLegacy() {
    var empty = calc(tally(0, 0, 0, 0, 0, 0, 0), new TollContext(0, 2, 4.0, 0, 0));

    assertThat(empty.marks()).isZero();
    assertThat(empty.toll()).isEqualTo(4.0);
    assertThat(empty.legacy()).isEqualTo(2);
    assertThat(empty.residueNext()).isEqualTo(4.0);
    assertThat(calc(tally(0, 0, 0, 0, 0, 0, 0)).legacy()).isZero();
  }

  @Test
  void applicationYmlBindsToTheSameValuesAsTheDefaults() throws Exception {
    var sources =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));
    var bound =
        new Binder(ConfigurationPropertySources.from(sources))
            .bind("game", GameProperties.class)
            .get();

    assertThat(bound.getPrestige()).isEqualTo(CONFIG);
    assertThat(bound.getPrestige().getCounters().getFoodGrown().getPar()).isEqualTo(60000.0);
  }

  @Test
  void defaultsBindToTheDesignedValues() {
    assertThat(CONFIG.getPointsDivisor()).isEqualTo(9.0);
    assertThat(CONFIG.getAscentMarksPerFloor()).isEqualTo(0.4);
    assertThat(CONFIG.getGuardianMarks()).isEqualTo(1.5);
    assertThat(CONFIG.getLoadMark()).isEqualTo(0.05);
    assertThat(CONFIG.getLoadCap()).isEqualTo(8);
    assertThat(CONFIG.getDoorFloor()).isEqualTo(99);
    assertThat(CONFIG.getCounters().getRelicsBanked().getPar()).isEqualTo(320.0);
  }
}

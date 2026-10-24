package io.github.diogohmcruz.towerdungeon.domain.models;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/** The Year and its Quarters: the clock, the effects at the boundary ticks, and the save. */
class YearTest {
  private final GameProperties config = new GameProperties();
  private final YearRules rules = new YearRules(config.getYear());

  private GameState at(int tick) {
    var state = new GameState(config);
    for (var i = 0; i < tick; i++) {
      state.advanceYear();
    }
    return state;
  }

  @Test
  void aYearIsSixHundredTicksInFourQuartersOfOneHundredAndFifty() {
    assertThat(rules.quarterAt(0)).isEqualTo(Quarter.SPRING);
    assertThat(rules.quarterAt(149)).isEqualTo(Quarter.SPRING);
    assertThat(rules.quarterAt(150)).isEqualTo(Quarter.SUMMER);
    assertThat(rules.quarterAt(299)).isEqualTo(Quarter.SUMMER);
    assertThat(rules.quarterAt(300)).isEqualTo(Quarter.AUTUMN);
    assertThat(rules.quarterAt(449)).isEqualTo(Quarter.AUTUMN);
    assertThat(rules.quarterAt(450)).isEqualTo(Quarter.WINTER);
    assertThat(rules.quarterAt(599)).isEqualTo(Quarter.WINTER);
    assertThat(rules.nextQuarterStart(149)).isEqualTo(150);
    assertThat(rules.nextQuarterStart(599)).isZero();
  }

  @Test
  void aCampaignBeginsInSpringOfYearOne_AndTheYearTurnsAfterSixHundredTicks() {
    var state = new GameState(config);
    assertThat(state.getYear()).isEqualTo(1);
    assertThat(state.getYearTick()).isZero();
    assertThat(state.getQuarter()).isEqualTo(Quarter.SPRING);

    var turned = at(600);

    assertThat(turned.getYear()).isEqualTo(2);
    assertThat(turned.getYearTick()).isZero();
    assertThat(turned.getQuarter()).isEqualTo(Quarter.SPRING);
    assertThat(at(599).getQuarter()).isEqualTo(Quarter.WINTER);
    assertThat(at(150).getQuarter()).isEqualTo(Quarter.SUMMER);
    assertThat(at(150).getNextQuarter()).isEqualTo(Quarter.AUTUMN);
  }

  @Test
  void theDangerAtSeaIsOnePercentInMidSummerAndTwentyInMidWinter() {
    assertThat(rules.seaRisk(225)).isCloseTo(0.01, within(1e-12));
    assertThat(rules.seaRisk(525)).isCloseTo(0.20, within(1e-12));
    for (var tick = 0; tick < 600; tick++) {
      assertThat(rules.seaRisk(tick)).isBetween(0.01 - 1e-12, 0.20 + 1e-12);
    }
  }

  @Test
  void theDangerAtSeaChangesOnlyEveryTenTicks() {
    var changes = 0;
    for (var tick = 1; tick < 600; tick++) {
      if (rules.seaRisk(tick) != rules.seaRisk(tick - 1)) {
        changes++;
        assertThat(tick % 10).isEqualTo(5);
      }
    }
    assertThat(changes).isEqualTo(60);
    assertThat(rules.seaRisk(215)).isEqualTo(rules.seaRisk(224));
    assertThat(rules.seaRisk(225)).isNotEqualTo(rules.seaRisk(224));
  }

  @Test
  void theFrameFactsCarryTheDangerNowAndAtTheNextQuarter() {
    var state = at(225);

    assertThat(state.getSeaRisk()).isCloseTo(0.01, within(1e-12));
    assertThat(state.getNextSeaRisk()).isEqualTo(rules.seaRisk(300));
  }

  @Test
  void springLightensTheSanityDrains_AndOnlySpring() {
    assertThat(at(149).sanityDrainScale()).isEqualTo(0.80);
    assertThat(at(150).sanityDrainScale()).isEqualTo(1d);
    assertThat(at(599).sanityDrainScale()).isEqualTo(1d);
    assertThat(at(600).sanityDrainScale()).isEqualTo(0.80);
  }

  @Test
  void springScalesTheDepthDrainButNotTheGains() {
    var spring = partyAt(0);
    var summer = partyAt(150);
    var before = firstSanity(spring);

    spring.drainDepthSanity(10);
    summer.drainDepthSanity(10);

    var drainSummer = before - firstSanity(summer);
    assertThat(before - firstSanity(spring)).isCloseTo(drainSummer * 0.80, within(1e-9));
    spring.rewardFloorClimbed();
    summer.rewardFloorClimbed();
    assertThat(firstSanity(summer) - (before - drainSummer))
        .isCloseTo(firstSanity(spring) - (before - drainSummer * 0.80), within(1e-9));
  }

  @Test
  void summerLightensTheSuppliesDrainByFivePercent() {
    var spring = at(149);
    var summer = at(150);
    var autumn = at(300);
    spring.setSupplies(100d);
    summer.setSupplies(100d);
    autumn.setSupplies(100d);

    spring.drainExpeditionSupplies(4);
    summer.drainExpeditionSupplies(4);
    autumn.drainExpeditionSupplies(4);

    var plain = 100d - spring.getSupplies();
    assertThat(plain).isGreaterThan(0d);
    assertThat(100d - summer.getSupplies()).isCloseTo(plain * 0.95, within(1e-9));
    assertThat(100d - autumn.getSupplies()).isCloseTo(plain, within(1e-9));
  }

  @Test
  void autumnRaisesTheFieldsByTwentyPercent_AndNoQuarterLowersFoodIncome() {
    var base = new GameState(config).getVillage().getProductionRate();
    assertThat(base).isPositive();

    assertThat(at(299).getVillage().getProductionRate()).isEqualTo(base);
    assertThat(at(300).getVillage().getProductionRate()).isCloseTo(base * 1.2, within(1e-9));
    assertThat(at(449).getVillage().getProductionRate()).isCloseTo(base * 1.2, within(1e-9));
    assertThat(at(450).getVillage().getProductionRate()).isEqualTo(base);
    for (var tick = 0; tick < 600; tick += 7) {
      assertThat(at(tick).getVillage().getProductionRate()).isGreaterThanOrEqualTo(base);
    }
  }

  @Test
  void aQuarterNeverLowersFoodEvenIfTheTuningAsksIt() {
    config.getYear().setAutumnFieldsYield(0.5);

    assertThat(at(300).getVillage().getProductionRate())
        .isEqualTo(new GameState(config).getVillage().getProductionRate());
  }

  @Test
  void winterRaisesAttritionByTenPercent_ButNeverStarvationDamage() {
    assertThat(at(449).attritionScale()).isEqualTo(1d);
    assertThat(at(450).attritionScale()).isEqualTo(1.10);
    assertThat(at(599).attritionScale()).isEqualTo(1.10);
    assertThat(at(600).attritionScale()).isEqualTo(1d);
    assertThat(at(450).starvationDamage()).isEqualTo(at(0).starvationDamage());

    var autumn = partyAt(300);
    var winter = partyAt(450);
    var healthBefore = healthOf(autumn);
    autumn.passingTime(1000L);
    winter.passingTime(1000L);
    assertThat(healthBefore - healthOf(winter))
        .isCloseTo((healthBefore - healthOf(autumn)) * 1.10, within(1e-9));
  }

  @Test
  void theYearAndItsTickAreSaved_AndAnOldSaveIsYearOneInSpring() {
    var state = at(725);
    var snapshot = state.toSnapshot();
    assertThat(snapshot.campaign().year()).isEqualTo(2);
    assertThat(snapshot.campaign().yearTick()).isEqualTo(125);

    var restored = GameState.restore(snapshot, config, new RandomSource(1), plan());
    assertThat(restored.getYear()).isEqualTo(2);
    assertThat(restored.getYearTick()).isEqualTo(125);

    var c = snapshot.campaign();
    var old =
        new io.github.diogohmcruz.towerdungeon.domain.models.snapshot.CampaignSnapshot(
            c.credit(),
            c.reserves(),
            c.vault(),
            c.upgrades(),
            c.milestones(),
            c.shortcutsOpened(),
            c.tower(),
            c.highestFloor(),
            c.expeditionsCompleted(),
            c.enemiesDefeated(),
            c.outcome(),
            c.lastExpeditionSummary(),
            c.villageFood(),
            c.villagers(),
            c.trades(),
            c.artifactsTaken(),
            c.tally(),
            c.villageTier(),
            c.wallOfNames(),
            c.phase(),
            c.pending(),
            c.choices(),
            c.portBuilt(),
            c.boatTicks(),
            c.arrivalsCame(),
            0,
            0,
            c.voyage());
    var loaded =
        GameState.restore(
            new io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot(
                old, null, snapshot.random(), null),
            config,
            new RandomSource(1),
            plan());
    assertThat(loaded.getYear()).isEqualTo(1);
    assertThat(loaded.getQuarter()).isEqualTo(Quarter.SPRING);
  }

  @Test
  void aRestoredAutumnKeepsItsHarvest() {
    var base = new GameState(config).getVillage().getProductionRate();
    var restored = GameState.restore(at(310).toSnapshot(), config, new RandomSource(1), plan());

    assertThat(restored.getVillage().getProductionRate()).isCloseTo(base * 1.2, within(1e-9));
  }

  @Test
  void aCampaignThatHasEndedKeepsNoMoreTime() {
    var state = at(10);
    state.setPhase(CampaignPhase.OFFER);

    state.advanceYear();

    assertThat(state.getYearTick()).isEqualTo(10);
  }

  private static ArchitecturePlan plan() {
    return org.mockito.Mockito.mock(ArchitecturePlan.class);
  }

  private GameState partyAt(int tick) {
    var state = at(tick);
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setSanityRules(config.getSanity());
    unit.changeSanity(-30);
    state
        .getUnitsOnTower()
        .computeIfAbsent(UnitStats.WARRIOR, _ -> new java.util.ArrayList<>())
        .add(unit);
    return state;
  }

  private static double firstSanity(GameState state) {
    return state.getUnitsOnTower().values().stream()
        .flatMap(java.util.List::stream)
        .findFirst()
        .orElseThrow()
        .getSanity();
  }

  private static double healthOf(GameState state) {
    return state.getUnitsOnTower().values().stream()
        .flatMap(java.util.List::stream)
        .mapToDouble(Unit::getCurrentHealth)
        .sum();
  }
}

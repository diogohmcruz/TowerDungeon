package io.github.diogohmcruz.towerdungeon.domain.models;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.FoodHealingConfig;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.RoundRobinFoodDistributionStrategy;

class RolledStrengthsTest {
  private static final double SPREAD = 0.30;

  private final GameProperties config = new GameProperties();
  private final GameState state = new GameState(config);

  @Test
  void theDefaultSpreadIsThirtyPercent() {
    assertThat(config.getRecruit().getStrengthSpread()).isEqualTo(SPREAD);
  }

  @Test
  void aSeededRecruitRollsHealthThenDamagePinnedToTheSeed() {
    var reference = new SplittableRandom(42);
    var healthFactor = reference.nextDouble(0.7, 1.3);
    var damageFactor = reference.nextDouble(0.7, 1.3);

    var unit = state.recruit(UnitStats.WARRIOR, new SplittableRandom(42));

    assertThat(unit.getHealthFactor()).isEqualTo(healthFactor);
    assertThat(unit.getDamageFactor()).isEqualTo(damageFactor);
    assertThat(unit.getMaxHealth()).isEqualTo(UnitStats.WARRIOR.getHealth() * healthFactor);
    assertThat(unit.getCurrentHealth()).isEqualTo(unit.getMaxHealth());
    assertThat(unit.getDamage()).isEqualTo(UnitStats.WARRIOR.getDamage() * damageFactor);
  }

  @Test
  void manyRollsStayWithinTheSpreadAndActuallyVary() {
    var random = new SplittableRandom(1);
    var healthFactors = new ArrayList<Double>();
    for (var i = 0; i < 2000; i++) {
      var unit = state.recruit(UnitStats.ARCHER, random);
      assertThat(unit.getHealthFactor()).isBetween(1 - SPREAD, 1 + SPREAD);
      assertThat(unit.getDamageFactor()).isBetween(1 - SPREAD, 1 + SPREAD);
      healthFactors.add(unit.getHealthFactor());
    }
    assertThat(healthFactors).doesNotHaveDuplicates().hasSize(2000);
    assertThat(healthFactors.stream().mapToDouble(d -> d).min().orElseThrow()).isLessThan(0.75);
    assertThat(healthFactors.stream().mapToDouble(d -> d).max().orElseThrow()).isGreaterThan(1.25);
  }

  @Test
  void aUnitWithFixedStrengthsSkipsTheRollAndKeepsItsPresetFactors() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setFixedStrengths(true);
    unit.setHealthFactor(5.0);
    unit.setDamageFactor(5.0);

    unit.rollStrengths(new SplittableRandom(3), SPREAD);

    assertThat(unit.getHealthFactor()).isEqualTo(5.0);
    assertThat(unit.getDamageFactor()).isEqualTo(5.0);
    assertThat(unit.getMaxHealth()).isEqualTo(UnitStats.WARRIOR.getHealth() * 5.0);
    assertThat(unit.getCurrentHealth()).isEqualTo(unit.getMaxHealth());
    assertThat(unit.getDamage()).isEqualTo(UnitStats.WARRIOR.getDamage() * 5.0);
  }

  @Test
  void aFixedUnitWithoutPresetsKeepsExactlyOne() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setFixedStrengths(true);

    unit.rollStrengths(new SplittableRandom(3), SPREAD);

    assertThat(unit.getHealthFactor()).isEqualTo(1.0);
    assertThat(unit.getDamageFactor()).isEqualTo(1.0);
  }

  @Test
  void aNewUnitIsAverageUntilRolled() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());

    assertThat(unit.getHealthFactor()).isEqualTo(1.0);
    assertThat(unit.getMaxHealth()).isEqualTo(UnitStats.WARRIOR.getHealth());
    assertThat(unit.getDamage()).isEqualTo(UnitStats.WARRIOR.getDamage());
  }

  @Test
  void toStringShowsTheUnitsOwnMaximum() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setHealthFactor(1.3);

    assertThat(unit.toString())
        .contains(
            String.format(
                "%.1f/%.1f", unit.getCurrentHealth(), UnitStats.WARRIOR.getHealth() * 1.3));
  }

  @Test
  void foodHealsAUnitUpToItsOwnMaximumNotTheTypes() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setHealthFactor(1.3);
    var own = unit.getMaxHealth();
    unit.setCurrentHealth(own - 20);
    var villageConfig = new GameProperties.Village();
    villageConfig.setStartingFood(1000);
    var village = new Village(villageConfig);

    var strategy = new RoundRobinFoodDistributionStrategy();
    for (var i = 0; i < 20; i++) {
      strategy.distribute(List.of(unit), village, new FoodHealingConfig(1.0, 10.0));
    }

    assertThat(unit.getCurrentHealth()).isEqualTo(own, within(1e-9));
    assertThat(unit.isWounded()).isFalse();
    assertThat(own).isGreaterThan(UnitStats.WARRIOR.getHealth());
  }

  @Test
  void homePartyWoundsMeasureMissingHealthAgainstTheUnitsOwnMaximum() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.setHealthFactor(1.3);
    unit.setCurrentHealth(unit.getMaxHealth() - 7);
    state.getUnits().computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>()).add(unit);

    assertThat(state.getHomePartyWounds()).isEqualTo(7.0, within(1e-9));
  }
}

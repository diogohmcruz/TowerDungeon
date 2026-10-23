package io.github.diogohmcruz.towerdungeon.domain.models.healing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.Part;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.Village;

class RoundRobinFoodDistributionStrategyTest {
  private static final FoodHealingConfig CONFIG = new FoodHealingConfig(1.0, 5.0);

  private final RoundRobinFoodDistributionStrategy strategy =
      new RoundRobinFoodDistributionStrategy();

  private static Village pantry(double food) {
    var config = new GameProperties.Village();
    config.setStartingFood(food);
    return new Village(config);
  }

  private static Unit wounded(double missing) {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.setCurrentHealth(unit.getStats().getHealth() - missing);
    return unit;
  }

  private static double missing(Unit unit) {
    return unit.getStats().getHealth() - unit.getCurrentHealth();
  }

  @Test
  void healsOneUnitPerTickUpToTheBudgetAndChargesCostPerHp() {
    var village = pantry(100);
    var unit = wounded(20);

    var spent = strategy.distribute(List.of(unit), village, new FoodHealingConfig(2.0, 5.0));

    assertThat(missing(unit)).isEqualTo(15);
    assertThat(spent).isEqualTo(10);
    assertThat(village.getFood()).isEqualTo(90);
  }

  @Test
  void healsOnlyWhatIsMissing() {
    var village = pantry(100);
    var unit = wounded(2);

    var spent = strategy.distribute(List.of(unit), village, CONFIG);

    assertThat(missing(unit)).isZero();
    assertThat(spent).isEqualTo(2);
  }

  @Test
  void cursorMovesToTheNextUnitAfterEachHeal() {
    var village = pantry(100);
    var first = wounded(20);
    var second = wounded(20);
    var units = List.of(first, second);

    strategy.distribute(units, village, CONFIG);
    strategy.distribute(units, village, CONFIG);
    strategy.distribute(units, village, CONFIG);

    assertThat(missing(first)).isEqualTo(10);
    assertThat(missing(second)).isEqualTo(15);
  }

  @Test
  void skipsUnitsAtFullHealth() {
    var village = pantry(100);
    var healthy = new Unit(UnitStats.WARRIOR);
    var hurt = wounded(10);

    strategy.distribute(List.of(healthy, hurt), village, CONFIG);

    assertThat(missing(hurt)).isEqualTo(5);
  }

  @Test
  void healingIsLimitedByWhatThePantryCanAfford() {
    var village = pantry(3);
    var unit = wounded(20);

    var spent = strategy.distribute(List.of(unit), village, CONFIG);

    assertThat(missing(unit)).isEqualTo(17);
    assertThat(spent).isEqualTo(3);
    assertThat(village.getFood()).isZero();
  }

  @Test
  void recoverySpendsTheBudgetOnTheLowestRatioPartAndStopsAtItsMaximum() {
    var village = pantry(100);
    var unit = new Unit(UnitStats.WARRIOR);
    unit.damagePart(BodyPart.HEAD, 1.0);
    unit.damagePart(BodyPart.TORSO, 4.0);

    var spent = strategy.distribute(List.of(unit), village, CONFIG);

    // The head (ratio 0.5) is filled first with 1, then the torso takes the other 4.
    assertThat(spent).isCloseTo(5.0, within(1e-9));
    assertThat(part(unit, BodyPart.HEAD).getCurrentHealth()).isCloseTo(2.0, within(1e-9));
    assertThat(part(unit, BodyPart.TORSO).getCurrentHealth()).isCloseTo(10.0, within(1e-9));
  }

  @Test
  void recoveryNeverBringsBackALostPartAndDoesNotCountItAsMissing() {
    var village = pantry(100);
    var unit = new Unit(UnitStats.WARRIOR);
    unit.damagePart(BodyPart.LEFT_LEG, 100);

    var spent = strategy.distribute(List.of(unit), village, CONFIG);

    assertThat(spent).isZero();
    assertThat(village.getFood()).isEqualTo(100);
    assertThat(part(unit, BodyPart.LEFT_LEG).isLost()).isTrue();
    assertThat(unit.getCurrentHealth()).isEqualTo(unit.getMaxHealth());
  }

  private static Part part(Unit unit, BodyPart kind) {
    return unit.getParts().stream().filter(p -> p.getKind() == kind).findFirst().orElseThrow();
  }

  @Test
  void doesNothingWithoutFoodUnitsOrBudget() {
    var unit = wounded(10);

    assertThat(strategy.distribute(List.of(unit), pantry(0), CONFIG)).isZero();
    assertThat(strategy.distribute(List.of(), pantry(10), CONFIG)).isZero();
    assertThat(strategy.distribute(List.of(unit), pantry(10), new FoodHealingConfig(1.0, 0.0)))
        .isZero();
    assertThat(missing(unit)).isEqualTo(10);
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

class UnitBodyTest {
  private static final double EPS = 1e-9;

  private static Unit warrior() {
    return new Unit(UnitStats.WARRIOR, cosmetic());
  }

  private static Part part(Unit unit, BodyPart kind) {
    return unit.getParts().stream().filter(p -> p.getKind() == kind).findFirst().orElseThrow();
  }

  @Test
  void aUnitHasSixPartsWeightedFromTheConfigThatSumToItsRolledTotal() {
    var unit = warrior();
    unit.setHealthFactor(1.5);
    var total = UnitStats.WARRIOR.getHealth() * 1.5;

    assertThat(unit.getParts()).hasSize(6);
    assertThat(part(unit, BodyPart.TORSO).getMaxHealth()).isCloseTo(total * 0.50, within(EPS));
    assertThat(part(unit, BodyPart.HEAD).getMaxHealth()).isCloseTo(total * 0.10, within(EPS));
    assertThat(part(unit, BodyPart.LEFT_ARM).getMaxHealth()).isCloseTo(total * 0.08, within(EPS));
    assertThat(part(unit, BodyPart.RIGHT_ARM).getMaxHealth()).isCloseTo(total * 0.08, within(EPS));
    assertThat(part(unit, BodyPart.LEFT_LEG).getMaxHealth()).isCloseTo(total * 0.12, within(EPS));
    assertThat(part(unit, BodyPart.RIGHT_LEG).getMaxHealth()).isCloseTo(total * 0.12, within(EPS));
    assertThat(unit.getMaxHealth()).isCloseTo(total, within(EPS));
    assertThat(unit.getCurrentHealth()).isCloseTo(total, within(EPS));
  }

  @Test
  void configuredWeightsAreUsedWhenAppliedToTheBody() {
    var weights = new GameProperties.Body.Weights();
    weights.setTorso(0.4);
    weights.setHead(0.2);
    var unit = warrior();

    unit.setBodyWeights(weights);

    assertThat(part(unit, BodyPart.TORSO).getMaxHealth())
        .isCloseTo(UnitStats.WARRIOR.getHealth() * 0.4, within(EPS));
    assertThat(part(unit, BodyPart.HEAD).getMaxHealth())
        .isCloseTo(UnitStats.WARRIOR.getHealth() * 0.2, within(EPS));
  }

  @Test
  void aHitDrainsEveryPartAtTheSameRatio() {
    var unit = warrior();

    unit.receiveAttack(5.0, null);

    assertThat(unit.getCurrentHealth()).isCloseTo(15.0, within(EPS));
    unit.getParts().forEach(p -> assertThat(p.ratio()).isCloseTo(0.75, within(EPS)));
    assertThat(unit.isDead()).isFalse();
  }

  @Test
  void aHitThatWouldHaveKilledTheOldPoolKillsTheUnitAtTheSameTick() {
    var unit = warrior();
    var oldPool = UnitStats.WARRIOR.getHealth();

    unit.receiveAttack(oldPool - 0.5, null);
    assertThat(unit.isDead()).isFalse();
    unit.receiveAttack(0.5, null);

    assertThat(unit.isDead()).isTrue();
    assertThat(unit.getCurrentHealth()).isZero();
  }

  @Test
  void aSeriesOfSmallHitsKillsExactlyWhenTheirSumReachesTheTotal() {
    var unit = warrior();

    for (var i = 0; i < 6; i++) {
      unit.receiveAttack(20.0 / 7, null);
    }
    assertThat(unit.isDead()).isFalse();
    unit.receiveAttack(20.0 / 7, null);

    assertThat(unit.isDead()).isTrue();
  }

  @Test
  void theWeaknessMultiplierAppliesToTheWholeHit() {
    var healer = new Unit(UnitStats.HEALER, cosmetic());
    var multiplier = UnitStats.HEALER.getWeaknesses().get(AttackType.MELEE);

    healer.receiveAttack(2.0, AttackType.MELEE);

    assertThat(healer.getCurrentHealth())
        .isCloseTo(UnitStats.HEALER.getHealth() - 2.0 * multiplier, within(EPS));
  }

  @Test
  void zeroingTheHeadOrTheTorsoKills() {
    var head = warrior();
    head.damagePart(BodyPart.HEAD, 100);
    var torso = warrior();
    torso.damagePart(BodyPart.TORSO, 100);

    assertThat(head.isDead()).isTrue();
    assertThat(torso.isDead()).isTrue();
  }

  @Test
  void zeroingAnArmLosesItForGoodAndHalvesDamage() {
    var unit = warrior();
    var damage = unit.getDamage();

    unit.damagePart(BodyPart.LEFT_ARM, 100);

    assertThat(unit.isDead()).isFalse();
    assertThat(part(unit, BodyPart.LEFT_ARM).isLost()).isTrue();
    assertThat(unit.getDamage()).isCloseTo(damage * 0.5, within(EPS));
    assertThat(unit.getMaxHealth()).isCloseTo(20.0 * (1 - 0.08), within(EPS));

    unit.damagePart(BodyPart.RIGHT_ARM, 100);
    assertThat(unit.getDamage()).isCloseTo(damage * 0.25, within(EPS));
  }

  @Test
  void aLostArmDoesNotWeakenACaster() {
    var mage = new Unit(UnitStats.MAGE, cosmetic());
    var damage = mage.getDamage();

    mage.damagePart(BodyPart.LEFT_ARM, 100);

    assertThat(mage.getDamage()).isEqualTo(damage);
  }

  @Test
  void zeroingALegLosesItAndMakesTheUnitTakeMoreDamage() {
    var unit = warrior();
    unit.damagePart(BodyPart.RIGHT_LEG, 100);
    var before = unit.getCurrentHealth();

    unit.receiveAttack(2.0, null);

    assertThat(part(unit, BodyPart.RIGHT_LEG).isLost()).isTrue();
    assertThat(before - unit.getCurrentHealth()).isCloseTo(2.0 * 1.25, within(EPS));
  }

  @Test
  void aLostPartNeverReturnsAndIsNotCountedAsMissing() {
    var unit = warrior();
    unit.damagePart(BodyPart.LEFT_LEG, 100);

    unit.heal(100);

    assertThat(part(unit, BodyPart.LEFT_LEG).isLost()).isTrue();
    assertThat(part(unit, BodyPart.LEFT_LEG).getCurrentHealth()).isZero();
    assertThat(unit.isWounded()).isFalse();
    assertThat(unit.getCurrentHealth()).isEqualTo(unit.getMaxHealth());
  }

  @Test
  void aUnitIsWoundedWhenAnyPresentPartIsBelowItsMaximum() {
    var unit = warrior();
    assertThat(unit.isWounded()).isFalse();

    unit.damagePart(BodyPart.HEAD, 0.1);

    assertThat(unit.isWounded()).isTrue();
  }

  @Test
  void healingGoesToTheLowestRatioPresentPart() {
    var unit = warrior();
    unit.damagePart(BodyPart.TORSO, 1.0);
    unit.damagePart(BodyPart.LEFT_ARM, 0.8);

    unit.heal(0.4);

    assertThat(part(unit, BodyPart.LEFT_ARM).getCurrentHealth()).isCloseTo(1.2, within(EPS));
    assertThat(part(unit, BodyPart.TORSO).getCurrentHealth()).isCloseTo(9.0, within(EPS));
  }

  @Test
  void healingStopsAtAPartsMaximumAndSpillsToTheNextLowest() {
    var unit = warrior();
    unit.damagePart(BodyPart.HEAD, 1.0);
    unit.damagePart(BodyPart.TORSO, 1.0);

    var healed = unit.heal(1.5);

    assertThat(healed).isCloseTo(1.5, within(EPS));
    assertThat(part(unit, BodyPart.HEAD).getCurrentHealth()).isCloseTo(2.0, within(EPS));
    assertThat(part(unit, BodyPart.TORSO).getCurrentHealth()).isCloseTo(9.5, within(EPS));
  }

  @Test
  void healingNeverExceedsTheMissingHealth() {
    var unit = warrior();
    unit.receiveAttack(3.0, null);

    var healed = unit.heal(50);

    assertThat(healed).isCloseTo(3.0, within(EPS));
    assertThat(unit.isWounded()).isFalse();
  }

  @Test
  void aHealingHitHealsInsteadOfDamaging() {
    var unit = warrior();
    unit.receiveAttack(10.0, null);

    unit.receiveAttack(2.0, AttackType.HEAL);

    assertThat(unit.getCurrentHealth()).isCloseTo(10.0 + 2.0 * 1.5, within(EPS));
  }

  @Test
  void environmentalDamageDoesNotKillThroughTheHeadSoonerThanBefore() {
    var unit = warrior();

    unit.receiveAttack(19.0, null);

    assertThat(unit.isDead()).isFalse();
    assertThat(part(unit, BodyPart.HEAD).getCurrentHealth()).isCloseTo(0.1, within(EPS));
  }
}

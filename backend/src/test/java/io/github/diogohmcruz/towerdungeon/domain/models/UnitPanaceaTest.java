package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** The Panacea's death-save and the reattachment of a lost part, on one Unit. */
class UnitPanaceaTest {
  private static Unit warrior(Panacea panacea) {
    var unit = new Unit(UnitStats.WARRIOR, "Tomas");
    unit.setPanacea(panacea);
    return unit;
  }

  private static Part part(Unit unit, BodyPart kind) {
    return unit.getParts().stream().filter(p -> p.getKind() == kind).findFirst().orElseThrow();
  }

  @Test
  void aKillingHitOnTheTorsoLeavesItAtTenPercentAndSpendsThePanacea() {
    var panacea = new Panacea(0.10);
    var unit = warrior(panacea);

    unit.damagePart(BodyPart.TORSO, 1000);

    assertThat(unit.isDead()).isFalse();
    assertThat(part(unit, BodyPart.TORSO).getCurrentHealth())
        .isCloseTo(part(unit, BodyPart.TORSO).getMaxHealth() * 0.10, within(1e-9));
    assertThat(panacea.isArmed()).isFalse();
  }

  @Test
  void aKillingHitOnTheHeadIsSavedTheSameWayOnlyOnce() {
    var panacea = new Panacea(0.10);
    var unit = warrior(panacea);

    unit.damagePart(BodyPart.HEAD, 1000);
    assertThat(unit.isDead()).isFalse();
    assertThat(part(unit, BodyPart.HEAD).ratio()).isCloseTo(0.10, within(1e-9));

    unit.damagePart(BodyPart.HEAD, 1000);
    assertThat(unit.isDead()).isTrue();
  }

  @Test
  void aHitThatTakesBothVitalPartsToZeroAtOnceCannotBeSaved() {
    var panacea = new Panacea(0.10);
    var unit = warrior(panacea);

    unit.receiveAttack(1000d, null);

    assertThat(unit.isDead()).isTrue();
    assertThat(panacea.isArmed()).isTrue();
  }

  @Test
  void losingALimbDoesNotSpendThePanaceaAndWithoutOneTheTorsoKills() {
    var panacea = new Panacea(0.10);
    var unit = warrior(panacea);

    unit.damagePart(BodyPart.LEFT_ARM, 100);
    assertThat(panacea.isArmed()).isTrue();

    var bare = warrior(null);
    bare.damagePart(BodyPart.TORSO, 1000);
    assertThat(bare.isDead()).isTrue();
  }

  @Test
  void reattachingBringsBackOnlyALostLimbAtTheGivenShare() {
    var unit = warrior(null);
    unit.damagePart(BodyPart.LEFT_LEG, 100);
    var max = part(unit, BodyPart.LEFT_LEG).getMaxHealth();

    assertThat(unit.lostParts()).containsExactly(BodyPart.LEFT_LEG);
    assertThat(unit.reattach(BodyPart.LEFT_LEG, 0.25)).isTrue();
    assertThat(unit.hasPart(BodyPart.LEFT_LEG)).isTrue();
    assertThat(part(unit, BodyPart.LEFT_LEG).getCurrentHealth())
        .isCloseTo(max * 0.25, within(1e-9));
    assertThat(unit.reattach(BodyPart.LEFT_LEG, 0.25)).isFalse();
    assertThat(unit.reattach(BodyPart.HEAD, 0.25)).isFalse();
  }
}

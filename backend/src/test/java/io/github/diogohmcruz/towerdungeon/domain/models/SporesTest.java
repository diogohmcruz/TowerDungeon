package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** The Spore Cellar, Spores as loot, the Remedy, reattachment and the Panacea's making. */
class SporesTest {
  private final GameProperties config = new GameProperties();

  private GameState state(VillageTier tier, int highestFloor) {
    config.getVillage().setStartingFood(500);
    config.getVillage().setStartingVillagers(20);
    var state = new GameState(config);
    state.recordFloorReached(highestFloor);
    state.setVillageTier(tier);
    return state;
  }

  private static Unit home(GameState state) {
    var unit = new Unit(UnitStats.WARRIOR, "Tomas");
    state.addUnits(UnitStats.WARRIOR, List.of(unit));
    return unit;
  }

  private static Part part(Unit unit, BodyPart kind) {
    return unit.getParts().stream().filter(p -> p.getKind() == kind).findFirst().orElseThrow();
  }

  @Test
  void theSporeCellarStaysClosedUntilVillageTierThreeAndOpensAtIt() {
    var closed = state(VillageTier.SHELTERED, 30);
    var open = state(VillageTier.FORTIFIED, 20);

    assertThat(closed.isSporeCellarOpen()).isFalse();
    assertThat(closed.assignTrade(Trade.SPOREWARDEN, 1))
        .hasValueSatisfying(reason -> assertThat(reason).contains("Village Tier III"));
    assertThat(open.isSporeCellarOpen()).isTrue();
    assertThat(open.assignTrade(Trade.SPOREWARDEN, 1)).isEmpty();
    assertThat(open.getVillage().hands(Trade.SPOREWARDEN)).isEqualTo(1);
  }

  @Test
  void sixSporewardensMakeThreeTenthsOfASporePerTickIntoTheStockroom() {
    var state = state(VillageTier.FORTIFIED, 20);
    for (var i = 0; i < 6; i++) {
      assertThat(state.assignTrade(Trade.SPOREWARDEN, 1)).isEmpty();
    }

    state.getVillage().triggerLifecycle(0, 20);

    assertThat(state.getVillage().getSpores()).isCloseTo(0.3, within(1e-9));
    assertThat(state.getVillage().hands(Trade.TILLER)).isEqualTo(14);
    assertThat(state.getTradeViews())
        .anySatisfy(
            view -> {
              assertThat(view.trade()).isEqualTo("SPOREWARDEN");
              assertThat(view.workplace()).isEqualTo("Spore Cellar");
              assertThat(view.hands()).isEqualTo(6);
            });
  }

  @Test
  void sporesStopAtTheStockroomCap() {
    var state = state(VillageTier.FORTIFIED, 20);
    state.getVillage().restoreSpores(6, state.getStockroomCap() - 0.1);

    state.getVillage().triggerLifecycle(0, 20);

    assertThat(state.getVillage().getSpores()).isEqualTo(state.getStockroomCap());
  }

  @Test
  void lootYieldsOneSporePerClearedFloorFromFloorTwentyAndTripleOnAGuardianFloor() {
    var state = state(VillageTier.FORTIFIED, 40);

    state.gatherFloorSpores(19, false);
    assertThat(state.getCarriedSpores()).isZero();
    state.gatherFloorSpores(20, false);
    assertThat(state.getCarriedSpores()).isEqualTo(1.0);
    state.gatherFloorSpores(30, true);
    assertThat(state.getCarriedSpores()).isEqualTo(4.0);
  }

  @Test
  void sporesFoundAreBankedAtExtractionAndForfeitedByAWipe() {
    var banked = state(VillageTier.FORTIFIED, 40);
    banked.gatherFloorSpores(25, false);
    banked.gatherFloorSpores(30, true);
    banked.bankLoot();
    assertThat(banked.getVillage().getSpores()).isEqualTo(4.0);
    assertThat(banked.getCarriedSpores()).isZero();

    var wiped = state(VillageTier.FORTIFIED, 40);
    wiped.gatherFloorSpores(25, false);
    wiped.forfeitLoot();
    assertThat(wiped.getVillage().getSpores()).isZero();
    assertThat(wiped.getCarriedSpores()).isZero();
  }

  @Test
  void aRemedyHealsTwentyOnTheMostWoundedPartFirstAndCostsASpore() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 3);
    unit.damagePart(BodyPart.LEFT_LEG, 2.0);
    unit.damagePart(BodyPart.TORSO, 3.0);

    assertThat(state.giveRemedy(unit.getId())).isEmpty();

    assertThat(state.getVillage().getSpores()).isEqualTo(2.0);
    assertThat(unit.isWounded()).isFalse();
  }

  @Test
  void aRemedyNeverRegrowsALostPartAndSpendsNothingOnAnUnwoundedUnit() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 3);
    unit.damagePart(BodyPart.LEFT_ARM, 100);
    unit.damagePart(BodyPart.TORSO, 4.0);

    assertThat(state.giveRemedy(unit.getId())).isEmpty();

    assertThat(unit.hasPart(BodyPart.LEFT_ARM)).isFalse();
    assertThat(part(unit, BodyPart.LEFT_ARM).isLost()).isTrue();
    assertThat(unit.isWounded()).isFalse();
    assertThat(state.giveRemedy(unit.getId())).hasValueSatisfying(r -> assertThat(r).isNotBlank());
    assertThat(state.getVillage().getSpores()).isEqualTo(2.0);
  }

  @Test
  void aRemedyNeedsASporeAndAUnitAtHome() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    unit.damagePart(BodyPart.TORSO, 4.0);

    assertThat(state.giveRemedy(unit.getId()))
        .hasValueSatisfying(r -> assertThat(r).contains("no Spores"));
    assertThat(state.giveRemedy(-1)).hasValueSatisfying(r -> assertThat(r).contains("not at home"));
    assertThat(unit.isWounded()).isTrue();
  }

  @Test
  void aSporeDoseReattachesALostArmOrLegAtAQuarterOfItsMaximum() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 2);
    unit.damagePart(BodyPart.RIGHT_LEG, 100);
    var max = part(unit, BodyPart.RIGHT_LEG).getMaxHealth();

    assertThat(state.reattachPart(unit.getId(), BodyPart.RIGHT_LEG)).isEmpty();

    assertThat(unit.hasPart(BodyPart.RIGHT_LEG)).isTrue();
    assertThat(part(unit, BodyPart.RIGHT_LEG).getCurrentHealth())
        .isCloseTo(max * 0.25, within(1e-9));
    assertThat(part(unit, BodyPart.RIGHT_LEG).getMaxHealth()).isEqualTo(max);
    assertThat(state.getVillage().getSpores()).isEqualTo(1.0);
  }

  @Test
  void aSporeDoseReattachesOnlyALostLimb() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 5);

    assertThat(state.reattachPart(unit.getId(), BodyPart.HEAD))
        .hasValueSatisfying(r -> assertThat(r).contains("never lost"));
    assertThat(state.reattachPart(unit.getId(), BodyPart.LEFT_ARM))
        .hasValueSatisfying(r -> assertThat(r).contains("not lost"));
    assertThat(state.getVillage().getSpores()).isEqualTo(5.0);
  }

  @Test
  void recoveryStillHealsAReattachedPartOnItsOwnSerialQueue() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 1);
    unit.damagePart(BodyPart.LEFT_LEG, 100);
    assertThat(state.reattachPart(unit.getId(), BodyPart.LEFT_LEG)).isEmpty();
    var before = part(unit, BodyPart.LEFT_LEG).getCurrentHealth();

    state.healHomePartyWithFood();

    assertThat(part(unit, BodyPart.LEFT_LEG).getCurrentHealth()).isGreaterThan(before);
  }

  @Test
  void aPanaceaTakesFourSporesAndFortyMaterialsFromTheVaultAndTenCapacity() {
    var state = state(VillageTier.FORTIFIED, 20);
    home(state);
    state.getVillage().restoreSpores(0, 5);
    state.getResources().put(ResourceType.MATERIALS, 100d);
    var plain = config.getSupply().getBaseMax() + state.getSupplyCapacityBonus();

    assertThat(state.getLoadout().panaceaBlocked()).isNull();
    state.enlistRandom(UnitStats.WARRIOR);
    state.startRun(0, true);

    assertThat(state.isPanaceaCarried()).isTrue();
    assertThat(state.getVillage().getSpores()).isEqualTo(1.0);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(60.0);
    assertThat(state.getMaxSupplies()).isEqualTo(plain - 10);
  }

  @Test
  void aPanaceaThatCannotBeMadeIsNotCarriedAndSpendsNothing() {
    var state = state(VillageTier.FORTIFIED, 20);
    home(state);
    state.getVillage().restoreSpores(0, 3);
    state.getResources().put(ResourceType.MATERIALS, 100d);
    assertThat(state.panaceaBlocked()).hasValueSatisfying(r -> assertThat(r).contains("4 Spores"));

    state.getVillage().restoreSpores(0, 4);
    state.getResources().put(ResourceType.MATERIALS, 39d);
    assertThat(state.panaceaBlocked())
        .hasValueSatisfying(r -> assertThat(r).contains("40 Materials"));

    state.enlistRandom(UnitStats.WARRIOR);
    state.startRun(0, true);

    assertThat(state.isPanaceaCarried()).isFalse();
    assertThat(state.getVillage().getSpores()).isEqualTo(4.0);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(39.0);
  }

  @Test
  void theCarriedPanaceaSavesTheFirstUnitToFallAndNoOtherThisExpedition() {
    var state = state(VillageTier.FORTIFIED, 20);
    var first = home(state);
    var second = home(state);
    state.getVillage().restoreSpores(0, 4);
    state.getResources().put(ResourceType.MATERIALS, 40d);
    state.enlistRandom(UnitStats.WARRIOR);
    state.enlistRandom(UnitStats.WARRIOR);
    state.startRun(0, true);

    first.damagePart(BodyPart.TORSO, 100);
    second.damagePart(BodyPart.TORSO, 100);

    assertThat(List.of(first, second)).filteredOn(Unit::isDead).hasSize(1);
    assertThat(state.isPanaceaCarried()).isFalse();
  }

  @Test
  void anExtractionTakesThePanaceaOffTheUnits() {
    var state = state(VillageTier.FORTIFIED, 20);
    var unit = home(state);
    state.getVillage().restoreSpores(0, 4);
    state.getResources().put(ResourceType.MATERIALS, 40d);
    state.enlistRandom(UnitStats.WARRIOR);
    state.startRun(0, true);

    state.returnPartyHome();
    state.completeExpedition();
    unit.damagePart(BodyPart.TORSO, 1000);

    assertThat(unit.isDead()).isTrue();
    assertThat(state.isPanaceaCarried()).isFalse();
  }
}

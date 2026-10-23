package io.github.diogohmcruz.towerdungeon.domain.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class TalismansAndArtifactsTest {
  private final GameProperties config = new GameProperties();
  private final ItemRating rating = new ItemRating(config.getItems().getWeights());
  private final HomeFloorArtifacts artifacts = new HomeFloorArtifacts(config);

  private static Item talisman(ItemEffect effect) {
    return new Item(
        "t-" + effect.id(),
        effect.name(),
        ItemTier.TALISMAN,
        ItemCategory.TRINKET,
        Map.of(),
        effect.id());
  }

  private Item artifact(ItemEffect effect) {
    return artifacts.all().stream().filter(item -> item.has(effect)).findFirst().orElseThrow();
  }

  private Unit wearing(UnitStats type, Item... items) {
    var unit = new Unit(type);
    for (var item : items) {
      unit.equip(item, rating);
    }
    return unit;
  }

  private Unit wearing(Item... items) {
    return wearing(UnitStats.WARRIOR, items);
  }

  @Test
  void theEffectIdsAreAClosedStableSet() {
    assertThat(ItemEffect.values())
        .extracting(ItemEffect::id)
        .containsExactly(
            "steadfast",
            "keen",
            "wary",
            "ember",
            "gloom",
            "salted",
            "thorn",
            "lantern",
            "sargans-mask",
            "lilicens-mana-vessel",
            "dragon-slayers-blade",
            "yuvas-salt-satchel");
    assertThat(ItemEffect.of("keen")).contains(ItemEffect.KEEN);
    assertThat(ItemEffect.of("nope")).isEmpty();
  }

  @Test
  void aTalismanFitsAnySlotAndLeftoversStayInTheirOwn() {
    var talisman = talisman(ItemEffect.KEEN);
    assertThat(java.util.Arrays.stream(Slot.values()).allMatch(slot -> slot.fits(talisman)))
        .isTrue();
    var leftover =
        new Item(
            "l", "l", ItemTier.LEFTOVER, ItemCategory.WEAPON, Map.of(ItemStat.POWER, 1.0), null);
    assertThat(Slot.HEAD.fits(leftover)).isFalse();
    assertThat(Slot.LEFT_ARM.fits(leftover)).isTrue();
  }

  @Test
  void talismansTakeTrinketSlotsFirstThenAnyEmptySlot() {
    var unit = new Unit(UnitStats.WARRIOR);
    var slots = new ArrayList<Slot>();
    for (var effect : List.of(ItemEffect.KEEN, ItemEffect.WARY, ItemEffect.EMBER)) {
      slots.add(unit.equip(talisman(effect), rating).slot());
    }
    assertThat(slots).containsExactly(Slot.TRINKET_1, Slot.TRINKET_2, Slot.HEAD);
  }

  @Test
  void steadfastRaisesMaximumSanityAndLosingItClampsSanity() {
    var unit = wearing(talisman(ItemEffect.STEADFAST));
    assertThat(unit.getMaxSanity()).isEqualTo(110.0);
    unit.changeSanity(10);
    assertThat(unit.getSanity()).isEqualTo(110.0);

    unit.dropAll();
    assertThat(unit.getMaxSanity()).isEqualTo(100.0);
    assertThat(unit.getSanity()).isEqualTo(100.0);
  }

  @Test
  void theSanityStatOfWornItemsAddsToMaximumSanity() {
    var charm =
        new Item(
            "c", "c", ItemTier.LEFTOVER, ItemCategory.TRINKET, Map.of(ItemStat.SANITY, 6.0), null);
    var unit = wearing(charm);
    assertThat(unit.getMaxSanity()).isEqualTo(106.0);
    assertThat(unit.getBaseMaxSanity()).isEqualTo(100.0);
  }

  @Test
  void keenAndWaryAddAccuracyAndEvasion() {
    var bare = new Unit(UnitStats.ARCHER);
    var unit = wearing(UnitStats.ARCHER, talisman(ItemEffect.KEEN), talisman(ItemEffect.WARY));
    assertThat(unit.getAccuracy()).isEqualTo(bare.getAccuracy() + 10);
    assertThat(unit.getEvasion()).isEqualTo(bare.getEvasion() + 10);
  }

  @Test
  void emberAddsDamageToMagicAttacksOnly() {
    var mage = wearing(UnitStats.MAGE, talisman(ItemEffect.EMBER));
    var plainMage = new Unit(UnitStats.MAGE);
    assertThat(mage.getDamage()).isEqualTo(plainMage.getDamage() + 1);

    var warrior = wearing(UnitStats.WARRIOR, talisman(ItemEffect.EMBER));
    assertThat(warrior.getDamage()).isEqualTo(new Unit(UnitStats.WARRIOR).getDamage());
    assertThat(UnitStats.MAGE.getAttackType()).isEqualTo(AttackType.MAGIC);
  }

  @Test
  void gloomAndSaltedCutDamageTakenFromTheirEnemies() {
    var unit = wearing(talisman(ItemEffect.GLOOM), talisman(ItemEffect.SALTED));
    assertThat(unit.takenFactorFrom(EnemyStats.DEAD_SOLDIERS)).isCloseTo(0.95, within(1e-9));
    assertThat(unit.takenFactorFrom(EnemyStats.NECROMANCER)).isCloseTo(0.95, within(1e-9));
    assertThat(unit.takenFactorFrom(EnemyStats.SLIME)).isCloseTo(0.75, within(1e-9));
    assertThat(unit.takenFactorFrom(EnemyStats.TOWER_BEAST)).isEqualTo(1.0);
  }

  @Test
  void thornValueAndLanternBonusFollowTheWornTalismans() {
    var unit = wearing(talisman(ItemEffect.THORN), talisman(ItemEffect.LANTERN));
    assertThat(unit.thornDamage()).isEqualTo(1.0);
    assertThat(unit.floorClimbedSanityBonus()).isEqualTo(2.0);
    var bare = new Unit(UnitStats.WARRIOR);
    assertThat(bare.thornDamage()).isZero();
    assertThat(bare.floorClimbedSanityBonus()).isZero();
  }

  @Test
  void sargansMaskMakesPhobiasHarmless() {
    var unit = wearing(artifact(ItemEffect.SARGANS_MASK));
    unit.addPhobia(EnemyStats.SLIME);
    unit.changeSanity(-100);

    assertThat(unit.damageFactorAgainst(EnemyStats.SLIME)).isEqualTo(1.0);
    assertThat(unit.takenFactorFrom(EnemyStats.SLIME)).isEqualTo(1.0);
    unit.meetEnemies(3, Set.of(EnemyStats.SLIME));
    assertThat(unit.getSanity()).isZero();
    assertThat(unit.getPhobias()).allMatch(phobia -> !phobia.isKnown());

    var bare = new Unit(UnitStats.WARRIOR);
    bare.addPhobia(EnemyStats.SLIME);
    assertThat(bare.damageFactorAgainst(EnemyStats.SLIME)).isLessThan(1.0);
  }

  @Test
  void manaVesselAddsMaximumManaToCastersOnly() {
    var mage = new Unit(UnitStats.MAGE);
    mage.chargeToFull(5.0);
    mage.equip(artifact(ItemEffect.LILICENS_MANA_VESSEL), rating);

    assertThat(mage.getMaxMana()).isEqualTo(10.0);
    assertThat(mage.getBaseMaxMana()).isEqualTo(5.0);
    assertThat(mage.getCurrentMana()).isEqualTo(5.0);
    mage.rechargeMana(100);
    assertThat(mage.getCurrentMana()).isEqualTo(10.0);

    var warrior = wearing(artifact(ItemEffect.LILICENS_MANA_VESSEL));
    assertThat(warrior.getMaxMana()).isZero();
  }

  @Test
  void dragonSlayersBladeDoublesDamageAgainstDragonsOnly() {
    var unit = wearing(artifact(ItemEffect.DRAGON_SLAYERS_BLADE));
    assertThat(unit.damageFactorAgainst(EnemyStats.DRAGON)).isEqualTo(2.0);
    assertThat(unit.damageFactorAgainst(EnemyStats.DEAD_DRAGON)).isEqualTo(2.0);
    assertThat(unit.damageFactorAgainst(EnemyStats.SLIME)).isEqualTo(1.0);
  }

  @Test
  void saltSatchelHalvesAttritionButNotOtherDamage() {
    var bare = new Unit(UnitStats.WARRIOR);
    var unit = wearing(artifact(ItemEffect.YUVAS_SALT_SATCHEL));

    bare.receiveAttrition(10);
    unit.receiveAttrition(10);

    var lostBare = bare.getMaxHealth() - bare.getCurrentHealth();
    var lostWearer = unit.getMaxHealth() - unit.getCurrentHealth();
    assertThat(lostWearer).isCloseTo(lostBare / 2, within(1e-9));

    var hurt = wearing(artifact(ItemEffect.YUVAS_SALT_SATCHEL));
    hurt.receiveAttack(10.0, null);
    assertThat(hurt.getMaxHealth() - hurt.getCurrentHealth()).isCloseTo(lostBare, within(1e-9));
  }

  @Test
  void anArtifactIsNeverSurplusAndNeverDisplaced() {
    var unit = new Unit(UnitStats.WARRIOR);
    for (var slot : Slot.values()) {
      unit.equip(
          new Item("w" + slot, "w", ItemTier.TALISMAN, ItemCategory.TRINKET, Map.of(), "keen"),
          rating);
    }
    assertThat(unit.getGear().getSlots()).hasSize(8);

    var result = unit.equip(artifact(ItemEffect.SARGANS_MASK), rating);

    assertThat(result.slot()).isNotNull();
    assertThat(result.surplus()).isNotNull();
    assertThat(result.surplus().tier()).isEqualTo(ItemTier.TALISMAN);
    assertThat(unit.getGear().getSlots().values()).contains(artifact(ItemEffect.SARGANS_MASK));

    var mighty =
        new Item(
            "m",
            "m",
            ItemTier.TALISMAN,
            ItemCategory.TRINKET,
            Map.of(ItemStat.POWER, 99.0),
            "keen");
    var offered = unit.equip(mighty, rating);
    assertThat(offered.surplus()).isNotEqualTo(artifact(ItemEffect.SARGANS_MASK));
    assertThat(unit.getGear().getSlots().values()).contains(artifact(ItemEffect.SARGANS_MASK));
  }

  @Test
  void artifactsAreOfferedAtTheirHomeFloorUntilTaken() {
    assertThat(artifacts.artifactsFor(40, Set.of()))
        .extracting(Item::name)
        .containsExactly("Sargan's Mask");
    assertThat(artifacts.artifactsFor(30, Set.of()))
        .extracting(Item::name)
        .containsExactly("Lilicen's Mana Vessel");
    assertThat(artifacts.artifactsFor(60, Set.of()))
        .extracting(Item::name)
        .containsExactly("the Dragon-Slayer's Blade");
    assertThat(artifacts.artifactsFor(20, Set.of()))
        .extracting(Item::name)
        .containsExactly("Yuva's Salt Satchel");
    assertThat(artifacts.artifactsFor(21, Set.of())).isEmpty();
    assertThat(artifacts.artifactsFor(40, Set.of("sargans-mask"))).isEmpty();
    assertThat(artifacts.artifactsFor(40, Set.of("sargans-mask")))
        .isEqualTo(artifacts.artifactsFor(41, Set.of()));
  }

  @Test
  void aSecondGenerationOffersTheSameArtifactAgain() {
    var first = artifacts.artifactsFor(40, new HashSet<>());
    var second = artifacts.artifactsFor(40, new HashSet<>());
    assertThat(second).isEqualTo(first);
  }

  @Test
  void homeFloorsComeFromConfig() {
    config.getItems().getArtifacts().setSargansMaskFloor(7);
    assertThat(artifacts.artifactsFor(7, Set.of())).hasSize(1);
    assertThat(artifacts.artifactsFor(40, Set.of())).isEmpty();
  }

  @Test
  void generatedTalismansCarryOneOfTheEightEffectsDeterministically() {
    var seen = EnumSet.noneOf(ItemEffect.class);
    var generator = new TalismanGenerator(new SplittableRandom(3), config);
    for (var i = 0; i < 200; i++) {
      var item = generator.generate();
      assertThat(item.tier()).isEqualTo(ItemTier.TALISMAN);
      assertThat(item.hasEffect()).isTrue();
      seen.add(ItemEffect.of(item.effectId()).orElseThrow());
    }
    assertThat(seen).hasSize(8).allMatch(effect -> effect.tier() == ItemTier.TALISMAN);

    var a = new TalismanGenerator(new SplittableRandom(9), config).generate();
    var b = new TalismanGenerator(new SplittableRandom(9), config).generate();
    assertThat(a).isEqualTo(b);
  }

  @Test
  void talismansDropRarerThanLeftoversAndAtTheConfiguredRate() {
    var generator = new TalismanGenerator(new SplittableRandom(5), config);
    var talismans = 0;
    var rolls = 10_000;
    for (var i = 0; i < rolls; i++) {
      if (generator.rollTier() == ItemTier.TALISMAN) {
        talismans++;
      }
    }
    assertThat(talismans / (double) rolls).isCloseTo(0.1, within(0.02));
    assertThat(config.getItems().getDropRates().getTalisman())
        .isLessThan(config.getItems().getDropRates().getLeftover());

    config.getItems().getDropRates().setTalisman(0.0);
    var never = new TalismanGenerator(new SplittableRandom(5), config);
    for (var i = 0; i < 100; i++) {
      assertThat(never.rollTier()).isEqualTo(ItemTier.LEFTOVER);
    }
  }

  @Test
  void aSurplusTalismanIsWorthThreeRelicsAndAnArtifactNone() {
    var value = config.getItems().getSurplusRelics();
    assertThat(value.of(ItemTier.TALISMAN)).isEqualTo(3);
    assertThat(value.of(ItemTier.ARTIFACT)).isZero();
  }

  @Test
  void floorClimbedGainIncludesLanternForTheWearer() {
    var state = new GameState(config);
    var lantern = wearing(talisman(ItemEffect.LANTERN));
    var bare = new Unit(UnitStats.WARRIOR);
    lantern.changeSanity(-50);
    bare.changeSanity(-50);
    state.setUnitsOnTower(Map.of(UnitStats.WARRIOR, new ArrayList<>(List.of(lantern, bare))));

    state.rewardFloorClimbed();

    assertThat(lantern.getSanity()).isEqualTo(50 + 5 + 2);
    assertThat(bare.getSanity()).isEqualTo(50 + 5);
  }
}

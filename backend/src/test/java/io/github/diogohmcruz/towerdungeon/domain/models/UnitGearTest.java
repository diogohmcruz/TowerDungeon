package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;

class UnitGearTest {
  private static final double EPS = 1e-9;
  private final ItemRating rating = new ItemRating(new GameProperties().getItems().getWeights());

  private static Item item(String id, ItemCategory category, ItemStat stat, double value) {
    return new Item(id, id, ItemTier.LEFTOVER, category, Map.of(stat, value), null);
  }

  private static Unit warrior() {
    return new Unit(UnitStats.WARRIOR);
  }

  @Test
  void powerAddsFlatDamagePerAttack() {
    var unit = warrior();
    var bare = unit.getDamage();

    unit.equip(item("sword", ItemCategory.WEAPON, ItemStat.POWER, 3.0), rating);
    unit.equip(item("axe", ItemCategory.WEAPON, ItemStat.POWER, 1.5), rating);

    assertThat(unit.getDamage()).isCloseTo(bare + 4.5, within(EPS));
  }

  @Test
  void armourTakesFlatDamageOffBlowsOnThePartWearingIt() {
    var unit = warrior();
    var torso = unit.getParts().get(0);
    var head = unit.getParts().get(1);
    unit.equip(item("plate", ItemCategory.ARMOUR, ItemStat.ARMOUR, 1.0), rating);

    unit.damagePart(BodyPart.TORSO, 4.0);
    unit.damagePart(BodyPart.HEAD, 1.5);

    assertThat(torso.missing()).isCloseTo(3.0, within(EPS));
    assertThat(head.missing()).isCloseTo(1.5, within(EPS));
  }

  @Test
  void armourNeverBlocksMoreThanEightyPercentOfABlow() {
    var unit = warrior();
    var torso = unit.getParts().get(0);
    unit.equip(item("wall", ItemCategory.ARMOUR, ItemStat.ARMOUR, 1_000.0), rating);

    unit.damagePart(BodyPart.TORSO, 4.0);

    assertThat(torso.missing()).isCloseTo(0.8, within(EPS));
  }

  @Test
  void environmentalDamageIgnoresArmour() {
    var unit = warrior();
    unit.equip(item("wall", ItemCategory.ARMOUR, ItemStat.ARMOUR, 1_000.0), rating);

    unit.receiveAttack(4.0, null);

    assertThat(unit.getMaxHealth() - unit.getCurrentHealth()).isCloseTo(4.0, within(EPS));
  }

  @Test
  void aLostPartReturnsItsItemAndKeepsTheRestWorn() {
    var unit = warrior();
    var sword = item("sword", ItemCategory.WEAPON, ItemStat.POWER, 3.0);
    var boots = item("boots", ItemCategory.BOOTS, ItemStat.EVASION, 5.0);
    var charm = item("charm", ItemCategory.TRINKET, ItemStat.SANITY, 5.0);
    unit.equip(sword, rating);
    unit.equip(boots, rating);
    unit.equip(charm, rating);

    var dropped = unit.damagePart(BodyPart.LEFT_ARM, 1_000);

    assertThat(dropped).containsExactly(sword);
    assertThat(unit.getGear().getSlots().values()).containsExactlyInAnyOrder(boots, charm);
    assertThat(unit.getDamage()).isCloseTo(UnitStats.WARRIOR.getDamage() * 0.5, within(EPS));
  }

  @Test
  void aHitThatMaimsReturnsTheLostPartsItem() {
    var unit = warrior();
    var boots = item("boots", ItemCategory.BOOTS, ItemStat.ARMOUR, 0.1);
    unit.equip(boots, rating);
    var leg = unit.getParts().get(4);
    leg.setCurrentHealth(0.001);

    var dropped = unit.damagePart(BodyPart.LEFT_LEG, 5.0);

    assertThat(leg.isLost()).isTrue();
    assertThat(dropped).containsExactly(boots);
  }

  @Test
  void trinketsComeBackOnlyOnDeath() {
    var unit = warrior();
    var charm = item("charm", ItemCategory.TRINKET, ItemStat.SANITY, 5.0);
    var helm = item("helm", ItemCategory.HELM, ItemStat.ARMOUR, 1.0);
    unit.equip(charm, rating);
    unit.equip(helm, rating);

    for (var part : BodyPart.values()) {
      if (!part.isVital()) {
        assertThat(unit.damagePart(part, 1_000)).doesNotContain(charm);
      }
    }

    assertThat(unit.dropAll()).containsExactlyInAnyOrder(charm, helm);
    assertThat(unit.getGear().getSlots()).isEmpty();
  }
}

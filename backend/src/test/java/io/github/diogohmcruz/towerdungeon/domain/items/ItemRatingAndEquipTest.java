package io.github.diogohmcruz.towerdungeon.domain.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class ItemRatingAndEquipTest {
  private final ItemRating rating = new ItemRating(new GameProperties().getItems().getWeights());

  private static Item item(ItemCategory category, Map<ItemStat, Double> stats) {
    return new Item("i" + stats.hashCode(), "x", ItemTier.LEFTOVER, category, stats, null);
  }

  private static Item weapon(double power) {
    return item(ItemCategory.WEAPON, Map.of(ItemStat.POWER, power));
  }

  private static Unit warrior() {
    return new Unit(UnitStats.WARRIOR);
  }

  @Test
  void ratingIsTheWeightedSum() {
    var item =
        new Item(
            "a",
            "a",
            ItemTier.LEFTOVER,
            ItemCategory.WEAPON,
            Map.of(
                ItemStat.POWER, 2.0,
                ItemStat.ARMOUR, 1.0,
                ItemStat.ACCURACY, 10.0,
                ItemStat.EVASION, 5.0,
                ItemStat.SANITY, 5.0),
            "some-effect");

    assertThat(rating.of(item)).isCloseTo(2.0 + 0.8 + 6.0 + 3.0 + 2.0 + 2.0, within(1e-9));
  }

  @Test
  void eachCategoryFitsItsOwnSlotsOnly() {
    assertThat(Slot.values()).hasSize(8);
    assertThat(Slot.HEAD.accepts(ItemCategory.HELM)).isTrue();
    assertThat(Slot.TORSO.accepts(ItemCategory.ARMOUR)).isTrue();
    assertThat(Slot.LEFT_ARM.accepts(ItemCategory.WEAPON)).isTrue();
    assertThat(Slot.RIGHT_ARM.accepts(ItemCategory.WEAPON)).isTrue();
    assertThat(Slot.LEFT_LEG.accepts(ItemCategory.BOOTS)).isTrue();
    assertThat(Slot.RIGHT_LEG.accepts(ItemCategory.BOOTS)).isTrue();
    assertThat(Slot.TRINKET_1.accepts(ItemCategory.TRINKET)).isTrue();
    assertThat(Slot.TRINKET_2.accepts(ItemCategory.TRINKET)).isTrue();
    assertThat(Slot.HEAD.accepts(ItemCategory.WEAPON)).isFalse();
    assertThat(Slot.TORSO.accepts(ItemCategory.HELM)).isFalse();
  }

  @Test
  void aFoundItemFillsEmptyFittingSlotsFirst() {
    var unit = warrior();

    var first = unit.equip(weapon(1), rating);
    var second = unit.equip(weapon(2), rating);

    assertThat(first.slot()).isEqualTo(Slot.LEFT_ARM);
    assertThat(second.slot()).isEqualTo(Slot.RIGHT_ARM);
    assertThat(first.hasSurplus()).isFalse();
    assertThat(second.hasSurplus()).isFalse();
  }

  @Test
  void aBetterItemReplacesTheLowestRatedFittingOneAndTheOldIsSurplus() {
    var unit = warrior();
    var weak = weapon(1);
    var strong = weapon(5);
    unit.equip(strong, rating);
    unit.equip(weak, rating);

    var better = weapon(3);
    var result = unit.equip(better, rating);

    assertThat(result.slot()).isEqualTo(Slot.RIGHT_ARM);
    assertThat(result.surplus()).isEqualTo(weak);
    assertThat(result.surplus().tier()).isEqualTo(ItemTier.LEFTOVER);
    assertThat(unit.getGear().at(Slot.LEFT_ARM)).isEqualTo(strong);
    assertThat(unit.getGear().at(Slot.RIGHT_ARM)).isEqualTo(better);
  }

  @Test
  void anItemNoBetterThanTheWorstIsSurplus() {
    var unit = warrior();
    unit.equip(weapon(2), rating);
    unit.equip(weapon(4), rating);
    var tie = weapon(2.0);

    var result = unit.equip(tie, rating);

    assertThat(result.equipped()).isFalse();
    assertThat(result.surplus()).isEqualTo(tie);
  }

  @Test
  void slotsOnALostPartDoNotTakeItems() {
    var unit = warrior();
    unit.damagePart(BodyPart.LEFT_ARM, 1_000);
    unit.damagePart(BodyPart.RIGHT_ARM, 1_000);
    var sword = weapon(9);

    var result = unit.equip(sword, rating);

    assertThat(result.equipped()).isFalse();
    assertThat(result.surplus()).isEqualTo(sword);
  }

  @Test
  void trinketsGoToTheTwoTrinketSlots() {
    var unit = warrior();

    var a = unit.equip(item(ItemCategory.TRINKET, Map.of(ItemStat.SANITY, 5.0)), rating);
    var b = unit.equip(item(ItemCategory.TRINKET, Map.of(ItemStat.SANITY, 6.0)), rating);

    assertThat(a.slot()).isEqualTo(Slot.TRINKET_1);
    assertThat(b.slot()).isEqualTo(Slot.TRINKET_2);
  }
}

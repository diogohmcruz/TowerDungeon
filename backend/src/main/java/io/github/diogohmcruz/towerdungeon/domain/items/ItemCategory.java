package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.List;

/** What kind of gear an item is, which decides the slots it fits and the stats it tends to roll. */
public enum ItemCategory {
  WEAPON(ItemStat.POWER, ItemStat.ACCURACY),
  HELM(ItemStat.EVASION, ItemStat.ARMOUR),
  ARMOUR(ItemStat.ARMOUR),
  BOOTS(ItemStat.EVASION, ItemStat.ARMOUR),
  TRINKET(ItemStat.SANITY);

  private final List<ItemStat> statTable;

  ItemCategory(ItemStat... statTable) {
    this.statTable = List.of(statTable);
  }

  /** The stats a generated item of this category draws from. */
  public List<ItemStat> statTable() {
    return statTable;
  }
}

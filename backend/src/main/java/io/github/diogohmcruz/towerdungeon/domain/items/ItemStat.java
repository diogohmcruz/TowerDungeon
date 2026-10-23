package io.github.diogohmcruz.towerdungeon.domain.items;

/** The numbers an item can carry. */
public enum ItemStat {
  /** Flat damage added to each attack. */
  POWER,
  /** Damage taken off every hit on the part wearing it. */
  ARMOUR,
  /** Stored for the miss formulas. */
  ACCURACY,
  /** Stored for the miss formulas. */
  EVASION,
  /** Stored for the mind rules. */
  SANITY
}

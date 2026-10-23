package io.github.diogohmcruz.towerdungeon.domain.items;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** Scores an item as a weighted sum of its stats, so two items of one category can be compared. */
public class ItemRating {
  private final GameProperties.Items.Weights weights;

  public ItemRating(GameProperties.Items.Weights weights) {
    this.weights = weights;
  }

  public double of(Item item) {
    return item.stat(ItemStat.POWER) * weights.getPower()
        + item.stat(ItemStat.ARMOUR) * weights.getArmour()
        + item.stat(ItemStat.ACCURACY) * weights.getAccuracy()
        + item.stat(ItemStat.EVASION) * weights.getEvasion()
        + item.stat(ItemStat.SANITY) * weights.getSanity()
        + (item.hasEffect() ? weights.getEffect() : 0.0);
  }
}

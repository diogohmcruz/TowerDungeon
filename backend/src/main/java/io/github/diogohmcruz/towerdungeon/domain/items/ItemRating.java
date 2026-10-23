package io.github.diogohmcruz.towerdungeon.domain.items;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * Scores an item as a weighted sum of its stats, so two items of one category can be compared. A
 * rating made {@link #forAffinity for a unit's affinity} scores items of that category higher.
 */
public class ItemRating {
  private final GameProperties.Items.Weights weights;
  private final double affinityMultiplier;
  private final ItemCategory affinity;

  public ItemRating(GameProperties.Items.Weights weights) {
    this(weights, new GameProperties.Items.Affinity().getRatingMultiplier(), null);
  }

  public ItemRating(GameProperties.Items.Weights weights, double affinityMultiplier) {
    this(weights, affinityMultiplier, null);
  }

  private ItemRating(
      GameProperties.Items.Weights weights, double affinityMultiplier, ItemCategory affinity) {
    this.weights = weights;
    this.affinityMultiplier = affinityMultiplier;
    this.affinity = affinity;
  }

  /** The same scoring seen by a unit with this affinity (none for {@code null}). */
  public ItemRating forAffinity(ItemCategory affinity) {
    return new ItemRating(weights, affinityMultiplier, affinity);
  }

  public double of(Item item) {
    var base =
        item.stat(ItemStat.POWER) * weights.getPower()
            + item.stat(ItemStat.ARMOUR) * weights.getArmour()
            + item.stat(ItemStat.ACCURACY) * weights.getAccuracy()
            + item.stat(ItemStat.EVASION) * weights.getEvasion()
            + item.stat(ItemStat.SANITY) * weights.getSanity()
            + (item.hasEffect() ? weights.getEffect() : 0.0);
    return item.category() == affinity ? base * affinityMultiplier : base;
  }
}

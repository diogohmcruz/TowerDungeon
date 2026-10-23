package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Map;

/**
 * A piece of gear. Stats missing from the map count as zero. {@code effectId} names one of a closed
 * set of effects, or is {@code null} for none; no effect exists until Talismans land.
 */
public record Item(
    String id,
    String name,
    ItemTier tier,
    ItemCategory category,
    Map<ItemStat, Double> stats,
    String effectId) {

  public Item {
    stats = Map.copyOf(stats);
  }

  public double stat(ItemStat stat) {
    return stats.getOrDefault(stat, 0.0);
  }

  /** Whether the item carries the given effect. */
  public boolean has(ItemEffect effect) {
    return effect.id().equals(effectId);
  }

  public boolean hasEffect() {
    return effectId != null;
  }
}

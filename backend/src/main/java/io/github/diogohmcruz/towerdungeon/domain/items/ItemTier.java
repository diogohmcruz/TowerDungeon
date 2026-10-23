package io.github.diogohmcruz.towerdungeon.domain.items;

/** How rare an item is. Talismans and Artifacts carry a special effect and fit any slot. */
public enum ItemTier {
  LEFTOVER,
  TALISMAN,
  ARTIFACT;

  /** Whether an item of this tier may be worn in any slot, whatever its category. */
  public boolean fitsAnySlot() {
    return this != LEFTOVER;
  }
}

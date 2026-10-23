package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Optional;

import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;

/**
 * The eight places a unit wears gear. A body slot follows its body part, so it goes when the part
 * is lost; the two trinket slots belong to no part and are lost only with the unit.
 */
public enum Slot {
  HEAD(BodyPart.HEAD, ItemCategory.HELM),
  TORSO(BodyPart.TORSO, ItemCategory.ARMOUR),
  LEFT_ARM(BodyPart.LEFT_ARM, ItemCategory.WEAPON),
  RIGHT_ARM(BodyPart.RIGHT_ARM, ItemCategory.WEAPON),
  LEFT_LEG(BodyPart.LEFT_LEG, ItemCategory.BOOTS),
  RIGHT_LEG(BodyPart.RIGHT_LEG, ItemCategory.BOOTS),
  TRINKET_1(null, ItemCategory.TRINKET),
  TRINKET_2(null, ItemCategory.TRINKET);

  private final BodyPart bodyPart;
  private final ItemCategory accepts;

  Slot(BodyPart bodyPart, ItemCategory accepts) {
    this.bodyPart = bodyPart;
    this.accepts = accepts;
  }

  public boolean accepts(ItemCategory category) {
    return accepts == category;
  }

  /** Whether this slot can hold the item: its own category, or any item of a special tier. */
  public boolean fits(Item item) {
    return item.tier().fitsAnySlot() || accepts(item.category());
  }

  /** The body part this slot sits on; empty for a trinket slot. */
  public Optional<BodyPart> bodyPart() {
    return Optional.ofNullable(bodyPart);
  }

  /** The slot on a body part. */
  public static Slot on(BodyPart part) {
    for (var slot : values()) {
      if (slot.bodyPart == part) {
        return slot;
      }
    }
    throw new IllegalArgumentException("No slot on " + part);
  }
}

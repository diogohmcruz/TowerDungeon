package io.github.diogohmcruz.towerdungeon.domain.items;

/**
 * What happened to an item offered to a unit. {@code slot} is where it now sits, or {@code null}
 * when it was not equipped. {@code surplus} is the item left over, or {@code null} for none: the
 * offered item itself when it did not fit, or the one it displaced.
 */
public record EquipResult(Slot slot, Item surplus) {
  public boolean equipped() {
    return slot != null;
  }

  public boolean hasSurplus() {
    return surplus != null;
  }
}

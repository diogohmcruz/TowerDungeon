package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a unit wears: at most one item per {@link Slot}. Items arrive only through {@link #equip}
 * and leave only as drops, never by hand.
 */
public class Loadout {
  private final Map<Slot, Item> slots = new EnumMap<>(Slot.class);

  /** The worn items by slot. */
  public Map<Slot, Item> getSlots() {
    return Collections.unmodifiableMap(slots);
  }

  /** Puts back what was worn, exactly as saved; no rating, no fitting. */
  public void restore(Map<Slot, Item> saved) {
    slots.clear();
    slots.putAll(saved);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Loadout loadout && slots.equals(loadout.slots);
  }

  @Override
  public int hashCode() {
    return slots.hashCode();
  }

  public Item at(Slot slot) {
    return slots.get(slot);
  }

  /** The sum of one stat over everything worn. */
  public double total(ItemStat stat) {
    return slots.values().stream().mapToDouble(item -> item.stat(stat)).sum();
  }

  /** One stat of whatever is worn in a slot; zero when it is empty. */
  public double stat(Slot slot, ItemStat stat) {
    var item = slots.get(slot);
    return item == null ? 0.0 : item.stat(stat);
  }

  /**
   * Offers an item: it takes an empty fitting slot, else replaces the lowest-rated fitting item if
   * it rates higher, else is surplus. Only slots passing {@code usable} are considered. A displaced
   * item is reported as surplus.
   */
  public EquipResult equip(Item item, ItemRating rating, Predicate<Slot> usable) {
    var fitting =
        java.util.Arrays.stream(Slot.values())
            .filter(slot -> slot.accepts(item.category()) && usable.test(slot))
            .toList();
    for (var slot : fitting) {
      if (!slots.containsKey(slot)) {
        slots.put(slot, item);
        return new EquipResult(slot, null);
      }
    }
    var weakest =
        fitting.stream().min(Comparator.comparingDouble(slot -> rating.of(slots.get(slot))));
    if (weakest.isPresent() && rating.of(item) > rating.of(slots.get(weakest.get()))) {
      var displaced = slots.put(weakest.get(), item);
      return new EquipResult(weakest.get(), displaced);
    }
    return new EquipResult(null, item);
  }

  /** Takes the item off a slot, returning it, or {@code null} if the slot was empty. */
  public Item release(Slot slot) {
    return slots.remove(slot);
  }

  /** Takes off everything, in slot order. */
  public List<Item> releaseAll() {
    var all = new ArrayList<>(slots.values());
    slots.clear();
    return all;
  }
}

package io.github.diogohmcruz.towerdungeon.domain.items;

import java.util.Collection;
import java.util.List;

/**
 * Where the gear of the fallen waits on its floor. The Bundle takes everything waiting on a floor
 * when it is cleared and hands back what nobody claimed.
 */
public interface FloorGearStore {
  /** Removes and returns the gear waiting on {@code floor}. */
  List<Item> takeAll(int floor);

  /** Puts gear back on {@code floor}. */
  void putBack(int floor, Collection<Item> items);
}

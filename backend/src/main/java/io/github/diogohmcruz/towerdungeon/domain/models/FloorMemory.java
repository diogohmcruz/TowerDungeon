package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;

/**
 * What the Tower remembers about each floor beyond its rebuilt garrison, for the whole campaign.
 * That is the gear the fallen left behind and the Risen they became; it is kept apart from {@link
 * TowerFloor} because a floor is rebuilt from scratch every time the party arrives.
 */
public class FloorMemory {
  private final Map<Integer, List<Item>> gear = new TreeMap<>();

  /** The names carried by the Risen standing on each floor until it is cleared once. */
  private final Map<Integer, List<String>> risen = new TreeMap<>();

  /**
   * Leaves {@code items} on {@code floor}. The floor keeps at most {@code cap} items: when more
   * pile up, those with the lowest rating vanish first, and the ones already there win a tie.
   */
  public void leaveGear(int floor, Collection<Item> items, ItemRating rating, int cap) {
    if (items.isEmpty()) {
      return;
    }
    var pile = new ArrayList<>(gear.getOrDefault(floor, List.of()));
    pile.addAll(items);
    pile.sort(Comparator.comparingDouble(rating::of).reversed());
    var kept = new ArrayList<>(pile.subList(0, Math.min(pile.size(), Math.max(cap, 0))));
    if (kept.isEmpty()) {
      gear.remove(floor);
    } else {
      gear.put(floor, kept);
    }
  }

  /** Removes and returns the gear waiting on {@code floor}. */
  public List<Item> takeGear(int floor) {
    var taken = gear.remove(floor);
    return taken == null ? List.of() : taken;
  }

  /** A copy of the gear waiting on every floor that has any. */
  public Map<Integer, List<Item>> gearByFloor() {
    var copy = new TreeMap<Integer, List<Item>>();
    gear.forEach((floor, items) -> copy.put(floor, List.copyOf(items)));
    return copy;
  }

  /** Replaces the remembered gear with a persisted copy. */
  public void restoreGear(Map<Integer, List<Item>> saved) {
    gear.clear();
    saved.forEach(
        (floor, items) -> {
          if (!items.isEmpty()) {
            gear.put(floor, new ArrayList<>(items));
          }
        });
  }

  /**
   * Raises Risen on {@code floor} carrying {@code names}, in that order, until the floor holds
   * {@code cap}; a floor already at its cap gains none, so repeated Wipes never compound. Returns
   * how many were raised.
   */
  public int raiseRisen(int floor, List<String> names, int cap) {
    var standing = new ArrayList<>(risen.getOrDefault(floor, List.of()));
    var room = Math.max(0, cap - standing.size());
    var raised = names.subList(0, Math.min(room, names.size()));
    if (raised.isEmpty()) {
      return 0;
    }
    standing.addAll(raised);
    risen.put(floor, standing);
    return raised.size();
  }

  /** The names of the Risen standing on {@code floor}. */
  public List<String> risenOn(int floor) {
    return List.copyOf(risen.getOrDefault(floor, List.of()));
  }

  /** Lays the Risen of {@code floor} to rest: the floor has been cleared. */
  public void layRisenToRest(int floor) {
    risen.remove(floor);
  }

  /** A copy of the Risen standing on every floor that has any. */
  public Map<Integer, List<String>> risenByFloor() {
    var copy = new TreeMap<Integer, List<String>>();
    risen.forEach((floor, names) -> copy.put(floor, List.copyOf(names)));
    return copy;
  }

  /** Replaces the Risen with a persisted copy. */
  public void restoreRisen(Map<Integer, List<String>> saved) {
    risen.clear();
    saved.forEach(
        (floor, names) -> {
          if (!names.isEmpty()) {
            risen.put(floor, new ArrayList<>(names));
          }
        });
  }
}

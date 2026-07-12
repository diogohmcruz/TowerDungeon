package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * A follow-up wave the player musters mid-run and sends up after an active delve. Instead of joining
 * the party instantly, the wave sets out from the base of the tower and climbs one floor at a time,
 * carrying its own rations drawn from the village pantry. It burns food every tick as it ascends —
 * if the reserve runs dry before it reaches the party, climbers starve on the stairs — and only once
 * it catches up to the party's current floor does it merge in (units and any leftover food). This is
 * the player's lever over an otherwise autonomous delve: who to send up, and when — fresh,
 * fully-recharged mages to relieve spent ones, extra porters and food to extend a deep push.
 */
@Data
public class Reinforcement {
  private final Map<UnitStats, List<Unit>> units;

  /** How high the wave has climbed so far (starts at the base, rises toward the party). */
  private double currentFloor;

  /** Food the wave still carries; drained as it climbs, added to the party's stock on merge. */
  private double supplies;

  /** Carrying capacity of this wave (base + its own porters), so it too can haul extra rations. */
  private final double maxSupplies;

  public Reinforcement(Map<UnitStats, List<Unit>> units, double supplies, double maxSupplies) {
    this.units = units;
    this.currentFloor = 0d;
    this.supplies = supplies;
    this.maxSupplies = maxSupplies;
  }

  /** Number of climbers still alive in the wave. */
  public int size() {
    return (int) units.values().stream().flatMap(List::stream).count();
  }

  public boolean isEmpty() {
    return units.values().stream().allMatch(List::isEmpty);
  }

  public boolean hasSupplies() {
    return supplies > 0;
  }

  public void drainSupplies(double amount) {
    this.supplies = Math.max(0d, this.supplies - amount);
  }

  public void climb(double floors) {
    this.currentFloor += Math.max(0d, floors);
  }
}

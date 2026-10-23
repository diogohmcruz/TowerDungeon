package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.items.Item;

/**
 * What persists of the Tower. Floors are not stored (each is rebuilt on arrival); the garrison of
 * the floor the party stands on is part of the {@link ExpeditionSnapshot}. The gear the fallen left
 * on each floor, and the names of the Risen standing there, are kept for the whole campaign.
 */
public record TowerSnapshot(
    int currentFloor,
    List<Integer> clearedBosses,
    Map<Integer, List<Item>> floorGear,
    Map<Integer, List<String>> risen) {

  /** A save from before gear or Risen were left on floors has none waiting. */
  public TowerSnapshot {
    floorGear = floorGear == null ? Map.of() : floorGear;
    risen = risen == null ? Map.of() : risen;
  }
}

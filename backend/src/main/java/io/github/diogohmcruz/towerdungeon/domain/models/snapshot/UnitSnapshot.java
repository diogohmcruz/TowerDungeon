package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.Slot;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * One unit's facts. Health lives in the body parts (the totals are their sums); the rolled factors
 * are stored too, so a restore never re-rolls them. The mind is saved whole: sanity, its maximum,
 * every phobia with its known flag, and the floor that last frightened the unit. The worn items are
 * kept by slot. The affinity is {@code null} for none, as in a save from before affinities.
 */
public record UnitSnapshot(
    int id,
    String name,
    @JsonFormat(shape = JsonFormat.Shape.STRING) UnitStats type,
    double healthFactor,
    double damageFactor,
    boolean fixedStrengths,
    double maxMana,
    double currentMana,
    List<PartSnapshot> parts,
    Double sanity,
    Double maxSanity,
    List<PhobiaSnapshot> phobias,
    Integer fearFloor,
    Map<Slot, Item> gear,
    ItemCategory affinity) {

  /**
   * A save from before sanity existed has none of these: its units come back sane and fearless (no
   * floor frightened them, {@code -1}).
   */
  public UnitSnapshot {
    phobias = phobias == null ? List.of() : phobias;
    fearFloor = fearFloor == null ? -1 : fearFloor;
    gear = gear == null ? Map.of() : gear;
  }
}

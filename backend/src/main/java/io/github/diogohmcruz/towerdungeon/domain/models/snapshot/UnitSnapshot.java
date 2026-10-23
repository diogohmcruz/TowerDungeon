package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * One unit's facts. Health lives in the body parts (the totals are their sums); the rolled factors
 * are stored too, so a restore never re-rolls them. The mind is saved whole: sanity, its maximum,
 * every phobia with its known flag, and the floor that last frightened the unit.
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
    int fearFloor) {

  /** A save from before sanity existed has none of these: its units come back sane and fearless. */
  public UnitSnapshot {
    phobias = phobias == null ? List.of() : phobias;
  }
}

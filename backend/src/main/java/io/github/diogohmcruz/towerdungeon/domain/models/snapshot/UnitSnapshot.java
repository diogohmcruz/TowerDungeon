package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * One unit's facts. Health lives in the body parts (the totals are their sums); the rolled factors
 * are stored too, so a restore never re-rolls them.
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
    List<PartSnapshot> parts) {}

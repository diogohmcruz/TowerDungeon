package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;

/** One garrison enemy's facts; maximum health derives from the type and the health multiplier. */
public record EnemySnapshot(
    int id,
    String name,
    @JsonFormat(shape = JsonFormat.Shape.STRING) EnemyStats type,
    double currentHealth,
    double healthMultiplier,
    double damageMultiplier,
    boolean boss) {}

package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;

/**
 * One garrison enemy's facts. Health lives in the body parts (the totals are their sums), so the
 * layout the enemy was born with is kept and never re-rolled.
 */
public record EnemySnapshot(
    int id,
    String name,
    @JsonFormat(shape = JsonFormat.Shape.STRING) EnemyStats type,
    double healthMultiplier,
    double damageMultiplier,
    boolean boss,
    List<PartSnapshot> parts) {}

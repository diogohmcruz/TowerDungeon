package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;

/** One phobia as saved: the real feared type, and whether the player has learned it. */
public record PhobiaSnapshot(
    @JsonFormat(shape = JsonFormat.Shape.STRING) EnemyStats enemyType, boolean known) {}

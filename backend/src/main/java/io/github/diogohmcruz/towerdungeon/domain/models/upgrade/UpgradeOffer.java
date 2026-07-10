package io.github.diogohmcruz.towerdungeon.domain.models.upgrade;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;

/**
 * A player-facing view of an upgrade: its current level, whether it is maxed, and the cost of the
 * next purchase. Streamed inside the game state so the frontend can render the upgrade panel with
 * live affordability.
 */
public record UpgradeOffer(
    String id,
    String name,
    String description,
    int level,
    boolean maxed,
    boolean repeatable,
    String unlockUnit,
    Map<ResourceType, Double> nextCost) {}

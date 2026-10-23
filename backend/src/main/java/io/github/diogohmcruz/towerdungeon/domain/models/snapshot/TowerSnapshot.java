package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;

/**
 * What persists of the Tower. Floors are not stored (each is rebuilt on arrival); the garrison of
 * the floor the party stands on is part of the {@link ExpeditionSnapshot}.
 */
public record TowerSnapshot(int currentFloor, List<Integer> clearedBosses) {}

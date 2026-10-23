package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;

/** A Reinforcement Wave still climbing toward the party. */
public record WaveSnapshot(
    List<UnitSnapshot> units, double currentFloor, double supplies, double maxSupplies) {}

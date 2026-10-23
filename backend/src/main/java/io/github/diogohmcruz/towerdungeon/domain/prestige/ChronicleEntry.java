package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.List;

import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;

/** One ended Campaign in the Chronicle. */
public record ChronicleEntry(
    int generation,
    GameOutcome outcome,
    double toll,
    int legacy,
    int highestFloor,
    int guardiansSlain,
    int expeditions,
    List<FallenName> fallen) {

  public ChronicleEntry {
    fallen = fallen == null ? List.of() : List.copyOf(fallen);
  }
}

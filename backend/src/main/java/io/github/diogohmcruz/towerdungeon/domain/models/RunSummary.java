package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * A post-run report shown back in the village once an expedition ends. Captures how the delve went
 * — how deep the party pushed, what they slew and hauled out, and who they lost — snapshotted at
 * the moment the run resolves (before loot is banked on extract or forfeited on a wipe).
 */
public record RunSummary(
    String outcome,
    int startFloor,
    int deepestFloor,
    int floorsCleared,
    int enemiesDefeated,
    double creditsGained,
    double materialsGained,
    double relicsGained,
    int unitsLost,
    int survivors) {

  public static final String EXTRACTED = "EXTRACTED";
  public static final String WIPED = "WIPED";
}

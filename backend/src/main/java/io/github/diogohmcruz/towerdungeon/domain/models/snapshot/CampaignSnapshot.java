package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.Fallen;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;

/**
 * The facts of a Campaign that are always saved. Derived numbers (unlocked unit types, damage
 * multiplier, carrying capacity, available upgrades) are recomputed on load, never stored. Lists
 * are sorted so equal states give equal records.
 *
 * @param reserves the units resting at the village
 * @param vault banked resources
 * @param upgrades purchased upgrade levels
 * @param shortcutsOpened shortcuts ever opened (a ratchet: a rebalance never closes one)
 * @param tower null until the first Departure
 * @param trades the Villager pool's assignments, stores and The Count; null in older saves
 * @param artifactsTaken ids of the Artifacts taken this generation; absent in older saves
 * @param tally the never-spent counters; null in older saves
 * @param villageTier the Village Tier level reached, 1 to 4 (0 in older saves reads as Tier I)
 * @param wallOfNames every Unit that fell on the Tower or starved at home, in the order they fell
 */
public record CampaignSnapshot(
    double credit,
    List<UnitSnapshot> reserves,
    Map<ResourceType, Double> vault,
    Map<UpgradeType, Integer> upgrades,
    List<MilestoneType> milestones,
    List<ShortcutType> shortcutsOpened,
    TowerSnapshot tower,
    int highestFloor,
    int expeditionsCompleted,
    int enemiesDefeated,
    GameOutcome outcome,
    RunSummary lastExpeditionSummary,
    double villageFood,
    int villagers,
    TradesSnapshot trades,
    List<String> artifactsTaken,
    TallySnapshot tally,
    int villageTier,
    List<Fallen> wallOfNames) {

  /** A save from before Artifacts has none taken, and before the Wall of Names no names on it. */
  public CampaignSnapshot {
    artifactsTaken = artifactsTaken == null ? List.of() : artifactsTaken;
    wallOfNames = wallOfNames == null ? List.of() : wallOfNames;
  }

  /** The same record with the reserves cleared, for comparing everything but who is in them. */
  public CampaignSnapshot withoutReserves() {
    return new CampaignSnapshot(
        credit,
        List.of(),
        vault,
        upgrades,
        milestones,
        shortcutsOpened,
        tower,
        highestFloor,
        expeditionsCompleted,
        enemiesDefeated,
        outcome,
        lastExpeditionSummary,
        villageFood,
        villagers,
        trades,
        artifactsTaken,
        tally,
        villageTier,
        wallOfNames);
  }
}

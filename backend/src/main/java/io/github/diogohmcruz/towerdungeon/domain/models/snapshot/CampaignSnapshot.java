package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

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
    TradesSnapshot trades) {

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
        trades);
  }
}

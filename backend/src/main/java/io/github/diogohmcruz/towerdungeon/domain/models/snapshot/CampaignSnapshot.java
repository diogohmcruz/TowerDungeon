package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.Fallen;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionReport;

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
 * @param phase {@code ACTIVE} while the Campaign is played, {@code OFFER} once it has ended; absent
 *     in older saves, which read as active
 * @param pending the Succession report computed when the Campaign ended; null while it is active
 * @param choices the Succession's answers for the next Generation, by question; absent in older
 *     saves
 * @param portBuilt whether the Port was finished; absent in older saves, which read as not built
 *     (the footing is derived from the Expedition count)
 * @param boatTicks connected lifecycle ticks since the Port was finished; absent in older saves
 * @param arrivalsCame whether the Boat has docked and the Arrivals have come; absent in older saves
 * @param year the Year the Campaign is in, from 1; absent in older saves, which read as Year 1
 * @param yearTick connected lifecycle ticks gone in the Year; absent in older saves, which read as
 *     the first tick, in Spring
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
    List<Fallen> wallOfNames,
    CampaignPhase phase,
    SuccessionReport pending,
    Map<String, String> choices,
    Boolean portBuilt,
    Integer boatTicks,
    Boolean arrivalsCame,
    int year,
    int yearTick) {

  /** A save from before Artifacts has none taken, and before the Wall of Names no names on it. */
  public CampaignSnapshot {
    artifactsTaken = artifactsTaken == null ? List.of() : artifactsTaken;
    wallOfNames = wallOfNames == null ? List.of() : wallOfNames;
    phase = phase == null ? CampaignPhase.ACTIVE : phase;
    choices = choices == null ? Map.of() : choices;
    portBuilt = Boolean.TRUE.equals(portBuilt);
    boatTicks = boatTicks == null ? 0 : boatTicks;
    arrivalsCame = Boolean.TRUE.equals(arrivalsCame);
    year = Math.max(1, year);
    yearTick = Math.max(0, yearTick);
  }

  /**
   * The same record with the reserves cleared and the Year reset, for comparing everything but who
   * is in them and how long the Campaign has been connected.
   */
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
        wallOfNames,
        phase,
        pending,
        choices,
        portBuilt,
        boatTicks,
        arrivalsCame,
        1,
        0);
  }
}

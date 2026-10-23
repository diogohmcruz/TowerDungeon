package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.Fallen;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;

/**
 * The in-flight Expedition, saved so a refresh cannot reroll a floor or lose the party. The Loadout
 * is planned and has no field yet; it joins this record when it exists.
 *
 * @param surplus items that fitted nobody, counted per tier
 * @param runFallen the Units that fell on this Expedition, in the order they fell
 * @param saltedSupplies how much of the Supplies carried is salted
 * @param saltPack Salt the Salt Pack still carries
 * @param saltBearerId the Unit carrying the Pack; null when there is none
 * @param garrison the enemies standing on the party's current floor
 */
public record ExpeditionSnapshot(
    List<UnitSnapshot> party,
    double supplies,
    Map<ResourceType, Double> carriedLoot,
    double carriedCredits,
    Map<ItemTier, Integer> surplus,
    double lastFoodReturned,
    int runStartFloor,
    int runEnemiesDefeated,
    int runUnitsLost,
    List<Fallen> runFallen,
    List<EnemySnapshot> garrison,
    List<WaveSnapshot> waves,
    double saltedSupplies,
    int saltPack,
    Integer saltBearerId) {

  /** A save from before the fallen were recorded has none. */
  public ExpeditionSnapshot {
    runFallen = runFallen == null ? List.of() : runFallen;
  }
}

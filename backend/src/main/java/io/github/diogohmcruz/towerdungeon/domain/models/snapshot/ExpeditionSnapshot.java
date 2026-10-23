package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;

/**
 * The in-flight Expedition, saved so a refresh cannot reroll a floor or lose the party. The Loadout
 * is planned and has no field yet; it joins this record when it exists.
 *
 * @param surplus items that fitted nobody, counted per tier
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
    List<EnemySnapshot> garrison,
    List<WaveSnapshot> waves) {}

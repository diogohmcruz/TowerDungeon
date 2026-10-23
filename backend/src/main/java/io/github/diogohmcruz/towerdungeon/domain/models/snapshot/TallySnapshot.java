package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

/**
 * The Tally's counters, which spending never reduces.
 *
 * @param foodGrown Food the Tillers have grown
 * @param creditsBanked Credits found on Floors and banked (selling Food does not count)
 * @param materialsBanked Materials banked by Extraction or Victory
 * @param relicsBanked Relics banked by Extraction or Victory
 * @param floorsCleared Floors cleared by Expeditions that came home
 * @param peakVillagers the most Villagers the Village has held
 * @param inheritedStartFloor the highest Floor an Expedition set out from by a Shortcut the Roll
 *     gave (Narrow Stair); 0 for none. The Toll pays Ascent only above it
 */
public record TallySnapshot(
    double foodGrown,
    double creditsBanked,
    double materialsBanked,
    double relicsBanked,
    int floorsCleared,
    int peakVillagers,
    int inheritedStartFloor) {}

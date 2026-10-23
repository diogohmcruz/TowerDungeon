package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * What a Campaign has done, as the seven counters the Toll is read from. An immutable value: it
 * holds no state, the live counters (#20) will produce it.
 *
 * @param foodGrown Food grown by the Village
 * @param materialsBanked Materials banked by Extraction or Victory
 * @param creditsBanked Credits found on Floors and banked by Extraction or Victory
 * @param relicsBanked Relics banked by Extraction or Victory
 * @param expeditionsCompleted qualifying Expeditions completed
 * @param highestFloor the Highest Floor reached
 * @param guardiansSlain distinct Guardian Floors cleared, at most 10
 */
public record Tally(
    double foodGrown,
    double materialsBanked,
    double creditsBanked,
    double relicsBanked,
    int expeditionsCompleted,
    int highestFloor,
    int guardiansSlain) {}

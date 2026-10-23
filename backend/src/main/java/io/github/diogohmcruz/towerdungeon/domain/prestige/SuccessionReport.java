package io.github.diogohmcruz.towerdungeon.domain.prestige;

import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;

/**
 * What an ended Campaign was worth, computed once when it ended and stored with it, so a refresh on
 * the end screen shows the same report.
 *
 * @param outcome how the Campaign ended
 * @param generation the Generation that ended
 * @param toll the full breakdown of the Toll
 * @param legacy the Legacy awarded: the Toll's, or the pity floor of a Defeat
 * @param pity whether {@code legacy} is the Defeat pity floor and not what the Toll paid
 * @param guardiansSlain Guardians slain in the Campaign
 * @param highestFloor the Highest Floor reached
 * @param expeditions Expeditions completed
 */
public record SuccessionReport(
    GameOutcome outcome,
    int generation,
    Toll toll,
    int legacy,
    boolean pity,
    int guardiansSlain,
    int highestFloor,
    int expeditions) {}

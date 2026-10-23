package io.github.diogohmcruz.towerdungeon.domain.prestige;

import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;

/**
 * The sum of every ended Campaign's counters; it never starts over. Counters only rise.
 *
 * @param bestGuardians the most Guardians any one Campaign slew (the Toll's G_best)
 */
public record LifetimeLedger(
    double foodGrown,
    double materialsBanked,
    double creditsBanked,
    double relicsBanked,
    int expeditions,
    int unitsLost,
    int guardiansSlain,
    int bestGuardians,
    int highestFloor,
    int victories,
    int exoduses,
    int defeats) {

  public static final LifetimeLedger EMPTY = new LifetimeLedger(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

  /** The ledger with one ended Campaign added. */
  public LifetimeLedger plus(Tally tally, int unitsLostInCampaign, GameOutcome outcome) {
    return new LifetimeLedger(
        foodGrown + tally.foodGrown(),
        materialsBanked + tally.materialsBanked(),
        creditsBanked + tally.creditsBanked(),
        relicsBanked + tally.relicsBanked(),
        expeditions + tally.expeditionsCompleted(),
        unitsLost + unitsLostInCampaign,
        guardiansSlain + tally.guardiansSlain(),
        Math.max(bestGuardians, tally.guardiansSlain()),
        Math.max(highestFloor, tally.highestFloor()),
        victories + (outcome == GameOutcome.VICTORY ? 1 : 0),
        exoduses + (outcome == GameOutcome.EXODUS ? 1 : 0),
        defeats + (outcome == GameOutcome.DEFEAT ? 1 : 0));
  }
}

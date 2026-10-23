package io.github.diogohmcruz.towerdungeon.domain.prestige;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * Turns a Campaign's {@link Tally} into its Toll and Legacy. A pure function: no state, no I/O.
 *
 * <pre>
 * S  = sum M_i * sqrt(c_i / p_i) + ascent * max(0, min(HighestFloor, door) - B) + guardian * G
 * T  = S * (1 + load-mark * min(L, load-cap)) + Residue
 * NG = max(0, G - G_best)
 * P  = floor(T / points-divisor) + NG + Offering
 * Residue_next = T mod points-divisor
 * </pre>
 */
public final class TollCalculator {
  private TollCalculator() {}

  public static Toll calculate(Tally tally, TollContext context, GameProperties.Prestige config) {
    var counters = config.getCounters();
    double food = marks(counters.getFoodGrown(), tally.foodGrown());
    double materials = marks(counters.getMaterialsBanked(), tally.materialsBanked());
    double credits = marks(counters.getCreditsBanked(), tally.creditsBanked());
    double relics = marks(counters.getRelicsBanked(), tally.relicsBanked());
    double expeditions = marks(counters.getExpeditions(), tally.expeditionsCompleted());

    int floorsClimbed =
        Math.max(0, Math.min(tally.highestFloor(), config.getDoorFloor()) - context.baseline());
    double ascent = config.getAscentMarksPerFloor() * floorsClimbed;
    double guardians = config.getGuardianMarks() * tally.guardiansSlain();

    double marks = food + materials + credits + relics + expeditions + ascent + guardians;
    double loadFactor = 1 + config.getLoadMark() * Math.min(context.load(), config.getLoadCap());
    double toll = marks * loadFactor + context.residue();

    int newGround = Math.max(0, tally.guardiansSlain() - context.bestGuardians());
    double divisor = config.getPointsDivisor();
    int legacy = (int) Math.floor(toll / divisor) + newGround + context.offering();
    double residueNext = toll % divisor;

    return new Toll(
        food,
        materials,
        credits,
        relics,
        expeditions,
        ascent,
        guardians,
        marks,
        toll,
        newGround,
        legacy,
        residueNext);
  }

  private static double marks(GameProperties.Prestige.Counter counter, double count) {
    return counter.getMarks() * Math.sqrt(Math.max(0, count) / counter.getPar());
  }
}

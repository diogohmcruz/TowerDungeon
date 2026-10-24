package io.github.diogohmcruz.towerdungeon.agent.balance;

import java.util.List;
import java.util.Locale;

/**
 * What {@code runs} duels of one {@link PartyPlan} against one Floor came to. Every figure is a
 * mean over all the runs (a Wipe counts up to the tick it came), except where it says otherwise.
 *
 * @param guardian whether the Floor is held by a Guardian
 * @param surge whether it is a Surge Floor
 * @param winRate the share of runs in which the Party cleared the Floor
 * @param ticksMean mean ticks on the Floor, the clearing tick included
 * @param ticksSpread the standard deviation of the ticks
 * @param ticksMin the fewest ticks of any run
 * @param ticksMax the most ticks of any run
 * @param suppliesSpent Supplies drained on the Floor
 * @param unitsLost Units that fell on the Floor, by any cause
 * @param takenCombat health lost to the Garrison's blows
 * @param takenAttrition health lost to Attrition
 * @param takenStarvation health lost to Starvation
 * @param partyStart Units at the start (the plan's size)
 * @param garrisonHealth the Garrison's total health at the start, mean over the runs
 * @param damagePerUnitTick {@code d}: damage dealt per Unit per tick, over the Units on the Floor
 *     (mean of the party at the start and end of each run)
 * @param unitsNeeded the sizing figure {@code sqrt(HP / (1.25 d))} from the measured {@code d}
 * @param wipes how many runs ended in a Wipe
 * @param timeouts runs stopped by the tick limit (neither cleared nor wiped); 0 in a sound plan
 */
public record DuelReport(
    int floor,
    boolean guardian,
    boolean surge,
    long seed,
    int runs,
    double winRate,
    double ticksMean,
    double ticksSpread,
    int ticksMin,
    int ticksMax,
    double suppliesSpent,
    double unitsLost,
    double takenCombat,
    double takenAttrition,
    double takenStarvation,
    double partyStart,
    double garrisonHealth,
    double damagePerUnitTick,
    double unitsNeeded,
    int wipes,
    int timeouts) {

  /** The kind of Floor, as the baseline names it. */
  public String kind() {
    return guardian ? "Guardian" : surge ? "Surge" : "-";
  }

  /** The header of {@link #row()}'s table, Markdown. */
  public static String header() {
    return "| Floor | Kind | Runs | Win rate | Ticks | Ticks sd | Garrison HP | d | Units needed |"
        + " Party | Supplies spent | Units lost | Taken combat | Taken attrition | Taken"
        + " starvation |\n"
        + "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|";
  }

  /** One Markdown table row. */
  public String row() {
    return String.format(
        Locale.ROOT,
        "| %d | %s | %d | %.1f%% | %.2f | %.2f | %.2f | %.3f | %s | %.2f | %.2f | %.2f | %.2f |"
            + " %.2f | %.2f |",
        floor,
        kind(),
        runs,
        winRate * 100,
        ticksMean,
        ticksSpread,
        garrisonHealth,
        damagePerUnitTick,
        Double.isNaN(unitsNeeded) ? "n/a" : String.format(Locale.ROOT, "%.2f", unitsNeeded),
        partyStart,
        suppliesSpent,
        unitsLost,
        takenCombat,
        takenAttrition,
        takenStarvation);
  }

  /** A table of reports, one row each. */
  public static String markdown(List<DuelReport> reports) {
    var out = new StringBuilder(header());
    reports.forEach(report -> out.append('\n').append(report.row()));
    return out.append('\n').toString();
  }
}

package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

/**
 * What one closed attempt came to, kept by the server in the results store. Times are whole
 * seconds. There is no score: the Order of Arrival is read from the time or the Floor, and {@link
 * Cost} stands beside it, never added to it.
 *
 * @param playerId the Campaign Key's player id; never shown
 * @param alias the sanitised display name
 * @param status how the attempt ended
 * @param ticks connected lifecycle ticks the attempt ran
 * @param seconds the same in whole seconds: the time to Finish of a finisher
 * @param finishFloor the Finish Floor of the race
 * @param highestFloor the highest Floor reached
 * @param arrivals the second at which each Floor was first reached
 */
public record TrialResult(
    String raceId,
    String playerId,
    int attempt,
    String alias,
    ContenderLabel label,
    ContenderStatus status,
    long ticks,
    long seconds,
    int finishFloor,
    int highestFloor,
    Cost cost,
    Map<Integer, Long> arrivals,
    Instant closedAt) {

  /**
   * The Cost column: what the climb spent. Loot is what was banked, at sell prices (Materials and
   * Relics).
   */
  public record Cost(int expeditions, int wipes, int unitsLost, double lootBanked) {
    public static final Cost NONE = new Cost(0, 0, 0, 0);
  }

  public TrialResult {
    label = label == null ? ContenderLabel.HUMAN : label;
    alias = alias == null ? Alias.ANONYMOUS : alias;
    cost = cost == null ? Cost.NONE : cost;
    arrivals = arrivals == null ? Map.of() : Map.copyOf(arrivals);
  }

  public boolean finished() {
    return status == ContenderStatus.FINISHED;
  }

  /** When the highest Floor was first reached; the time to Finish if it was never noted. */
  public long arrivalAtHighest() {
    return arrivals.getOrDefault(highestFloor, seconds);
  }

  /**
   * The Order of Arrival: finishers first, by time; the rest by Highest Floor, then by who arrived
   * there earlier. Equal is a tie. Cost never enters it.
   */
  public static final Comparator<TrialResult> ARRIVAL =
      Comparator.comparing((TrialResult r) -> !r.finished())
          .thenComparingLong(r -> r.finished() ? r.seconds() : 0)
          .thenComparingInt(r -> r.finished() ? 0 : -r.highestFloor())
          .thenComparingLong(r -> r.finished() ? 0 : r.arrivalAtHighest());
}

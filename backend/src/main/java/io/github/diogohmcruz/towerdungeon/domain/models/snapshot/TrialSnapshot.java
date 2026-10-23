package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderStatus;

/**
 * The persisted facts of a Trial. The Terms' seed is not here: it rides in the {@link
 * RandomSnapshot}, and no Trial file ever leaves the server.
 *
 * @param expeditions Expeditions begun (the Cost column's count; not the Shortcut threshold's)
 * @param wipes Expeditions that ended in a Wipe
 * @param arrivals the tick at which each Floor was first reached
 */
public record TrialSnapshot(
    String raceId,
    int attempt,
    int finishFloor,
    long limitTicks,
    long ticksSinceStart,
    ContenderStatus status,
    int expeditions,
    int wipes,
    Map<Integer, Long> arrivals) {

  public TrialSnapshot {
    arrivals = arrivals == null ? Map.of() : Map.copyOf(arrivals);
  }

  /** A Trial saved before its Cost and timeline were kept. */
  public TrialSnapshot(
      String raceId,
      int attempt,
      int finishFloor,
      long limitTicks,
      long ticksSinceStart,
      ContenderStatus status) {
    this(raceId, attempt, finishFloor, limitTicks, ticksSinceStart, status, 0, 0, Map.of());
  }
}

package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A player's line for one Race, kept in the real Campaign's meta and shown in the Race record
 * section of the Chronicle screen. It is a plain line and no more: no Legacy, no Toll, no effect on
 * play. {@code finishFloor} is for the cosmetic pillar mark, which the client draws only on a Floor
 * the player has discovered.
 *
 * @param floor the Highest Floor of the attempt
 * @param place the place among the Contenders who had closed an attempt when it was written
 * @param tied whether the place is shared
 * @param of how many Contenders of the same table had closed an attempt then
 * @param seconds the time the attempt ran, to Finish for a finisher
 * @param fallen Units lost
 * @param line the text as shown
 */
public record RaceRecord(
    String raceId,
    int attempt,
    int finishFloor,
    int floor,
    int place,
    boolean tied,
    int of,
    long seconds,
    int fallen,
    Instant at,
    String line) {

  /** The record of a table row. */
  public static RaceRecord of(
      String raceId, int finishFloor, RaceTable.Row row, int of, Instant at) {
    var line =
        "Race of %s. Floor %d. %s of %d%s. %s. %d fallen."
            .formatted(
                at.atZone(ZoneOffset.UTC).toLocalDate(),
                row.highestFloor(),
                ordinal(row.place()),
                of,
                row.tied() ? " (tied)" : "",
                clock(row.seconds()),
                row.cost().unitsLost());
    return new RaceRecord(
        raceId,
        row.attempt(),
        finishFloor,
        row.highestFloor(),
        row.place(),
        row.tied(),
        of,
        row.seconds(),
        row.cost().unitsLost(),
        at,
        line);
  }

  /** 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st. */
  static String ordinal(int n) {
    var tens = n % 100;
    var suffix =
        tens >= 11 && tens <= 13
            ? "th"
            : switch (n % 10) {
              case 1 -> "st";
              case 2 -> "nd";
              case 3 -> "rd";
              default -> "th";
            };
    return n + suffix;
  }

  /** Minutes and seconds; minutes are not folded into hours. */
  static String clock(long seconds) {
    return "%d:%02d".formatted(seconds / 60, seconds % 60);
  }
}

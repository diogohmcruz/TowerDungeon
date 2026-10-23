package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.List;
import java.util.Optional;

/**
 * The Open Breach's question at the Succession: which Guardian Floor, slain in an earlier
 * Generation, holds only its garrison in the next. Its answers are ids made of the Floor ({@code
 * BREACH_15}) and an explicit {@code BREACH_NONE}, so it needs no enum of its own; the answer is
 * stored in {@code choices} under {@link #GROUP}.
 */
public final class OpenBreach {
  /** The question's name in the Succession's {@code choices}. */
  public static final String GROUP = "BREACH";

  /** The answer that leaves every Guardian where it stands. */
  public static final String NONE = "BREACH_NONE";

  private static final String PREFIX = "BREACH_";

  private OpenBreach() {}

  /** The id that chooses {@code floor}. */
  public static String idOf(int floor) {
    return PREFIX + floor;
  }

  /** Whether {@code id} is an answer to this question, well formed or not. */
  public static boolean isAnswer(String id) {
    return id != null && id.startsWith(PREFIX);
  }

  /** The Floor an id names; empty for {@link #NONE} and for anything that is not a Floor. */
  public static Optional<Integer> floorOf(String id) {
    if (!isAnswer(id) || NONE.equals(id)) {
      return Optional.empty();
    }
    try {
      var floor = Integer.parseInt(id.substring(PREFIX.length()));
      return floor > 0 ? Optional.of(floor) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * The Floors that may be opened: Guardian Floors slain in any Generation up to the one that has
   * just ended, except the Summit's, whose Guardian ends the Campaign.
   */
  public static List<Integer> candidates(PlayerMeta meta, int maxFloor) {
    return meta.slainUpTo(meta.generation()).stream().filter(floor -> floor < maxFloor).toList();
  }

  /**
   * The Floor to open for the Generation that follows {@code meta}'s, from the answer given: 0 when
   * the Roll does not hold the Open Breach, nothing was chosen, or the Floor is not on offer.
   */
  public static int resolve(PlayerMeta meta, String answer, int maxFloor) {
    if (!meta.holds(InheritanceType.THE_OPEN_BREACH)) {
      return 0;
    }
    return floorOf(answer).filter(floor -> candidates(meta, maxFloor).contains(floor)).orElse(0);
  }
}

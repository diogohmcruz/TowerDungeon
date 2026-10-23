package io.github.diogohmcruz.towerdungeon.domain.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * What became of a {@link Command}: whether it took effect, a stable reason code, and the notices
 * the caller may want to show. The WebSocket handler forwards the notices and ignores the rest, so
 * the wire does not change; an agent surface reads all of it.
 *
 * @param outcome whether the command took effect in full, in part, or not at all
 * @param reason the stable code; {@link Reason#OK} when the outcome is {@link Outcome#APPLIED}
 * @param message one plain sentence; fixed text, never an echo of caller-supplied strings
 * @param notices notices to show the player
 * @param stateSeq the state sequence after the command, or 0 when no Campaign was reached
 */
public record CommandResult(
    Outcome outcome, Reason reason, String message, List<Notice> notices, long stateSeq) {

  public enum Outcome {
    APPLIED,
    PARTIAL,
    REJECTED
  }

  /** Why a command ended as it did. */
  public enum Reason {
    OK,
    NO_CAMPAIGN,
    NOT_UNLOCKED,
    INSUFFICIENT_CREDITS,
    EXPEDITION_ACTIVE,
    NO_EXPEDITION,
    CAMPAIGN_ENDED,
    START_FLOOR_LOCKED,
    NO_UNITS,
    INSUFFICIENT_UNITS,
    UNKNOWN_ID,
    UPGRADE_MAXED,
    UPGRADE_UNAFFORDABLE,
    NOTHING_TO_SELL,
    TRADE_REFUSED,
    PURCHASE_REFUSED,
    PARTY_HELD,
    NOT_HELD,
    INVALID_ARGUMENT,
    NOT_ENDED,
    TOLL_TOO_LOW,
    NOT_AVAILABLE,
    WRITE_FAILED,
    TRIAL_CLOSED
  }

  public CommandResult {
    notices = List.copyOf(notices);
  }

  public static CommandResult applied(String message) {
    return new CommandResult(Outcome.APPLIED, Reason.OK, message, List.of(), 0);
  }

  public static CommandResult partial(Reason reason, String message) {
    return new CommandResult(Outcome.PARTIAL, reason, message, List.of(), 0);
  }

  public static CommandResult rejected(Reason reason, String message) {
    return new CommandResult(Outcome.REJECTED, reason, message, List.of(), 0);
  }

  /** The same result with a notice added. */
  public CommandResult withNotice(Notice notice) {
    var all = new ArrayList<>(notices);
    all.add(notice);
    return new CommandResult(outcome, reason, message, all, stateSeq);
  }

  public CommandResult withStateSeq(long seq) {
    return new CommandResult(outcome, reason, message, notices, seq);
  }

  /** Whether the command changed the state (in full or in part). */
  public boolean changedState() {
    return outcome != Outcome.REJECTED;
  }
}

package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.ToIntFunction;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import lombok.RequiredArgsConstructor;

/**
 * Decides who may enter a Trial and which attempt they are on. It checks the Terms (the race
 * exists, is open and is a Season; the Tower plan and the balance are the ones the race was set
 * on), the attempts the Key has used, and how many Contenders are connected. The Trial's own
 * Campaign file is named from the Key, the race and the attempt, so each attempt has a file of its
 * own and none of them is the real Campaign's.
 */
@Component
@RequiredArgsConstructor
public class TrialDesk {
  /** Why a Trial was refused. The name is sent to the client as the close reason. */
  public enum Refusal {
    UNKNOWN_RACE,
    NOT_OPEN,
    SPRINT_NOT_OPEN,
    PLAN_MISMATCH,
    CONFIG_MISMATCH,
    NO_ATTEMPTS,
    ATTEMPT_OPEN,
    FULL
  }

  public static class Refused extends RuntimeException {
    private final transient Refusal reason;

    public Refused(Refusal reason) {
      super(reason.name());
      this.reason = reason;
    }

    public Refusal reason() {
      return reason;
    }
  }

  /**
   * @param terms the race
   * @param attempt which attempt of the Key this is, from 1
   * @param trialId the id of the Trial's Campaign file and slot: 32 hex digits, like a player id
   */
  public record Admission(Terms terms, int attempt, String trialId) {}

  private final TermsRegistry registry;
  private final AttemptLedger ledger;
  private final GameProperties config;

  public TermsRegistry terms() {
    return registry;
  }

  public AttemptLedger ledger() {
    return ledger;
  }

  /**
   * Admits a Key to its Trial in a race: its current attempt (starting the first when it has none),
   * or, when {@code next}, the following one once the current is over.
   *
   * @param connectedOthers how many Trials of the race other Keys have connected right now
   * @throws Refused when the Key may not enter
   */
  public Admission admit(
      String playerId,
      String raceId,
      boolean next,
      Instant now,
      ToIntFunction<Terms> connectedOthers) {
    var terms = registry.find(raceId).orElseThrow(() -> new Refused(Refusal.UNKNOWN_RACE));
    if (terms.kind() != TermsKind.SEASON) {
      throw new Refused(Refusal.SPRINT_NOT_OPEN);
    }
    if (!terms.isOpenAt(now)) {
      throw new Refused(Refusal.NOT_OPEN);
    }
    if (terms.planVersion() != ArchitecturePlan.VERSION) {
      throw new Refused(Refusal.PLAN_MISMATCH);
    }
    if (!terms.configHash().equals(ConfigHash.of(config))) {
      throw new Refused(Refusal.CONFIG_MISMATCH);
    }
    synchronized (ledger) {
      var standing = ledger.standing(raceId, playerId);
      var starting = standing.started() == 0 || next;
      if (next && standing.started() > 0 && !standing.currentClosed()) {
        throw new Refused(Refusal.ATTEMPT_OPEN);
      }
      if (starting && standing.started() >= terms.maxAttempts()) {
        throw new Refused(Refusal.NO_ATTEMPTS);
      }
      if (connectedOthers.applyAsInt(terms) >= terms.maxConcurrent()) {
        throw new Refused(Refusal.FULL);
      }
      var attempt = starting ? ledger.start(raceId, playerId) : standing.started();
      return new Admission(terms, attempt, trialIdOf(playerId, raceId, attempt));
    }
  }

  /** The id of one attempt's Trial: no other id in the store can be mistaken for a player's. */
  public static String trialIdOf(String playerId, String raceId, int attempt) {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  ("trial|" + playerId + "|" + raceId + "|" + attempt)
                      .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JRE", e);
    }
  }
}

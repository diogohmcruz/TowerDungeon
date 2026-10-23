package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What a player keeps across Generations: the {@code meta} half of the record. Everything else in
 * the file ends with the Campaign.
 *
 * @param generation the Generation being played, or just ended and awaiting the next; 1 at first
 * @param legacy Legacy earned and not yet spent (the Roll spends it)
 * @param roll the Inheritances bought, by id (the Roll arrives with its own issue; empty until
 *     then)
 * @param residue Toll Marks carried into the next Toll
 * @param lifetime the Lifetime Ledger
 * @param chronicle every ended Campaign, oldest first, capped
 * @param towerSecret server-only; with the Generation it derives the Tower's seed. In the file it
 *     is sealed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerMeta(
    int generation,
    int legacy,
    List<String> roll,
    double residue,
    LifetimeLedger lifetime,
    List<ChronicleEntry> chronicle,
    String towerSecret) {

  public PlayerMeta {
    generation = Math.max(1, generation);
    roll = roll == null ? List.of() : List.copyOf(roll);
    lifetime = lifetime == null ? LifetimeLedger.EMPTY : lifetime;
    chronicle = chronicle == null ? List.of() : List.copyOf(chronicle);
  }

  /** The meta of a player who has never finished a Campaign. */
  public static PlayerMeta first(String towerSecret) {
    return new PlayerMeta(1, 0, List.of(), 0, LifetimeLedger.EMPTY, List.of(), towerSecret);
  }

  /**
   * The meta once the Campaign has ended: Legacy and residue from the report, the Lifetime Ledger
   * and the Chronicle updated. The Generation number does not move until the next one begins.
   */
  public PlayerMeta ended(
      SuccessionReport report,
      Tally tally,
      int unitsLost,
      List<FallenName> fallen,
      int chronicleLimit) {
    var entries = new ArrayList<>(chronicle);
    entries.add(
        new ChronicleEntry(
            report.generation(),
            report.outcome(),
            report.toll().toll(),
            report.legacy(),
            tally.highestFloor(),
            tally.guardiansSlain(),
            tally.expeditionsCompleted(),
            fallen));
    while (entries.size() > Math.max(1, chronicleLimit)) {
      entries.removeFirst();
    }
    return new PlayerMeta(
        generation,
        legacy + report.legacy(),
        roll,
        report.toll().residueNext(),
        lifetime.plus(tally, unitsLost, report.outcome()),
        entries,
        towerSecret);
  }

  /** Whether the meta has a Tower secret yet. */
  @JsonIgnore
  public boolean hasSecret() {
    return towerSecret != null && !towerSecret.isBlank();
  }

  /** The same meta under a Tower secret. */
  public PlayerMeta withSecret(String secret) {
    return new PlayerMeta(generation, legacy, roll, residue, lifetime, chronicle, secret);
  }

  /** The meta of the next Generation: the same record, one Generation on. */
  public PlayerMeta nextGeneration() {
    return new PlayerMeta(generation + 1, legacy, roll, residue, lifetime, chronicle, towerSecret);
  }

  /**
   * The Tower seed of the current Generation: the first eight bytes of SHA-256 over the secret and
   * the Generation. Only the server knows the secret, so a player cannot compute the Tower in
   * advance.
   */
  @JsonIgnore
  public long seed() {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256")
              .digest((towerSecret + ":" + generation).getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.wrap(digest).getLong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

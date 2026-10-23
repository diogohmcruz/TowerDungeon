package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * What a player keeps across Generations: the {@code meta} half of the record. Everything else in
 * the file ends with the Campaign.
 *
 * @param generation the Generation being played, or just ended and awaiting the next; 1 at first
 * @param legacy Legacy earned and not yet spent (the Roll spends it)
 * @param roll the Inheritances bought, each with the Legacy paid for it
 * @param residue Toll Marks carried into the next Toll
 * @param lifetime the Lifetime Ledger
 * @param chronicle every ended Campaign, oldest first, capped
 * @param slainGuardians the Guardian Floors slain, by the Generation that slew them (capped to the
 *     Chronicle's length): what the Open Breach chooses from
 * @param epitaphs the Epitaphs earned, by id; a ratchet
 * @param openBreach the Guardian Floor the Generation in play holds open, 0 for none
 * @param towerSecret server-only; with the Generation it derives the Tower's seed. In the file it
 *     is sealed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerMeta(
    int generation,
    int legacy,
    List<RollEntry> roll,
    double residue,
    LifetimeLedger lifetime,
    List<ChronicleEntry> chronicle,
    Map<Integer, List<Integer>> slainGuardians,
    List<String> epitaphs,
    int openBreach,
    String towerSecret) {

  public PlayerMeta {
    generation = Math.max(1, generation);
    roll = roll == null ? List.of() : List.copyOf(roll);
    lifetime = lifetime == null ? LifetimeLedger.EMPTY : lifetime;
    chronicle = chronicle == null ? List.of() : List.copyOf(chronicle);
    var slain = new TreeMap<Integer, List<Integer>>();
    if (slainGuardians != null) {
      slainGuardians.forEach((slewIn, floors) -> slain.put(slewIn, List.copyOf(floors)));
    }
    slainGuardians = Collections.unmodifiableMap(slain);
    epitaphs = epitaphs == null ? List.of() : List.copyOf(epitaphs);
    openBreach = Math.max(0, openBreach);
  }

  /** A meta with no Guardians on record, no Epitaphs and no Open Breach. */
  public PlayerMeta(
      int generation,
      int legacy,
      List<RollEntry> roll,
      double residue,
      LifetimeLedger lifetime,
      List<ChronicleEntry> chronicle,
      String towerSecret) {
    this(generation, legacy, roll, residue, lifetime, chronicle, null, null, 0, towerSecret);
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
    return ended(report, tally, unitsLost, fallen, chronicleLimit, List.of(), null);
  }

  /**
   * As {@link #ended(SuccessionReport, Tally, int, List, int)}, recording the Guardian Floors the
   * Campaign slew and granting the Epitaphs the new Lifetime Ledger earns ({@code epitaphRules}
   * null grants none).
   */
  public PlayerMeta ended(
      SuccessionReport report,
      Tally tally,
      int unitsLost,
      List<FallenName> fallen,
      int chronicleLimit,
      List<Integer> guardianFloors,
      GameProperties.Prestige.Epitaphs epitaphRules) {
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
    var ledger = lifetime.plus(tally, unitsLost, report.outcome());
    var slain = new TreeMap<>(slainGuardians);
    if (!guardianFloors.isEmpty()) {
      slain.put(report.generation(), guardianFloors.stream().sorted().toList());
    }
    while (slain.size() > Math.max(1, chronicleLimit)) {
      slain.remove(slain.firstKey());
    }
    return new PlayerMeta(
        generation,
        legacy + report.legacy(),
        roll,
        report.toll().residueNext(),
        ledger,
        entries,
        slain,
        epitaphRules == null ? epitaphs : Epitaph.granted(epitaphs, ledger, epitaphRules),
        openBreach,
        towerSecret);
  }

  /**
   * Whether Blood of the Door is on the Roll: the Door opens for this and every later Generation.
   */
  @JsonIgnore
  public boolean opensTheDoor() {
    return holds(InheritanceType.BLOOD_OF_THE_DOOR);
  }

  /** Legacy spent on the Roll: what was paid for what is held. */
  @JsonIgnore
  public int spent() {
    return roll.stream().mapToInt(RollEntry::paid).sum();
  }

  /** Whether the Roll holds the Inheritance. */
  public boolean holds(InheritanceType type) {
    return roll.stream().anyMatch(entry -> entry.id().equals(type.name()));
  }

  /** The same meta with a new Roll and the Legacy that is left after it. */
  public PlayerMeta withRoll(List<RollEntry> newRoll, int newLegacy) {
    return new PlayerMeta(
        generation,
        newLegacy,
        newRoll,
        residue,
        lifetime,
        chronicle,
        slainGuardians,
        epitaphs,
        openBreach,
        towerSecret);
  }

  /** Whether the meta has a Tower secret yet. */
  @JsonIgnore
  public boolean hasSecret() {
    return towerSecret != null && !towerSecret.isBlank();
  }

  /** The same meta under a Tower secret. */
  public PlayerMeta withSecret(String secret) {
    return new PlayerMeta(
        generation,
        legacy,
        roll,
        residue,
        lifetime,
        chronicle,
        slainGuardians,
        epitaphs,
        openBreach,
        secret);
  }

  /** The meta of the next Generation: the same record, one Generation on. */
  public PlayerMeta nextGeneration() {
    return new PlayerMeta(
        generation + 1,
        legacy,
        roll,
        residue,
        lifetime,
        chronicle,
        slainGuardians,
        epitaphs,
        0,
        towerSecret);
  }

  /** The same meta with the Guardian Floor the Generation in play holds open (0 for none). */
  public PlayerMeta withOpenBreach(int floor) {
    return new PlayerMeta(
        generation,
        legacy,
        roll,
        residue,
        lifetime,
        chronicle,
        slainGuardians,
        epitaphs,
        floor,
        towerSecret);
  }

  /** Whether the Epitaph has been earned. */
  public boolean has(Epitaph epitaph) {
    return epitaphs.contains(epitaph.name());
  }

  /**
   * The Guardian Floors slain in any Generation up to and including {@code generation}, lowest
   * first and without repeats: what the Open Breach may choose from.
   */
  public List<Integer> slainUpTo(int generation) {
    return slainGuardians.entrySet().stream()
        .filter(entry -> entry.getKey() <= generation)
        .flatMap(entry -> entry.getValue().stream())
        .distinct()
        .sorted()
        .toList();
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

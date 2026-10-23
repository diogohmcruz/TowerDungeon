package io.github.diogohmcruz.towerdungeon.persistence;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;

/**
 * Keeps one opaque, versioned record per Campaign. The store does not know the record's shape:
 * serialization and migration belong to the caller.
 */
public interface CampaignStore {
  /** Sequence number of a Campaign that has never been saved. */
  long INITIAL_SEQ = 0;

  /**
   * Stores a record as the new head. The save is assigned {@code expectedSeq + 1}, which is
   * emitted.
   *
   * @param expectedSeq the sequence number the caller last saw, or {@link #INITIAL_SEQ} for a first
   *     save; a different current value fails with {@link CampaignConflictException} and changes
   *     nothing
   * @param rotation whether the previous head becomes the newest backup
   */
  Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      byte[] payload);

  /**
   * As {@link #save(String, long, Rotation, int, String, byte[])}, but the payload is produced by
   * {@code payload} when the write runs, so a store with a writer thread can serialize there
   * instead of on the caller's thread. The default produces it when subscribed.
   */
  default Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      Supplier<byte[]> payload) {
    return Mono.fromSupplier(payload)
        .flatMap(bytes -> save(playerId, expectedSeq, rotation, schema, gameVersion, bytes));
  }

  /** The newest readable record, or empty when none is recoverable. */
  Mono<LoadResult> load(String playerId);

  enum Rotation {
    ROTATE,
    KEEP
  }

  /** Where a loaded record came from: the head ({@code backup == 0}) or backup {@code n}. */
  record Source(int backup) {
    public static final Source HEAD = new Source(0);

    public boolean isHead() {
      return backup == 0;
    }
  }

  /**
   * @param quarantined the sources found unreadable during this load and set aside
   */
  record LoadResult(
      byte[] payload,
      int schema,
      String gameVersion,
      long seq,
      Instant savedAt,
      Source source,
      List<Source> quarantined) {}
}

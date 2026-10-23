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

  /**
   * Keeps a copy of a record as it was before a migration step, as {@code pre-migration-v{schema}}.
   * The copy is never rotated or deleted, and an existing copy of the same schema is left as is.
   * The default keeps nothing, for stores that have no files (test doubles).
   */
  default Mono<Void> savePreMigration(
      String playerId, int schema, String gameVersion, byte[] payload) {
    return Mono.empty();
  }

  /**
   * The newest readable record, looked for in the head, then backups 1 to 5, then the pre-migration
   * copies (newest schema first); empty when none is recoverable.
   */
  Mono<LoadResult> load(String playerId);

  enum Rotation {
    ROTATE,
    KEEP
  }

  /**
   * Where a loaded record came from: the head ({@code backup == 0}), backup {@code n}, or the
   * pre-migration copy of schema {@code n} (stored as {@code backup == -n}).
   */
  record Source(int backup) {
    public static final Source HEAD = new Source(0);

    public static Source preMigration(int schema) {
      return new Source(-schema);
    }

    public boolean isPreMigration() {
      return backup < 0;
    }

    public boolean isHead() {
      return backup == 0;
    }
  }

  /**
   * @param quarantined the sources found unreadable during this load and set aside
   * @param notices what the player should be told about how the record was read (set by the
   *     migration layer, empty from the file store)
   * @param campaignLost the record could not be migrated to the current schema: {@code payload} is
   *     empty and the caller starts a fresh Campaign
   */
  record LoadResult(
      byte[] payload,
      int schema,
      String gameVersion,
      long seq,
      Instant savedAt,
      Source source,
      List<Source> quarantined,
      List<String> notices,
      boolean campaignLost) {

    public LoadResult(
        byte[] payload,
        int schema,
        String gameVersion,
        long seq,
        Instant savedAt,
        Source source,
        List<Source> quarantined) {
      this(payload, schema, gameVersion, seq, savedAt, source, quarantined, List.of(), false);
    }
  }
}

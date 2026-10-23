package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.function.Supplier;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reads every record through the migration chain: what {@link #load} returns is always at the
 * current schema, with ids that no longer exist dropped. A copy of the record is kept as {@code
 * pre-migration-v{n}.tdc} before each step. A record that cannot be migrated comes back flagged
 * {@link LoadResult#campaignLost()} with an empty payload, so the caller starts a fresh Campaign;
 * its copy stays on disk. A record that is already current but unreadable is passed through as it
 * is, so the caller refuses it. A record newer than this build is passed through untouched. Saves
 * go straight to the delegate.
 */
public class MigratingCampaignStore implements CampaignStore {
  private final CampaignStore delegate;
  private final CampaignCodec codec;

  public MigratingCampaignStore(CampaignStore delegate, CampaignCodec codec) {
    this.delegate = delegate;
    this.codec = codec;
  }

  @Override
  public Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      byte[] payload) {
    return delegate.save(playerId, expectedSeq, rotation, schema, gameVersion, payload);
  }

  @Override
  public Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      Supplier<byte[]> payload) {
    return delegate.save(playerId, expectedSeq, rotation, schema, gameVersion, payload);
  }

  @Override
  public Mono<Void> savePreMigration(
      String playerId, int schema, String gameVersion, byte[] payload) {
    return delegate.savePreMigration(playerId, schema, gameVersion, payload);
  }

  @Override
  public Mono<LoadResult> load(String playerId) {
    return delegate
        .load(playerId)
        .publishOn(Schedulers.boundedElastic())
        .map(result -> migrated(playerId, result));
  }

  private LoadResult migrated(String playerId, LoadResult result) {
    if (result.schema() > codec.current()) {
      return result;
    }
    var decoded =
        codec.decode(
            result.schema(),
            result.payload(),
            (schema, copy) ->
                delegate.savePreMigration(playerId, schema, result.gameVersion(), copy).block());
    if (decoded.campaignLost() && result.schema() == codec.current()) {
      // Nothing was migrated, so this is a damaged record, not a redesign: let the caller fail.
      return result;
    }
    byte[] payload =
        decoded.campaignLost() ? new byte[0] : codec.encode(decoded.snapshot(), decoded.meta());
    return new LoadResult(
        payload,
        codec.current(),
        result.gameVersion(),
        result.seq(),
        result.savedAt(),
        result.source(),
        result.quarantined(),
        decoded.notices(),
        decoded.campaignLost());
  }
}

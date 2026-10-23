package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads a player's stored Campaign as a snapshot plus its {@code meta}. The store behind it is the
 * {@link MigratingCampaignStore}, so the record is already at the current schema.
 */
@Component
@RequiredArgsConstructor
public class CampaignLoader {
  private final CampaignStore store;
  private final CampaignCodec codec;

  /**
   * @param snapshot null when the player has no recoverable Campaign
   * @param meta the {@code meta} section; null when nothing is stored or the campaign was lost
   * @param seq the sequence number to pass as {@code expectedSeq} on the next save
   * @param stored false when no record was found at all
   */
  public record Loaded(
      GameSnapshot snapshot,
      ObjectNode meta,
      long seq,
      boolean stored,
      CampaignStore.Source source,
      List<String> notices) {}

  public Mono<Loaded> load(String playerId) {
    return store
        .load(playerId)
        .map(this::decode)
        .switchIfEmpty(
            Mono.fromSupplier(
                () ->
                    new Loaded(
                        null,
                        null,
                        CampaignStore.INITIAL_SEQ,
                        false,
                        CampaignStore.Source.HEAD,
                        List.of())));
  }

  private Loaded decode(CampaignStore.LoadResult result) {
    if (result.campaignLost()) {
      return new Loaded(null, null, result.seq(), true, result.source(), result.notices());
    }
    var decoded =
        codec.decode(result.schema(), result.payload(), CampaignCodec.PreMigrationSink.NONE);
    return new Loaded(
        decoded.snapshot(), decoded.meta(), result.seq(), true, result.source(), result.notices());
  }
}

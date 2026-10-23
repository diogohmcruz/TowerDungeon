package io.github.diogohmcruz.towerdungeon.persistence;

import reactor.core.publisher.Mono;

/**
 * Resolves the caller's player id (the {@link CampaignStore} key) from the Campaign Key sent as a
 * bearer token. The Campaign Key handshake owns the real rules; this is the seam the HTTP endpoints
 * use to reach it.
 */
@FunctionalInterface
public interface PlayerIdResolver {
  /** The player id for a key, or empty when the key is not valid. */
  Mono<String> resolve(String bearerKey);
}

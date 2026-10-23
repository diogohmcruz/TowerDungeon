package io.github.diogohmcruz.towerdungeon.persistence;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** The {@link PlayerIdResolver} of the Campaign Key handshake: see {@link CampaignKey}. */
@Component
public class CampaignKeyPlayerIdResolver implements PlayerIdResolver {
  @Override
  public Mono<String> resolve(String bearerKey) {
    return CampaignKey.playerId(bearerKey).map(Mono::just).orElseGet(Mono::empty);
  }
}

package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToLongFunction;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import lombok.RequiredArgsConstructor;

/** {@link LiveCampaigns} over the {@link GameService}'s player slots. */
@Component
@RequiredArgsConstructor
public class GameServiceLiveCampaigns implements LiveCampaigns {
  private final GameService gameService;

  @Override
  public Optional<GameSnapshot> snapshot(String playerId) {
    return gameService.snapshotOf(playerId);
  }

  @Override
  public boolean replaceIfIdle(
      String playerId, GameSnapshot snapshot, ToLongFunction<OptionalLong> persist) {
    return gameService.replaceIfIdle(playerId, snapshot, persist);
  }
}

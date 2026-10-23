package io.github.diogohmcruz.towerdungeon.persistence;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToLongFunction;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;

/** The Campaigns currently held in memory, as far as export and import need them. */
public interface LiveCampaigns {
  /** The persisted record of a player's live Campaign, or empty when none is loaded. */
  Optional<GameSnapshot> snapshot(String playerId);

  /**
   * Replaces a player's live Campaign with {@code snapshot}, under the Campaign's state lock,
   * unless an Expedition is active or Held. {@code persist} writes the new Campaign and returns the
   * sequence number it was saved at; it is given the sequence the live Campaign is at, or empty
   * when the player has none loaded (then only the file changes). If it fails nothing changes.
   *
   * @return false, changing nothing, when an Expedition is active or Held
   */
  boolean replaceIfIdle(
      String playerId, GameSnapshot snapshot, ToLongFunction<OptionalLong> persist);
}

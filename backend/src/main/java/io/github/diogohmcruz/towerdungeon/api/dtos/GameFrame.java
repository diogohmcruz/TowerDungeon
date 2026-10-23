package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.diogohmcruz.towerdungeon.domain.models.GameState;

/**
 * One outbound frame: the game state, unwrapped so the existing client parser keeps working, plus
 * the session's own facts under {@code session}.
 */
public record GameFrame(@JsonUnwrapped GameState state, SessionMeta session) {

  /**
   * @param resumed true when the Campaign was loaded from the store, false for a fresh one
   * @param savedAt when the loaded record was saved; null for a fresh Campaign
   * @param schema the schema of the loaded record (the current one for a fresh Campaign)
   * @param notices things the player should be told, delivered once
   * @param held whether a Held Party is waiting; always false until the Held Party exists
   */
  public record SessionMeta(
      boolean resumed, Instant savedAt, int schema, List<Notice> notices, boolean held) {}

  /** A message for the player, with a stable code the client can switch on. */
  public record Notice(String code, String message) {}
}

package io.github.diogohmcruz.towerdungeon.api.dtos;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionView;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollForecast;
import io.github.diogohmcruz.towerdungeon.domain.trial.RaceRecord;

/**
 * One outbound frame: the game state, unwrapped so the existing client parser keeps working, plus
 * the session's own facts under {@code session}, and the Toll forecast under {@code forecast} (null
 * once the Campaign has ended), the player's Race records for the Chronicle screen, and the Finish
 * Floors that wear a pillar mark (only those the player has discovered).
 */
public record GameFrame(
    @JsonUnwrapped GameState state,
    SessionMeta session,
    TollForecast forecast,
    List<RaceRecord> raceRecords,
    List<Integer> pillarMarks) {

  /**
   * @param resumed true when the Campaign was loaded from the store, false for a fresh one
   * @param savedAt when the loaded record was saved; null for a fresh Campaign
   * @param schema the schema of the loaded record (the current one for a fresh Campaign)
   * @param notices things the player should be told, delivered once
   * @param held whether a Held Party is waiting for the player to resume
   * @param succession the Roll, the Legacy to spend and the choices open, while the Campaign waits
   *     in OFFER; null while it is played
   */
  public record SessionMeta(
      boolean resumed,
      Instant savedAt,
      int schema,
      List<Notice> notices,
      boolean held,
      SuccessionView succession) {}
}

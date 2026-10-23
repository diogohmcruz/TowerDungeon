package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persisted record of one Campaign: the always-saved {@code campaign} facts and the in-flight
 * {@code expedition} (null when no expedition is active). A {@code meta} section is reserved for
 * Prestige and is not written yet.
 *
 * @param random the seed and where the random streams stand, so a reloaded Campaign carries on from
 *     the same draws; null in a save from before seeded Campaigns, which then gets a new seed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameSnapshot(
    CampaignSnapshot campaign, ExpeditionSnapshot expedition, RandomSnapshot random) {

  /** A record with no random state, as saves from before seeded Campaigns have. */
  public GameSnapshot(CampaignSnapshot campaign, ExpeditionSnapshot expedition) {
    this(campaign, expedition, null);
  }

  /**
   * The same record without the seed. A file that leaves the server must not carry it, and one that
   * comes in must not bring one: a seed a client knows would show the Tower.
   */
  public GameSnapshot withoutRandom() {
    return new GameSnapshot(campaign, expedition, null);
  }
}

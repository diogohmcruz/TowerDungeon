package io.github.diogohmcruz.towerdungeon.domain.models;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.Getter;

/**
 * The permission the people who hold the Base grant the Village. The gates are config; this holds
 * the names, what each Tier opens and the one Ledger line its rise writes.
 */
@Getter
public enum VillageTier {
  HUDDLED(1, "Huddled", "Fields, Quarry, Scribe's Study", null),
  SHELTERED(
      2,
      "Sheltered",
      "Salt Pans",
      "Village Tier II, Sheltered. The Parliament lets the Salt Pans be worked."),
  FORTIFIED(
      3,
      "Fortified",
      "Spore Cellar",
      "Village Tier III, Fortified. The cellar below the Base is opened."),
  /** Opens nothing yet: the Relic-cost buildings it was meant for are not specified. */
  ENTRENCHED(
      4,
      "Entrenched",
      "",
      "Village Tier IV, Entrenched. The Alliance trusts you with the old stairs.");

  private final int level;
  private final String displayName;
  private final String opens;
  private final String ledgerLine;

  VillageTier(int level, String displayName, String opens, String ledgerLine) {
    this.level = level;
    this.displayName = displayName;
    this.opens = opens;
    this.ledgerLine = ledgerLine;
  }

  public boolean atLeast(VillageTier other) {
    return level >= other.level;
  }

  public static VillageTier ofLevel(int level) {
    for (var tier : values()) {
      if (tier.level == level) {
        return tier;
      }
    }
    return HUDDLED;
  }

  /** The highest Tier whose gate, and every gate below it, the Village now satisfies. */
  public static VillageTier reached(
      GameProperties.Tiers gates, CampaignTally tally, int highestFloor, int expeditions) {
    var reached = HUDDLED;
    for (var tier : values()) {
      if (tier == HUDDLED) {
        continue;
      }
      if (!tier.met(gates, tally, highestFloor, expeditions)) {
        break;
      }
      reached = tier;
    }
    return reached;
  }

  private boolean met(
      GameProperties.Tiers gates, CampaignTally tally, int highestFloor, int expeditions) {
    var gate =
        switch (this) {
          case SHELTERED -> gates.getSheltered();
          case FORTIFIED -> gates.getFortified();
          case ENTRENCHED -> gates.getEntrenched();
          case HUDDLED -> throw new IllegalStateException("The start asks nothing");
        };
    return tally.getFoodGrown() >= gate.getFoodGrown()
        && tally.getMaterialsBanked() >= gate.getMaterialsBanked()
        && tally.getRelicsBanked() >= gate.getRelicsBanked()
        && highestFloor >= gate.getHighestFloor()
        && expeditions >= gate.getExpeditions();
  }
}

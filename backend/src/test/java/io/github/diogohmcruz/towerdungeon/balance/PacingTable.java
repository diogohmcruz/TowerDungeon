package io.github.diogohmcruz.towerdungeon.balance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import io.github.diogohmcruz.towerdungeon.agent.balance.PartyPlan;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import tools.jackson.databind.json.JsonMapper;

/**
 * The pacing table of {@code prestige-and-persistence.md} 3.12 as data: for each Generation the
 * Highest Floor it should reach, the Legacy it should earn and the reference {@link PartyPlan} of
 * the floor duel harness (#112), which the table loads into. The data is {@code
 * balance/pacing-table.json} on the test classpath; the numbers are placeholders the floor duel
 * harness refits.
 *
 * @param assumptions the economy model the plans are checked against
 * @param rows one per Generation, in order
 */
public record PacingTable(Assumptions assumptions, List<Row> rows) {

  /**
   * @param expeditionsPerGeneration Expeditions a Campaign plays before its final push (3.10 models
   *     15 to 17 full climbs; 12 is the cautious figure)
   * @param relicLoreCapacity Carrying Capacity the Relic Lore Milestone gives
   * @param relicsPerExpedition Relics from surplus items per Expedition; 0, since the rate is not
   *     measured
   */
  public record Assumptions(
      int expeditionsPerGeneration, double relicLoreCapacity, double relicsPerExpedition) {}

  /**
   * @param generation 1 to 12
   * @param highestFloor the Highest Floor the Generation should reach
   * @param legacy the Legacy it should earn
   * @param rationale one line saying why
   * @param rollBuysAtStart the Inheritances bought with the Legacy held at the start, in order
   * @param plan the reference plan for the final push
   */
  public record Row(
      int generation,
      int highestFloor,
      int legacy,
      String rationale,
      List<InheritanceType> rollBuysAtStart,
      PartyPlan plan) {}

  /** What the Expeditions before the final push bank, and what the plan costs. */
  public record Economy(double credits, double materials, double relics) {}

  public static PacingTable load() {
    try (InputStream in = PacingTable.class.getResourceAsStream("/balance/pacing-table.json")) {
      return JsonMapper.builder()
          .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build()
          .readValue(in, PacingTable.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * What the Expeditions before the final push bank. The i-th of n climbs from the start Floor and
   * clears {@code ceil((H - start) * i / (n + 1))} Floors, so the Campaign works its way up to the
   * target. A Floor pays Credits and Materials by the reward config, Guardians x3, and the Loot of
   * every Floor cleared is banked (Extraction). Quarry Heap and Relics are not counted.
   */
  public Economy earned(Row row, GameProperties props) {
    var n = assumptions.expeditionsPerGeneration();
    var credits = 0.0;
    var materials = 0.0;
    var span = Math.max(1, row.highestFloor() - row.plan().startFloor());
    for (var i = 1; i <= n; i++) {
      var cleared = (int) Math.ceil(span * (double) i / (n + 1));
      for (var f = row.plan().startFloor(); f < row.plan().startFloor() + cleared; f++) {
        var factor =
            f % props.getBoss().getInterval() == 0
                ? props.getReward().getBossRewardMultiplier()
                : 1.0;
        credits += props.getReward().getCreditPerFloor() * f * factor;
        materials += props.getReward().getMaterialsPerFloor() * f * factor;
      }
    }
    var heldCredits = props.getStart().getCredit();
    var roll = props.getPrestige().getRoll();
    if (row.plan().inheritances().contains(InheritanceType.SEED_GRAIN)) {
      heldCredits = roll.getSeedGrain().getCredits();
    }
    if ("CREDITS".equals(row.plan().dowry())) {
      heldCredits += roll.getDowry().getCredits();
    }
    var relics = n * assumptions.relicsPerExpedition();
    if ("RELICS".equals(row.plan().dowry())) {
      relics += roll.getDowry().getRelics();
    }
    return new Economy(credits + heldCredits, materials, relics);
  }

  /** What the plan costs: Credits for recruits, Materials and Relics for Upgrades. */
  public Economy cost(Row row, GameProperties props) {
    var plan = row.plan();
    var credits = 0.0;
    for (var unit : UnitStats.values()) {
      credits += unit.getCost() * Math.max(0, plan.count(unit) - free(row, unit));
    }
    var materials = 0.0;
    var relics = 0.0;
    for (var upgrade : UpgradeType.values()) {
      for (var owned = 0; owned < plan.level(upgrade); owned++) {
        var price = upgrade.costAtLevel(owned);
        materials += price.getOrDefault(ResourceType.MATERIALS, 0.0);
        relics += price.getOrDefault(ResourceType.RELICS, 0.0);
      }
    }
    return new Economy(credits, materials, relics);
  }

  /** Units of a Type the Campaign starts with free: the Founders, and the Heir (a Warrior here). */
  static int free(Row row, UnitStats unit) {
    var heir = row.plan().inheritances().contains(InheritanceType.THE_HEIR) ? 1 : 0;
    return switch (unit) {
      case WARRIOR -> 3 + heir;
      case ARCHER -> 1;
      default -> 0;
    };
  }
}

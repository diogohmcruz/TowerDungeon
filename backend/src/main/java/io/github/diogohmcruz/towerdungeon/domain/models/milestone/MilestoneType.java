package io.github.diogohmcruz.towerdungeon.domain.models.milestone;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.Getter;

/**
 * Story-driven milestones that trigger automatically as the campaign progresses — either by the
 * party reaching a given tower floor or by completing a number of expeditions. Mirrors the manga,
 * where new companions join and the party hardens the deeper and longer they delve. Unlike {@code
 * UpgradeType}, milestones are free and one-time: they are granted the moment their condition is
 * met.
 */
@Getter
public enum MilestoneType {
  ELIQUO_JOINS(
      "Captain Eliquo joins",
      "The veteran expedition captain recognizes the party's grit and takes command.",
      TriggerKind.EXPEDITIONS,
      3,
      UnitStats.CAPTAIN,
      0d,
      0d),
  SARGAN_JOINS(
      "Sargan the Fire Mage joins",
      "Deep on the fifth floor the party is joined by Sargan, a formidable fire mage.",
      TriggerKind.FLOOR,
      5,
      UnitStats.MAGE,
      0d,
      0d),
  SEASONED_DELVERS(
      "Seasoned Delvers",
      "Survival is the best teacher — hardened veterans strike 10% harder.",
      TriggerKind.EXPEDITIONS,
      8,
      null,
      0d,
      0.10),
  RELIC_LORE(
      "Relic Lore",
      "Ancient relics found deep in the tower teach the party to pack for the descent (+25 carry).",
      TriggerKind.FLOOR,
      10,
      null,
      25d,
      0d);

  /** What kind of progress unlocks a milestone. */
  public enum TriggerKind {
    FLOOR,
    EXPEDITIONS
  }

  private final String displayName;
  private final String description;
  private final TriggerKind triggerKind;
  private final int threshold;
  private final UnitStats unlockUnit;
  private final double capacityBonus;
  private final double damageBonus;

  MilestoneType(
      String displayName,
      String description,
      TriggerKind triggerKind,
      int threshold,
      UnitStats unlockUnit,
      double capacityBonus,
      double damageBonus) {
    this.displayName = displayName;
    this.description = description;
    this.triggerKind = triggerKind;
    this.threshold = threshold;
    this.unlockUnit = unlockUnit;
    this.capacityBonus = capacityBonus;
    this.damageBonus = damageBonus;
  }

  /** Whether the milestone's condition is met given the campaign's progress so far. */
  public boolean isSatisfied(int deepestFloor, int expeditionsCompleted) {
    return switch (triggerKind) {
      case FLOOR -> deepestFloor >= threshold;
      case EXPEDITIONS -> expeditionsCompleted >= threshold;
    };
  }

  /** Human-readable unlock condition for the UI, e.g. "Reach floor 5". */
  public String getTriggerLabel() {
    return switch (triggerKind) {
      case FLOOR -> "Reach floor " + threshold;
      case EXPEDITIONS -> "Complete " + threshold + " expeditions";
    };
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models.shortcut;

import lombok.Getter;

/**
 * Footholds carved into the tower that let a fresh expedition begin partway up instead of climbing
 * from the base. In the manga the kingdom's sappers and knights slowly secure the cleared lower
 * halls — rigging lifts up the central shaft, garrisoning landings and anchoring advance camps — so
 * each new sortie can push off from a hard-won waypoint. Shortcuts open automatically once the
 * campaign has spilled enough monster blood or mounted enough expeditions; each one deposits the
 * party on a specific floor.
 */
@Getter
public enum ShortcutType {
  SUPPLY_LIFT(
      "Supply Lift",
      "The quartermasters rig a rope-and-counterweight lift up the cleared shaft, hauling the"
          + " vanguard past the slime-choked entrance halls in a single haul.",
      TriggerKind.KILLS,
      25,
      3),
  VANGUARD_CAMP(
      "Vanguard Camp",
      "After enough sorties, the crown funds a fortified camp on the fifth landing — palisades,"
          + " a field kitchen and a watch — so expeditions start beyond the outer wards.",
      TriggerKind.EXPEDITIONS,
      3,
      5),
  KNIGHTS_WAYSTATION(
      "Knights' Waystation",
      "Where the royal knights broke the tower's undead garrison, a waystation now holds the"
          + " landing: a foothold at the very threshold of the deep tower.",
      TriggerKind.KILLS,
      100,
      10),
  DEEP_ANCHOR(
      "Deep Anchor",
      "Veteran delvers hammer iron anchors and guide-lines into the fifteenth floor, opening a"
          + " secured descent line the seasoned party can rappel straight down to.",
      TriggerKind.EXPEDITIONS,
      8,
      15);

  /** What kind of campaign progress opens a shortcut. */
  public enum TriggerKind {
    EXPEDITIONS,
    KILLS
  }

  private final String displayName;
  private final String description;
  private final TriggerKind triggerKind;
  private final int threshold;
  private final int floor;

  ShortcutType(
      String displayName, String description, TriggerKind triggerKind, int threshold, int floor) {
    this.displayName = displayName;
    this.description = description;
    this.triggerKind = triggerKind;
    this.threshold = threshold;
    this.floor = floor;
  }

  /** Whether the shortcut has been opened given the campaign's progress so far. */
  public boolean isUnlocked(int expeditionsCompleted, int enemiesDefeated) {
    return switch (triggerKind) {
      case EXPEDITIONS -> expeditionsCompleted >= threshold;
      case KILLS -> enemiesDefeated >= threshold;
    };
  }

  /** Human-readable unlock condition for the UI, e.g. "Defeat 25 enemies". */
  public String getTriggerLabel() {
    return switch (triggerKind) {
      case EXPEDITIONS -> "Complete " + threshold + " expeditions";
      case KILLS -> "Defeat " + threshold + " enemies";
    };
  }
}

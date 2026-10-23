package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.Optional;

/**
 * Every Inheritance the Roll will ever hold, by a stable id. The constant name is the id written
 * into the player's file, so a name never changes once released. All seventeen exist from the
 * start; those whose effect is not built yet are {@linkplain #isAvailable() unavailable}: listed,
 * never bought. Costs and effect sizes are config ({@code game.prestige.roll}), not here.
 */
public enum InheritanceType {
  SEED_GRAIN("Seed Grain", 1, Kind.POWER, true),
  MUSTER_ROLL("Muster Roll", 1, Kind.QUALITY, true),
  HARDY_STOCK("Hardy Stock", 1, Kind.POWER, true),
  WHETSTONE_HABIT("Whetstone Habit", 1, Kind.POWER, true),
  THE_DOWRY("The Dowry", 2, Kind.DECISION, true),
  HEAVIER_PACKS("Heavier Packs", 2, Kind.POWER, true),
  VILLAGE_CHARTER("Village Charter", 2, Kind.POWER, true),
  THE_HEIR("The Heir", 2, Kind.DECISION, true),
  BLOOD_OF_THE_DOOR("Blood of the Door", 3, Kind.DECISION, true),
  NARROW_STAIR("Narrow Stair", 3, Kind.DECISION, false),
  OVERREACH("Overreach", 3, Kind.DECISION, false),
  QUARTERMASTERS_TALLY("Quartermaster's Tally", 3, Kind.QUALITY, false),
  HARD_WON_HABITS("Hard-Won Habits", 4, Kind.POWER, true),
  THE_OPEN_BREACH("The Open Breach", 4, Kind.DECISION, true),
  READER_OF_OMENS("Reader of Omens", 4, Kind.DECISION, false),
  OFFERING_STONE("Offering Stone", 4, Kind.DECISION, true),
  SECOND_PACT("Second Pact", 4, Kind.DECISION, false);

  /** Power, decision (or access) and quality of life. */
  public enum Kind {
    POWER,
    DECISION,
    QUALITY
  }

  private final String displayName;
  private final int course;
  private final Kind kind;
  private final boolean available;

  InheritanceType(String displayName, int course, Kind kind, boolean available) {
    this.displayName = displayName;
    this.course = course;
    this.kind = kind;
    this.available = available;
  }

  public String displayName() {
    return displayName;
  }

  /** The Course (row) it sits in, 1 to 4. */
  public int course() {
    return course;
  }

  public Kind kind() {
    return kind;
  }

  /** Whether its effect exists, and so whether it can be bought. */
  public boolean isAvailable() {
    return available;
  }

  /** The Inheritance with this id; empty for an id the registry does not know. */
  public static Optional<InheritanceType> find(String id) {
    if (id == null) {
      return Optional.empty();
    }
    for (var type : values()) {
      if (type.name().equals(id)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }
}

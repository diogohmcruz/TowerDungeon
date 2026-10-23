package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.Optional;

import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * The choices a Succession offers for the next Generation, by stable id. Each belongs to a {@link
 * Group}, one answer per group, and is open only while the Roll holds the Inheritance it needs.
 */
public enum SuccessionChoice {
  DOWRY_CREDITS(Group.DOWRY, null, null),
  DOWRY_MATERIALS(Group.DOWRY, ResourceType.MATERIALS, null),
  DOWRY_RELICS(Group.DOWRY, ResourceType.RELICS, null),
  HEIR_WARRIOR(Group.HEIR, null, UnitStats.WARRIOR),
  HEIR_ARCHER(Group.HEIR, null, UnitStats.ARCHER),
  HEIR_PORTER(Group.HEIR, null, UnitStats.PORTER);

  /** A question the Succession asks once. */
  public enum Group {
    DOWRY(InheritanceType.THE_DOWRY),
    HEIR(InheritanceType.THE_HEIR);

    private final InheritanceType requires;

    Group(InheritanceType requires) {
      this.requires = requires;
    }

    /** The Inheritance that opens the question. */
    public InheritanceType requires() {
      return requires;
    }

    /** The answer given when the player begins the Generation without choosing. */
    public SuccessionChoice fallback() {
      for (var choice : SuccessionChoice.values()) {
        if (choice.group == this) {
          return choice;
        }
      }
      throw new IllegalStateException(name());
    }
  }

  private final Group group;
  private final ResourceType dowryResource;
  private final UnitStats heirType;

  SuccessionChoice(Group group, ResourceType dowryResource, UnitStats heirType) {
    this.group = group;
    this.dowryResource = dowryResource;
    this.heirType = heirType;
  }

  public Group group() {
    return group;
  }

  /** The Resource a Dowry choice pays in; null for Credits and for the Heir's choices. */
  public ResourceType dowryResource() {
    return dowryResource;
  }

  /** The Unit Type an Heir choice names; null for the Dowry's. */
  public UnitStats heirType() {
    return heirType;
  }

  public static Optional<SuccessionChoice> find(String id) {
    if (id == null) {
      return Optional.empty();
    }
    for (var choice : values()) {
      if (choice.name().equals(id)) {
        return Optional.of(choice);
      }
    }
    return Optional.empty();
  }
}

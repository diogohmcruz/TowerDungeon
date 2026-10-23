package io.github.diogohmcruz.towerdungeon.domain.prestige;

/** Why the Roll will not take a purchase or a refund. */
public enum RollRefusal {
  /** The id is not in the registry. */
  UNKNOWN,
  /** Its effect is not built yet. */
  UNAVAILABLE,
  ALREADY_HELD,
  NOT_HELD,
  /** Not enough Legacy has been spent in the Courses below. */
  COURSE_CLOSED,
  INSUFFICIENT_LEGACY,
  /** Giving it back would close a Course that other held Inheritances stand on. */
  HELD_UP
}

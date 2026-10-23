package io.github.diogohmcruz.towerdungeon.domain.models;

/** One of the four Quarters of a Year. */
public enum Quarter {
  SPRING,
  SUMMER,
  AUTUMN,
  WINTER;

  /** The Quarter after this one; Winter gives way to the next Year's Spring. */
  public Quarter next() {
    return values()[(ordinal() + 1) % values().length];
  }
}

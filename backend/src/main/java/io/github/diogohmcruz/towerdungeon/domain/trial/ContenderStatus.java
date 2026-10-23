package io.github.diogohmcruz.towerdungeon.domain.trial;

/** Where a Contender stands in a Trial. LOST_CONTACT arrives with the Sprint. */
public enum ContenderStatus {
  /** Still climbing. The clock runs while connected. */
  RACING,
  /** The Finish Floor's Guardian is slain: the result is frozen. */
  FINISHED,
  /** The clock ran out first. */
  TIMED_OUT,
  /** The Tower took everyone and left no means to field another soldier. */
  DEFEATED;

  /** Whether the Trial is over and its Campaign sealed. */
  public boolean isClosed() {
    return this != RACING;
  }
}

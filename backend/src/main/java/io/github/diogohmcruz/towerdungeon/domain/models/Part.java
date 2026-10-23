package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Data;

/**
 * One body part of a {@link Unit}: its own health pool. A lost arm or leg keeps its place in the
 * list, flagged {@code lost}, and no longer counts towards the unit's health totals.
 */
@Data
public class Part {
  private final BodyPart kind;
  private double maxHealth;
  private double currentHealth;
  private boolean lost;

  public Part(BodyPart kind, double maxHealth) {
    this.kind = kind;
    this.maxHealth = maxHealth;
    this.currentHealth = maxHealth;
  }

  /** Health left as a share of this part's maximum. */
  public double ratio() {
    return maxHealth > 0 ? currentHealth / maxHealth : 1.0;
  }

  /** Health this part is missing from its maximum. */
  public double missing() {
    return maxHealth - currentHealth;
  }
}

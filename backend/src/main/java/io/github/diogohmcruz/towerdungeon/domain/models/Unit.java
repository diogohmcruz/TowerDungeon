package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseUnit {
  private final UnitStats stats;

  public Unit(UnitStats stats) {
    super();
    this.stats = stats;
    this.setCurrentHealth(stats.getHealth());
  }

  public void setCurrentHealth(Double currentHealth) {
    super.setCurrentHealth(Math.min(currentHealth, stats.getHealth()));
  }

  public void receiveAttack(Double attack, AttackType attackType) {
    var attackTotal =
        attack
            * (attackType == null
                ? 1.0
                : this.getStats().getWeaknesses().getOrDefault(attackType, 1.0));
    super.receiveAttack(attackTotal);
  }

  @Override
  public String toString() {
    return this.getName() + "[" + this.getStats().name() + "]";
  }
}

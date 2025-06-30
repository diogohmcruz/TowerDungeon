package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Enemy extends BaseUnit {
  private final EnemyStats stats;

  public Enemy(EnemyStats stats) {
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

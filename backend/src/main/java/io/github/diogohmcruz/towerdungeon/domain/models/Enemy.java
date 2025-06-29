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

  public void receiveAttack(Double attack, AttackType attackType) {
    setCurrentHealth(
        getCurrentHealth()
            - (attack * this.getStats().getWeaknesses().getOrDefault(attackType, 1.0)));
  }

  @Override
  public String toString() {
    return this.getName() + "[" + this.getStats().name() + "]";
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;

@Data
public class Enemy {
  private final Integer id;
  private final EnemyStats stats;
  private Double currentHealth;

  public Enemy(EnemyStats stats) {
    this.id = ThreadLocalRandom.current().nextInt();
    this.stats = stats;
    this.currentHealth= stats.getHealth();
  }

  public void receiveAttack(Double attack, AttackType attackType) {
    currentHealth -= attack * this.getStats().getWeaknesses().getOrDefault(attackType, 1.0);
  }
}

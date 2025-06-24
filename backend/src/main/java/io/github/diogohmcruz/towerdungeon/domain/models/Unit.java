package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;

@Data
public class Unit {
  private final Integer id;
  private final UnitStats stats;
  private Double currentHealth;

  public Unit(UnitStats stats) {
    this.id = ThreadLocalRandom.current().nextInt();
    this.stats = stats;
    this.currentHealth= stats.getHealth();
  }

  public void receiveAttack(Double attack) {
    currentHealth -= attack;
  }
}

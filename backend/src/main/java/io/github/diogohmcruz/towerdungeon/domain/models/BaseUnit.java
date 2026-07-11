package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.concurrent.ThreadLocalRandom;

import lombok.Data;
import net.datafaker.Faker;

@Data
public class BaseUnit {
  private final Integer id;
  private final String name;
  private Double currentHealth = 0.0;
  private Double maxHealth = 0.0;

  public BaseUnit() {
    this(new Faker().greekPhilosopher().name());
  }

  public BaseUnit(String name) {
    this.id = ThreadLocalRandom.current().nextInt();
    this.name = name;
  }

  public void setCurrentHealth(Double currentHealth) {
    var capped =
        maxHealth != null && maxHealth > 0 ? Math.min(currentHealth, maxHealth) : currentHealth;
    this.currentHealth = Math.max(0, capped);
  }

  public void receiveAttack(Double attack) {
    setCurrentHealth(currentHealth - attack);
  }

  public boolean isDead() {
    return currentHealth <= 0;
  }
}

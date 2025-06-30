package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.concurrent.ThreadLocalRandom;

import lombok.Data;
import net.datafaker.Faker;

@Data
public class BaseUnit {
  private final Integer id;
  private final String name;
  private Double currentHealth = 0.0;

  public BaseUnit() {
    this.id = ThreadLocalRandom.current().nextInt();
    this.name = new Faker().greekPhilosopher().name();
  }

  public void setCurrentHealth(Double currentHealth) {
    this.currentHealth = Math.max(0, currentHealth);
  }

  public void receiveAttack(Double attack) {
    currentHealth -= attack;
  }
}

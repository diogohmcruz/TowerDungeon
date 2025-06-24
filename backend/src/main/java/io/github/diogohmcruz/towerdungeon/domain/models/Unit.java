package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import net.datafaker.Faker;

@Data
public class Unit {
  private final Integer id;
  private final String name;
  private final UnitStats stats;
  private Double currentHealth;

  public Unit(UnitStats stats) {
    this.id = ThreadLocalRandom.current().nextInt();
    this.name = new Faker().greekPhilosopher().name();
    this.stats = stats;
    this.currentHealth= stats.getHealth();
  }

  public void receiveAttack(Double attack) {
    currentHealth -= attack;
  }
}

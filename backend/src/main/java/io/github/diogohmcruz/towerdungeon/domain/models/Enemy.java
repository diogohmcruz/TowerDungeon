package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.datafaker.Faker;

@Data
@EqualsAndHashCode(callSuper = true)
public class Enemy extends BaseUnit {
  private final EnemyStats stats;

  @Getter(AccessLevel.NONE)
  private final double healthMultiplier;

  @Getter(AccessLevel.NONE)
  private final double damageMultiplier;

  private final boolean boss;

  public Enemy(EnemyStats stats) {
    this(stats, 1.0, 1.0, false);
  }

  public Enemy(EnemyStats stats, double healthMultiplier, double damageMultiplier, boolean boss) {
    super(nameFor(stats));
    this.stats = stats;
    this.healthMultiplier = healthMultiplier;
    this.damageMultiplier = damageMultiplier;
    this.boss = boss;
    this.setMaxHealth(stats.getHealth() * healthMultiplier);
    this.setCurrentHealth(this.getMaxHealth());
  }

  private static String nameFor(EnemyStats stats) {
    var faker = new Faker();
    if (stats == EnemyStats.SLIME) {
      String color = faker.color().name();
      return Character.toUpperCase(color.charAt(0)) + color.substring(1) + " Slime";
    }
    return faker.greekPhilosopher().name();
  }

  public double getEffectiveDamage() {
    return stats.getDamage() * damageMultiplier;
  }

  public void receiveAttack(Double attack, AttackType attackType) {
    double attackTypeOrTrueDamage =
        attackType == null ? 1.0 : this.getStats().getWeaknesses().getOrDefault(attackType, 1.0);
    var attackTotal = attack * attackTypeOrTrueDamage;
    super.receiveAttack(attackTotal);
  }

  @Override
  public String toString() {
    return String.format(
        "%s%s[%s][%.1f/%.1f]",
        boss ? "BOSS " : "",
        this.getName(),
        this.stats.name(),
        this.getCurrentHealth(),
        this.getMaxHealth());
  }
}

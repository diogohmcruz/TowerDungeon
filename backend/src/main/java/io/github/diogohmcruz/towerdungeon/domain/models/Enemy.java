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

  private static final String[] GOLEM_MATERIALS = {
    "Granite", "Basalt", "Obsidian", "Marble", "Slate", "Iron", "Bronze", "Onyx"
  };

  private static String nameFor(EnemyStats stats) {
    var faker = new Faker();
    return switch (stats) {
      case SLIME -> capitalize(faker.color().name()) + " Slime";
      case TOWER_BEAST -> "Tower " + capitalize(faker.animal().name());
      case CHIMERA -> capitalize(faker.animal().name()) + " Chimera";
      case STONE_GOLEM ->
          GOLEM_MATERIALS[
                  java.util.concurrent.ThreadLocalRandom.current().nextInt(GOLEM_MATERIALS.length)]
              + " Golem";
      default -> faker.greekPhilosopher().name();
    };
  }

  private static String capitalize(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

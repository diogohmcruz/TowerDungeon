package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TowerFloor {
  private final Integer id;
  private final Integer difficulty;

  @Builder.Default private final boolean boss = false;
  private final List<Enemy> enemies = new ArrayList<>();

  /**
   * Fills the floor with a weighted horde until the given budget is spent. Heavier archetypes cost
   * more of the budget, so a floor's headcount depends on both its budget and which tiers can spawn
   * at its difficulty.
   */
  public void populateEnemies(double budget) {
    double remaining = budget;
    while (remaining > 0) {
      var randomEnemyStatsIndex = Math.floor(ThreadLocalRandom.current().nextDouble(difficulty));
      Enemy enemy = new Enemy(EnemyStats.values()[(int) randomEnemyStatsIndex]);
      enemies.add(enemy);
      remaining -= enemy.getStats().getWeight();
    }
  }

  public void populateBoss(GameProperties.Boss bossConfig) {
    var values = EnemyStats.values();
    int archetypeIndex = Math.min(difficulty, values.length - 1);
    var bossStats = values[archetypeIndex];
    double healthMultiplier =
        bossConfig.getHealthBaseMultiplier() + bossConfig.getHealthMultiplierPerFloor() * id;
    enemies.add(new Enemy(bossStats, healthMultiplier, bossConfig.getDamageMultiplier(), true));
  }

  public void removeEnemy(Enemy targetEnemy) {
    enemies.removeIf(enemy -> enemy.getId().equals(targetEnemy.getId()));
  }
}

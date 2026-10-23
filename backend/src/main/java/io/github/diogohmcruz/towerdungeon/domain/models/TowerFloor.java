package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.Architecture;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TowerFloor {
  private final Integer id;
  private final Integer difficulty;
  private final Architecture architecture;

  /** One of four cosmetic variants of the Architecture, 0 to 3. */
  private final int variant;

  @Builder.Default private final boolean boss = false;
  private final List<Enemy> enemies = new ArrayList<>();

  /**
   * Fills the floor with a weighted horde until the given budget is spent. Heavier archetypes cost
   * more of the budget, so a floor's headcount depends on both its budget and which tiers can spawn
   * at its difficulty.
   */
  public void populateEnemies(
      double budget, RandomGenerator random, GameProperties.Enemies enemiesConfig) {
    double remaining = budget;
    while (remaining > 0) {
      var randomEnemyStatsIndex = Math.floor(random.nextDouble(difficulty));
      Enemy enemy =
          new Enemy(EnemyStats.values()[(int) randomEnemyStatsIndex], random, enemiesConfig);
      enemies.add(enemy);
      remaining -= enemy.getStats().getWeight();
    }
  }

  /** Puts the floor's guardian on it; a guardian always has all six body parts. */
  public void populateBoss(
      GameProperties.Boss bossConfig,
      GameProperties.Enemies enemiesConfig,
      RandomGenerator random) {
    var values = EnemyStats.values();
    int archetypeIndex = Math.min(difficulty, values.length - 1);
    var bossStats = values[archetypeIndex];
    double healthMultiplier =
        bossConfig.getHealthBaseMultiplier() + bossConfig.getHealthMultiplierPerFloor() * id;
    enemies.add(
        new Enemy(
            bossStats,
            healthMultiplier,
            bossConfig.getDamageMultiplier(),
            true,
            random,
            enemiesConfig));
  }

  public void removeEnemy(Enemy targetEnemy) {
    enemies.removeIf(enemy -> enemy.getId().equals(targetEnemy.getId()));
  }
}

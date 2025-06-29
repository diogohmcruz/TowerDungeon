package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TowerFloor {
  private final Integer id;
  private final Integer difficulty;
  private final List<Enemy> enemies = new ArrayList<>();

  public void populateEnemies() {
    int enemyCount = id;
    while (enemyCount > 0) {
      var randomEnemyStatsIndex = Math.floor(ThreadLocalRandom.current().nextDouble(difficulty));
      Enemy enemy = new Enemy(EnemyStats.values()[(int) randomEnemyStatsIndex]);
      enemies.add(enemy);
      enemyCount -= enemy.getStats().getWeight();
    }
  }

  public void removeEnemy(Enemy targetEnemy) {
    enemies.removeIf(enemy -> enemy.getId().equals(targetEnemy.getId()));
  }
}

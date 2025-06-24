package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import lombok.Data;

@Data
public class Tower {
  private Integer currentFloor = 0;
  private final Integer maxFloor = 100;
  private List<Enemy> enemies = new ArrayList<>();

  public void removeEnemy(Enemy targetEnemy) {
    enemies.removeIf(enemy -> enemy.getId().equals(targetEnemy.getId()));
  }

  public void moveToNextFloor() {
    if (currentFloor < maxFloor) {
      currentFloor++;
      enemies = new ArrayList<>();
      int enemyCount = currentFloor;
      while (enemyCount > 0) {
        var percentageOfTowerComplete = (double) currentFloor / maxFloor;
        var maxEnemyStatsLevel = EnemyStats.values().length * percentageOfTowerComplete + 1;
        var randomEnemyStatsIndex = Math.floor(ThreadLocalRandom.current().nextDouble(maxEnemyStatsLevel));
        Enemy enemy = new Enemy(EnemyStats.values()[(int) randomEnemyStatsIndex]);
        enemies.add(enemy);
        enemyCount -= enemy.getStats().getWeight();
      }
    }
  }
}

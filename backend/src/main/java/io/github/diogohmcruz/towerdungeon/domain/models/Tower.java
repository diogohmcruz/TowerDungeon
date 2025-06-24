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
      for (int i = 0; i < currentFloor + 1; i++) {
        var randomEnemyStatsIndex = ThreadLocalRandom.current().nextInt(EnemyStats.values().length);
        Enemy enemy = new Enemy(EnemyStats.values()[randomEnemyStatsIndex]);
        enemies.add(enemy);
      }
    }
  }
}

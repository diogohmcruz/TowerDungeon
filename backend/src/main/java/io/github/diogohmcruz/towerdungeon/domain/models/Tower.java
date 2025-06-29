package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class Tower {
  private Integer currentFloor = 0;
  private final Integer maxFloor = 100;
  private Map<Integer, TowerFloor> floors = new HashMap<>();

  public void moveToNextFloor() {
    if (currentFloor < maxFloor) {
      currentFloor++;
      var percentageOfTowerComplete = (double) currentFloor / maxFloor;
      var maxEnemyStatsLevel = EnemyStats.values().length * percentageOfTowerComplete + 1;
      var towerFloor =
          TowerFloor.builder().id(currentFloor).difficulty((int) maxEnemyStatsLevel).build();
      towerFloor.populateEnemies();
      floors.put(currentFloor, towerFloor);
    } else {
      throw new IllegalStateException("You have reached the maximum floor of the tower.");
    }
  }

  public TowerFloor getCurrentTowerFloor() {
    return floors.get(currentFloor);
  }
}

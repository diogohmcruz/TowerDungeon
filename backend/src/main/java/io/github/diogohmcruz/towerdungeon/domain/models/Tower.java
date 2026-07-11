package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.Data;

@Data
public class Tower {
  private Integer currentFloor = 0;
  private final Integer maxFloor = 100;
  private Map<Integer, TowerFloor> floors = new HashMap<>();

  @JsonIgnore private final GameProperties.Boss bossConfig;

  public void moveToNextFloor() {
    if (currentFloor < maxFloor) {
      currentFloor++;
      var percentageOfTowerComplete = (double) currentFloor / maxFloor;
      var maxEnemyStatsLevel = EnemyStats.values().length * percentageOfTowerComplete + 1;
      var isBossFloor =
          currentFloor % bossConfig.getInterval() == 0 || currentFloor.equals(maxFloor);
      var towerFloor =
          TowerFloor.builder()
              .id(currentFloor)
              .difficulty((int) maxEnemyStatsLevel)
              .boss(isBossFloor)
              .build();
      if (isBossFloor) {
        towerFloor.populateBoss(bossConfig);
      } else {
        towerFloor.populateEnemies();
      }
      floors.put(currentFloor, towerFloor);
    } else {
      throw new IllegalStateException("You have reached the maximum floor of the tower.");
    }
  }

  public TowerFloor getCurrentTowerFloor() {
    return floors.get(currentFloor);
  }

  /**
   * Starts a fresh expedition on the same tower: the party drops back to the base and climbs again,
   * but every floor discovered on previous runs stays in {@link #floors} so the tower keeps its
   * full height on screen. Re-cleared floors are repopulated with new defenders as the party
   * reaches them.
   */
  public void startNewRun() {
    this.currentFloor = 0;
  }
}

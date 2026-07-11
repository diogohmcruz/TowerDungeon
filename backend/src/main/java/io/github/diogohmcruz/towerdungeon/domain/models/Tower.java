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
      floors.put(currentFloor, buildFloor(currentFloor));
    } else {
      throw new IllegalStateException("You have reached the maximum floor of the tower.");
    }
  }

  /** Builds a fully populated floor (enemies or boss) for the given depth. */
  private TowerFloor buildFloor(int floor) {
    var percentageOfTowerComplete = (double) floor / maxFloor;
    var maxEnemyStatsLevel = EnemyStats.values().length * percentageOfTowerComplete + 1;
    var isBossFloor = floor % bossConfig.getInterval() == 0 || floor == maxFloor;
    var towerFloor =
        TowerFloor.builder()
            .id(floor)
            .difficulty((int) maxEnemyStatsLevel)
            .boss(isBossFloor)
            .build();
    if (isBossFloor) {
      towerFloor.populateBoss(bossConfig);
    } else {
      towerFloor.populateEnemies();
    }
    return towerFloor;
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

  /**
   * Starts a fresh expedition partway up the tower via an unlocked shortcut. The party is deposited
   * on {@code startFloor}, which is (re)populated with defenders so combat begins there instead of
   * at the base. A non-positive floor falls back to a base-floor climb.
   */
  public void startNewRunAt(int startFloor) {
    if (startFloor <= 0) {
      startNewRun();
      return;
    }
    var floor = Math.min(startFloor, maxFloor);
    this.currentFloor = floor;
    floors.put(floor, buildFloor(floor));
  }
}

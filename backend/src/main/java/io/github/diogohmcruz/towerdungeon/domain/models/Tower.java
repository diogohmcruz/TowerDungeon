package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.Data;

@Data
public class Tower {
  private Integer currentFloor = 0;
  private final Integer maxFloor;
  private Map<Integer, TowerFloor> floors = new HashMap<>();

  /** Guardian floors already slain this campaign; thins the garrisons of the floors below them. */
  @JsonIgnore private final Set<Integer> clearedBosses = new HashSet<>();

  @JsonIgnore private final GameProperties.Boss bossConfig;
  @JsonIgnore private final GameProperties.Enemies enemiesConfig;

  public void moveToNextFloor() {
    if (currentFloor < maxFloor) {
      currentFloor++;
      floors.put(currentFloor, buildFloor(currentFloor));
    } else {
      throw new IllegalStateException("You have reached the maximum floor of the tower.");
    }
  }

  /** Records that the guardian on {@code bossFloor} has been defeated. */
  public void markBossCleared(int bossFloor) {
    clearedBosses.add(bossFloor);
  }

  /** Number of cleared guardians standing above {@code floor} — the floor's "easing" pressure. */
  private int easingStepsFor(int floor) {
    return (int) clearedBosses.stream().filter(bossFloor -> bossFloor > floor).count();
  }

  /** Builds a fully populated floor (enemies or boss) for the given depth. */
  private TowerFloor buildFloor(int floor) {
    var percentageOfTowerComplete = (double) floor / maxFloor;
    var baseDifficulty = EnemyStats.values().length * percentageOfTowerComplete + 1;
    var isBossFloor = floor % bossConfig.getInterval() == 0 || floor == maxFloor;

    var easingSteps = easingStepsFor(floor);
    var difficulty =
        (int)
            Math.clamp(
                baseDifficulty - (double) enemiesConfig.getEaseDifficultyPerBoss() * easingSteps,
                1,
                EnemyStats.values().length);

    var towerFloor =
        TowerFloor.builder().id(floor).difficulty(difficulty).boss(isBossFloor).build();
    if (isBossFloor) {
      towerFloor.populateBoss(bossConfig);
    } else {
      towerFloor.populateEnemies(enemyBudget(floor, easingSteps));
    }
    return towerFloor;
  }

  /**
   * Weighted garrison budget for a regular floor: a base plus linear and quadratic growth with
   * depth, boosted on periodic "surge" floors, swung randomly, then thinned once for every cleared
   * guardian above it.
   */
  private double enemyBudget(int floor, int easingSteps) {
    var e = enemiesConfig;
    var budget =
        e.getBaseBudget()
            + floor * e.getBudgetPerFloor()
            + (double) floor * floor * e.getQuadraticPerFloor();
    if (e.getSurgeInterval() > 0 && floor % e.getSurgeInterval() == 0) {
      budget *= e.getSurgeMultiplier();
    }
    if (e.getVariance() > 0) {
      budget *= 1 + ThreadLocalRandom.current().nextDouble(-e.getVariance(), e.getVariance());
    }
    budget *= Math.pow(1 - e.getEaseBudgetPerBoss(), easingSteps);
    return Math.max(e.getMinBudget(), budget);
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

package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
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

  /** Source of every random draw in floor generation; seed it to make a climb reproducible. */
  @JsonIgnore private final RandomGenerator random;

  /** The gear the fallen left and the Risen they became, per floor; survives every rebuild. */
  @JsonIgnore private final FloorMemory floorMemory = new FloorMemory();

  /** Backend-only: the frontend only ever sees the Architectures of discovered floors. */
  @JsonIgnore private final ArchitecturePlan architecturePlan;

  /**
   * Bumps whenever a floor is added or a guardian is first slain (easing changes), so the frontend
   * knows when to redraw the tower. Re-populating a floor's enemies does not bump it.
   */
  private int layoutVersion = 0;

  public void moveToNextFloor() {
    if (currentFloor < maxFloor) {
      currentFloor++;
      putFloor(currentFloor);
    } else {
      throw new IllegalStateException("You have reached the maximum floor of the tower.");
    }
  }

  /** Records that the guardian on {@code bossFloor} has been defeated. */
  public void markBossCleared(int bossFloor) {
    if (clearedBosses.add(bossFloor)) {
      layoutVersion++;
    }
  }

  /** The floors of the slain guardians, lowest first. */
  public List<Integer> getSlainGuardians() {
    return clearedBosses.stream().sorted().toList();
  }

  private void putFloor(int floor) {
    if (floors.put(floor, buildFloor(floor)) == null) {
      layoutVersion++;
    }
  }

  /** Number of cleared guardians standing above {@code floor} — the floor's "easing" pressure. */
  private int easingStepsFor(int floor) {
    return (int) clearedBosses.stream().filter(bossFloor -> bossFloor > floor).count();
  }

  /** Number of cleared guardians standing below {@code floor} — the floor's "step-up" pressure. */
  private int stepUpsFor(int floor) {
    return (int) clearedBosses.stream().filter(bossFloor -> bossFloor < floor).count();
  }

  /**
   * First-reach discount: x{@code gentleStartMultiplier} on Floor 1, rising linearly to x1.0 once
   * {@code gentleFloors} floors have passed.
   */
  private double gentleMultiplier(int floor) {
    var e = enemiesConfig;
    if (e.getGentleFloors() <= 0 || floor > e.getGentleFloors()) {
      return 1.0;
    }
    var progress = (double) (floor - 1) / e.getGentleFloors();
    return e.getGentleStartMultiplier() + (1 - e.getGentleStartMultiplier()) * progress;
  }

  /**
   * Rebuilds a tower from its persisted facts. Floors are not persisted: the floors up to {@code
   * highestFloor} come back as empty stubs (every floor is rebuilt on arrival anyway), and the
   * floor the party stands on takes the saved {@code garrison} when one is given.
   */
  public static Tower restore(
      int maxFloor,
      GameProperties.Boss bossConfig,
      GameProperties.Enemies enemiesConfig,
      RandomGenerator random,
      ArchitecturePlan architecturePlan,
      int currentFloor,
      int highestFloor,
      Set<Integer> clearedBosses,
      List<Enemy> garrison) {
    var tower = new Tower(maxFloor, bossConfig, enemiesConfig, random, architecturePlan);
    tower.clearedBosses.addAll(clearedBosses);
    tower.currentFloor = currentFloor;
    var topStub = Math.min(Math.max(highestFloor, currentFloor), maxFloor);
    for (var floor = 1; floor <= topStub; floor++) {
      var stub = tower.newFloor(floor);
      if (floor == currentFloor) {
        stub.getEnemies().addAll(garrison);
      }
      tower.floors.put(floor, stub);
    }
    tower.layoutVersion = tower.floors.size() + tower.clearedBosses.size();
    return tower;
  }

  /** An empty floor with its depth-derived traits (difficulty, architecture, boss flag). */
  private TowerFloor newFloor(int floor) {
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

    return TowerFloor.builder()
        .id(floor)
        .difficulty(difficulty)
        .architecture(architecturePlan.architectureOf(floor))
        .variant(architecturePlan.variantOf(floor))
        .boss(isBossFloor)
        .build();
  }

  /** Builds a fully populated floor (enemies or boss) for the given depth. */
  private TowerFloor buildFloor(int floor) {
    var towerFloor = newFloor(floor);
    if (towerFloor.isBoss()) {
      towerFloor.populateBoss(bossConfig, enemiesConfig, random);
    } else {
      towerFloor.populateEnemies(enemyBudget(floor), random, enemiesConfig);
      towerFloor.addRisen(floorMemory.risenOn(floor), random, enemiesConfig);
    }
    return towerFloor;
  }

  /**
   * The garrison budget of a regular floor before its random swing: a base plus linear and
   * quadratic growth with depth, discounted on the first floors, boosted on periodic "surge"
   * floors, thinned once for every cleared guardian above it and raised once for every one below.
   */
  public double easedBaseBudget(int floor) {
    var e = enemiesConfig;
    var budget =
        e.getBaseBudget()
            + floor * e.getBudgetPerFloor()
            + (double) floor * floor * e.getQuadraticPerFloor();
    budget *= gentleMultiplier(floor);
    if (e.getSurgeInterval() > 0 && floor % e.getSurgeInterval() == 0) {
      budget *= e.getSurgeMultiplier();
    }
    budget *= Math.pow(1 - e.getEaseBudgetPerBoss(), easingStepsFor(floor));
    budget *= Math.pow(1 + e.getStepBudgetPerBoss(), stepUpsFor(floor));
    return budget;
  }

  /** The budget of a regular floor: its eased base, swung randomly, never below the minimum. */
  private double enemyBudget(int floor) {
    var e = enemiesConfig;
    var budget = easedBaseBudget(floor);
    if (e.getVariance() > 0) {
      budget *= 1 + random.nextDouble(-e.getVariance(), e.getVariance());
    }
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
    putFloor(floor);
  }
}

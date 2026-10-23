package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/**
 * The opening of the game must be gentle: the four configured Founders (Yuva, two Warriors and an
 * Archer) are expected to clear Floors 1 to 3 on average.
 */
class FoundersFirstReachTest {
  private static final int RUNS = 300;
  private static final int FLOORS = 3;
  private static final int MAX_TICKS_PER_FLOOR = 500;

  /** Floors cleared (0 to FLOORS) by one seeded run of the Founders from Floor 1. */
  private static int floorsCleared(long seed) {
    var config = new GameProperties();
    var random = new RandomSource(seed);
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    var tower = new Tower(100, config.getBoss(), config.getEnemies(), random, plan);
    var state = new GameState(config, random);
    var combat = new PartyCombat();

    var founders =
        config.getStart().getFounders().stream()
            .map(founder -> state.recruitFounder(founder))
            .toList();
    for (var unit : founders) {
      state.getUnitsOnTower().computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }

    for (var floor = 1; floor <= FLOORS; floor++) {
      tower.moveToNextFloor();
      var towerFloor = tower.getCurrentTowerFloor();
      for (var tick = 0; !towerFloor.getEnemies().isEmpty(); tick++) {
        if (!state.hasUnitsOnTower() || tick > MAX_TICKS_PER_FLOOR) {
          return floor - 1;
        }
        var unitsOnTower = new HashMap<>(state.getUnitsOnTower());
        combat.unitsAttack(unitsOnTower, towerFloor.getEnemies(), towerFloor, state);
        combat.enemiesAttack(state, towerFloor.getEnemies(), unitsOnTower);
      }
    }
    return FLOORS;
  }

  @Test
  void theFoundersClearTheFirstThreeFloorsOnAverage() {
    var clears = 0;
    var floorsTotal = 0;
    for (var seed = 1; seed <= RUNS; seed++) {
      var cleared = floorsCleared(seed);
      floorsTotal += cleared;
      if (cleared == FLOORS) {
        clears++;
      }
    }
    System.out.printf(
        "FOUNDERS: %d of %d runs cleared Floors 1-3, mean floors cleared %.2f%n",
        clears, RUNS, (double) floorsTotal / RUNS);

    assertThat(clears).isGreaterThanOrEqualTo(RUNS * 95 / 100);
  }
}

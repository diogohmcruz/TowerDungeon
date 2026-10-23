package io.github.diogohmcruz.towerdungeon.domain.services;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class PartyCombatStrengthsTest {
  private final GameProperties config = noMisses(new GameProperties());
  private final GameState state = new GameState(config);
  private final PartyCombat combat = new PartyCombat();

  /** The scaling checks compare exact damage, so no strike may miss. */
  private static GameProperties noMisses(GameProperties config) {
    var aiming = config.getAiming();
    aiming.setMinMiss(0);
    aiming.setTorsoMiss(0);
    aiming.setHeadMiss(0);
    aiming.setArmMiss(0);
    aiming.setLegMiss(0);
    return config;
  }

  private double damageDealtBy(Unit unit, EnemyStats enemyStats) {
    var enemy = new Enemy(enemyStats, new SplittableRandom(3), config.getEnemies());
    var before = enemy.getCurrentHealth();
    var floor = TowerFloor.builder().id(1).difficulty(1).build();
    floor.getEnemies().add(enemy);
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    party.put(unit.getStats(), List.of(unit));
    combat.unitsAttack(party, floor.getEnemies(), floor, state);
    return before - enemy.getCurrentHealth();
  }

  @Test
  void aUnitRolledAtOnePointThreeDealsThirtyPercentMoreThanAnAverageOne() {
    var average = new Unit(UnitStats.WARRIOR, cosmetic());
    var strong = new Unit(UnitStats.WARRIOR, cosmetic());
    strong.setDamageFactor(1.3);

    var base = damageDealtBy(average, EnemyStats.STONE_GOLEM);
    var boosted = damageDealtBy(strong, EnemyStats.STONE_GOLEM);

    assertThat(base).isPositive();
    assertThat(boosted).isEqualTo(base * 1.3, within(1e-9));
  }

  @Test
  void aHealersHealScalesWithItsRolledDamage() {
    var heal = new double[2];
    var factors = new double[] {1.0, 1.3};
    for (var i = 0; i < 2; i++) {
      var healer = state.recruit(UnitStats.HEALER, new SplittableRandom(1));
      healer.setDamageFactor(factors[i]);
      var warrior = new Unit(UnitStats.WARRIOR, cosmetic());
      warrior.setCurrentHealth(warrior.getMaxHealth() - 30);
      var floor = TowerFloor.builder().id(1).difficulty(1).build();
      floor
          .getEnemies()
          .add(new Enemy(EnemyStats.SLIME, new SplittableRandom(3), config.getEnemies()));
      var party = new LinkedHashMap<UnitStats, List<Unit>>();
      party.put(UnitStats.HEALER, List.of(healer));
      party.put(UnitStats.WARRIOR, List.of(warrior));
      var before = warrior.getCurrentHealth();
      combat.unitsAttack(party, floor.getEnemies(), floor, state);
      heal[i] = warrior.getCurrentHealth() - before;
    }

    assertThat(heal[0]).isPositive();
    assertThat(heal[1]).isEqualTo(heal[0] * 1.3, within(1e-9));
  }
}

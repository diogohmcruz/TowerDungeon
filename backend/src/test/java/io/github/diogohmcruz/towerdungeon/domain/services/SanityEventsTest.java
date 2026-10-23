package io.github.diogohmcruz.towerdungeon.domain.services;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class SanityEventsTest {
  private final GameProperties config = noMisses();
  private final GameState state = new GameState(config);
  private final PartyCombat combat = new PartyCombat();

  /** Enemy strikes are aimed and can miss; these tests measure damage, so nothing may miss. */
  private static GameProperties noMisses() {
    var config = new GameProperties();
    config.getAiming().setMinMiss(0);
    config.getAiming().setMaxMiss(0);
    // These tests measure the plain drains; Spring (the first Quarter) is covered by YearTest.
    config.getYear().setSpringSanityDrain(1d);
    return config;
  }

  private TowerFloor floorWith(int id, EnemyStats... types) {
    var floor = TowerFloor.builder().id(id).difficulty(1).build();
    for (var type : types) {
      floor.getEnemies().add(new Enemy(type, new SplittableRandom(3), config.getEnemies()));
    }
    return floor;
  }

  private static Map<UnitStats, List<Unit>> partyOf(Unit... units) {
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    for (var unit : units) {
      party.computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }
    return party;
  }

  private void tick(Map<UnitStats, List<Unit>> party, TowerFloor floor) {
    combat.unitsAttack(party, floor.getEnemies(), floor, state);
  }

  private double damageDealt(Unit unit, EnemyStats target) {
    var floor = floorWith(1, target);
    var enemy = floor.getEnemies().getFirst();
    var before = enemy.getCurrentHealth();
    tick(partyOf(unit), floor);
    return before - enemy.getCurrentHealth();
  }

  private double damageTaken(Unit unit, EnemyStats attacker) {
    var enemies = List.of(new Enemy(attacker, new SplittableRandom(3), config.getEnemies()));
    var party = partyOf(unit);
    state.setUnitsOnTower(party);
    unit.setHealthFactor(100); // tough enough that the hit is never cut short by death
    var before = unit.getCurrentHealth();
    combat.enemiesAttack(state, enemies, party);
    return before - unit.getCurrentHealth();
  }

  @Test
  void aKillGivesTheKillerTwoSanity() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.changeSanity(-50);
    var floor = floorWith(1, EnemyStats.SLIME);
    floor.getEnemies().getFirst().setCurrentHealth(0.001);

    tick(partyOf(unit), floor);

    assertThat(floor.getEnemies()).isEmpty();
    assertThat(unit.getSanity()).isEqualTo(52.0);
  }

  @Test
  void anAllyDyingDrainsTenFromEveryLivingMember() {
    var doomed = new Unit(UnitStats.WARRIOR, cosmetic());
    var one = new Unit(UnitStats.ARCHER, cosmetic());
    var two = new Unit(UnitStats.WARRIOR, cosmetic());
    state.setUnitsOnTower(partyOf(doomed, one, two));

    state.removeUnit(doomed);

    assertThat(one.getSanity()).isEqualTo(90.0);
    assertThat(two.getSanity()).isEqualTo(90.0);
  }

  @Test
  void climbingAFloorGivesEveryPartyMemberFive() {
    var one = new Unit(UnitStats.WARRIOR, cosmetic());
    var two = new Unit(UnitStats.ARCHER, cosmetic());
    one.changeSanity(-20);
    two.changeSanity(-1);
    state.setUnitsOnTower(partyOf(one, two));

    state.rewardFloorClimbed();

    assertThat(one.getSanity()).isEqualTo(85.0);
    assertThat(two.getSanity()).isEqualTo(100.0);
  }

  @Test
  void depthDrainsOneHundredthOfTheFloorNumberEachTick() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    state.setUnitsOnTower(partyOf(unit));

    state.drainDepthSanity(40);

    assertThat(unit.getSanity()).isEqualTo(99.6, within(1e-9));
  }

  @Test
  void meetingAFearedEnemyCostsFiveOncePerFloorAndRevealsThePhobia() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.addPhobia(EnemyStats.STONE_GOLEM);
    unit.addPhobia(EnemyStats.DRAGON);
    var party = partyOf(unit);
    var golemFloor = floorWith(3, EnemyStats.STONE_GOLEM);

    tick(party, golemFloor);
    tick(party, golemFloor);

    assertThat(unit.getSanity()).isEqualTo(95.0);
    assertThat(unit.getPhobias().get(0).isKnown()).isTrue();
    assertThat(unit.getPhobias().get(1).isKnown()).isFalse();

    tick(party, floorWith(4, EnemyStats.STONE_GOLEM));

    assertThat(unit.getSanity()).isEqualTo(90.0);
  }

  @Test
  void aFloorWithoutTheFearedTypeRevealsAndCostsNothing() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.addPhobia(EnemyStats.DRAGON);

    tick(partyOf(unit), floorWith(2, EnemyStats.STONE_GOLEM));

    assertThat(unit.getSanity()).isEqualTo(100.0);
    assertThat(unit.getPhobias().getFirst().isKnown()).isFalse();
  }

  @Test
  void aPhobicUnitDealsLessAtFullSanityAndHalfAtZero() {
    var plain = new Unit(UnitStats.WARRIOR, cosmetic());
    var base = damageDealt(plain, EnemyStats.STONE_GOLEM);

    var phobic = new Unit(UnitStats.WARRIOR, cosmetic());
    phobic.addPhobia(EnemyStats.STONE_GOLEM);
    // The meeting itself costs 5 sanity before the strike, so compare at the sanity it strikes at.
    var expectedAtStrike = 1 - 0.5 * (0.25 + 0.75 * (1 - 95.0 / 100.0));
    assertThat(damageDealt(phobic, EnemyStats.STONE_GOLEM))
        .isEqualTo(base * expectedAtStrike, within(1e-9));

    var broken = new Unit(UnitStats.WARRIOR, cosmetic());
    broken.addPhobia(EnemyStats.STONE_GOLEM);
    broken.changeSanity(-100);
    assertThat(damageDealt(broken, EnemyStats.STONE_GOLEM)).isEqualTo(base * 0.5, within(1e-9));

    var elsewhere = new Unit(UnitStats.WARRIOR, cosmetic());
    elsewhere.addPhobia(EnemyStats.DRAGON);
    assertThat(damageDealt(elsewhere, EnemyStats.STONE_GOLEM)).isEqualTo(base, within(1e-9));
  }

  @Test
  void aPhobicUnitTakesMoreAtFullSanityAndOneAndAHalfTimesAtZero() {
    var base = damageTaken(new Unit(UnitStats.WARRIOR, cosmetic()), EnemyStats.STONE_GOLEM);

    var phobic = new Unit(UnitStats.WARRIOR, cosmetic());
    phobic.addPhobia(EnemyStats.STONE_GOLEM);
    assertThat(damageTaken(phobic, EnemyStats.STONE_GOLEM)).isEqualTo(base * 1.125, within(1e-9));

    var broken = new Unit(UnitStats.WARRIOR, cosmetic());
    broken.addPhobia(EnemyStats.STONE_GOLEM);
    broken.changeSanity(-100);
    assertThat(damageTaken(broken, EnemyStats.STONE_GOLEM)).isEqualTo(base * 1.5, within(1e-9));
  }

  @Test
  void reservesRecoverTwoSanityForHalfAFoodEachTickAndStopWhenThePantryIsEmpty() {
    var unit = new Unit(UnitStats.WARRIOR, cosmetic());
    unit.changeSanity(-50);
    var full = new Unit(UnitStats.ARCHER, cosmetic());
    state.addUnits(UnitStats.WARRIOR, List.of(unit));
    state.addUnits(UnitStats.ARCHER, List.of(full));
    var food = state.getVillage().getFood();

    state.recoverHomeSanity();

    assertThat(unit.getSanity()).isEqualTo(52.0);
    assertThat(full.getSanity()).isEqualTo(100.0);
    assertThat(state.getVillage().getFood()).isEqualTo(food - 0.5, within(1e-9));

    state.getVillage().takeFood(state.getVillage().getFood());
    state.recoverHomeSanity();

    assertThat(unit.getSanity()).isEqualTo(52.0);
  }
}

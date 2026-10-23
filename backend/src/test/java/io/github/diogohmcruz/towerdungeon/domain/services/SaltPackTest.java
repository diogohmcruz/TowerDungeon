package io.github.diogohmcruz.towerdungeon.domain.services;

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
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.VillageTier;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/**
 * The Salt Pack: Salt carried against Chymus Insects, the Salt-bearer and the +50% on a salted one.
 */
class SaltPackTest {
  private final GameProperties config = new GameProperties();
  private GameState state;
  private final PartyCombat combat = new PartyCombat();

  {
    config.getRecruit().setStrengthSpread(0);
    var aiming = config.getAiming();
    aiming.setMinMiss(0);
    aiming.setTorsoMiss(0);
    aiming.setHeadMiss(0);
    aiming.setArmMiss(0);
    aiming.setLegMiss(0);
    config.getVillage().setStartingFood(500);
    config.getVillage().setStartingVillagers(20);
    state = new GameState(config);
    state.setVillageTier(VillageTier.SHELTERED);
  }

  private Unit recruit(UnitStats stats) {
    return state.recruit(stats, new SplittableRandom(1));
  }

  private Map<UnitStats, List<Unit>> party(Unit... units) {
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    for (var unit : units) {
      party.computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }
    return party;
  }

  private Enemy enemy(EnemyStats stats) {
    return new Enemy(stats, new SplittableRandom(3), config.getEnemies());
  }

  private static TowerFloor floorOf(Enemy... enemies) {
    var floor = TowerFloor.builder().id(1).difficulty(1).build();
    floor.getEnemies().addAll(List.of(enemies));
    return floor;
  }

  /** Sets out with the party on the Tower and a Pack of {@code salt} carried by a Tank. */
  private void setOut(int salt, Unit... party) {
    state.setUnitsOnTower(party(party));
    state.getVillage().restoreSalt(0, 20);
    state.startRun(0, salt, UnitStats.TANK);
  }

  @Test
  void thePackTakesItsSaltFromTheStockroomAndTenCarryingCapacity() {
    var tank = recruit(UnitStats.TANK);
    var capacityWithoutPack = config.getSupply().getBaseMax() + state.getSupplyCapacityBonus();
    state.setUnitsOnTower(party(tank));
    state.getVillage().restoreSalt(0, 20);

    state.startRun(0, 3, UnitStats.TANK);

    assertThat(state.getSaltPack()).isEqualTo(3);
    assertThat(state.getSaltBearerId()).isEqualTo(tank.getId());
    assertThat(state.getVillage().getSalt()).isEqualTo(17.0);
    assertThat(state.getMaxSupplies())
        .isEqualTo(capacityWithoutPack + tank.getStats().getSupplyBonus() - 10.0);
  }

  @Test
  void withoutAPackNothingIsTakenAndNoCapacityIsSpent() {
    var tank = recruit(UnitStats.TANK);
    state.setUnitsOnTower(party(tank));
    state.getVillage().restoreSalt(0, 20);

    state.startRun(0, 0, UnitStats.TANK);

    assertThat(state.getSaltPack()).isZero();
    assertThat(state.getSaltBearerId()).isNull();
    assertThat(state.getVillage().getSalt()).isEqualTo(20.0);
  }

  @Test
  void aPackWithNoBearerInThePartyIsNotTakenAndTheSaltStaysHome() {
    var warrior = recruit(UnitStats.WARRIOR);
    state.setUnitsOnTower(party(warrior));
    state.getVillage().restoreSalt(0, 20);

    state.startRun(0, 3, UnitStats.TANK);

    assertThat(state.getSaltPack()).isZero();
    assertThat(state.getVillage().getSalt()).isEqualTo(20.0);
  }

  @Test
  void youCannotCarryMoreSaltThanTheStockroomHolds() {
    var tank = recruit(UnitStats.TANK);
    state.setUnitsOnTower(party(tank));
    state.getVillage().restoreSalt(0, 2.5);

    state.startRun(0, 9, UnitStats.TANK);

    assertThat(state.getSaltPack()).isEqualTo(2);
  }

  @Test
  void oneSaltSaltsOneChymusInsectAndSlimesAreNeverSalted() {
    var tank = recruit(UnitStats.TANK);
    setOut(2, tank);
    var first = enemy(EnemyStats.CHYMUS_INSECT);
    var second = enemy(EnemyStats.CHYMUS_INSECT);
    var third = enemy(EnemyStats.CHYMUS_INSECT);
    var slime = enemy(EnemyStats.SLIME);

    state.saltGarrison(List.of(slime, first, second, third));

    assertThat(first.isSalted()).isTrue();
    assertThat(second.isSalted()).isTrue();
    assertThat(third.isSalted()).isFalse();
    assertThat(slime.isSalted()).isFalse();
    assertThat(state.getSaltPack()).isZero();
  }

  @Test
  void aSaltedChymusInsectTakesFiftyPercentMoreFromTheSameHit() {
    var warrior = recruit(UnitStats.WARRIOR);
    var tank = recruit(UnitStats.TANK);
    setOut(1, tank);
    var salted = enemy(EnemyStats.CHYMUS_INSECT);
    var plain = enemy(EnemyStats.CHYMUS_INSECT);
    state.saltGarrison(List.of(salted));
    var floorSalted = floorOf(salted);
    var floorPlain = floorOf(plain);
    var soloState = new GameState(config);
    soloState.setUnitsOnTower(party(warrior));

    combat.unitsAttack(party(warrior), floorSalted.getEnemies(), floorSalted, state);
    combat.unitsAttack(party(warrior), floorPlain.getEnemies(), floorPlain, soloState);

    var dealtSalted = salted.getMaxHealth() - salted.getCurrentHealth();
    var dealtPlain = plain.getMaxHealth() - plain.getCurrentHealth();
    assertThat(dealtPlain).isPositive();
    assertThat(dealtSalted).isCloseTo(dealtPlain * 1.5, within(1e-6));
  }

  @Test
  void aSlimeIsHurtJustTheSameWhateverThePartyCarries() {
    var warrior = recruit(UnitStats.WARRIOR);
    var tank = recruit(UnitStats.TANK);
    setOut(3, tank);
    var slime = enemy(EnemyStats.SLIME);
    var control = enemy(EnemyStats.SLIME);
    var carrying = floorOf(slime);
    var bare = floorOf(control);
    var bareState = new GameState(config);

    combat.unitsAttack(party(warrior), carrying.getEnemies(), carrying, state);
    combat.unitsAttack(party(warrior), bare.getEnemies(), bare, bareState);

    assertThat(slime.getCurrentHealth()).isEqualTo(control.getCurrentHealth());
    assertThat(state.getSaltPack()).isEqualTo(3);
  }

  /**
   * The share of the damage 100 weak enemies of one type do that lands on the bearer, among five
   * Tanks of whom the first is the bearer. Weak enemies, so that nobody dies and every hit counts.
   */
  private double bearerShareOfDamage(EnemyStats type, boolean salted) {
    var tanks = new ArrayList<Unit>();
    for (var i = 0; i < 5; i++) {
      tanks.add(recruit(UnitStats.TANK));
    }
    setOut(5, tanks.toArray(Unit[]::new));
    var enemies = new ArrayList<Enemy>();
    for (var i = 0; i < 100; i++) {
      var weak = new Enemy(type, 1.0, 0.001, false, new SplittableRandom(i), config.getEnemies());
      weak.setSalted(salted);
      enemies.add(weak);
    }
    var bearer = state.saltBearer().orElseThrow();
    var before = tanks.stream().mapToDouble(Unit::getCurrentHealth).sum();
    var bearerBefore = bearer.getCurrentHealth();

    combat.enemiesAttack(state, enemies, state.getUnitsOnTower());

    var lost = before - tanks.stream().mapToDouble(Unit::getCurrentHealth).sum();
    return (bearerBefore - bearer.getCurrentHealth()) / lost;
  }

  @Test
  void aChymusInsectPrefersTheSaltBearerOverTheOthers() {
    // One in five at random; with the bias of 0.75 the bearer takes about 0.75 + 0.25 / 5 = 0.8.
    assertThat(bearerShareOfDamage(EnemyStats.CHYMUS_INSECT, true)).isGreaterThan(0.6);
  }

  @Test
  void aSlimeIgnoresTheSaltBearer() {
    assertThat(bearerShareOfDamage(EnemyStats.SLIME, false)).isLessThan(0.4);
  }

  @Test
  void leftoverSaltGoesBackToTheStockroomOnExtraction() {
    var tank = recruit(UnitStats.TANK);
    setOut(5, tank);
    state.saltGarrison(List.of(enemy(EnemyStats.CHYMUS_INSECT)));
    assertThat(state.getVillage().getSalt()).isEqualTo(15.0);

    state.returnLeftoverSuppliesToVillage();

    assertThat(state.getSaltPack()).isZero();
    assertThat(state.getSaltBearerId()).isNull();
    assertThat(state.getVillage().getSalt()).isEqualTo(19.0);
  }

  @Test
  void aFallenBearerTakesTheSaltAndFreesTheCapacity() {
    var tank = recruit(UnitStats.TANK);
    var warrior = recruit(UnitStats.WARRIOR);
    setOut(5, tank, warrior);
    var cappedWith = state.getMaxSupplies();

    state.removeUnit(tank);

    assertThat(state.getSaltPack()).isZero();
    assertThat(state.getSaltBearerId()).isNull();
    assertThat(state.getMaxSupplies()).isGreaterThan(cappedWith - tank.getStats().getSupplyBonus());
    assertThat(state.getVillage().getSalt()).isEqualTo(15.0);
  }

  @Test
  void surplusSaltSellsForTwoCreditsEach() {
    state.getVillage().restoreSalt(0, 7.5);
    var before = state.getCredit();

    var profit = state.sellAllSalt();

    assertThat(profit).isEqualTo(14.0);
    assertThat(state.getCredit()).isEqualTo(before + 14.0);
    assertThat(state.getVillage().getSalt()).isEqualTo(0.5);
  }

  @Test
  void thePackAndTheSaltedGarrisonSurviveASaveAndLoad() {
    var tank = recruit(UnitStats.TANK);
    setOut(4, tank);
    var tower =
        new Tower(
            config.getTower().getMaxFloor(),
            config.getBoss().copy(),
            config.getEnemies().copy(),
            new RandomSource(1),
            arch());
    state.setTower(tower);
    tower.startNewRun();
    tower.moveToNextFloor();
    state.setExpeditionActive(true);
    var garrison = tower.getCurrentTowerFloor().getEnemies();
    garrison.clear();
    garrison.add(enemy(EnemyStats.CHYMUS_INSECT));
    garrison.add(enemy(EnemyStats.CHYMUS_INSECT));
    state.saltGarrison(garrison);
    state.saltGarrison(garrison);

    var restored = GameState.restore(state.toSnapshot(), config, new RandomSource(1), arch());

    assertThat(state.getSaltPack()).isEqualTo(2);
    assertThat(restored.getSaltPack()).isEqualTo(2);
    assertThat(restored.getSaltBearerId()).isEqualTo(tank.getId());
    assertThat(restored.getMaxSupplies()).isEqualTo(state.getMaxSupplies());
    assertThat(restored.getTower().getCurrentTowerFloor().getEnemies())
        .allSatisfy(enemy -> assertThat(enemy.isSalted()).isTrue());
    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
  }

  private ArchitecturePlan arch() {
    return ArchitecturePlan.generate(
        config.getArchitecture().getSeed(),
        config.getTower().getMaxFloor(),
        config.getBoss().getInterval(),
        config.getEnemies().getSurgeInterval());
  }
}

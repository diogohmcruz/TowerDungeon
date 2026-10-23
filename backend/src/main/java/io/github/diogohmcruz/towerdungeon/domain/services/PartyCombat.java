package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.extern.slf4j.Slf4j;

/**
 * One tick of fighting on a floor: the party strikes, then the garrison strikes back. Kept apart
 * from {@link GameService} so the rules can be tested with a seeded random source. Every draw is
 * taken from the combat stream of the Campaign's {@code RandomSource}.
 *
 * <p>A caster makes at most one cast per tick. A Healer heals a random ally who was wounded when
 * the tick began; if there was none it strikes a random enemy that a Heal hit would harm. Because
 * "wounded" is judged once, at the start of the tick, two Healers may both spend a cast on the same
 * last wounded ally.
 */
@Slf4j
@Component
public class PartyCombat {
  public void unitsAttack(
      Map<UnitStats, List<Unit>> unitsOnTower,
      List<Enemy> currentEnemies,
      TowerFloor currentTowerFloor,
      GameState gameState) {
    unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState, strike -> {});
  }

  /** As {@link #unitsAttack(Map, List, TowerFloor, GameState)}, reporting each aimed strike. */
  public void unitsAttack(
      Map<UnitStats, List<Unit>> unitsOnTower,
      List<Enemy> currentEnemies,
      TowerFloor currentTowerFloor,
      GameState gameState,
      Consumer<AimedStrike.Strike> onStrike) {
    var damageMultiplier = gameState.getDamageMultiplier();
    var manaCost = gameState.getConfig().getMagic().getCostPerCast();
    var party = unitsOnTower.values().stream().flatMap(List::stream).toList();
    var woundedAtStart = party.stream().filter(Unit::isWounded).toList();
    var enemiesAtStart = List.copyOf(currentEnemies);
    var healersThatCast = new HashSet<Integer>();
    var aiming = gameState.getConfig().getAiming();
    var random = gameState.getRandom().combat();
    var aimedStrike = new AimedStrike(random, aiming);

    meetFearedEnemies(party, currentEnemies, currentTowerFloor);
    gameState.saltGarrison(currentEnemies);

    party.stream()
        .filter(PartyCombat::isHealer)
        .forEach(
            healer -> {
              var candidates =
                  woundedAtStart.stream()
                      .filter(unit -> !healer.getId().equals(unit.getId()))
                      .toList();
              if (candidates.isEmpty()) {
                return;
              }
              healersThatCast.add(healer.getId());
              if (!healer.canCast(manaCost)) {
                return;
              }
              var target = candidates.get(random.nextInt(candidates.size()));
              target.receiveAttack(healer.getDamage(), AttackType.HEAL);
              healer.spendMana(manaCost);
            });

    party.stream()
        .filter(unit -> !healersThatCast.contains(unit.getId()))
        .forEach(
            currentUnit -> {
              if (currentEnemies.isEmpty()) {
                log.info(
                    "No enemies left for unit {} to attack in the current floor. Skipping"
                        + " attack.",
                    currentUnit);
                return;
              }
              if (currentUnit.isCaster() && !currentUnit.canCast(manaCost)) {
                return;
              }
              var targets = targetsFor(currentUnit, currentEnemies, enemiesAtStart);
              if (targets.isEmpty()) {
                return;
              }
              var targetEnemy = targets.get(random.nextInt(targets.size()));
              var wasAlive = !targetEnemy.isDead();
              var unitType = currentUnit.getStats();
              var strike =
                  aimedStrike.resolve(
                      targetEnemy,
                      currentUnit.getDamage()
                          * damageMultiplier
                          * currentUnit.damageFactorAgainst(targetEnemy.getStats())
                          * gameState.packDamageFactorAgainst(targetEnemy),
                      unitType.getAttackType(),
                      aiming.profileOf(unitType),
                      currentUnit.getAccuracy(),
                      aiming.headBiasOf(unitType));
              onStrike.accept(strike);
              if (currentUnit.isCaster()) {
                currentUnit.spendMana(manaCost);
              }
              if (wasAlive && targetEnemy.isDead()) {
                log.info("Unit {} defeated enemy {}", currentUnit, targetEnemy);
                currentTowerFloor.removeEnemy(targetEnemy);
                gameState.recordEnemyKill();
                currentUnit.changeSanity(gameState.getConfig().getSanity().getKillGain());
              }
            });
  }

  /**
   * The garrison strikes back.
   *
   * @return every item the party lost: worn on a part that was lost, or by a unit that died. Where
   *     they land is not decided here.
   */
  public List<Item> enemiesAttack(
      GameState gameState, List<Enemy> currentEnemies, Map<UnitStats, List<Unit>> unitsOnTower) {
    return enemiesAttack(gameState, currentEnemies, unitsOnTower, strike -> {});
  }

  /** As {@link #enemiesAttack(GameState, List, Map)}, reporting each aimed strike. */
  public List<Item> enemiesAttack(
      GameState gameState,
      List<Enemy> currentEnemies,
      Map<UnitStats, List<Unit>> unitsOnTower,
      Consumer<AimedStrike.Strike> onStrike) {
    var drops = new ArrayList<Item>();
    var random = gameState.getRandom().combat();
    List.copyOf(currentEnemies)
        .forEach(
            currentEnemy -> {
              if (currentEnemy.isDead()) {
                return;
              }
              var targetUnitElement = carrierTarget(gameState, currentEnemy, random);
              if (targetUnitElement == null) {
                var unitStatsKeys = new ArrayList<>(unitsOnTower.keySet());
                Collections.shuffle(unitStatsKeys, random);
                var targetUnit = unitsOnTower.get(unitStatsKeys.getFirst());
                if (targetUnit.isEmpty()) {
                  log.info(
                      "No units available for enemy {} to attack. Skipping attack.", currentEnemy);
                  return;
                }
                var randomUnitOnTowerIndex = random.nextInt(targetUnit.size());
                targetUnitElement = targetUnit.get(randomUnitOnTowerIndex);
              }
              var healthBefore = targetUnitElement.getCurrentHealth();
              var strike =
                  new AimedStrike(random, gameState.getConfig().getAiming())
                      .resolve(
                          targetUnitElement,
                          currentEnemy.getEffectiveDamage()
                              * targetUnitElement.takenFactorFrom(currentEnemy.getStats()),
                          currentEnemy.getStats().getAttackType(),
                          gameState.getConfig().getAiming().profileOf(currentEnemy.getStats()),
                          currentEnemy.getAccuracy(),
                          1.0);
              onStrike.accept(strike);
              drops.addAll(strike.dropped());

              returnThorns(
                  gameState, currentEnemies, currentEnemy, targetUnitElement, healthBefore);

              if (targetUnitElement.isDead()) {
                log.info("Enemy {} defeated unit {}", currentEnemy, targetUnitElement);
                drops.addAll(targetUnitElement.dropAll());
                gameState.removeUnit(targetUnitElement);
              }
            });
    return drops;
  }

  /**
   * The target bias: an enemy the Salt Pack works on aims at the Salt-bearer, with the configured
   * chance. Null leaves the choice to the usual draw. Nothing is drawn unless the bias applies.
   */
  private Unit carrierTarget(GameState gameState, Enemy enemy, RandomGenerator random) {
    if (!gameState.aimsAtSaltBearer(enemy)
        || random.nextDouble() >= gameState.getConfig().getSalt().getBearerTargetBias()) {
      return null;
    }
    return gameState.saltBearer().orElse(null);
  }

  /**
   * A melee attacker that hurt a unit wearing Thorn takes the thorn damage back on its torso. One
   * that dies of it leaves the garrison and counts as a kill.
   */
  private static void returnThorns(
      GameState gameState,
      List<Enemy> currentEnemies,
      Enemy attacker,
      Unit victim,
      double healthBefore) {
    var thorn = victim.thornDamage();
    if (thorn <= 0
        || attacker.getStats().getAttackType() != AttackType.MELEE
        || victim.getCurrentHealth() >= healthBefore) {
      return;
    }
    attacker.damagePart(BodyPart.TORSO, thorn);
    if (attacker.isDead()) {
      log.info("Enemy {} died on the thorns of unit {}", attacker, victim);
      currentEnemies.removeIf(enemy -> enemy.getId().equals(attacker.getId()));
      gameState.recordEnemyKill();
    }
  }

  /**
   * Lets every party member see who garrisons the floor it stands on: a feared type present reveals
   * the phobia and, once per floor, costs the unit sanity.
   */
  private static void meetFearedEnemies(List<Unit> party, List<Enemy> enemies, TowerFloor floor) {
    var present = enemies.stream().map(Enemy::getStats).collect(Collectors.toSet());
    var floorId = floor.getId() == null ? 0 : floor.getId();
    party.forEach(unit -> unit.meetEnemies(floorId, present));
  }

  /**
   * Casters aim at the enemies alive when the tick began, so one may spend its cast on an enemy
   * another unit has just killed. Everyone else picks from whoever still stands.
   */
  private static List<Enemy> targetsFor(Unit unit, List<Enemy> standing, List<Enemy> aliveAtStart) {
    var candidates = unit.isCaster() ? aliveAtStart : standing;
    return isHealer(unit) ? enemiesAHealHitHarms(candidates) : candidates;
  }

  private static boolean isHealer(Unit unit) {
    return unit.getStats().getAttackType() == AttackType.HEAL;
  }

  /** Enemies a Heal hit would hurt: a multiplier of zero is immune, a negative one is healed. */
  private static List<Enemy> enemiesAHealHitHarms(List<Enemy> enemies) {
    return enemies.stream()
        .filter(enemy -> enemy.getStats().getWeaknesses().getOrDefault(AttackType.HEAL, 1.0) > 0)
        .toList();
  }
}

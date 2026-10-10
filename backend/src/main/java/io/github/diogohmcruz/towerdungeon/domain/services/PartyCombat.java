package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * One tick of fighting on a floor: the party strikes, then the garrison strikes back. Kept apart
 * from {@link GameService} so the rules can be tested with a seeded random source.
 *
 * <p>A caster makes at most one cast per tick. A Healer heals a random ally who was wounded when
 * the tick began; if there was none it strikes a random enemy that a Heal hit would harm. Because
 * "wounded" is judged once, at the start of the tick, two Healers may both spend a cast on the same
 * last wounded ally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyCombat {
  private final RandomGenerator random;

  public void unitsAttack(
      Map<UnitStats, List<Unit>> unitsOnTower,
      List<Enemy> currentEnemies,
      TowerFloor currentTowerFloor,
      GameState gameState) {
    var damageMultiplier = gameState.getDamageMultiplier();
    var manaCost = gameState.getConfig().getMagic().getCostPerCast();
    var party = unitsOnTower.values().stream().flatMap(List::stream).toList();
    var woundedAtStart = party.stream().filter(PartyCombat::isWounded).toList();
    var healersThatCast = new HashSet<Integer>();

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
              target.receiveAttack(healer.getStats().getDamage(), AttackType.HEAL);
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
              var targets =
                  isHealer(currentUnit) ? enemiesAHealHitHarms(currentEnemies) : currentEnemies;
              if (targets.isEmpty()) {
                return;
              }
              var targetEnemy = targets.get(random.nextInt(targets.size()));
              targetEnemy.receiveAttack(
                  currentUnit.getStats().getDamage() * damageMultiplier,
                  currentUnit.getStats().getAttackType());
              if (currentUnit.isCaster()) {
                currentUnit.spendMana(manaCost);
              }
              if (targetEnemy.isDead()) {
                log.info("Unit {} defeated enemy {}", currentUnit, targetEnemy);
                currentTowerFloor.removeEnemy(targetEnemy);
                gameState.recordEnemyKill();
              }
            });
  }

  public void enemiesAttack(
      GameState gameState, List<Enemy> currentEnemies, Map<UnitStats, List<Unit>> unitsOnTower) {
    currentEnemies.forEach(
        currentEnemy -> {
          var unitStatsKeys = new ArrayList<>(unitsOnTower.keySet());
          Collections.shuffle(unitStatsKeys, random);
          var targetUnit = unitsOnTower.get(unitStatsKeys.getFirst());
          if (targetUnit.isEmpty()) {
            log.info("No units available for enemy {} to attack. Skipping attack.", currentEnemy);
            return;
          }
          var randomUnitOnTowerIndex = random.nextInt(targetUnit.size());
          var targetUnitElement = targetUnit.get(randomUnitOnTowerIndex);
          targetUnitElement.receiveAttack(
              currentEnemy.getEffectiveDamage(), currentEnemy.getStats().getAttackType());

          if (targetUnitElement.getCurrentHealth() <= 0) {
            log.info("Enemy {} defeated unit {}", currentEnemy, targetUnitElement);
            gameState.removeUnit(targetUnitElement);
          }
        });
  }

  private static boolean isHealer(Unit unit) {
    return unit.getStats().getAttackType() == AttackType.HEAL;
  }

  private static boolean isWounded(Unit unit) {
    return unit.getCurrentHealth() < unit.getMaxHealth();
  }

  /** Enemies a Heal hit would hurt: a multiplier of zero is immune, a negative one is healed. */
  private static List<Enemy> enemiesAHealHitHarms(List<Enemy> enemies) {
    return enemies.stream()
        .filter(enemy -> enemy.getStats().getWeaknesses().getOrDefault(AttackType.HEAL, 1.0) > 0)
        .toList();
  }
}

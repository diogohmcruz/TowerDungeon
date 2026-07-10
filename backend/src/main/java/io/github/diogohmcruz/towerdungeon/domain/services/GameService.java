package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.UpgradeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.exceptions.InvalidInvasion;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
  private static final long TIME_INTERVAL = 10L;
  private static final double SUPPLY_DRAIN_PER_UNIT = 1.5;
  private static final double STARVATION_DAMAGE = 5.0;
  private static final double MATERIALS_PER_FLOOR = 5.0;
  private static final int RELIC_DEPTH_THRESHOLD = 10;
  private final Map<String, GameState> players = new ConcurrentHashMap<>();

  public GameState getState(String sessionId) {
    return players.computeIfAbsent(sessionId, _ -> new GameState());
  }

  public void handleMessage(String sessionId, BuyActionDTO buyActionDTO) {
    var gameState = players.get(sessionId);
    if (!gameState.isUnitUnlocked(buyActionDTO.unitStats())) {
      log.warn(
          "Player[{}] tried to recruit locked unit {}. Ignoring.",
          sessionId,
          buyActionDTO.unitStats());
      return;
    }
    var newUnits = new ArrayList<Unit>();
    var quantity = buyActionDTO.quantity();
    while (gameState.getCredit() > buyActionDTO.unitStats().getCost() && quantity > 0) {
      gameState.setCredit(gameState.getCredit() - buyActionDTO.unitStats().getCost());
      var unit = new Unit(buyActionDTO.unitStats());
      newUnits.add(unit);
      quantity--;
    }
    gameState.addUnits(buyActionDTO.unitStats(), newUnits);
    log.info("Received buy action from session [{}]. New units: {}", sessionId, newUnits);
  }

  public void handleBuyVillagersAction(String sessionId) {
    var gameState = players.get(sessionId);
    var villagersCount = gameState.buyVillager();
    log.info("Player[{}] total villagers: {}", sessionId, villagersCount);
  }

  public void handleSellFoodAction(String sessionId) {
    var gameState = players.get(sessionId);
    var credits = gameState.sellFood();
    log.info("Player[{}] sold food for credits: {}", sessionId, credits);
  }

  public void handleUpgradeAction(String sessionId, UpgradeActionDTO upgradeActionDTO) {
    var gameState = players.get(sessionId);
    UpgradeType type;
    try {
      type = UpgradeType.valueOf(upgradeActionDTO.upgradeId());
    } catch (IllegalArgumentException | NullPointerException e) {
      log.warn("Player[{}] requested unknown upgrade {}.", sessionId, upgradeActionDTO.upgradeId());
      return;
    }
    var applied = gameState.applyUpgrade(type);
    log.info(
        "Player[{}] upgrade {} -> {}", sessionId, type, applied ? "purchased" : "rejected");
  }

  public void handleMessage(String sessionId, InvadeActionDTO invadeActionDTO) {
    log.info("Received invade action from session [{}] {}", sessionId, invadeActionDTO);
    var gameState = players.get(sessionId);
    if (gameState.getTower() != null) {
      log.warn("Player[{}] tried to invade while a run is already active. Ignoring.", sessionId);
      return;
    }
    var units = gameState.getUnits();
    final var unitsOnTower = gameState.getUnitsOnTower();
    var invadeUnits = invadeActionDTO.units();
    if (invadeUnits.isEmpty()) {
      sendAllToTower(units, unitsOnTower);
    } else {
      sendToTower(units, invadeUnits, gameState);
    }
    gameState.setUnits(units);
    gameState.setUnitsOnTower(unitsOnTower);
    if (!gameState.hasUnitsOnTower()) {
      log.warn("Player[{}] tried to invade with no available units. Ignoring.", sessionId);
      return;
    }
    gameState.setTower(new Tower());
    gameState.startRun();
  }

  public void handleExtractAction(String sessionId) {
    var gameState = players.get(sessionId);
    if (gameState.getTower() == null) {
      log.warn("Player[{}] tried to extract with no active run.", sessionId);
      return;
    }
    gameState.returnLeftoverSuppliesToVillage();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.setTower(null);
    gameState.completeExpedition();
    log.info(
        "Player[{}] extracted. Leftover food returned to village, loot banked, party returned home"
            + " to recover on the village's food.",
        sessionId);
  }

  private static void sendAllToTower(
      Map<UnitStats, List<Unit>> units, Map<UnitStats, List<Unit>> unitsOnTower) {
    var unitsTemp = mergeAllArmy(units, unitsOnTower);
    units.clear();
    unitsOnTower.clear();
    unitsOnTower.putAll(unitsTemp);
  }

  private static void sendToTower(
      Map<UnitStats, List<Unit>> units, Map<UnitStats, Integer> invadeUnits, GameState gameState) {
    invadeUnits.forEach(
        (unit, quantity) -> {
          List<Unit> currentUnits = units.getOrDefault(unit, new ArrayList<>());
          var currentUnitsCount = currentUnits.size();
          var quantityOnBase = currentUnitsCount - quantity;
          if (quantityOnBase < 0) {
            throw new InvalidInvasion(unit, quantity, currentUnitsCount);
          }
          while (quantity > 0) {
            gameState.enlistRandom(unit);
            quantity--;
          }
        });
  }

  private static HashMap<UnitStats, List<Unit>> mergeAllArmy(
      Map<UnitStats, List<Unit>> units, Map<UnitStats, List<Unit>> unitsOnTower) {
    var unitsTemp = new HashMap<>(units);
    unitsOnTower.forEach(
        (key, value) ->
            unitsTemp.merge(
                key,
                value,
                (a, b) -> {
                  a.addAll(b);
                  return a;
                }));
    return unitsTemp;
  }

  @Scheduled(fixedRate = 1000)
  public void gameLoop() {
    players.values().forEach(GameState::triggerLifecycle);
    players.values().forEach(this::updateInvasion);
  }

  @Scheduled(fixedRate = TIME_INTERVAL)
  public void invasionTimer() {
    players.values().forEach(gameState -> gameState.passingTime(TIME_INTERVAL));
  }

  private void updateInvasion(GameState gameState) {
    var tower = gameState.getTower();
    if (tower == null) {
      return;
    }
    if (gameState.getUnitsOnTower() == null || !gameState.hasUnitsOnTower()) {
      log.warn("No units on tower. Invasion ended.");
      endRunOnWipe(gameState);
      return;
    }
    gameState.drainSupplies(gameState.getTowerPartySize() * SUPPLY_DRAIN_PER_UNIT);
    if (!gameState.hasSupplies()) {
      applyStarvation(gameState);
      if (!gameState.hasUnitsOnTower()) {
        log.warn("Party starved to death. Run lost, all carried loot forfeited.");
        endRunOnWipe(gameState);
        return;
      }
    }
    var unitsOnTower = new HashMap<>(gameState.getUnitsOnTower());
    var currentTowerFloor = tower.getCurrentTowerFloor();
    if (currentTowerFloor == null || currentTowerFloor.getEnemies().isEmpty()) {
      var clearedFloor = tower.getCurrentFloor();
      gameState.addCredit(clearedFloor * 10.0);
      gatherFloorLoot(gameState, clearedFloor);
      tower.moveToNextFloor();
      gameState.recordFloorReached(tower.getCurrentFloor());
      if (tower.getMaxFloor().equals(tower.getCurrentFloor())) {
        log.info("WIN! Player has reached the top of the tower!");
        return;
      }
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    var currentEnemies = currentTowerFloor.getEnemies();
    unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState.getDamageMultiplier());
    enemiesAttack(gameState, currentEnemies, unitsOnTower);
  }

  private static void gatherFloorLoot(GameState gameState, int clearedFloor) {
    if (clearedFloor <= 0) {
      return;
    }
    gameState.gatherLoot(ResourceType.MATERIALS, clearedFloor * MATERIALS_PER_FLOOR);
    if (clearedFloor >= RELIC_DEPTH_THRESHOLD) {
      gameState.gatherLoot(ResourceType.RELICS, (double) (clearedFloor / RELIC_DEPTH_THRESHOLD));
    }
  }

  private static void applyStarvation(GameState gameState) {
    var starving =
        gameState.getUnitsOnTower().values().stream().flatMap(Collection::stream).toList();
    starving.forEach(unit -> unit.receiveAttack(STARVATION_DAMAGE, null));
    starving.stream().filter(BaseUnit::isDead).toList().forEach(gameState::removeUnit);
  }

  private static void endRunOnWipe(GameState gameState) {
    gameState.forfeitLoot();
    gameState.setTower(null);
    gameState.setSupplies(0d);
    gameState.getUnitsOnTower().clear();
    gameState.completeExpedition();
  }

  private static void unitsAttack(
      Map<UnitStats, List<Unit>> unitsOnTower,
      List<Enemy> currentEnemies,
      TowerFloor currentTowerFloor,
      double damageMultiplier) {
    unitsOnTower.entrySet().stream()
        .filter(entry -> AttackType.HEAL.equals(entry.getKey().getAttackType()))
        .map(Entry::getValue)
        .flatMap(Collection::stream)
        .forEach(
            healerUnit -> {
              var otherUnitsOnTower =
                  unitsOnTower.values().stream()
                      .flatMap(List::stream)
                      .filter(unit -> !healerUnit.getId().equals(unit.getId()))
                      .toList();
              var randomKeyIndex = ThreadLocalRandom.current().nextInt(otherUnitsOnTower.size());
              var targetUnit = otherUnitsOnTower.get(randomKeyIndex);
              targetUnit.receiveAttack(healerUnit.getStats().getDamage(), AttackType.HEAL);
            });
    unitsOnTower.values().stream()
        .flatMap(List::stream)
        .forEach(
            currentUnit -> {
              if (currentEnemies.isEmpty()) {
                log.info(
                    "No enemies left for unit {} to attack in the current floor. Skipping"
                        + " attack.",
                    currentUnit);
                return;
              }
              var randomKeyIndex = ThreadLocalRandom.current().nextInt(currentEnemies.size());
              var targetEnemy = currentEnemies.get(randomKeyIndex);
              targetEnemy.receiveAttack(
                  currentUnit.getStats().getDamage() * damageMultiplier,
                  currentUnit.getStats().getAttackType());
              if (targetEnemy.isDead()) {
                log.info("Unit {} defeated enemy {}", currentUnit, targetEnemy);
                currentTowerFloor.removeEnemy(targetEnemy);
              }
            });
  }

  private static void enemiesAttack(
      GameState gameState, List<Enemy> currentEnemies, Map<UnitStats, List<Unit>> unitsOnTower) {
    currentEnemies.forEach(
        currentEnemy -> {
          var unitStatsKeys = new ArrayList<>(unitsOnTower.keySet());
          Collections.shuffle(unitStatsKeys);
          var targetUnit = unitsOnTower.get(unitStatsKeys.getFirst());
          if (targetUnit.isEmpty()) {
            log.info("No units available for enemy {} to attack. Skipping attack.", currentEnemy);
            return;
          }
          var randomUnitOnTowerIndex = ThreadLocalRandom.current().nextInt(targetUnit.size());
          var targetUnitElement = targetUnit.get(randomUnitOnTowerIndex);
          targetUnitElement.receiveAttack(
              currentEnemy.getStats().getDamage(), currentEnemy.getStats().getAttackType());

          if (targetUnitElement.getCurrentHealth() <= 0) {
            log.info("Enemy {} defeated unit {}", currentEnemy, targetUnitElement);
            gameState.removeUnit(targetUnitElement);
          }
        });
  }

  public void closeSession(String id) {
    players.remove(id);
  }
}

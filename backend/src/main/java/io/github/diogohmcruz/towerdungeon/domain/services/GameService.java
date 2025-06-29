package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.domain.exceptions.InvalidInvasion;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
  private static final long TIME_INTERVAL = 10L;
  private final Map<String, GameState> players = new ConcurrentHashMap<>();

  public GameState getState(String sessionId) {
    return players.computeIfAbsent(sessionId, _ -> new GameState());
  }

  public void handleMessage(String sessionId, BuyActionDTO buyActionDTO) {
    log.info("Received buy action from session [{}] {}", sessionId, buyActionDTO);
    var gameState = players.get(sessionId);
    var newUnits = new ArrayList<Unit>();
    for (int i = 0; i < buyActionDTO.quantity(); i++) {
      var unit = new Unit(buyActionDTO.unitStats());
      newUnits.add(unit);
    }
    gameState.addUnits(buyActionDTO.unitStats(), newUnits);
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

  public void handleMessage(String sessionId, InvadeActionDTO invadeActionDTO) {
    log.info("Received invade action from session [{}] {}", sessionId, invadeActionDTO);
    var gameState = players.get(sessionId);
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
    gameState.setTower(new Tower());
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
      gameState.setTower(null);
      return;
    }
    var unitsOnTower = new HashMap<>(gameState.getUnitsOnTower());
    var currentTowerFloor = tower.getCurrentTowerFloor();
    if (currentTowerFloor == null || currentTowerFloor.getEnemies().isEmpty()) {
      gameState.addCredit(tower.getCurrentFloor() * 10.0);
      tower.moveToNextFloor();
      if (tower.getMaxFloor().equals(tower.getCurrentFloor())) {
        log.info("WIN! Player has reached the top of the tower!");
        return;
      }
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    var currentEnemies = currentTowerFloor.getEnemies();
    unitsOnTower.forEach(
        (unit, value) ->
            value.forEach(
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
                  targetEnemy.receiveAttack(unit.getDamage(), unit.getAttackType());
                  if (targetEnemy.getCurrentHealth() <= 0) {
                    log.info("Unit {} defeated enemy {}", currentUnit, targetEnemy);
                    currentTowerFloor.removeEnemy(targetEnemy);
                  }
                }));
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
          targetUnitElement.receiveAttack(currentEnemy.getStats().getDamage());

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

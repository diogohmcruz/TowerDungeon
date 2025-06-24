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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
  private final Map<String, GameState> players = new ConcurrentHashMap<>();

  public GameState getState(String sessionId) {
    return players.computeIfAbsent(sessionId, id -> new GameState());
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

  public void handleMessage(String sessionId, InvadeActionDTO invadeActionDTO) {
    log.info("Received invade action from session [{}] {}", sessionId, invadeActionDTO);
    var gameState = players.get(sessionId);
    var units = gameState.getUnits();
    final var unitsOnTower = gameState.getUnitsOnTower();
    if (invadeActionDTO.units().isEmpty()) {
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
      units.clear();
      unitsOnTower.clear();
      unitsOnTower.putAll(unitsTemp);
    } else {
      invadeActionDTO
          .units()
          .forEach(
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
    gameState.setUnits(units);
    gameState.setUnitsOnTower(unitsOnTower);
    gameState.setTower(new Tower());
  }

  @Scheduled(fixedRate = 1000)
  public void gameLoop() {
    players.values().forEach(GameState::updateMana);
    players.values().forEach(this::updateInvasion);
  }

  @Scheduled(fixedRate = 10)
  public void invasionTimer() {
    players.values().forEach(GameState::passingTime);
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
    var enemiesInCurrentFloor = tower.getEnemies();
    unitsOnTower.forEach(
        (unit, value) -> {
          value.forEach(
              currentUnit -> {
                if (enemiesInCurrentFloor.isEmpty()) {
                  log.info("No enemies left on the current floor. Skipping attack.");
                  return;
                }
                var randomKeyIndex =
                    ThreadLocalRandom.current().nextInt(enemiesInCurrentFloor.size());
                var targetEnemy = enemiesInCurrentFloor.get(randomKeyIndex);
                targetEnemy.receiveAttack(unit.getDamage(), unit.getAttackType());
                if (targetEnemy.getCurrentHealth() <= 0) {
                  log.info(
                      "Enemy {} has been defeated by unit {}",
                      targetEnemy.getId(),
                      currentUnit.getId());
                  tower.removeEnemy(targetEnemy);
                }
              });
        });
    if (enemiesInCurrentFloor.isEmpty()) {
      gameState.addCredit(tower.getCurrentFloor() * 10.0);
      tower.moveToNextFloor();
      if (tower.getMaxFloor().equals(tower.getCurrentFloor())) {
        log.info("WIN! Player has reached the top of the tower!");
        return;
      }
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    enemiesInCurrentFloor.forEach(
        currentEnemy -> {
          var unitStatsKeys = new ArrayList<>(unitsOnTower.keySet());
          Collections.shuffle(unitStatsKeys);
          var targetUnit = unitsOnTower.get(unitStatsKeys.get(0));
          if (targetUnit.isEmpty()) {
            log.info(
                "No units available to attack enemy {}. Skipping attack.", currentEnemy.getId());
            return;
          }
          var randomUnitOnTowerIndex = ThreadLocalRandom.current().nextInt(targetUnit.size());
          var targetUnitElement = targetUnit.get(randomUnitOnTowerIndex);
          targetUnitElement.receiveAttack(currentEnemy.getStats().getDamage());

          if (targetUnitElement.getCurrentHealth() <= 0) {
            log.info("Enemy {} defeated unit {}", currentEnemy.getId(), targetUnitElement.getId());
            gameState.removeUnit(targetUnitElement);
          }
        });
  }
}

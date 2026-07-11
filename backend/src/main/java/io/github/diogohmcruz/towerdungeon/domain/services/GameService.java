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
import java.util.function.Consumer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.UpgradeActionDTO;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.exceptions.InvalidInvasion;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
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
  private final GameProperties config;
  private final ObjectMapper objectMapper;
  private final Map<String, GameState> players = new ConcurrentHashMap<>();

  private <T> T access(String sessionId, StateFn<T> body) {
    var gameState = players.computeIfAbsent(sessionId, _ -> new GameState(config));
    try {
      return gameState.callLocked(() -> body.apply(gameState));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to access game state for session " + sessionId, e);
    }
  }

  /** Mutates one player's state under its lock. Delegates to the {@link #access} gateway. */
  private void withPlayer(String sessionId, Consumer<GameState> action) {
    access(
        sessionId,
        gameState -> {
          action.accept(gameState);
          return null;
        });
  }

  /** Mutates every player's state, each under its own lock via the {@link #access} gateway. */
  private void forEachPlayer(java.util.function.Consumer<GameState> action) {
    players.keySet().forEach(sessionId -> withPlayer(sessionId, action));
  }

  /**
   * Serializes a consistent snapshot of a player's state to JSON under its lock, so the reactive
   * send thread never observes a half-mutated graph (which previously surfaced as {@code
   * IndexOutOfBounds} / {@code ConcurrentModificationException} during serialization). The live
   * {@link GameState} is never handed to the transport layer — only its rendered JSON.
   */
  public String renderState(String sessionId) {
    return access(sessionId, objectMapper::writeValueAsString);
  }

  public void handleMessage(String sessionId, BuyActionDTO buyActionDTO) {
    withPlayer(
        sessionId,
        gameState -> {
          if (!gameState.isUnitUnlocked(buyActionDTO.unitStats())) {
            log.warn(
                "Player[{}] tried to recruit locked unit {}. Ignoring.",
                sessionId,
                buyActionDTO.unitStats());
            return;
          }
          var newUnits = new ArrayList<Unit>();
          var quantity = buyActionDTO.quantity();
          var cost = buyActionDTO.unitStats().getCost();
          while (gameState.getSpendableCredit() > cost && quantity > 0) {
            gameState.spendCredit(cost);
            var unit = new Unit(buyActionDTO.unitStats());
            newUnits.add(unit);
            quantity--;
          }
          gameState.addUnits(buyActionDTO.unitStats(), newUnits);
          log.info("Received buy action from session [{}]. New units: {}", sessionId, newUnits);
        });
  }

  public void handleBuyVillagersAction(String sessionId) {
    withPlayer(
        sessionId,
        gameState -> {
          var villagersCount = gameState.buyVillager();
          log.info("Player[{}] total villagers: {}", sessionId, villagersCount);
        });
  }

  public void handleSellFoodAction(String sessionId) {
    withPlayer(
        sessionId,
        gameState -> {
          var credits = gameState.sellFood();
          log.info("Player[{}] sold food for credits: {}", sessionId, credits);
        });
  }

  public void handleSellMaterialsAction(String sessionId) {
    withPlayer(
        sessionId,
        gameState -> {
          var profit = gameState.sellAllMaterials();
          log.info("Player[{}] sold all materials for {} credits", sessionId, profit);
        });
  }

  public void handleSellRelicsAction(String sessionId) {
    withPlayer(
        sessionId,
        gameState -> {
          var profit = gameState.sellAllRelics();
          log.info("Player[{}] sold all relics for {} credits", sessionId, profit);
        });
  }

  public void handleUpgradeAction(String sessionId, UpgradeActionDTO upgradeActionDTO) {
    withPlayer(
        sessionId,
        gameState -> {
          UpgradeType type;
          try {
            type = UpgradeType.valueOf(upgradeActionDTO.upgradeId());
          } catch (IllegalArgumentException | NullPointerException e) {
            log.warn(
                "Player[{}] requested unknown upgrade {}.",
                sessionId,
                upgradeActionDTO.upgradeId());
            return;
          }
          var applied = gameState.applyUpgrade(type);
          log.info(
              "Player[{}] upgrade {} -> {}", sessionId, type, applied ? "purchased" : "rejected");
        });
  }

  public void handleMessage(String sessionId, InvadeActionDTO invadeActionDTO) {
    log.info("Received invade action from session [{}] {}", sessionId, invadeActionDTO);
    withPlayer(
        sessionId,
        gameState -> {
          if (gameState.isExpeditionActive()) {
            log.warn(
                "Player[{}] tried to invade while a run is already active. Ignoring.", sessionId);
            return;
          }
          if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
            log.warn(
                "Player[{}] tried to invade after the campaign ended ({}). Ignoring.",
                sessionId,
                gameState.getGameOutcome());
            return;
          }
          var requestedFloor =
              invadeActionDTO.startFloor() == null ? 0 : invadeActionDTO.startFloor();
          if (!gameState.isStartFloorUnlocked(requestedFloor)) {
            log.warn(
                "Player[{}] tried to start on floor {} without an open shortcut. Ignoring.",
                sessionId,
                requestedFloor);
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
          if (gameState.getTower() == null) {
            gameState.setTower(
                new Tower(config.getTower().getMaxFloor(), config.getBoss(), config.getEnemies()));
          }
          gameState.getTower().startNewRunAt(requestedFloor);
          gameState.setRunStartFloor(requestedFloor > 0 ? requestedFloor : 0);
          gameState.startRun();
          if (requestedFloor > 0) {
            gameState.recordFloorReached(requestedFloor);
          }
        });
  }

  public void handleExtractAction(String sessionId) {
    withPlayer(
        sessionId,
        gameState -> {
          if (!gameState.isExpeditionActive()) {
            log.warn("Player[{}] tried to extract with no active run.", sessionId);
            return;
          }
          gameState.buildRunSummary(RunSummary.EXTRACTED);
          gameState.returnLeftoverSuppliesToVillage();
          gameState.returnPartyHome();
          gameState.bankLoot();
          gameState.setExpeditionActive(false);
          gameState.completeExpedition();
          log.info(
              "Player[{}] extracted. Leftover food returned to village, loot banked, party returned"
                  + " home to recover on the village's food.",
              sessionId);
        });
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

  @Scheduled(fixedRateString = "${game.loop.lifecycle-ms}")
  public void gameLoop() {
    forEachPlayer(
        gameState -> {
          gameState.triggerLifecycle();
          updateInvasion(gameState);
        });
  }

  @Scheduled(fixedRateString = "${game.loop.invasion-tick-ms}")
  public void invasionTimer() {
    var tick = config.getLoop().getInvasionTickMs();
    forEachPlayer(gameState -> gameState.passingTime(tick));
  }

  private void updateInvasion(GameState gameState) {
    if (!gameState.isExpeditionActive()) {
      return;
    }
    var tower = gameState.getTower();
    if (gameState.getUnitsOnTower() == null || !gameState.hasUnitsOnTower()) {
      log.warn("No units on tower. Invasion ended.");
      endRunOnWipe(gameState);
      return;
    }
    gameState.drainSupplies(
        gameState.getTowerPartySize() * config.getLoop().getSupplyDrainPerUnit());
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
      var bossCleared = currentTowerFloor != null && currentTowerFloor.isBoss();
      var reward = config.getReward();
      var rewardMultiplier = bossCleared ? reward.getBossRewardMultiplier() : 1.0;
      gameState.gatherCredits(clearedFloor * reward.getCreditPerFloor() * rewardMultiplier);
      gatherFloorLoot(gameState, clearedFloor, rewardMultiplier);
      if (bossCleared) {
        tower.markBossCleared(clearedFloor);
        log.info(
            "Defeated the guardian of floor {}! The lesser floors below it grow easier.",
            clearedFloor);
      }
      if (clearedFloor >= tower.getMaxFloor()) {
        log.info("VICTORY! Player conquered the summit of the tower on floor {}!", clearedFloor);
        endRunOnVictory(gameState);
        return;
      }
      tower.moveToNextFloor();
      gameState.recordFloorReached(tower.getCurrentFloor());
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    var currentEnemies = currentTowerFloor.getEnemies();
    unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState);
    enemiesAttack(gameState, currentEnemies, unitsOnTower);
  }

  private void gatherFloorLoot(GameState gameState, int clearedFloor, double rewardMultiplier) {
    if (clearedFloor <= 0) {
      return;
    }
    var reward = config.getReward();
    gameState.gatherLoot(
        ResourceType.MATERIALS, clearedFloor * reward.getMaterialsPerFloor() * rewardMultiplier);
    gameState.gatherSupplies(reward.getSuppliesPerFloor() * rewardMultiplier);
    if (clearedFloor >= reward.getRelicDepthThreshold()) {
      gameState.gatherLoot(
          ResourceType.RELICS,
          (clearedFloor / (double) reward.getRelicDepthThreshold()) * rewardMultiplier);
    }
  }

  private void applyStarvation(GameState gameState) {
    var starving =
        gameState.getUnitsOnTower().values().stream().flatMap(Collection::stream).toList();
    starving.forEach(unit -> unit.receiveAttack(config.getLoop().getStarvationDamage(), null));
    starving.stream().filter(BaseUnit::isDead).toList().forEach(gameState::removeUnit);
  }

  private static void endRunOnWipe(GameState gameState) {
    gameState.buildRunSummary(RunSummary.WIPED);
    gameState.forfeitLoot();
    gameState.setExpeditionActive(false);
    gameState.setSupplies(0d);
    gameState.getUnitsOnTower().clear();
    gameState.completeExpedition();
  }

  /**
   * Ends the campaign in triumph: the summit is conquered, so the party banks its full haul and
   * marches home victorious, and the game is marked won.
   */
  private static void endRunOnVictory(GameState gameState) {
    gameState.buildRunSummary(RunSummary.VICTORY);
    gameState.returnLeftoverSuppliesToVillage();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    gameState.winCampaign();
  }

  private static void unitsAttack(
      Map<UnitStats, List<Unit>> unitsOnTower,
      List<Enemy> currentEnemies,
      TowerFloor currentTowerFloor,
      GameState gameState) {
    var damageMultiplier = gameState.getDamageMultiplier();
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
                gameState.recordEnemyKill();
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
              currentEnemy.getEffectiveDamage(), currentEnemy.getStats().getAttackType());

          if (targetUnitElement.getCurrentHealth() <= 0) {
            log.info("Enemy {} defeated unit {}", currentEnemy, targetUnitElement);
            gameState.removeUnit(targetUnitElement);
          }
        });
  }

  public void closeSession(String id) {
    players.remove(id);
  }

  /** A body that reads or mutates a locked {@link GameState} and produces a result; may throw. */
  @FunctionalInterface
  private interface StateFn<T> {

    T apply(GameState state) throws Exception;
  }
}

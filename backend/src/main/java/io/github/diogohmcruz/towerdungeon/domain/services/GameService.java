package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.ReinforceActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.UpgradeActionDTO;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.exceptions.InvalidInvasion;
import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
  private final GameProperties config;
  private final ObjectMapper objectMapper;
  private final RandomGenerator random;
  private final ArchitecturePlan architecturePlan;
  private final PartyCombat partyCombat;
  private final Map<String, GameState> players = new ConcurrentHashMap<>();

  /**
   * Starts a brand-new campaign: an empty {@link GameState} with the configured Founders recruited
   * into Reserves at no cost. Restoring a saved campaign must not go through here.
   */
  GameState newCampaign() {
    var gameState = new GameState(config);
    for (var founder : config.getStart().getFounders()) {
      gameState.addUnits(founder.getUnitType(), List.of(gameState.recruitFounder(founder, random)));
    }
    return gameState;
  }

  /** The live state of a session, if it has one. Test hook. */
  GameState playerState(String sessionId) {
    return players.get(sessionId);
  }

  private <T> T access(String sessionId, StateFn<T> body) {
    var gameState = players.computeIfAbsent(sessionId, _ -> newCampaign());
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
            var unit = gameState.recruit(buyActionDTO.unitStats(), random);
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
                new Tower(
                    config.getTower().getMaxFloor(),
                    config.getBoss(),
                    config.getEnemies(),
                    random,
                    architecturePlan));
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
          gameState.returnReinforcementsHome();
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

  public void handleMessage(String sessionId, ReinforceActionDTO reinforceActionDTO) {
    log.info("Received reinforce action from session [{}] {}", sessionId, reinforceActionDTO);
    withPlayer(
        sessionId,
        gameState -> {
          if (!gameState.isExpeditionActive()) {
            log.warn(
                "Player[{}] tried to send reinforcements with no active run. Ignoring.", sessionId);
            return;
          }
          if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
            log.warn(
                "Player[{}] tried to reinforce after the campaign ended ({}). Ignoring.",
                sessionId,
                gameState.getGameOutcome());
            return;
          }
          gameState.sendReinforcement(reinforceActionDTO.units());
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
    gameState.rechargeTowerCasters();
    gameState.advanceReinforcements();
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
    partyCombat.unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState);
    partyCombat.enemiesAttack(gameState, currentEnemies, unitsOnTower);
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
    gameState.returnReinforcementsHome();
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
    gameState.returnReinforcementsHome();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    gameState.winCampaign();
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

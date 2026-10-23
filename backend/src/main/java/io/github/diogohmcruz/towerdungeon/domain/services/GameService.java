package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.api.dtos.AssignTradeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyPostsActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.BuyTomeActionDTO;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame.Notice;
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
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.LoadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
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
  private final CampaignStore store;
  private final FloorBundle floorBundle;
  private final AutosaveCoordinator saves;
  private final Map<String, PlayerSlot> players = new ConcurrentHashMap<>();
  private volatile boolean running = true;

  /** What a connection gets back from {@link #attach}: a signal for when it has been replaced. */
  public record Attachment(Mono<Void> replaced) {}

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

  /** The live state of a player, if it has a loaded slot. Test hook. */
  public GameState playerState(String playerId) {
    var slot = players.get(playerId);
    return slot == null ? null : slot.state();
  }

  /** Whether a slot (attached or lingering) exists for the player. Test hook. */
  public boolean hasSlot(String playerId) {
    return players.containsKey(playerId);
  }

  /** Whether a connection is attached to the player's slot. Test hook. */
  public boolean isConnected(String playerId) {
    var slot = players.get(playerId);
    return slot != null && slot.isAttached();
  }

  /**
   * Attaches a connection to the player's slot, loading the Campaign through the store when the
   * slot does not exist yet (a new key starts a fresh Campaign). The newest connection wins: the
   * previous one's {@link Attachment#replaced()} fires. Loads are single-flight per player. Fails
   * with {@link SchemaNewerException} when the saved record is newer than this build.
   */
  public Mono<Attachment> attach(String playerId, String wsId) {
    return Mono.defer(
        () -> {
          var connection = new PlayerSlot.Connection(wsId);
          var slot =
              players.compute(
                  playerId,
                  (id, existing) -> {
                    var current = existing != null ? existing : newSlot(id);
                    current.attach(connection);
                    return current;
                  });
          return slot.loading().thenReturn(new Attachment(connection.replaced().asMono()));
        });
  }

  /** Detaches the connection if it is still the attached one; the slot lingers before eviction. */
  public void detach(String playerId, String wsId) {
    var slot = players.get(playerId);
    if (slot != null && slot.detach(wsId, System.nanoTime())) {
      // The clock stops here. An Expedition in flight is held until the player comes back.
      access(
          slot,
          state -> {
            if (state.isExpeditionActive()) {
              slot.hold();
            }
            return Boolean.TRUE;
          });
      saves.flush(slot);
    }
  }

  /** Removes the slots that have been detached for longer than the configured linger. */
  @Scheduled(fixedRate = 1000)
  public void evictLingering() {
    evictLingering(System.nanoTime());
  }

  void evictLingering(long nowNanos) {
    var linger = config.getPersistence().getLinger().toNanos();
    players
        .keySet()
        .forEach(
            id ->
                players.computeIfPresent(
                    id,
                    (_, slot) -> {
                      if (!slot.hasLingeredFor(linger, nowNanos)) {
                        return slot;
                      }
                      if (slot.saves().isDirty()) {
                        saves.flush(slot);
                      }
                      return null;
                    }));
  }

  /** Writes the Campaigns that are due; runs often and only ever hands work to the store. */
  @Scheduled(fixedRate = 500)
  public void autosave() {
    if (running) {
      players.values().forEach(saves::tick);
    }
  }

  /** Stops the game clocks and writes every attached or unsaved Campaign, within the bound. */
  void shutdown() {
    running = false;
    saves.flushAll(players.values(), config.getPersistence().getShutdownWait());
  }

  /** Queues a notice for the player's next frame. Does nothing when the player has no slot. */
  public void notifyPlayer(String playerId, Notice notice) {
    var slot = players.get(playerId);
    if (slot != null) {
      slot.addNotice(notice);
    }
  }

  private PlayerSlot newSlot(String playerId) {
    var slot = new PlayerSlot(playerId);
    slot.loading(
        loadCampaign(slot)
            .doOnError(
                e -> {
                  log.error("Failed to load the Campaign of player {}", playerId, e);
                  players.remove(playerId, slot);
                })
            .cache());
    return slot;
  }

  private Mono<GameState> loadCampaign(PlayerSlot slot) {
    return store
        .load(slot.playerId())
        .map(result -> restoreCampaign(slot, result))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  var fresh = newCampaign();
                  slot.loaded(fresh, false, null, CampaignSchema.CURRENT);
                  saves.opened(slot, CampaignStore.INITIAL_SEQ);
                  return fresh;
                }));
  }

  private GameState restoreCampaign(PlayerSlot slot, LoadResult result) {
    if (result.schema() > CampaignSchema.CURRENT) {
      throw new SchemaNewerException(result.schema());
    }
    var snapshot = objectMapper.readValue(result.payload(), GameSnapshot.class);
    var state = GameState.restore(snapshot, config, random, architecturePlan);
    if (!result.quarantined().isEmpty()) {
      slot.addNotice(
          new Notice("SAVE_DAMAGED", "A damaged save was set aside and an older one was loaded."));
    } else if (!result.source().isHead()) {
      slot.addNotice(new Notice("SAVE_FROM_BACKUP", "Your latest save was unreadable."));
    }
    slot.loaded(state, true, result.savedAt(), result.schema());
    saves.opened(slot, result.seq());
    if (state.isExpeditionActive()) {
      slot.hold();
    }
    return state;
  }

  /** Runs a body under the state's lock; empty when the slot has no loaded Campaign. */
  private <T> Optional<T> access(String playerId, StateFn<T> body) {
    var slot = players.get(playerId);
    return slot == null ? Optional.empty() : access(slot, body);
  }

  private <T> Optional<T> access(PlayerSlot slot, StateFn<T> body) {
    var gameState = slot.state();
    if (gameState == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(gameState.callLocked(() -> body.apply(gameState)));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to access game state for player " + slot.playerId(), e);
    }
  }

  /**
   * Mutates one player's state under its lock. Find-only: a player without a slot is ignored, so a
   * late message can never resurrect a closed Campaign.
   */
  private void withPlayer(String playerId, Consumer<GameState> action) {
    withPlayer(playerId, action, false);
  }

  /** As {@link #withPlayer(String, Consumer)}, and the change is written as a purchase. */
  private void withPurchase(String playerId, Consumer<GameState> action) {
    withPlayer(playerId, action, true);
  }

  private void withPlayer(String playerId, Consumer<GameState> action, boolean purchase) {
    var slot = players.get(playerId);
    var applied =
        slot == null
            ? Optional.empty()
            : access(
                slot,
                gameState -> {
                  applyAndRecord(slot, gameState, action);
                  if (purchase) {
                    saves.purchased(slot);
                  }
                  return Boolean.TRUE;
                });
    if (applied.isEmpty()) {
      log.debug("Ignoring an action for player {} with no live Campaign", playerId);
    }
  }

  /** Runs a mutation and tells the coordinator what it changed. Call under the state's lock. */
  private void applyAndRecord(PlayerSlot slot, GameState gameState, Consumer<GameState> action) {
    var before = AutosaveCoordinator.Marks.of(gameState);
    try {
      action.accept(gameState);
    } finally {
      if (!gameState.isExpeditionActive()) {
        slot.resume();
      }
      saves.recorded(slot, before, AutosaveCoordinator.Marks.of(gameState));
    }
  }

  /**
   * Mutates every connected player's state, each under its own lock. Detached slots and Held
   * Parties do not tick.
   */
  private void forEachPlayer(Consumer<GameState> action) {
    if (!running) {
      return;
    }
    for (var slot : players.values()) {
      if (!slot.isAttached() || slot.isHeld()) {
        continue;
      }
      try {
        access(
            slot,
            gameState -> {
              applyAndRecord(slot, gameState, action);
              return Boolean.TRUE;
            });
      } catch (RuntimeException e) {
        log.error("Tick failed for player {}", slot.playerId(), e);
      }
    }
  }

  /**
   * Serializes a consistent {@link GameFrame} of a player's state to JSON under its lock, so the
   * reactive send thread never observes a half-mutated graph (which previously surfaced as {@code
   * IndexOutOfBounds} / {@code ConcurrentModificationException} during serialization). The live
   * {@link GameState} is never handed to the transport layer, only its rendered JSON. Empty when
   * the player has no live Campaign.
   */
  public Optional<String> renderState(String playerId) {
    var slot = players.get(playerId);
    return slot == null
        ? Optional.empty()
        : access(
            slot,
            state ->
                objectMapper.writeValueAsString(
                    new GameFrame(
                        state, slot.takeMeta(slot.isHeld() && state.isExpeditionActive()))));
  }

  public void handleMessage(String playerId, BuyActionDTO buyActionDTO) {
    withPurchase(
        playerId,
        gameState -> {
          if (!gameState.isUnitUnlocked(buyActionDTO.unitStats())) {
            log.warn(
                "Player[{}] tried to recruit locked unit {}. Ignoring.",
                playerId,
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
          log.info("Received buy action from session [{}]. New units: {}", playerId, newUnits);
        });
  }

  public void handleBuyVillagersAction(String playerId) {
    withPurchase(
        playerId,
        gameState -> {
          var villagersCount = gameState.buyVillager();
          log.info("Player[{}] total villagers: {}", playerId, villagersCount);
        });
  }

  public void handleSellFoodAction(String playerId) {
    withPurchase(
        playerId,
        gameState -> {
          var credits = gameState.sellFood();
          log.info("Player[{}] sold food for credits: {}", playerId, credits);
        });
  }

  public void handleSellMaterialsAction(String playerId) {
    withPurchase(
        playerId,
        gameState -> {
          var profit = gameState.sellAllMaterials();
          log.info("Player[{}] sold all materials for {} credits", playerId, profit);
        });
  }

  public void handleSellRelicsAction(String playerId) {
    withPurchase(
        playerId,
        gameState -> {
          var profit = gameState.sellAllRelics();
          log.info("Player[{}] sold all relics for {} credits", playerId, profit);
        });
  }

  public void handleAssignTradeAction(String playerId, AssignTradeActionDTO dto) {
    withPurchase(
        playerId,
        gameState -> {
          if (dto == null || dto.trade() == null || dto.delta() == null) {
            log.warn("Player[{}] sent an incomplete trade assignment. Ignoring.", playerId);
            return;
          }
          var refused = gameState.assignTrade(dto.trade(), Integer.signum(dto.delta()));
          log.info(
              "Player[{}] assign {} {} -> {}",
              playerId,
              dto.trade(),
              dto.delta(),
              refused.orElse("moved"));
        });
  }

  public void handleBuyTomeAction(String playerId, BuyTomeActionDTO dto) {
    withPlayer(
        playerId,
        gameState -> {
          if (dto == null || dto.tome() == null) {
            log.warn("Player[{}] sent an incomplete Tome purchase. Ignoring.", playerId);
            return;
          }
          var refused = gameState.buyTome(dto.tome());
          log.info("Player[{}] Tome {} -> {}", playerId, dto.tome(), refused.orElse("bought"));
        });
  }

  public void handleBuyPostsAction(String playerId, BuyPostsActionDTO dto) {
    withPlayer(
        playerId,
        gameState -> {
          if (dto == null || dto.trade() == null) {
            log.warn("Player[{}] sent an incomplete Posts purchase. Ignoring.", playerId);
            return;
          }
          var refused = gameState.buyPosts(dto.trade());
          log.info("Player[{}] Posts {} -> {}", playerId, dto.trade(), refused.orElse("bought"));
        });
  }

  public void handleUpgradeAction(String playerId, UpgradeActionDTO upgradeActionDTO) {
    withPurchase(
        playerId,
        gameState -> {
          UpgradeType type;
          try {
            type = UpgradeType.valueOf(upgradeActionDTO.upgradeId());
          } catch (IllegalArgumentException | NullPointerException e) {
            log.warn(
                "Player[{}] requested unknown upgrade {}.", playerId, upgradeActionDTO.upgradeId());
            return;
          }
          var applied = gameState.applyUpgrade(type);
          log.info(
              "Player[{}] upgrade {} -> {}", playerId, type, applied ? "purchased" : "rejected");
        });
  }

  public void handleMessage(String playerId, InvadeActionDTO invadeActionDTO) {
    log.info("Received invade action from session [{}] {}", playerId, invadeActionDTO);
    withPlayer(
        playerId,
        gameState -> {
          if (gameState.isExpeditionActive()) {
            log.warn(
                "Player[{}] tried to invade while a run is already active. Ignoring.", playerId);
            return;
          }
          if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
            log.warn(
                "Player[{}] tried to invade after the campaign ended ({}). Ignoring.",
                playerId,
                gameState.getGameOutcome());
            return;
          }
          var requestedFloor =
              invadeActionDTO.startFloor() == null ? 0 : invadeActionDTO.startFloor();
          if (!gameState.isStartFloorUnlocked(requestedFloor)) {
            log.warn(
                "Player[{}] tried to start on floor {} without an open shortcut. Ignoring.",
                playerId,
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
            log.warn("Player[{}] tried to invade with no available units. Ignoring.", playerId);
            return;
          }
          if (gameState.getTower() == null) {
            gameState.setTower(
                new Tower(
                    config.getTower().getMaxFloor(),
                    config.getBoss().copy(),
                    config.getEnemies().copy(),
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

  public void handleExtractAction(String playerId) {
    withPlayer(
        playerId,
        gameState -> {
          if (!gameState.isExpeditionActive()) {
            log.warn("Player[{}] tried to extract with no active run.", playerId);
            return;
          }
          gameState.buildRunSummary(RunSummary.EXTRACTED);
          gameState.returnLeftoverSuppliesToVillage();
          gameState.returnReinforcementsHome();
          gameState.returnPartyHome();
          gameState.bankLoot();
          gameState.deliverQuarryHeap();
          gameState.setExpeditionActive(false);
          gameState.completeExpedition();
          log.info(
              "Player[{}] extracted. Leftover food returned to village, loot banked, party returned"
                  + " home to recover on the village's food.",
              playerId);
        });
  }

  /** Lets a Held Party march on: the clock starts again from exactly where it stopped. */
  public void handleResumeAction(String playerId) {
    var slot = players.get(playerId);
    if (slot != null && slot.isHeld()) {
      slot.resume();
      log.info("Player[{}] resumed the Held Party.", playerId);
    }
  }

  public void handleMessage(String playerId, ReinforceActionDTO reinforceActionDTO) {
    log.info("Received reinforce action from session [{}] {}", playerId, reinforceActionDTO);
    var slot = players.get(playerId);
    if (slot != null && slot.isHeld()) {
      slot.addNotice(
          new Notice(
              "PARTY_HELD", "The Party has not moved. Resume before sending reinforcements."));
      return;
    }
    withPlayer(
        playerId,
        gameState -> {
          if (!gameState.isExpeditionActive()) {
            log.warn(
                "Player[{}] tried to send reinforcements with no active run. Ignoring.", playerId);
            return;
          }
          if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
            log.warn(
                "Player[{}] tried to reinforce after the campaign ended ({}). Ignoring.",
                playerId,
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

  /** Moves the requested counts to the tower. Every count is checked before any unit moves. */
  private static void sendToTower(
      Map<UnitStats, List<Unit>> units, Map<UnitStats, Integer> invadeUnits, GameState gameState) {
    invadeUnits.forEach(
        (unit, quantity) -> {
          var inReserves = units.getOrDefault(unit, List.of()).size();
          if (inReserves < quantity) {
            throw new InvalidInvasion(unit, quantity, inReserves);
          }
        });
    invadeUnits.forEach(
        (unit, quantity) -> {
          for (var left = quantity; left > 0; left--) {
            gameState.enlistRandom(unit);
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
    gameState.drainDepthSanity(tower.getCurrentFloor());
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
      floorBundle.resolve(gameState, clearedFloor, bossCleared);
      gameState.layRisenToRest(clearedFloor);
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
      gameState.rewardFloorClimbed();
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    var currentEnemies = currentTowerFloor.getEnemies();
    partyCombat.unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState);
    gameState.dropGearOnCurrentFloor(
        partyCombat.enemiesAttack(gameState, currentEnemies, unitsOnTower));
  }

  private void gatherFloorLoot(GameState gameState, int clearedFloor, double rewardMultiplier) {
    if (clearedFloor <= 0) {
      return;
    }
    var reward = config.getReward();
    gameState.gatherLoot(
        ResourceType.MATERIALS, clearedFloor * reward.getMaterialsPerFloor() * rewardMultiplier);
    gameState.gatherSupplies(reward.getSuppliesPerFloor() * rewardMultiplier);
  }

  private void applyStarvation(GameState gameState) {
    var starving =
        gameState.getUnitsOnTower().values().stream().flatMap(Collection::stream).toList();
    starving.forEach(
        unit ->
            gameState.dropGearOnCurrentFloor(
                unit.receiveAttack(config.getLoop().getStarvationDamage(), null)));
    starving.stream().filter(BaseUnit::isDead).toList().forEach(gameState::removeUnit);
  }

  private static void endRunOnWipe(GameState gameState) {
    gameState.buildRunSummary(RunSummary.WIPED);
    gameState.raiseRisenOnWipe();
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
    gameState.deliverQuarryHeap();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    gameState.winCampaign();
  }

  /** A body that reads or mutates a locked {@link GameState} and produces a result; may throw. */
  @FunctionalInterface
  private interface StateFn<T> {

    T apply(GameState state) throws Exception;
  }
}

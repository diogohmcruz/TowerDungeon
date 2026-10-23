package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
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
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.observation.StateProjection;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollCalculator;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollContext;
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
  private void forEachPlayer(BiConsumer<PlayerSlot, GameState> action) {
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
              applyAndRecord(slot, gameState, state -> action.accept(slot, state));
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

  /**
   * Applies a command to one player's Campaign under its lock and says what became of it.
   * Find-only: a player without a loaded Campaign gets {@link Reason#NO_CAMPAIGN}, so a late
   * message can never resurrect a closed one. Rule failures are results, never exceptions; an
   * unexpected failure (for example a null map where one is required) still propagates to the
   * caller.
   */
  public CommandResult execute(String playerId, Command command) {
    var slot = players.get(playerId);
    var result =
        slot == null
            ? Optional.<CommandResult>empty()
            : access(slot, gameState -> applyAndSave(slot, gameState, playerId, command));
    if (result.isEmpty()) {
      log.debug("Ignoring a {} for player {} with no live Campaign", command.kind(), playerId);
      return CommandResult.rejected(Reason.NO_CAMPAIGN, "There is no live Campaign.");
    }
    return result.get();
  }

  /** The state sequence of a player's Campaign; empty with no live Campaign. */
  public Optional<Long> stateSeq(String playerId) {
    return observe(playerId, (slot, _) -> slot.stateSeq());
  }

  /** The compact projection of a player's Campaign; empty with no live Campaign. */
  public Optional<StateProjection> project(String playerId) {
    return observe(playerId, (slot, state) -> StateProjection.brief(state, slot.stateSeq()));
  }

  /** The events after {@code afterId}; empty with no live Campaign. */
  public Optional<List<EventLog.Event>> eventsSince(String playerId, long afterId) {
    return observe(playerId, (slot, _) -> slot.events().since(afterId));
  }

  /** The capped list of Expedition Summaries, oldest first; empty with no live Campaign. */
  public Optional<List<RunSummary>> expeditionSummaries(String playerId) {
    return observe(playerId, (slot, _) -> slot.summaries().all());
  }

  private <T> Optional<T> observe(String playerId, BiFunction<PlayerSlot, GameState, T> read) {
    var slot = players.get(playerId);
    return slot == null ? Optional.empty() : access(slot, state -> read.apply(slot, state));
  }

  /** Runs the command under the autosave bookkeeping; purchases are also written as purchases. */
  private CommandResult applyAndSave(
      PlayerSlot slot, GameState gameState, String playerId, Command command) {
    var result = new CommandResult[1];
    applyAndRecord(slot, gameState, state -> result[0] = apply(slot, state, playerId, command));
    if (isPurchase(command)) {
      saves.purchased(slot);
    }
    return result[0];
  }

  /** The commands that spend the player's resources: the ones a save should not lag behind. */
  private static boolean isPurchase(Command command) {
    return switch (command) {
      case Command.Recruit _,
          Command.BuyVillagers _,
          Command.SellFood _,
          Command.SellMaterials _,
          Command.SellRelics _,
          Command.AssignTrade _,
          Command.BuyUpgrade _ ->
          true;
      case Command.StartExpedition _,
          Command.Extract _,
          Command.Resume _,
          Command.BuyTome _,
          Command.BuyRing _,
          Command.BuyPosts _,
          Command.SendReinforcements _ ->
          false;
    };
  }

  private CommandResult apply(
      PlayerSlot slot, GameState gameState, String playerId, Command command) {
    CommandResult result;
    try {
      result =
          switch (command) {
            case Command.Recruit recruit -> recruit(slot, gameState, playerId, recruit);
            case Command.BuyVillagers _ -> buyVillagers(slot, gameState, playerId);
            case Command.SellFood _ -> sellFood(slot, gameState, playerId);
            case Command.SellMaterials _ ->
                sellStock(slot, gameState, playerId, ResourceType.MATERIALS);
            case Command.SellRelics _ -> sellStock(slot, gameState, playerId, ResourceType.RELICS);
            case Command.BuyUpgrade upgrade -> buyUpgrade(slot, gameState, playerId, upgrade);
            case Command.StartExpedition start -> startExpedition(slot, gameState, playerId, start);
            case Command.Extract _ -> extract(gameState, playerId);
            case Command.AssignTrade trade -> assignTrade(slot, gameState, playerId, trade);
            case Command.BuyTome tome -> buyTome(slot, gameState, playerId, tome);
            case Command.BuyRing _ -> buyRing(slot, gameState, playerId);
            case Command.BuyPosts posts -> buyPosts(slot, gameState, playerId, posts);
            case Command.Resume _ -> resume(slot, playerId);
            case Command.SendReinforcements wave ->
                sendReinforcements(slot, gameState, playerId, wave);
          };
    } catch (InvalidInvasion err) {
      log.warn("Rejected invasion from player {}: {}", playerId, err.getMessage());
      result =
          CommandResult.rejected(
                  Reason.INSUFFICIENT_UNITS, "Not enough units in reserves to send that many.")
              .withNotice(new Notice("INVALID_INVASION", err.getMessage()));
    }
    if (result.changedState()) {
      slot.bumpSeq();
      slot.collectSummary(gameState);
    }
    return result.withStateSeq(slot.stateSeq());
  }

  private CommandResult recruit(
      PlayerSlot slot, GameState gameState, String playerId, Command.Recruit command) {
    var unitStats = command.unitStats();
    if (!gameState.isUnitUnlocked(unitStats)) {
      log.warn("Player[{}] tried to recruit locked unit {}. Ignoring.", playerId, unitStats);
      return CommandResult.rejected(Reason.NOT_UNLOCKED, "That unit type is not unlocked.");
    }
    var newUnits = new ArrayList<Unit>();
    var quantity = command.quantity();
    var cost = unitStats.getCost();
    while (gameState.getSpendableCredit() > cost && quantity > 0) {
      gameState.spendCredit(cost);
      var unit = gameState.recruit(unitStats, random);
      newUnits.add(unit);
      quantity--;
    }
    gameState.addUnits(unitStats, newUnits);
    log.info("Received buy action from session [{}]. New units: {}", playerId, newUnits);
    var requested = command.quantity();
    if (requested != null && requested <= 0) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The quantity must be positive.");
    }
    var recruited = newUnits.size();
    if (recruited == 0) {
      return CommandResult.rejected(
          Reason.INSUFFICIENT_CREDITS, "The Credits must exceed the cost of one.");
    }
    slot.emit("RECRUITED", Map.of("unit", unitStats.name(), "count", recruited));
    return recruited < requested
        ? CommandResult.partial(
            Reason.INSUFFICIENT_CREDITS, "Recruited fewer than asked: the Credits ran out.")
        : CommandResult.applied("Recruited.");
  }

  private CommandResult buyVillagers(PlayerSlot slot, GameState gameState, String playerId) {
    var before = gameState.getVillage().getVillagersCount();
    var villagersCount = gameState.buyVillager();
    log.info("Player[{}] total villagers: {}", playerId, villagersCount);
    var bought = gameState.getVillage().getVillagersCount() - before;
    if (bought <= 0) {
      return CommandResult.rejected(
          Reason.INSUFFICIENT_CREDITS, "The Vault cannot pay for another villager.");
    }
    slot.emit("VILLAGERS_BOUGHT", Map.of("count", bought));
    return CommandResult.applied("Villagers hired.");
  }

  private CommandResult sellFood(PlayerSlot slot, GameState gameState, String playerId) {
    var before = gameState.getCredit();
    var credits = gameState.sellFood();
    log.info("Player[{}] sold food for credits: {}", playerId, credits);
    if (gameState.getCredit() <= before) {
      return CommandResult.rejected(Reason.NOTHING_TO_SELL, "There is no food to sell.");
    }
    slot.emit("SOLD", Map.of("what", "FOOD"));
    return CommandResult.applied("Food sold.");
  }

  private CommandResult sellStock(
      PlayerSlot slot, GameState gameState, String playerId, ResourceType type) {
    var stock = gameState.getResources().getOrDefault(type, 0d);
    var profit =
        type == ResourceType.MATERIALS ? gameState.sellAllMaterials() : gameState.sellAllRelics();
    log.info("Player[{}] sold all {} for {} credits", playerId, type, profit);
    if (stock <= 0) {
      return CommandResult.rejected(Reason.NOTHING_TO_SELL, "There is nothing to sell.");
    }
    slot.emit("SOLD", Map.of("what", type.name()));
    return CommandResult.applied("Sold.");
  }

  private CommandResult buyUpgrade(
      PlayerSlot slot, GameState gameState, String playerId, Command.BuyUpgrade command) {
    UpgradeType type;
    try {
      type = UpgradeType.valueOf(command.upgradeId());
    } catch (IllegalArgumentException | NullPointerException e) {
      // The id is caller-supplied text: never log it.
      log.warn("Player[{}] requested an unknown upgrade.", playerId);
      return CommandResult.rejected(Reason.UNKNOWN_ID, "There is no such upgrade.");
    }
    var maxed = !type.isAvailableAt(gameState.getPurchasedUpgrades().getOrDefault(type, 0));
    var applied = gameState.applyUpgrade(type);
    log.info("Player[{}] upgrade {} -> {}", playerId, type, applied ? "purchased" : "rejected");
    if (applied) {
      slot.emit("UPGRADE_BOUGHT", Map.of("upgrade", type.name()));
      return CommandResult.applied("Upgrade bought.");
    }
    return maxed
        ? CommandResult.rejected(Reason.UPGRADE_MAXED, "That upgrade is at its highest level.")
        : CommandResult.rejected(Reason.UPGRADE_UNAFFORDABLE, "The Vault cannot pay for it.");
  }

  private CommandResult assignTrade(
      PlayerSlot slot, GameState gameState, String playerId, Command.AssignTrade command) {
    if (command.trade() == null || command.delta() == null) {
      log.warn("Player[{}] sent an incomplete trade assignment. Ignoring.", playerId);
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The assignment is incomplete.");
    }
    var refused = gameState.assignTrade(command.trade(), Integer.signum(command.delta()));
    log.info(
        "Player[{}] assign {} {} -> {}",
        playerId,
        command.trade(),
        command.delta(),
        refused.orElse("moved"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.TRADE_REFUSED, refused.get());
    }
    slot.emit(
        "TRADE_ASSIGNED",
        Map.of("trade", command.trade().name(), "delta", Integer.signum(command.delta())));
    return CommandResult.applied("The villager was moved.");
  }

  private CommandResult buyTome(
      PlayerSlot slot, GameState gameState, String playerId, Command.BuyTome command) {
    if (command.tome() == null) {
      log.warn("Player[{}] sent an incomplete Tome purchase. Ignoring.", playerId);
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The purchase is incomplete.");
    }
    var refused = gameState.buyTome(command.tome());
    log.info("Player[{}] Tome {} -> {}", playerId, command.tome(), refused.orElse("bought"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("TOME_BOUGHT", Map.of("tome", command.tome().name()));
    return CommandResult.applied("The Tome was bought.");
  }

  private CommandResult buyRing(PlayerSlot slot, GameState gameState, String playerId) {
    var refused = gameState.buyRing();
    log.info("Player[{}] Ring -> {}", playerId, refused.orElse("bought"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("RING_BOUGHT", Map.of("level", gameState.getVillage().getRingLevel()));
    return CommandResult.applied("The Ring was strengthened.");
  }

  private CommandResult buyPosts(
      PlayerSlot slot, GameState gameState, String playerId, Command.BuyPosts command) {
    if (command.trade() == null) {
      log.warn("Player[{}] sent an incomplete Posts purchase. Ignoring.", playerId);
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The purchase is incomplete.");
    }
    var refused = gameState.buyPosts(command.trade());
    log.info("Player[{}] Posts {} -> {}", playerId, command.trade(), refused.orElse("bought"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("POSTS_BOUGHT", Map.of("trade", command.trade().name()));
    return CommandResult.applied("The Posts were bought.");
  }

  private CommandResult startExpedition(
      PlayerSlot slot, GameState gameState, String playerId, Command.StartExpedition command) {
    log.info("Received invade action from session [{}] {}", playerId, command);
    if (gameState.isExpeditionActive()) {
      log.warn("Player[{}] tried to invade while a run is already active. Ignoring.", playerId);
      return CommandResult.rejected(Reason.EXPEDITION_ACTIVE, "An expedition is already out.");
    }
    if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
      log.warn(
          "Player[{}] tried to invade after the campaign ended ({}). Ignoring.",
          playerId,
          gameState.getGameOutcome());
      return CommandResult.rejected(Reason.CAMPAIGN_ENDED, "The Campaign has ended.");
    }
    var requestedFloor = command.startFloor() == null ? 0 : command.startFloor();
    if (!gameState.isStartFloorUnlocked(requestedFloor)) {
      log.warn(
          "Player[{}] tried to start on floor {} without an open shortcut. Ignoring.",
          playerId,
          requestedFloor);
      return CommandResult.rejected(Reason.START_FLOOR_LOCKED, "That floor has no open shortcut.");
    }
    var units = gameState.getUnits();
    final var unitsOnTower = gameState.getUnitsOnTower();
    var invadeUnits = command.units();
    if (invadeUnits.isEmpty()) {
      sendAllToTower(units, unitsOnTower);
    } else {
      sendToTower(units, invadeUnits, gameState);
    }
    gameState.setUnits(units);
    gameState.setUnitsOnTower(unitsOnTower);
    if (!gameState.hasUnitsOnTower()) {
      log.warn("Player[{}] tried to invade with no available units. Ignoring.", playerId);
      return CommandResult.rejected(Reason.NO_UNITS, "There are no units to send.");
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
    gameState.startRun(command.salt() == null ? 0 : command.salt());
    if (requestedFloor > 0) {
      gameState.recordFloorReached(requestedFloor);
    }
    slot.emit(
        "EXPEDITION_STARTED",
        Map.of("startFloor", requestedFloor, "party", gameState.getTowerPartySize()));
    return CommandResult.applied("The expedition set out.");
  }

  private CommandResult extract(GameState gameState, String playerId) {
    if (!gameState.isExpeditionActive()) {
      log.warn("Player[{}] tried to extract with no active run.", playerId);
      return CommandResult.rejected(Reason.NO_EXPEDITION, "No expedition is out.");
    }
    gameState.buildRunSummary(RunSummary.EXTRACTED);
    gameState.returnLeftoverSuppliesToVillage();
    gameState.returnReinforcementsHome();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.deliverQuarryHeap();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    logToll(gameState, RunSummary.EXTRACTED);
    log.info(
        "Player[{}] extracted. Leftover food returned to village, loot banked, party returned"
            + " home to recover on the village's food.",
        playerId);
    return CommandResult.applied("The party extracted.");
  }

  /** Lets a Held Party march on: the clock starts again from exactly where it stopped. */
  private CommandResult resume(PlayerSlot slot, String playerId) {
    if (!slot.isHeld()) {
      return CommandResult.rejected(Reason.NOT_HELD, "No Party is held.");
    }
    slot.resume();
    log.info("Player[{}] resumed the Held Party.", playerId);
    slot.emit("PARTY_RESUMED", Map.of());
    return CommandResult.applied("The Party marches on.");
  }

  private CommandResult sendReinforcements(
      PlayerSlot slot, GameState gameState, String playerId, Command.SendReinforcements command) {
    log.info("Received reinforce action from session [{}] {}", playerId, command);
    if (slot.isHeld()) {
      // As before this change, the player's own screen is told through the slot's notice queue.
      slot.addNotice(
          new Notice(
              "PARTY_HELD", "The Party has not moved. Resume before sending reinforcements."));
      return CommandResult.rejected(Reason.PARTY_HELD, "The Party has not moved.");
    }
    if (!gameState.isExpeditionActive()) {
      log.warn("Player[{}] tried to send reinforcements with no active run. Ignoring.", playerId);
      return CommandResult.rejected(Reason.NO_EXPEDITION, "No expedition is out.");
    }
    if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
      log.warn(
          "Player[{}] tried to reinforce after the campaign ended ({}). Ignoring.",
          playerId,
          gameState.getGameOutcome());
      return CommandResult.rejected(Reason.CAMPAIGN_ENDED, "The Campaign has ended.");
    }
    var wavesBefore = gameState.getReinforcements().size();
    gameState.sendReinforcement(command.units());
    return gameState.getReinforcements().size() > wavesBefore
        ? CommandResult.applied("A wave set out.")
        : CommandResult.rejected(Reason.NO_UNITS, "There are no units at home to send.");
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
        (slot, gameState) -> {
          var slainBefore = gameState.getEnemiesDefeated();
          var floorBefore = floorOf(gameState);
          gameState.triggerLifecycle();
          updateInvasion(gameState);
          slot.bumpSeq();
          recordTick(slot, gameState, slainBefore, floorBefore);
        });
  }

  private static int floorOf(GameState gameState) {
    return gameState.getTower() == null ? 0 : gameState.getTower().getCurrentFloor();
  }

  /** Files what one lifecycle tick did, aggregated: one event per kind, not one per kill. */
  private static void recordTick(
      PlayerSlot slot, GameState gameState, int slainBefore, int floorBefore) {
    var slain = gameState.getEnemiesDefeated() - slainBefore;
    if (slain > 0) {
      slot.emit("ENEMIES_SLAIN", Map.of("count", slain, "floor", floorBefore));
    }
    var floor = floorOf(gameState);
    if (gameState.isExpeditionActive() && floor > floorBefore) {
      slot.emit("FLOOR_REACHED", Map.of("floor", floor));
    }
    slot.collectSummary(gameState);
  }

  @Scheduled(fixedRateString = "${game.loop.invasion-tick-ms}")
  public void invasionTimer() {
    var tick = config.getLoop().getInvasionTickMs();
    forEachPlayer((_, gameState) -> gameState.passingTime(tick));
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
    gameState.drainExpeditionSupplies(gameState.getTowerPartySize());
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

  private void endRunOnWipe(GameState gameState) {
    gameState.buildRunSummary(RunSummary.WIPED);
    gameState.raiseRisenOnWipe();
    gameState.forfeitLoot();
    gameState.returnReinforcementsHome();
    gameState.setExpeditionActive(false);
    gameState.setSupplies(0d);
    gameState.setSaltedSupplies(0d);
    gameState.getUnitsOnTower().clear();
    gameState.completeExpedition();
    logToll(gameState, RunSummary.WIPED);
  }

  /**
   * Ends the campaign in triumph: the summit is conquered, so the party banks its full haul and
   * marches home victorious, and the game is marked won.
   */
  private void endRunOnVictory(GameState gameState) {
    gameState.buildRunSummary(RunSummary.VICTORY);
    gameState.returnLeftoverSuppliesToVillage();
    gameState.returnReinforcementsHome();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.deliverQuarryHeap();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    gameState.winCampaign();
    logToll(gameState, RunSummary.VICTORY);
  }

  /**
   * Writes the Toll of the Expedition that just ended as one JSON INFO line, so the par values can
   * be refitted from the logs. Read-only over the live Tally; nothing is persisted and a failure
   * here never reaches the game.
   */
  private void logToll(GameState gameState, String outcome) {
    var prestige = config.getPrestige();
    if (!prestige.isLogToll()) {
      return;
    }
    try {
      var tower = gameState.getTower();
      var guardians = tower == null ? 0 : tower.getSlainGuardians().size();
      var toll =
          TollCalculator.calculate(gameState.tollTally(guardians), TollContext.NONE, prestige);
      var line = new LinkedHashMap<String, Object>();
      line.put("event", "EXPEDITION_TOLL");
      line.put("outcome", outcome);
      line.put("foodMarks", toll.foodMarks());
      line.put("materialsMarks", toll.materialsMarks());
      line.put("creditsMarks", toll.creditsMarks());
      line.put("relicsMarks", toll.relicsMarks());
      line.put("expeditionsMarks", toll.expeditionsMarks());
      line.put("ascentMarks", toll.ascentMarks());
      line.put("guardianMarks", toll.guardianMarks());
      line.put("marks", toll.marks());
      line.put("toll", toll.toll());
      line.put("legacy", toll.legacy());
      log.info(objectMapper.writeValueAsString(line));
    } catch (RuntimeException e) {
      log.warn("Could not write the Expedition's Toll to the log.", e);
    }
  }

  /** A body that reads or mutates a locked {@link GameState} and produces a result; may throw. */
  @FunctionalInterface
  private interface StateFn<T> {

    T apply(GameState state) throws Exception;
  }
}

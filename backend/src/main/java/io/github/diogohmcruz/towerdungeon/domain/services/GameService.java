package io.github.diogohmcruz.towerdungeon.domain.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import org.springframework.beans.factory.annotation.Autowired;
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
import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
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
import io.github.diogohmcruz.towerdungeon.domain.observation.AimTally;
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.observation.ExpeditionMeter;
import io.github.diogohmcruz.towerdungeon.domain.observation.StateProjection;
import io.github.diogohmcruz.towerdungeon.domain.prestige.FallenName;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.OpenBreach;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEffects;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEntry;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollRefusal;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollRules;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SavedLoadout;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionChoice;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionReport;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionView;
import io.github.diogohmcruz.towerdungeon.domain.prestige.Toll;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollCalculator;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollContext;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollForecast;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.trial.Alias;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderLabel;
import io.github.diogohmcruz.towerdungeon.domain.trial.RaceRecord;
import io.github.diogohmcruz.towerdungeon.domain.trial.Terms;
import io.github.diogohmcruz.towerdungeon.domain.trial.TermsRegistry;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialDesk;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialResult;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialRun;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.LoadResult;
import io.github.diogohmcruz.towerdungeon.persistence.MetaCodec;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Service
public class GameService {
  private final GameProperties config;
  private final ObjectMapper objectMapper;
  private final SeedSource seeds;
  private final ArchitecturePlan architecturePlan;
  private final PartyCombat partyCombat;
  private final CampaignStore store;
  private final FloorBundle floorBundle;
  private final AutosaveCoordinator saves;
  private final MetaCodec metaCodec;

  /** A Food sale with an amount keeps the Pantry feeding the Reserves this long, at the least. */
  static final int SELL_FOOD_FLOOR_SECONDS = 60;

  private final Map<String, PlayerSlot> players = new ConcurrentHashMap<>();
  private final ManualTickDriver manualDriver = new ManualTickDriver(this, System::nanoTime);
  private volatile boolean running = true;

  @Autowired
  public GameService(
      GameProperties config,
      ObjectMapper objectMapper,
      SeedSource seeds,
      ArchitecturePlan architecturePlan,
      PartyCombat partyCombat,
      CampaignStore store,
      FloorBundle floorBundle,
      AutosaveCoordinator saves,
      MetaCodec metaCodec) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.seeds = seeds;
    this.architecturePlan = architecturePlan;
    this.partyCombat = partyCombat;
    this.store = store;
    this.floorBundle = floorBundle;
    this.saves = saves;
    this.metaCodec = metaCodec;
  }

  /** As above, with the sealed meta under the development key: for tests and local wiring. */
  public GameService(
      GameProperties config,
      ObjectMapper objectMapper,
      SeedSource seeds,
      ArchitecturePlan architecturePlan,
      PartyCombat partyCombat,
      CampaignStore store,
      FloorBundle floorBundle,
      AutosaveCoordinator saves) {
    this(
        config,
        objectMapper,
        seeds,
        architecturePlan,
        partyCombat,
        store,
        floorBundle,
        saves,
        MetaCodec.development(objectMapper));
  }

  /**
   * What a connection gets back from {@link #attach}: the id of the slot it serves (the player's
   * own, or a Trial's) and a signal for when it has been replaced.
   */
  public record Attachment(String slotId, Mono<Void> replaced) {}

  /** Who may enter a Trial; absent where Trials are not offered (a bare engine in a test). */
  private TrialDesk trials;

  @Autowired(required = false)
  public void setTrials(TrialDesk trials) {
    this.trials = trials;
  }

  /**
   * Starts a brand-new human campaign: an empty {@link GameState} with the configured Founders
   * recruited into Reserves at no cost. Its seed is drawn from the server secret; a client can
   * never choose it. Restoring a saved campaign must not go through here.
   */
  GameState newCampaign() {
    return newCampaign(config.copy(), seeds.forHumanCampaign());
  }

  /** A brand-new Campaign that owns {@code campaignConfig}: nobody else's tuning can reach it. */
  GameState newCampaign(GameProperties campaignConfig) {
    return newCampaign(campaignConfig, seeds.forHumanCampaign());
  }

  /**
   * Starts the Campaign of a human player's Generation. Its seed is derived from the player's
   * secret and the Generation, so it is the same for a reload and cannot be chosen by a client.
   * Every Generation begins with the same Founders; a Founder who is stronger in Generation 1 only
   * is an ordinary recruit of the same name and type from Generation 2. A Generation that follows a
   * Defeat starts Hollow.
   */
  GameState newCampaign(PlayerMeta meta, boolean hollow) {
    return newCampaign(meta, hollow, Map.of());
  }

  /**
   * As {@link #newCampaign(PlayerMeta, boolean)}, under the Roll the meta holds: its Inheritances
   * shape the Campaign's copy of the config and are given to it once built, by the Succession's
   * {@code choices}.
   */
  GameState newCampaign(PlayerMeta meta, boolean hollow, Map<String, String> choices) {
    var campaignConfig = config.copy().withTowerMemory(meta.generation());
    var effects = RollEffects.of(meta, campaignConfig);
    effects.shape(campaignConfig);
    var campaign =
        newCampaign(campaignConfig, new RandomSource(meta.seed()), meta.generation(), hollow);
    campaign.setDoorOpen(meta.opensTheDoor());
    effects.start(campaign, choices);
    campaign.setSavedLoadouts(meta.savedLoadouts());
    return campaign;
  }

  /**
   * Starts a brand-new Sandbox campaign from a chosen seed, for the Sandbox driver. Only a Sandbox
   * may do this: the seed of a human campaign is never taken from a client. The same seed and the
   * same commands always make the same campaign.
   */
  public GameState newSandboxCampaign(long seed) {
    return newCampaign(config.copy(), new RandomSource(seed));
  }

  /**
   * Starts a Trial: the start kit every Contender shares (the configured Credits, Villagers, Food
   * and Founders, no Roll, Tower Memory 0), on a Tower the Terms seed. The four Founders' strengths
   * come from the Terms too, so every Contender starts with the identical four. Not a Campaign: it
   * is sealed in a file of its own and never offered for export.
   */
  GameState newTrialCampaign(Terms terms, int attempt) {
    var trialConfig = config.copy();
    var state = new GameState(trialConfig, seeds.forTrial(terms.seed()));
    var founderDraws = state.getRandom().founders();
    for (var founder : trialConfig.getStart().getFounders()) {
      state.addUnits(founder.getUnitType(), List.of(state.recruitFounder(founder, founderDraws)));
    }
    state.setTrial(
        new TrialRun(
            terms.raceId(),
            attempt,
            terms.finishFloor(),
            TermsRegistry.ticksIn(terms.limit(), trialConfig.getLoop().getLifecycleMs())));
    return state;
  }

  private GameState newCampaign(GameProperties campaignConfig, RandomSource random) {
    return newCampaign(campaignConfig, random, 1, false);
  }

  private GameState newCampaign(
      GameProperties campaignConfig, RandomSource random, int generation, boolean hollow) {
    var gameState = new GameState(campaignConfig, random);
    for (var founder : campaignConfig.getStart().getFounders()) {
      var effective =
          generation > 1 && founder.isFirstGenerationOnly()
              ? new GameProperties.Founder(founder.getName(), founder.getUnitType(), 1.0, false)
              : founder;
      gameState.addUnits(effective.getUnitType(), List.of(gameState.recruitFounder(effective)));
    }
    if (hollow) {
      var start = campaignConfig.getPrestige().getDefeatStart();
      gameState.startHollow(start.getVillagersLost(), start.getWritCredits());
    }
    return gameState;
  }

  /** The driver that advances Sandbox Campaigns, and only them. */
  ManualTickDriver manualDriver() {
    return manualDriver;
  }

  /**
   * Opens a Sandbox Campaign under a synthetic id: a manual clock, its own copy of the
   * configuration, and no connection, so nothing but the manual driver advances it and the autosave
   * coordinator never writes it. Its simulated time may not pass {@code simulatedLimit}. It runs on
   * {@code seed}, or on a fresh one when that is null; the seed in use is returned.
   */
  long openSandbox(
      String sandboxId, GameProperties campaignConfig, long simulatedLimit, Long seed) {
    var slot = new PlayerSlot(sandboxId, ClockMode.MANUAL);
    var random = seed == null ? seeds.forHumanCampaign() : new RandomSource(seed);
    var state = newCampaign(campaignConfig, random);
    var used = state.getRandom().snapshot().seed();
    // The Sandbox's own meta, from its seed, so a Generation begun in it is as repeatable as the
    // first. Nothing of it is ever written.
    slot.meta(PlayerMeta.first("sandbox-" + used));
    slot.simulatedLimit(simulatedLimit);
    slot.loaded(state, false, null, CampaignSchema.CURRENT);
    slot.loading(Mono.just(state));
    players.put(sandboxId, slot);
    return used;
  }

  /** Forgets a Sandbox Campaign. A human's Campaign is never closed through here. */
  boolean closeSandbox(String sandboxId) {
    var closed = new boolean[1];
    players.computeIfPresent(
        sandboxId,
        (_, slot) -> {
          closed[0] = slot.isManual();
          return closed[0] ? null : slot;
        });
    return closed[0];
  }

  /** Sets who watches a Sandbox's ticks (null for nobody). False for anything but a Sandbox. */
  boolean watchSandbox(String sandboxId, TickObserver observer) {
    var slot = players.get(sandboxId);
    if (slot == null || !slot.isManual()) {
      return false;
    }
    slot.observer(observer);
    return true;
  }

  /** The Campaign slot of an id, if any; manual or real-time. */
  PlayerSlot slotOf(String playerId) {
    return players.get(playerId);
  }

  /** The Tower's plan for the Generation the meta says is being played. */
  private ArchitecturePlan planFor(PlayerMeta meta) {
    return ArchitecturePlan.generate(
        meta.seed(),
        config.getTower().getMaxFloor(),
        config.getBoss().getInterval(),
        config.getEnemies().getSurgeInterval());
  }

  private void adopt(PlayerSlot slot, PlayerMeta meta) {
    slot.meta(meta);
    slot.plan(planFor(meta));
  }

  /**
   * The meta of a player with nothing stored. Its secret is drawn from the {@link SeedSource}
   * (twice, for 128 bits): from the server's own secret in production, fixed for tests and replays.
   */
  private PlayerMeta freshMeta() {
    return withSecret(PlayerMeta.first(""));
  }

  private PlayerMeta withSecret(PlayerMeta meta) {
    if (meta.hasSecret()) {
      return meta;
    }
    var high = seeds.forHumanCampaign().snapshot().seed();
    var low = seeds.forHumanCampaign().snapshot().seed();
    return meta.withSecret("%016x%016x".formatted(high, low));
  }

  /** The player's meta as the record's payload holds it; fresh when there is none. */
  private PlayerMeta metaOf(byte[] payload) {
    if (payload == null || payload.length == 0) {
      return freshMeta();
    }
    JsonNode root = objectMapper.readTree(payload);
    return withSecret(metaCodec.fromNode(root.path("meta")));
  }

  /** The live state of a player, if it has a loaded slot. Test hook. */
  public GameState playerState(String playerId) {
    var slot = players.get(playerId);
    return slot == null ? null : slot.state();
  }

  /** What the player keeps across Generations, if they have a loaded slot. Test hook. */
  public PlayerMeta playerMeta(String playerId) {
    var slot = players.get(playerId);
    return slot == null ? null : slot.meta();
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
          // Back in the real Campaign: any Trial of this Key lets go of its connection.
          trialSlotsOf(playerId, null).forEach(PlayerSlot::displace);
          return slot.loading()
              .thenReturn(new Attachment(playerId, connection.replaced().asMono()));
        });
  }

  /**
   * Attaches a connection to the Key's Trial in a race: its current attempt, or the next one when
   * {@code next} and the current is over. Entering detaches the real Campaign (frozen, as ADR 0001)
   * and the Key's other Trials; connecting to the real Campaign again reverses it. Fails with
   * {@link TrialDesk.Refused} when the Key may not enter.
   */
  public Mono<Attachment> attachTrial(String playerId, String raceId, boolean next, String wsId) {
    return attachTrial(playerId, raceId, next, wsId, null, ContenderLabel.HUMAN);
  }

  /**
   * As {@link #attachTrial(String, String, boolean, String)}, under an alias (cleaned here, so a
   * hostile one never reaches a table) and the label the Contender declares for itself.
   */
  public Mono<Attachment> attachTrial(
      String playerId,
      String raceId,
      boolean next,
      String wsId,
      String alias,
      ContenderLabel label) {
    var shownAs = Alias.clean(alias);
    return Mono.defer(
            () -> {
              if (trials == null) {
                throw new TrialDesk.Refused(TrialDesk.Refusal.UNKNOWN_RACE);
              }
              var admission =
                  trials.admit(
                      playerId,
                      raceId,
                      next,
                      Instant.now(),
                      terms -> connectedTrials(terms.raceId(), playerId));
              var real = players.get(playerId);
              if (real != null) {
                real.displace();
              }
              trialSlotsOf(playerId, admission.trialId()).forEach(PlayerSlot::displace);
              var connection = new PlayerSlot.Connection(wsId);
              var slot =
                  players.compute(
                      admission.trialId(),
                      (id, existing) -> {
                        var current =
                            existing != null
                                ? existing
                                : newTrialSlot(id, playerId, admission, shownAs, label);
                        current.attach(connection);
                        return current;
                      });
              return slot.loading()
                  .thenReturn(new Attachment(admission.trialId(), connection.replaced().asMono()));
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  /** The Trial slots of a Key, other than {@code except}. */
  private List<PlayerSlot> trialSlotsOf(String ownerId, String except) {
    return players.values().stream()
        .filter(
            slot ->
                slot.isTrial()
                    && slot.trial().ownerId().equals(ownerId)
                    && !slot.playerId().equals(except))
        .toList();
  }

  /** Trials of a race that other Keys have connected. */
  private int connectedTrials(String raceId, String exceptOwner) {
    return (int)
        players.values().stream()
            .filter(
                slot ->
                    slot.isTrial()
                        && slot.trial().raceId().equals(raceId)
                        && !slot.trial().ownerId().equals(exceptOwner)
                        && slot.isAttached())
            .count();
  }

  private PlayerSlot newTrialSlot(
      String trialId,
      String ownerId,
      TrialDesk.Admission admission,
      String alias,
      ContenderLabel label) {
    var slot = new PlayerSlot(trialId);
    slot.trial(
        new PlayerSlot.TrialLink(
            ownerId, admission.terms().raceId(), admission.attempt(), alias, label));
    // Every Contender climbs the Tower plan the Terms' seed makes; a Generation's meta is not
    // theirs.
    slot.plan(
        ArchitecturePlan.generate(
            admission.terms().seed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval()));
    slot.loading(
        store
            .load(trialId)
            .map(
                result ->
                    restoreCampaign(
                        slot,
                        result,
                        () -> newTrialCampaign(admission.terms(), admission.attempt())))
            .switchIfEmpty(
                Mono.fromSupplier(
                    () -> {
                      var fresh = newTrialCampaign(admission.terms(), admission.attempt());
                      slot.loaded(fresh, false, null, CampaignSchema.CURRENT);
                      saves.opened(slot, CampaignStore.INITIAL_SEQ);
                      return fresh;
                    }))
            .doOnError(
                e -> {
                  log.error("Failed to load a Trial of a player", e);
                  players.remove(trialId, slot);
                })
            .cache());
    return slot;
  }

  /** Detaches the connection if it is still the attached one; the slot lingers before eviction. */
  public void detach(String playerId, String wsId) {
    detachSlot(playerId, wsId);
    trialSlotsOf(playerId, null).forEach(trial -> detachSlot(trial.playerId(), wsId));
  }

  private void detachSlot(String playerId, String wsId) {
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
                      if (slot.isManual() || !slot.hasLingeredFor(linger, nowNanos)) {
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
        .map(
            result ->
                restoreCampaign(
                    slot,
                    result,
                    () -> {
                      var meta = freshMeta();
                      adopt(slot, meta);
                      return newCampaign(meta, false);
                    }))
        .switchIfEmpty(
            Mono.fromSupplier(
                () -> {
                  var meta = freshMeta();
                  adopt(slot, meta);
                  var fresh = newCampaign(meta, false);
                  slot.loaded(fresh, false, null, CampaignSchema.CURRENT);
                  saves.opened(slot, CampaignStore.INITIAL_SEQ);
                  return fresh;
                }));
  }

  private GameState restoreCampaign(PlayerSlot slot, LoadResult result, Supplier<GameState> fresh) {
    if (result.schema() > CampaignSchema.CURRENT) {
      throw new SchemaNewerException(result.schema());
    }
    result.notices().forEach(text -> slot.addNotice(new Notice("SAVE_MIGRATED", text)));
    if (result.campaignLost()) {
      // The record could not be read any more; a copy of it is kept. Start over where it left off.
      var started = fresh.get();
      slot.loaded(started, false, null, CampaignSchema.CURRENT);
      saves.opened(slot, result.seq());
      return started;
    }
    var snapshot = objectMapper.readValue(result.payload(), GameSnapshot.class);
    GameState state;
    if (slot.isTrial()) {
      state = restoreTrial(slot, snapshot);
    } else {
      var meta = metaOf(result.payload());
      adopt(slot, meta);
      var campaignConfig = config.copy().withTowerMemory(meta.generation());
      var effects = RollEffects.of(meta, campaignConfig);
      effects.shape(campaignConfig);
      state =
          GameState.restore(snapshot, campaignConfig, new RandomSource(meta.seed()), slot.plan());
      state.setDoorOpen(meta.opensTheDoor());
      effects.carry(state);
      state.setSavedLoadouts(meta.savedLoadouts());
    }
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

  /** A Trial as saved: on its Terms' Tower plan, with no meta and no Tower Memory. */
  private GameState restoreTrial(PlayerSlot slot, GameSnapshot snapshot) {
    var state = GameState.restore(snapshot, config.copy(), seeds.forHumanCampaign(), slot.plan());
    if (state.getTrial() == null) {
      throw new IllegalStateException("A Trial's file holds no Trial.");
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
    var out = gameState.isExpeditionActive();
    try {
      action.accept(gameState);
    } finally {
      if (out && !gameState.isExpeditionActive()) {
        // The Expedition ended in this step: nothing is left to hold.
        slot.resume();
      }
      saves.recorded(slot, before, AutosaveCoordinator.Marks.of(gameState));
    }
  }

  /**
   * Mutates every connected player's state, each under its own lock. Detached slots, Held Parties
   * and Sandbox Campaigns (manual clock) do not tick.
   */
  private void forEachPlayer(BiConsumer<PlayerSlot, GameState> action) {
    if (!running) {
      return;
    }
    for (var slot : players.values()) {
      if (!TickDriver.REALTIME.drives(slot) || !slot.isAttached() || slot.isHeld()) {
        continue;
      }
      try {
        access(
            slot,
            gameState -> {
              if (gameState.getPhase() == CampaignPhase.OFFER) {
                return Boolean.TRUE;
              }
              if (gameState.getGameOutcome() == GameOutcome.PLAYING) {
                applyAndRecord(slot, gameState, state -> action.accept(slot, state));
              }
              settleEnded(slot, gameState);
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
                        state,
                        slot.takeMeta(slot.isHeld(), SuccessionView.of(slot.meta(), state)),
                        tollForecast(slot, state),
                        raceRecords(slot),
                        pillarMarks(slot, state))));
  }

  /** The lines of the Races a player has run, for the Chronicle screen; none in a Trial. */
  private static List<RaceRecord> raceRecords(PlayerSlot slot) {
    var meta = slot.meta();
    return meta == null || slot.isTrial() ? List.of() : meta.raceRecords();
  }

  /** The Finish Floors to mark on a pillar: only Floors the player has discovered. */
  private static List<Integer> pillarMarks(PlayerSlot slot, GameState state) {
    return raceRecords(slot).stream()
        .map(RaceRecord::finishFloor)
        .filter(floor -> floor <= state.getHighestFloor())
        .distinct()
        .sorted()
        .toList();
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

  /** What a read sees besides the state itself. */
  public record Meta(long seq, boolean held, ClockMode clock) {}

  /** A read of one Campaign, run under its lock. */
  @FunctionalInterface
  public interface View<T> {
    T read(GameState state, Meta meta);
  }

  /**
   * Runs a read of a Campaign under its lock, with its sequence number, whether its Party is Held
   * and which clock drives it; empty with no live Campaign. The state must not leave the callback.
   */
  public <T> Optional<T> view(String playerId, View<T> read) {
    return observe(
        playerId,
        (slot, state) -> read.read(state, new Meta(slot.stateSeq(), slot.isHeld(), slot.clock())));
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
          Command.SellSalt _,
          Command.SendVoyage _,
          Command.AssignTrade _,
          Command.GiveRemedy _,
          Command.ReattachPart _,
          Command.BuyUpgrade _ ->
          true;
      case Command.StartExpedition _,
          Command.Extract _,
          Command.Resume _,
          Command.Hold _,
          Command.BuyTome _,
          Command.BuyRing _,
          Command.BuyPosts _,
          Command.BuildPort _,
          Command.SendReinforcements _,
          Command.Exodus _,
          Command.MusterRoll _,
          Command.BeginGeneration _ ->
          false;
      case Command.RollBuy _,
          Command.RollRefund _,
          Command.Choose _,
          Command.SaveLoadout _,
          Command.ClearLoadout _ ->
          true;
    };
  }

  private CommandResult apply(
      PlayerSlot slot, GameState gameState, String playerId, Command command) {
    CommandResult result;
    if (gameState.isTrialClosed()) {
      return CommandResult.rejected(Reason.TRIAL_CLOSED, "The Trial is over.")
          .withStateSeq(slot.stateSeq());
    }
    if (slot.isTrial() && (isSuccessionCommand(command) || command instanceof Command.Exodus)) {
      // A Trial has no Generations: it ends on its Finish Floor, its clock or a Defeat.
      return CommandResult.rejected(Reason.NOT_AVAILABLE, "A Trial has no succession.")
          .withStateSeq(slot.stateSeq());
    }
    try {
      if (gameState.getPhase() == CampaignPhase.OFFER && !isSuccessionCommand(command)) {
        return CommandResult.rejected(Reason.CAMPAIGN_ENDED, "The Campaign has ended.")
            .withStateSeq(slot.stateSeq());
      }
      result =
          switch (command) {
            case Command.Recruit recruit -> recruit(slot, gameState, playerId, recruit);
            case Command.BuyVillagers villagers ->
                buyVillagers(slot, gameState, playerId, villagers);
            case Command.SellFood sale -> sellFood(slot, gameState, playerId, sale);
            case Command.SellMaterials sale ->
                sellStock(slot, gameState, playerId, ResourceType.MATERIALS, sale.amount());
            case Command.SellRelics sale ->
                sellStock(slot, gameState, playerId, ResourceType.RELICS, sale.amount());
            case Command.SellSalt _ -> sellSalt(slot, gameState, playerId);
            case Command.BuyUpgrade upgrade -> buyUpgrade(slot, gameState, playerId, upgrade);
            case Command.StartExpedition start -> startExpedition(slot, gameState, playerId, start);
            case Command.Extract _ -> extract(slot, gameState, playerId);
            case Command.AssignTrade trade -> assignTrade(slot, gameState, playerId, trade);
            case Command.BuyTome tome -> buyTome(slot, gameState, playerId, tome);
            case Command.BuyRing _ -> buyRing(slot, gameState, playerId);
            case Command.GiveRemedy remedy -> giveRemedy(slot, gameState, playerId, remedy);
            case Command.ReattachPart reattach -> reattachPart(slot, gameState, playerId, reattach);
            case Command.BuyPosts posts -> buyPosts(slot, gameState, playerId, posts);
            case Command.BuildPort _ -> buildPort(slot, gameState, playerId);
            case Command.SendVoyage voyage -> sendVoyage(slot, gameState, playerId, voyage);
            case Command.Hold _ -> hold(slot, playerId);
            case Command.Resume _ -> resume(slot, playerId);
            case Command.SendReinforcements wave ->
                sendReinforcements(slot, gameState, playerId, wave);
            case Command.Exodus _ -> exodus(slot, gameState, playerId);
            case Command.BeginGeneration _ -> beginGeneration(slot, gameState, playerId);
            case Command.RollBuy buy -> rollBuy(slot, gameState, buy.inheritanceId());
            case Command.RollRefund refund -> rollRefund(slot, gameState, refund.inheritanceId());
            case Command.Choose choose -> choose(slot, gameState, choose.choiceId());
            case Command.MusterRoll _ -> musterRoll(slot, gameState, playerId);
            case Command.SaveLoadout save -> saveLoadout(slot, gameState, save);
            case Command.ClearLoadout clear -> clearLoadout(slot, gameState, clear.slot());
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
      var watcher = slot.observer();
      if (watcher != null) {
        watcher.afterCommand(gameState);
      }
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
    if (command.quantity() == null || command.quantity() <= 0) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The quantity must be positive.");
    }
    var newUnits = new ArrayList<Unit>();
    var quantity = command.quantity();
    var cost = unitStats.getCost();
    while (gameState.getSpendableCredit() > cost && quantity > 0) {
      gameState.spendCredit(cost);
      var unit = gameState.recruit(unitStats);
      newUnits.add(unit);
      quantity--;
    }
    gameState.addUnits(unitStats, newUnits);
    log.info("Received buy action from session [{}]. New units: {}", playerId, newUnits);
    var requested = command.quantity();
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

  private CommandResult buyVillagers(
      PlayerSlot slot, GameState gameState, String playerId, Command.BuyVillagers command) {
    if (command.count() != null && command.count() <= 0) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The count must be positive.");
    }
    var before = gameState.getVillage().getVillagersCount();
    var villagersCount =
        gameState.buyVillagers(command.count() == null ? Integer.MAX_VALUE : command.count());
    log.info("Player[{}] total villagers: {}", playerId, villagersCount);
    var bought = gameState.getVillage().getVillagersCount() - before;
    if (bought <= 0) {
      return CommandResult.rejected(
          Reason.INSUFFICIENT_CREDITS, "The Vault cannot pay for another villager.");
    }
    slot.emit("VILLAGERS_BOUGHT", Map.of("count", bought));
    return command.count() != null && bought < command.count()
        ? CommandResult.partial(
            Reason.INSUFFICIENT_CREDITS, "Hired fewer than asked: the Vault ran out.")
        : CommandResult.applied("Villagers hired.");
  }

  /**
   * Sells Food. With an amount the sale is checked first: it must not leave the Pantry under what
   * the Reserves' Upkeep would eat in {@link #SELL_FOOD_FLOOR_SECONDS} seconds beyond what the
   * Fields grow, unless forced. Nothing is sold when it is refused.
   */
  private CommandResult sellFood(
      PlayerSlot slot, GameState gameState, String playerId, Command.SellFood command) {
    if (command.amount() != null && command.amount() <= 0) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The amount must be positive.");
    }
    var most = command.amount() == null ? Integer.MAX_VALUE : command.amount();
    var village = gameState.getVillage();
    var pantry = village.getFood();
    var sellable = 0;
    while (sellable < most && pantry > 1) {
      pantry -= 1;
      sellable++;
    }
    if (sellable == 0) {
      return CommandResult.rejected(Reason.NOTHING_TO_SELL, "There is no food to sell.");
    }
    if (command.amount() != null && !command.force()) {
      var gap = village.upkeepFor(gameState.getHomeUnitCount()) - village.getProductionRate();
      if (gap > 0 && pantry < gap * SELL_FOOD_FLOOR_SECONDS) {
        return CommandResult.rejected(
            Reason.WOULD_STARVE, "The Pantry would not feed the Reserves after that sale.");
      }
    }
    var credits = gameState.sellFood(sellable);
    log.info("Player[{}] sold food for credits: {}", playerId, credits);
    slot.emit("SOLD", Map.of("what", "FOOD"));
    return command.amount() != null && sellable < command.amount()
        ? CommandResult.partial(
            Reason.NOTHING_TO_SELL, "Sold fewer than asked: the Pantry ran down to its keep-back.")
        : CommandResult.applied("Food sold.");
  }

  private CommandResult sellStock(
      PlayerSlot slot, GameState gameState, String playerId, ResourceType type, Double amount) {
    if (amount != null && !(amount > 0)) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The amount must be positive.");
    }
    var stock = gameState.getResources().getOrDefault(type, 0d);
    if (stock <= 0) {
      return CommandResult.rejected(Reason.NOTHING_TO_SELL, "There is nothing to sell.");
    }
    var most = amount == null ? Double.POSITIVE_INFINITY : amount;
    var profit =
        type == ResourceType.MATERIALS ? gameState.sellMaterials(most) : gameState.sellRelics(most);
    log.info("Player[{}] sold {} for {} credits", playerId, type, profit);
    slot.emit("SOLD", Map.of("what", type.name()));
    return amount != null && stock < amount
        ? CommandResult.partial(Reason.NOTHING_TO_SELL, "Sold fewer than asked: the stock ran out.")
        : CommandResult.applied("Sold.");
  }

  private CommandResult sellSalt(PlayerSlot slot, GameState gameState, String playerId) {
    var profit = gameState.sellAllSalt();
    log.info("Player[{}] sold the Salt for {} credits", playerId, profit);
    if (profit <= 0) {
      return CommandResult.rejected(Reason.NOTHING_TO_SELL, "There is no Salt to sell.");
    }
    slot.emit("SOLD", Map.of("what", "SALT"));
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

  private CommandResult giveRemedy(
      PlayerSlot slot, GameState gameState, String playerId, Command.GiveRemedy command) {
    if (command.unitId() == null) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The Remedy is incomplete.");
    }
    var refused = gameState.giveRemedy(command.unitId());
    log.info("Player[{}] Remedy -> {}", playerId, refused.isPresent() ? "refused" : "given");
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("REMEDY_GIVEN", Map.of("unit", command.unitId()));
    return CommandResult.applied("The Remedy was given.");
  }

  private CommandResult reattachPart(
      PlayerSlot slot, GameState gameState, String playerId, Command.ReattachPart command) {
    if (command.unitId() == null || command.part() == null) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The Spore Dose is incomplete.");
    }
    var refused = gameState.reattachPart(command.unitId(), command.part());
    log.info("Player[{}] reattach -> {}", playerId, refused.isPresent() ? "refused" : "given");
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("PART_REATTACHED", Map.of("unit", command.unitId(), "part", command.part().name()));
    return CommandResult.applied("The part was reattached.");
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

  private CommandResult buildPort(PlayerSlot slot, GameState gameState, String playerId) {
    var refused = gameState.buildPort();
    log.info("Player[{}] Port -> {}", playerId, refused.orElse("built"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("PORT_BUILT", Map.of());
    return CommandResult.applied("The Port was finished.");
  }

  private CommandResult sendVoyage(
      PlayerSlot slot, GameState gameState, String playerId, Command.SendVoyage command) {
    if (command.relics() == null || command.goods() == null) {
      return CommandResult.rejected(
          Reason.INVALID_ARGUMENT, "Say how many Relics, and Salt or Spores to bring back.");
    }
    var refused = gameState.sendVoyage(command.relics(), command.goods());
    log.info("Player[{}] Voyage {} -> {}", playerId, command, refused.orElse("sailed"));
    if (refused.isPresent()) {
      return CommandResult.rejected(Reason.PURCHASE_REFUSED, refused.get());
    }
    slot.emit("VOYAGE_SENT", Map.of("relics", command.relics(), "goods", command.goods().name()));
    return CommandResult.applied("The Boat sailed.");
  }

  private CommandResult startExpedition(
      PlayerSlot slot, GameState gameState, String playerId, Command.StartExpedition command) {
    log.info("Received invade action from session [{}] {}", playerId, command);
    if (command.units() == null) {
      return CommandResult.rejected(
          Reason.INVALID_ARGUMENT, "Say who goes: a count for each type, or an empty selection.");
    }
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
    if (command.overreach() && gameState.getOverreachRule() == null) {
      return CommandResult.rejected(Reason.NOT_UNLOCKED, "The Roll holds no Overreach.");
    }
    var requestedFloor = command.startFloor() == null ? 0 : command.startFloor();
    if (!gameState.isStartFloorUnlocked(requestedFloor)) {
      log.warn(
          "Player[{}] tried to start on floor {} without an open shortcut. Ignoring.",
          playerId,
          requestedFloor);
      return CommandResult.rejected(Reason.START_FLOOR_LOCKED, "That floor has no open shortcut.");
    }
    var tooLarge =
        gameState.startFloorRefusal(requestedFloor, requestedPartySize(gameState, command.units()));
    if (tooLarge.isPresent()) {
      log.warn(
          "Player[{}] tried to take a Party over the limit up floor {}.", playerId, requestedFloor);
      return CommandResult.rejected(Reason.PARTY_TOO_LARGE, tooLarge.get());
    }
    if (command.panacea()) {
      var cannot = gameState.panaceaBlocked();
      if (cannot.isPresent()) {
        return CommandResult.rejected(Reason.PURCHASE_REFUSED, cannot.get());
      }
    }
    var units = gameState.getUnits();
    final var unitsOnTower = gameState.getUnitsOnTower();
    var invadeUnits = command.units();
    var saltPack = command.saltPack() == null ? 0 : command.saltPack();
    if (saltPack > 0 && !bearerGoes(gameState, invadeUnits, command.saltBearer())) {
      return CommandResult.rejected(
          Reason.INVALID_ARGUMENT, "The Salt-bearer must be a Unit type that sets out.");
    }
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
              gameState.getConfig().getTower().getMaxFloor(),
              gameState.getConfig().getBoss().copy(),
              gameState.getConfig().getEnemies().copy(),
              gameState.getRandom(),
              slot.plan() == null ? architecturePlan : slot.plan()));
    }
    if (gameState.getTrial() != null) {
      gameState.getTower().setFinishFloor(gameState.getTrial().getFinishFloor());
    }
    gameState.getTower().startNewRunAt(requestedFloor);
    gameState.setRunStartFloor(requestedFloor > 0 ? requestedFloor : 0);
    gameState.startRun(
        command.salt() == null ? 0 : command.salt(),
        saltPack,
        command.saltBearer(),
        command.panacea(),
        command.overreach());
    if (requestedFloor > 0) {
      gameState.recordFloorReached(requestedFloor);
      gameState.noteStartFloor(requestedFloor);
    }
    slot.lastMuster(command);
    gameState.setLastMuster(
        new GameState.MusterRollOffer(
            true,
            requestedFloor,
            command.units() == null
                ? 0
                : command.units().values().stream().mapToInt(Integer::intValue).sum(),
            command.salt() == null ? 0 : command.salt(),
            saltPack,
            command.saltBearer() == null ? null : command.saltBearer().name(),
            command.panacea(),
            command.overreach()));
    slot.emit(
        "EXPEDITION_STARTED",
        Map.of("startFloor", requestedFloor, "party", gameState.getTowerPartySize()));
    return CommandResult.applied("The expedition set out.");
  }

  private CommandResult extract(PlayerSlot slot, GameState gameState, String playerId) {
    if (!gameState.isExpeditionActive()) {
      log.warn("Player[{}] tried to extract with no active run.", playerId);
      return CommandResult.rejected(Reason.NO_EXPEDITION, "No expedition is out.");
    }
    extractParty(gameState);
    slot.resume();
    log.info(
        "Player[{}] extracted. Leftover food returned to village, loot banked, party returned"
            + " home to recover on the village's food.",
        playerId);
    return CommandResult.applied("The party extracted.");
  }

  /** Brings the Party home with its haul, as an Extraction does. */
  private void extractParty(GameState gameState) {
    gameState.buildRunSummary(RunSummary.EXTRACTED);
    gameState.returnLeftoverSuppliesToVillage();
    gameState.returnReinforcementsHome();
    gameState.returnPartyHome();
    gameState.bankLoot();
    gameState.deliverQuarryHeap();
    gameState.setExpeditionActive(false);
    gameState.completeExpedition();
    logToll(gameState, RunSummary.EXTRACTED);
  }

  /** Holds the Party and the Village clock; holding what is already held changes nothing. */
  private CommandResult hold(PlayerSlot slot, String playerId) {
    if (slot.isHeld()) {
      return CommandResult.applied("Already held.");
    }
    slot.hold();
    log.info("Player[{}] held the clock.", playerId);
    slot.emit("CLOCK_HELD", Map.of());
    return CommandResult.applied("Time stands still.");
  }

  /** Lets a Held Party march on: the clock starts again from exactly where it stopped. */
  private CommandResult resume(PlayerSlot slot, String playerId) {
    if (!slot.isHeld()) {
      return CommandResult.applied("Nothing is held.");
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
    var atHome = new EnumMap<UnitStats, Integer>(UnitStats.class);
    gameState.getUnits().forEach((stats, list) -> atHome.put(stats, list.size()));
    gameState.sendReinforcement(command.units());
    if (gameState.getReinforcements().size() <= wavesBefore) {
      return CommandResult.rejected(Reason.NO_UNITS, "There are no units at home to send.");
    }
    var clamped =
        command.units() != null
            && command.units().entrySet().stream()
                .anyMatch(
                    e -> e.getValue() != null && e.getValue() > atHome.getOrDefault(e.getKey(), 0));
    return clamped
        ? CommandResult.partial(
            Reason.INSUFFICIENT_UNITS, "A wave set out with fewer than asked: not enough at home.")
        : CommandResult.applied("A wave set out.");
  }

  /**
   * How many Units the command would send: every Unit at home and on the Tower for an empty
   * selection, else the sum asked for, as far as there are Units to send.
   */
  private static int requestedPartySize(GameState gameState, Map<UnitStats, Integer> asked) {
    var available = countOf(gameState.getUnits()) + countOf(gameState.getUnitsOnTower());
    if (asked == null || asked.isEmpty()) {
      return available;
    }
    var wanted =
        asked.values().stream()
            .filter(Objects::nonNull)
            .mapToInt(count -> Math.max(0, count))
            .sum();
    return Math.min(wanted, available);
  }

  private static int countOf(Map<UnitStats, List<Unit>> units) {
    return units.values().stream().mapToInt(List::size).sum();
  }

  /** Whether a Unit of the bearer's type is among those about to set out. */
  private static boolean bearerGoes(
      GameState gameState, Map<UnitStats, Integer> invadeUnits, UnitStats bearer) {
    if (bearer == null) {
      return false;
    }
    return invadeUnits.isEmpty()
        ? !gameState.getUnits().getOrDefault(bearer, List.of()).isEmpty()
        : invadeUnits.getOrDefault(bearer, 0) > 0;
  }

  /**
   * A copy in {@code UnitStats} order. Enum hash codes differ between JVM runs, so a hashed map's
   * iteration order would feed the random picks differently from one launch to the next.
   */
  private static Map<UnitStats, List<Unit>> inOrder(Map<UnitStats, List<Unit>> units) {
    var copy = new EnumMap<UnitStats, List<Unit>>(UnitStats.class);
    copy.putAll(units);
    return copy;
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

  private static Map<UnitStats, List<Unit>> mergeAllArmy(
      Map<UnitStats, List<Unit>> units, Map<UnitStats, List<Unit>> unitsOnTower) {
    var unitsTemp = inOrder(units);
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

  private static boolean isSuccessionCommand(Command command) {
    return switch (command) {
      case Command.RollBuy _, Command.RollRefund _, Command.Choose _, Command.BeginGeneration _ ->
          true;
      default -> false;
    };
  }

  /** The Roll opens only while the Campaign waits in OFFER; at any other time it is sealed. */
  private static CommandResult rollSealed() {
    return CommandResult.rejected(Reason.NOT_ENDED, "The Campaign has not ended.");
  }

  private CommandResult rollBuy(PlayerSlot slot, GameState gameState, String id) {
    if (gameState.getPhase() != CampaignPhase.OFFER) {
      return rollSealed();
    }
    var type = InheritanceType.find(id);
    if (type.isEmpty()) {
      return CommandResult.rejected(Reason.UNKNOWN_ID, "There is no such Inheritance.");
    }
    var roll = gameState.getConfig().getPrestige().getRoll();
    var meta = slot.meta();
    var refusal = RollRules.whyNotBuy(meta, type.get(), roll);
    if (refusal != null) {
      return switch (refusal) {
        case UNAVAILABLE ->
            CommandResult.rejected(Reason.NOT_AVAILABLE, "That Inheritance cannot be had yet.");
        case COURSE_CLOSED ->
            CommandResult.rejected(
                Reason.COURSE_LOCKED, "That Course is closed: spend more Legacy below it first.");
        case INSUFFICIENT_LEGACY ->
            CommandResult.rejected(Reason.INSUFFICIENT_LEGACY, "There is not enough Legacy.");
        default -> CommandResult.rejected(Reason.PURCHASE_REFUSED, "The Roll already holds it.");
      };
    }
    slot.meta(RollRules.buy(meta, type.get(), roll));
    slot.emit(
        "ROLL_BOUGHT", Map.of("inheritance", type.get().name(), "paid", roll.costOf(type.get())));
    return CommandResult.applied("The Inheritance is yours.");
  }

  private CommandResult rollRefund(PlayerSlot slot, GameState gameState, String id) {
    if (gameState.getPhase() != CampaignPhase.OFFER) {
      return rollSealed();
    }
    var roll = gameState.getConfig().getPrestige().getRoll();
    var meta = slot.meta();
    var refusal = RollRules.whyNotRefund(meta, id, roll);
    if (refusal != null) {
      return CommandResult.rejected(
          Reason.PURCHASE_REFUSED,
          refusal == RollRefusal.HELD_UP
              ? "Other Inheritances stand on it: give those back first."
              : "The Roll does not hold it.");
    }
    var paid = meta.spent();
    slot.meta(RollRules.refund(meta, id));
    paid -= slot.meta().spent();
    // An answer that needed the Inheritance goes with it.
    if (id.equals(InheritanceType.THE_OPEN_BREACH.name())) {
      gameState.unchoose(OpenBreach.GROUP);
    }
    for (var group : SuccessionChoice.Group.values()) {
      if (group.requires().name().equals(id)) {
        gameState.unchoose(group.name());
      }
    }
    slot.emit("ROLL_REFUNDED", Map.of("inheritance", id, "paid", paid));
    return CommandResult.applied("The Legacy is returned.");
  }

  private CommandResult choose(PlayerSlot slot, GameState gameState, String id) {
    if (gameState.getPhase() != CampaignPhase.OFFER) {
      return rollSealed();
    }
    if (OpenBreach.isAnswer(id)) {
      return chooseBreach(slot, gameState, id);
    }
    var choice = SuccessionChoice.find(id);
    if (choice.isEmpty()) {
      return CommandResult.rejected(Reason.UNKNOWN_ID, "There is no such choice.");
    }
    var group = choice.get().group();
    var effects = RollEffects.of(slot.meta().roll(), gameState.getConfig());
    if (!effects.holds(group.requires())) {
      return CommandResult.rejected(Reason.NOT_AVAILABLE, "The Roll holds nothing to choose.");
    }
    gameState.choose(group.name(), choice.get().name());
    return CommandResult.applied("Chosen.");
  }

  /** The Open Breach's question: a Guardian Floor slain in an earlier Generation, or none. */
  private CommandResult chooseBreach(PlayerSlot slot, GameState gameState, String id) {
    var meta = slot.meta();
    if (!meta.holds(InheritanceType.THE_OPEN_BREACH)) {
      return CommandResult.rejected(Reason.NOT_AVAILABLE, "The Roll holds nothing to choose.");
    }
    if (!OpenBreach.NONE.equals(id)) {
      var floor = OpenBreach.floorOf(id);
      var maxFloor = gameState.getConfig().getTower().getMaxFloor();
      if (floor.isEmpty() || !OpenBreach.candidates(meta, maxFloor).contains(floor.get())) {
        return CommandResult.rejected(
            Reason.NOT_AVAILABLE, "No Guardian of that Floor was slain in an earlier Generation.");
      }
    }
    gameState.choose(OpenBreach.GROUP, id);
    return CommandResult.applied("Chosen.");
  }

  /** Quartermaster's Tally: keeps the Loadout in a slot of the player's meta. */
  private CommandResult saveLoadout(PlayerSlot slot, GameState gameState, Command.SaveLoadout c) {
    var refused = loadoutSlotRefusal(slot, gameState, c.slot());
    if (refused != null) {
      return refused;
    }
    var salt = c.salt() == null ? 0 : c.salt();
    var pack = c.saltPack() == null ? 0 : c.saltPack();
    if (salt < 0 || pack < 0) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "The Salt cannot be negative.");
    }
    if (c.overreach() && gameState.getOverreachRule() == null) {
      return CommandResult.rejected(Reason.NOT_UNLOCKED, "The Roll holds no Overreach.");
    }
    var saved =
        new SavedLoadout(
            c.slot(),
            salt,
            pack,
            c.saltBearer() == null ? null : c.saltBearer().name(),
            c.panacea(),
            c.overreach());
    var loadouts = new ArrayList<>(slot.meta().savedLoadouts());
    loadouts.removeIf(old -> old.slot() == c.slot());
    loadouts.add(saved);
    slot.meta(slot.meta().withSavedLoadouts(loadouts));
    gameState.setSavedLoadouts(slot.meta().savedLoadouts());
    slot.emit("LOADOUT_SAVED", Map.of("slot", c.slot()));
    return CommandResult.applied("The Loadout is saved.");
  }

  private CommandResult clearLoadout(PlayerSlot slot, GameState gameState, Integer which) {
    var refused = loadoutSlotRefusal(slot, gameState, which);
    if (refused != null) {
      return refused;
    }
    var loadouts = new ArrayList<>(slot.meta().savedLoadouts());
    if (!loadouts.removeIf(old -> old.slot() == which)) {
      return CommandResult.rejected(Reason.NOT_AVAILABLE, "No Loadout is saved there.");
    }
    slot.meta(slot.meta().withSavedLoadouts(loadouts));
    gameState.setSavedLoadouts(slot.meta().savedLoadouts());
    slot.emit("LOADOUT_CLEARED", Map.of("slot", which));
    return CommandResult.applied("The Loadout is forgotten.");
  }

  /** Why the slot cannot be used for a saved Loadout: no Tally on the Roll, or no such slot. */
  private static CommandResult loadoutSlotRefusal(
      PlayerSlot slot, GameState gameState, Integer which) {
    if (slot.meta() == null || gameState.getLoadoutSlots() <= 0) {
      return CommandResult.rejected(
          Reason.NOT_UNLOCKED, "The Roll holds no Quartermaster's Tally.");
    }
    if (which == null || which < 0 || which >= gameState.getLoadoutSlots()) {
      return CommandResult.rejected(Reason.INVALID_ARGUMENT, "There is no such Loadout slot.");
    }
    return null;
  }

  /** Muster Roll: the last Party and Loadout, sent again. */
  private CommandResult musterRoll(PlayerSlot slot, GameState gameState, String playerId) {
    // A Trial has no meta, and so no Roll.
    var held = slot.meta() == null ? List.<RollEntry>of() : slot.meta().roll();
    var effects = RollEffects.of(held, gameState.getConfig());
    if (!effects.holds(InheritanceType.MUSTER_ROLL)) {
      return CommandResult.rejected(Reason.NOT_UNLOCKED, "The Roll holds no Muster Roll.");
    }
    var last = slot.lastMuster();
    if (last == null) {
      return CommandResult.rejected(
          Reason.NOT_AVAILABLE, "No Party has set out yet this Generation.");
    }
    return startExpedition(slot, gameState, playerId, last);
  }

  /** The Toll the Campaign would pay if it ended now, with the Tally it was read from. */
  private record Forecast(
      Toll toll, io.github.diogohmcruz.towerdungeon.domain.prestige.Tally tally, int guardians) {}

  private Forecast forecast(PlayerSlot slot, GameState gameState) {
    var meta = slot.meta();
    var tower = gameState.getTower();
    var guardians = tower == null ? 0 : tower.getSlainGuardians().size();
    var tally = gameState.tollTally(guardians);
    var context =
        new TollContext(
            0,
            0,
            meta.residue(),
            meta.lifetime().bestGuardians(),
            gameState.getInheritedStartFloor());
    return new Forecast(
        TollCalculator.calculate(tally, context, gameState.getConfig().getPrestige()),
        tally,
        guardians);
  }

  /** The Toll bar's forecast; null once the Campaign has ended or before it has a Tally. */
  private TollForecast tollForecast(PlayerSlot slot, GameState gameState) {
    if (slot.isTrial()) {
      return null; // A Trial pays no Toll.
    }
    if (gameState.getPhase() != CampaignPhase.ACTIVE
        || gameState.getGameOutcome() != GameOutcome.PLAYING
        || gameState.getTally() == null) {
      return null;
    }
    var prestige = gameState.getConfig().getPrestige();
    var toll = forecast(slot, gameState).toll();
    double divisor = prestige.getPointsDivisor();
    double nextLegacyAt = (Math.floor(toll.toll() / divisor) + 1) * divisor;
    int minimum = prestige.getMinPointsToExodus();
    boolean open = !gameState.isExpeditionActive() && !slot.isHeld() && toll.legacy() >= minimum;
    return new TollForecast(toll, divisor, nextLegacyAt, minimum, open);
  }

  /**
   * Ends the Campaign in one write that is waited for: the Toll is computed and stored as {@code
   * pending}, the Chronicle, the Lifetime Ledger and the residue are updated, a copy is kept as
   * {@code ended-last}, and the Campaign enters the OFFER phase. If the write fails the Campaign is
   * put back exactly as it was and the failure is thrown. A Campaign already in OFFER is left
   * alone: its report is the one computed the first time. Call it under the state's lock.
   */
  private SuccessionReport endCampaign(PlayerSlot slot, GameState gameState, GameOutcome outcome) {
    if (gameState.getPhase() == CampaignPhase.OFFER && gameState.getPending() != null) {
      return gameState.getPending();
    }
    var prestige = gameState.getConfig().getPrestige();
    var meta = slot.meta();
    var forecast = forecast(slot, gameState);
    var toll = forecast.toll();
    var legacy = toll.legacy();
    var pity = false;
    var floor = prestige.getDefeatPity();
    if (outcome == GameOutcome.DEFEAT
        && legacy < floor.getPoints()
        && forecast.tally().expeditionsCompleted() >= floor.getMinQualifyingExpeditions()) {
      legacy = floor.getPoints();
      pity = true;
    }
    // The Offering Stone: Relics still in the Vault are offered, and still count as banked.
    var offering =
        RollEffects.of(meta.roll(), gameState.getConfig()).offering(gameState.vaultRelics());
    legacy += offering;
    var report =
        new SuccessionReport(
            outcome,
            meta.generation(),
            toll,
            legacy,
            pity,
            forecast.guardians(),
            forecast.tally().highestFloor(),
            forecast.tally().expeditionsCompleted(),
            offering);
    var wall = gameState.getWallOfNames();
    var fallen =
        wall.subList(Math.max(0, wall.size() - prestige.getChronicleNames()), wall.size()).stream()
            .map(unit -> new FallenName(unit.name(), unit.type()))
            .toList();
    var tower = gameState.getTower();
    var ended =
        meta.ended(
            report,
            forecast.tally(),
            wall.size(),
            fallen,
            prestige.getChronicleLimit(),
            tower == null ? List.of() : tower.getSlainGuardians(),
            prestige.getEpitaphs());

    var previousOutcome = gameState.getGameOutcome();
    var previousPhase = gameState.getPhase();
    var previousPending = gameState.getPending();
    gameState.setGameOutcome(outcome);
    gameState.setPhase(CampaignPhase.OFFER);
    gameState.setPending(report);
    try {
      if (!slot.isManual()) {
        saves.writeAwaited(slot, gameState.toSnapshot(), ended, true);
      }
    } catch (RuntimeException e) {
      gameState.setGameOutcome(previousOutcome);
      gameState.setPhase(previousPhase);
      gameState.setPending(previousPending);
      throw e;
    }
    slot.meta(ended);
    slot.emit(
        "CAMPAIGN_ENDED",
        Map.of("outcome", outcome.name(), "generation", report.generation(), "legacy", legacy));
    log.info(
        "Campaign of player {} ended: {} (Generation {}, {} Legacy).",
        slot.playerId(),
        outcome,
        report.generation(),
        legacy);
    return report;
  }

  /**
   * A Campaign the rules have ended (Victory, Defeat) but that is not yet in OFFER: records it. A
   * failed write is retried after a pause, and the player is told once.
   */
  void settleEnded(PlayerSlot slot, GameState gameState) {
    if (slot.isTrial()
        || gameState.getPhase() == CampaignPhase.OFFER
        || gameState.getGameOutcome() == GameOutcome.PLAYING) {
      return;
    }
    var now = System.nanoTime();
    if (!slot.mayRetryEnd(now)) {
      return;
    }
    try {
      endCampaign(slot, gameState, gameState.getGameOutcome());
      slot.endRecorded();
    } catch (AutosaveCoordinator.WriteFailedException e) {
      if (slot.endFailed(now, config.getPersistence().getRetryInitial().toNanos())) {
        slot.addNotice(
            new Notice(
                "ENDING_NOT_RECORDED",
                "The end of the Campaign could not be recorded. It will be tried again."));
      }
    }
  }

  private CommandResult exodus(PlayerSlot slot, GameState gameState, String playerId) {
    if (gameState.getGameOutcome() != GameOutcome.PLAYING) {
      return CommandResult.rejected(Reason.CAMPAIGN_ENDED, "The Campaign has ended.");
    }
    if (gameState.isExpeditionActive()) {
      return CommandResult.rejected(
          Reason.EXPEDITION_ACTIVE, "Extract first: Exodus cannot leave a Party on the Tower.");
    }
    if (slot.isHeld()) {
      return CommandResult.rejected(Reason.PARTY_HELD, "The clock is held. Resume first.");
    }
    var minimum = gameState.getConfig().getPrestige().getMinPointsToExodus();
    if (forecast(slot, gameState).toll().legacy() < minimum) {
      return CommandResult.rejected(
          Reason.TOLL_TOO_LOW, "The Toll is not yet worth leaving for: " + minimum + " Legacy.");
    }
    try {
      endCampaign(slot, gameState, GameOutcome.EXODUS);
    } catch (AutosaveCoordinator.WriteFailedException e) {
      log.warn("Player[{}] could not leave: the write failed.", playerId);
      return CommandResult.rejected(Reason.WRITE_FAILED, "The Exodus could not be recorded.")
          .withNotice(new Notice("ENDING_FAILED", "The Exodus could not be recorded. Try again."));
    }
    return CommandResult.applied("The village leaves in the night.");
  }

  /**
   * Starts the next Generation from a Campaign in OFFER: the Founders again, everything else new,
   * the player's meta kept. One write that is waited for; if it fails nothing changes. Once begun,
   * the Campaign is no longer in OFFER and a repeat does nothing.
   */
  private CommandResult beginGeneration(PlayerSlot slot, GameState gameState, String playerId) {
    if (gameState.getPhase() != CampaignPhase.OFFER) {
      return CommandResult.rejected(Reason.NOT_ENDED, "There is no ended Campaign to begin from.");
    }
    var pending = gameState.getPending();
    var ended = slot.meta();
    var meta =
        ended
            .nextGeneration()
            .withOpenBreach(
                OpenBreach.resolve(
                    ended,
                    gameState.getChoices().get(OpenBreach.GROUP),
                    gameState.getConfig().getTower().getMaxFloor()));
    var fresh =
        newCampaign(
            meta,
            pending != null && pending.outcome() == GameOutcome.DEFEAT,
            gameState.getChoices());
    try {
      if (!slot.isManual()) {
        saves.writeAwaited(slot, fresh.toSnapshot(), meta, false);
      }
    } catch (AutosaveCoordinator.WriteFailedException e) {
      log.warn(
          "Player[{}] could not begin Generation {}: the write failed.",
          playerId,
          meta.generation());
      return CommandResult.rejected(
              Reason.WRITE_FAILED, "The next Generation could not be recorded.")
          .withNotice(
              new Notice("BEGIN_FAILED", "The next Generation could not be recorded. Try again."));
    }
    adopt(slot, meta);
    slot.begun(fresh);
    slot.resume();
    slot.emit("GENERATION_BEGUN", Map.of("generation", meta.generation()));
    log.info("Player[{}] began Generation {}.", playerId, meta.generation());
    return CommandResult.applied("The next Generation begins.");
  }

  @Scheduled(fixedRateString = "${game.loop.lifecycle-ms}")
  public void gameLoop() {
    forEachPlayer(this::lifecycleTick);
  }

  /**
   * One lifecycle second for one Campaign: Village production, Upkeep, Starvation, Recovery and
   * Recharge, then the Expedition (Supplies drain, Wave climb, floor clear, one combat round). The
   * caller holds the state's lock. Both drivers run exactly this.
   */
  void lifecycleTick(PlayerSlot slot, GameState gameState) {
    var trial = gameState.getTrial();
    if (trial != null) {
      if (trial.isClosed()) {
        noteTrialClosed(slot, gameState);
        return;
      }
      trial.tick();
    }
    var slainBefore = gameState.getEnemiesDefeated();
    var floorBefore = floorOf(gameState);
    var boatBefore = gameState.getBoat().state();
    gameState.triggerLifecycle();
    if (boatBefore != gameState.getBoat().state()
        && gameState.getBoat().state() == GameState.BoatState.DOCKED) {
      slot.emit("BOAT_DOCKED", Map.of());
    }
    var voyageEnded = gameState.takeVoyageEnded();
    if (voyageEnded != null) {
      slot.emit(voyageEnded, Map.of());
    }
    var party = new AimTally();
    var garrison = new AimTally();
    updateInvasion(gameState, party, garrison);
    gameState.advanceYear();
    slot.bumpSeq();
    recordTick(slot, gameState, slainBefore, floorBefore);
    emitStrikes(slot, "PARTY", party);
    emitStrikes(slot, "GARRISON", garrison);
    if (trial != null) {
      if (gameState.getGameOutcome() == GameOutcome.DEFEAT) {
        trial.defeat();
      } else if (trial.isOutOfTime()) {
        timeOutTrial(gameState);
      }
      noteTrialClosed(slot, gameState);
    }
  }

  /** The clock ran out: the Party comes home with what it holds and the result freezes. */
  private void timeOutTrial(GameState gameState) {
    gameState.getTrial().timeOut();
    if (gameState.isExpeditionActive()) {
      extractParty(gameState);
    }
    log.info("A Trial ran out of time.");
  }

  /**
   * The Finish Floor's Guardian is slain: the result freezes at this tick and the Party is
   * Extracted. This is the Trial's own Summit Conquered, not a new Expedition Outcome.
   */
  private void endTrialOnFinish(GameState gameState) {
    gameState.getTrial().finish();
    extractParty(gameState);
    log.info("A Trial was won: the Guardian of its Finish Floor is slain.");
  }

  /**
   * Writes down, once, that a Trial is over: the result in the results store, the Race record in
   * the player's real Campaign, the event log and the save.
   */
  private void noteTrialClosed(PlayerSlot slot, GameState gameState) {
    var link = slot.trial();
    var trial = gameState.getTrial();
    if (link == null || trial == null || !trial.isClosed() || !slot.noteTrialClosed()) {
      return;
    }
    if (trials != null) {
      var result = resultOf(link, gameState, trial);
      if (trials.ledger().close(result)) {
        writeRaceRecord(link, result);
      }
    }
    slot.emit(
        "TRIAL_CLOSED",
        Map.of("status", trial.getStatus().name(), "ticks", trial.getTicksSinceStart()));
    slot.saves().event(true);
  }

  /** What a closed attempt came to, in whole seconds. */
  private TrialResult resultOf(PlayerSlot.TrialLink link, GameState gameState, TrialRun trial) {
    var lifecycleMs = Math.max(1, config.getLoop().getLifecycleMs());
    var arrivals = new TreeMap<Integer, Long>();
    trial.getArrivals().forEach((floor, tick) -> arrivals.put(floor, tick * lifecycleMs / 1000));
    return new TrialResult(
        link.raceId(),
        link.ownerId(),
        link.attempt(),
        link.alias(),
        link.label(),
        trial.getStatus(),
        trial.getTicksSinceStart(),
        trial.getTicksSinceStart() * lifecycleMs / 1000,
        trial.getFinishFloor(),
        gameState.getHighestFloor(),
        gameState.getTrialCost(),
        arrivals,
        Instant.now());
  }

  /**
   * Puts one plain line into the real Campaign of the Key: the place of its best attempt among the
   * Contenders who have closed one. The real Campaign may be frozen or not loaded at all, so the
   * line is added once it is loaded, off the Trial's thread, and saved with it.
   */
  private void writeRaceRecord(PlayerSlot.TrialLink link, TrialResult closed) {
    var ledger = trials.ledger();
    var table = ledger.table(link.raceId());
    var row = table.rowOf(link.ownerId());
    if (row.isEmpty()) {
      return;
    }
    var at =
        ledger.results(link.raceId()).stream()
            .filter(r -> r.playerId().equals(link.ownerId()) && r.attempt() == row.get().attempt())
            .map(TrialResult::closedAt)
            .findFirst()
            .orElse(closed.closedAt());
    var record =
        RaceRecord.of(
            link.raceId(),
            closed.finishFloor(),
            row.get(),
            table.contendersWith(link.ownerId()),
            at);
    var real =
        players.compute(
            link.ownerId(), (id, existing) -> existing != null ? existing : newSlot(id));
    var limit = config.getPrestige().getChronicleLimit();
    real.loading()
        .publishOn(Schedulers.boundedElastic())
        .subscribe(
            state -> {
              try {
                state.callLocked(
                    () -> {
                      var meta = real.meta();
                      if (meta != null) {
                        real.meta(meta.withRaceRecord(record, limit));
                        real.saves().event(false);
                      }
                      return null;
                    });
              } catch (Exception e) {
                log.error("Could not write the Race record of a player", e);
              }
            },
            e -> log.error("Could not load the Campaign to write its Race record", e));
  }

  /**
   * Attrition for {@code ms} milliseconds. The damage is proportional to the interval (and to part
   * health), so one call of 1000 equals a hundred calls of 10. The caller holds the lock.
   */
  void attritionTick(GameState gameState, long ms) {
    if (gameState.isTrialClosed()) {
      return;
    }
    gameState.passingTime(ms);
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
    forEachPlayer((_, gameState) -> attritionTick(gameState, tick));
  }

  /** Files one event for a side's aimed strikes this tick, if it threw any. */
  private static void emitStrikes(PlayerSlot slot, String side, AimTally tally) {
    if (!tally.isEmpty()) {
      slot.emit("AIMED_STRIKES", tally.toEventData(side));
    }
  }

  private void updateInvasion(GameState gameState, AimTally party, AimTally garrison) {
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
    var unitsOnTower = inOrder(gameState.getUnitsOnTower());
    var currentTowerFloor = tower.getCurrentTowerFloor();
    if (currentTowerFloor == null || currentTowerFloor.getEnemies().isEmpty()) {
      var clearedFloor = tower.getCurrentFloor();
      var doorSealed = gameState.isDoorSealedAt(clearedFloor);
      if (doorSealed && gameState.isDoorSeen()) {
        // The Floor has paid. The Party stands under Attrition and Supplies drain until it leaves.
        return;
      }
      var bossCleared = currentTowerFloor != null && currentTowerFloor.isBoss();
      var reward = gameState.getConfig().getReward();
      var rewardMultiplier = bossCleared ? reward.getBossRewardMultiplier() : 1.0;
      gameState.gatherCredits(clearedFloor * reward.getCreditPerFloor() * rewardMultiplier);
      gatherFloorLoot(gameState, clearedFloor, rewardMultiplier);
      gameState.gatherFloorSpores(clearedFloor, bossCleared);
      floorBundle.resolve(gameState, clearedFloor, bossCleared);
      gameState.layRisenToRest(clearedFloor);
      if (bossCleared) {
        tower.markBossCleared(clearedFloor);
        log.info(
            "Defeated the guardian of floor {}! The lesser floors below it grow easier.",
            clearedFloor);
      }
      if (bossCleared && gameState.finishesTrial(clearedFloor)) {
        endTrialOnFinish(gameState);
        return;
      }
      if (clearedFloor >= tower.getMaxFloor()) {
        log.info("VICTORY! Player conquered the summit of the tower on floor {}!", clearedFloor);
        endRunOnVictory(gameState);
        return;
      }
      if (doorSealed) {
        gameState.markDoorSeen();
        log.info("Floor {} is cleared and the door does not open.", clearedFloor);
        return;
      }
      tower.moveToNextFloor();
      gameState.recordFloorReached(tower.getCurrentFloor());
      gameState.rewardFloorClimbed();
      log.info("Beat the floor {}!", tower.getCurrentFloor());
      return;
    }
    var currentEnemies = currentTowerFloor.getEnemies();
    partyCombat.unitsAttack(unitsOnTower, currentEnemies, currentTowerFloor, gameState, party::add);
    gameState.dropGearOnCurrentFloor(
        partyCombat.enemiesAttack(gameState, currentEnemies, unitsOnTower, garrison::add));
  }

  private void gatherFloorLoot(GameState gameState, int clearedFloor, double rewardMultiplier) {
    if (clearedFloor <= 0) {
      return;
    }
    var reward = gameState.getConfig().getReward();
    gameState.gatherLoot(
        ResourceType.MATERIALS, clearedFloor * reward.getMaterialsPerFloor() * rewardMultiplier);
    gameState.gatherSupplies(reward.getSuppliesPerFloor() * rewardMultiplier);
  }

  private void applyStarvation(GameState gameState) {
    var starving =
        gameState.getUnitsOnTower().values().stream().flatMap(Collection::stream).toList();
    var meter = gameState.getMeter();
    starving.forEach(
        unit -> {
          var before = unit.getCurrentHealth();
          gameState.dropGearOnCurrentFloor(unit.receiveAttack(gameState.starvationDamage(), null));
          meter.taken(ExpeditionMeter.Cause.STARVATION, before - unit.getCurrentHealth());
        });
    meter.causeIs(ExpeditionMeter.Cause.STARVATION);
    starving.stream().filter(BaseUnit::isDead).toList().forEach(gameState::removeUnit);
    meter.causeIs(ExpeditionMeter.Cause.OTHER);
  }

  private void endRunOnWipe(GameState gameState) {
    gameState.buildRunSummary(RunSummary.WIPED);
    gameState.raiseRisenOnWipe();
    gameState.forfeitLoot();
    gameState.returnReinforcementsHome();
    gameState.setExpeditionActive(false);
    gameState.setSupplies(0d);
    gameState.setSaltedSupplies(0d);
    gameState.loseSaltPack();
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
    var prestige = gameState.getConfig().getPrestige();
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

  /**
   * The persisted record of a player's live Campaign, or empty when the player has no loaded slot.
   */
  public Optional<GameSnapshot> snapshotOf(String playerId) {
    var slot = players.get(playerId);
    if (slot != null && slot.isTrial()) {
      return Optional.empty(); // A Trial's file never leaves the server.
    }
    return access(playerId, GameState::toSnapshot);
  }

  /**
   * Replaces a player's live Campaign with {@code snapshot}, under the state's lock, unless an
   * Expedition is active or the Party is Held. {@code persist} writes the new Campaign and returns
   * the sequence number it was saved at; it is given the sequence the live slot is at, or empty
   * when the player has no live slot (then only the file changes, and the next connection loads
   * it). If it fails nothing changes.
   *
   * @return false, changing nothing, when an Expedition is active or Held
   */
  public boolean replaceIfIdle(
      String playerId, GameSnapshot snapshot, ToLongFunction<OptionalLong> persist) {
    return replaceIfIdle(playerId, snapshot, null, persist);
  }

  /**
   * As {@link #replaceIfIdle(String, GameSnapshot, ToLongFunction)}, also replacing the player's
   * meta with {@code meta} (the {@code meta} section of the imported file); null keeps the meta the
   * slot has.
   */
  public boolean replaceIfIdle(
      String playerId,
      GameSnapshot snapshot,
      ObjectNode meta,
      ToLongFunction<OptionalLong> persist) {
    var slot = players.get(playerId);
    if (slot != null && slot.isTrial()) {
      return false; // Nothing is imported into a Trial.
    }
    if (slot == null || slot.state() == null) {
      persist.applyAsLong(OptionalLong.empty());
      return true;
    }
    var replacementMeta = meta == null ? slot.meta() : withSecret(metaCodec.fromNode(meta));
    var replacement =
        GameState.restore(
            snapshot,
            config.copy(),
            new RandomSource(replacementMeta.seed()),
            meta == null ? slot.plan() : planFor(replacementMeta));
    return access(
            slot,
            state -> {
              if (state.isExpeditionActive() || slot.isHeld()) {
                return Boolean.FALSE;
              }
              long seq = persist.applyAsLong(OptionalLong.of(slot.saves().seq()));
              adopt(slot, replacementMeta);
              slot.loaded(replacement, true, Instant.now(), CampaignSchema.CURRENT);
              saves.opened(slot, seq);
              return Boolean.TRUE;
            })
        .orElse(Boolean.FALSE);
  }

  /** A body that reads or mutates a locked {@link GameState} and produces a result; may throw. */
  @FunctionalInterface
  private interface StateFn<T> {

    T apply(GameState state) throws Exception;
  }
}

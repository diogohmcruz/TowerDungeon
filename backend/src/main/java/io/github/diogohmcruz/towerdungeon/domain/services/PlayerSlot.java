package io.github.diogohmcruz.towerdungeon.domain.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame.SessionMeta;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.observation.ExpeditionSummaries;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionView;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderLabel;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * One player's live Campaign: the state, how it was obtained, and which connection (if any) is
 * attached. A slot with no connection does not tick and is evicted after it has lingered.
 */
final class PlayerSlot {
  /** An attached connection; {@code replaced} completes when a newer connection takes the slot. */
  record Connection(String wsId, Sinks.One<Void> replaced) {
    Connection(String wsId) {
      this(wsId, Sinks.one());
    }
  }

  /**
   * What makes a slot a Trial's: the Key it belongs to, the race and the attempt. The slot's own id
   * is the id of the Trial's file, never the Key's player id.
   *
   * @param alias the sanitised name shown in tables
   * @param label how the Contender declared itself
   */
  record TrialLink(
      String ownerId, String raceId, int attempt, String alias, ContenderLabel label) {}

  private final String playerId;
  private volatile TrialLink trial;
  private boolean trialClosureNoted;
  private final SaveTracker saves = new SaveTracker();
  private final Queue<Notice> notices = new ConcurrentLinkedQueue<>();
  private Mono<GameState> loading;
  private volatile GameState state;
  private volatile PlayerMeta meta;
  private volatile ArchitecturePlan plan;
  private long endRetryAt;
  private boolean endNoticeSent;
  private volatile boolean resumed;
  private volatile Instant savedAt;
  private volatile int schema;
  private volatile boolean held;

  /** The last Party and Loadout that set out this Generation; only Muster Roll reads it. */
  private Command.StartExpedition lastMuster;

  private Connection connection;
  private long detachedAtNanos;

  /**
   * Counts applied commands and lifecycle ticks. Only touched under the state's lock. Not the
   * persistence sequence number: that one counts saves.
   */
  private long stateSeq;

  private final EventLog events = new EventLog();
  private final ExpeditionSummaries summaries = new ExpeditionSummaries();
  private RunSummary lastSeenSummary;

  private final ClockMode clock;

  /** Simulated seconds a manual Campaign has run, and the most it may run. Under the lock. */
  private long simulatedSeconds;

  private long simulatedLimit = Long.MAX_VALUE;

  private volatile TickObserver observer;

  PlayerSlot(String playerId) {
    this(playerId, ClockMode.REALTIME);
  }

  PlayerSlot(String playerId, ClockMode clock) {
    this.playerId = playerId;
    this.clock = clock;
  }

  ClockMode clock() {
    return clock;
  }

  /** A manual Campaign is a Sandbox: nothing advances it but its driver, and it is never saved. */
  boolean isManual() {
    return clock == ClockMode.MANUAL;
  }

  long simulatedSeconds() {
    return simulatedSeconds;
  }

  long simulatedLimit() {
    return simulatedLimit;
  }

  /** Who watches this Sandbox's ticks, or null. */
  TickObserver observer() {
    return observer;
  }

  void observer(TickObserver observer) {
    this.observer = observer;
  }

  void simulatedLimit(long limit) {
    this.simulatedLimit = limit;
  }

  /** Counts simulated seconds that have just run. Call under the state's lock. */
  void addSimulatedSeconds(long seconds) {
    simulatedSeconds += seconds;
  }

  String playerId() {
    return playerId;
  }

  /** The Trial this slot holds, or null for a real Campaign or a Sandbox. */
  TrialLink trial() {
    return trial;
  }

  void trial(TrialLink trial) {
    this.trial = trial;
  }

  boolean isTrial() {
    return trial != null;
  }

  /** True once, the first time the end of this slot's Trial is noted. Call under the lock. */
  boolean noteTrialClosed() {
    var first = !trialClosureNoted;
    trialClosureNoted = true;
    return first;
  }

  Mono<GameState> loading() {
    return loading;
  }

  void loading(Mono<GameState> loading) {
    this.loading = loading;
  }

  SaveTracker saves() {
    return saves;
  }

  /** Whether the Party is Held: it stands still until the player resumes or extracts. */
  boolean isHeld() {
    return held;
  }

  void hold() {
    held = true;
  }

  void resume() {
    held = false;
  }

  /** The Campaign once loaded; null while the load is still in flight. */
  GameState state() {
    return state;
  }

  void loaded(GameState state, boolean resumed, Instant savedAt, int schema) {
    this.resumed = resumed;
    this.savedAt = savedAt;
    this.schema = schema;
    this.lastSeenSummary = state.getLastRunSummary();
    this.state = state;
  }

  /** What the player keeps across Generations; null until the Campaign is loaded. */
  PlayerMeta meta() {
    return meta;
  }

  void meta(PlayerMeta meta) {
    this.meta = meta;
  }

  /** The Tower's plan for the Generation being played; null until the Campaign is loaded. */
  ArchitecturePlan plan() {
    return plan;
  }

  void plan(ArchitecturePlan plan) {
    this.plan = plan;
  }

  /** Whether it is time to try again to record an ending that could not be written. */
  boolean mayRetryEnd(long nowNanos) {
    return endRetryAt == 0 || nowNanos >= endRetryAt;
  }

  /** The write of the ending failed; returns whether the player has not been told yet. */
  boolean endFailed(long nowNanos, long retryAfterNanos) {
    endRetryAt = nowNanos + retryAfterNanos;
    var first = !endNoticeSent;
    endNoticeSent = true;
    return first;
  }

  void endRecorded() {
    endRetryAt = 0;
    endNoticeSent = false;
  }

  /** The next Generation's Campaign takes the slot; the Expedition summaries start again. */
  void begun(GameState next) {
    this.lastSeenSummary = next.getLastRunSummary();
    this.state = next;
    this.lastMuster = null;
  }

  /** The last Party and Loadout that set out this Generation, for Muster Roll; null before one. */
  Command.StartExpedition lastMuster() {
    return lastMuster;
  }

  /** Remembers a Party and Loadout that set out. The Unit counts are copied. */
  void lastMuster(Command.StartExpedition command) {
    this.lastMuster =
        new Command.StartExpedition(
            command.units() == null ? Map.of() : new java.util.HashMap<>(command.units()),
            command.startFloor(),
            command.salt(),
            command.saltPack(),
            command.saltBearer(),
            command.panacea(),
            command.overreach());
  }

  long stateSeq() {
    return stateSeq;
  }

  /** Advances the state sequence. Call under the state's lock. */
  long bumpSeq() {
    return ++stateSeq;
  }

  EventLog events() {
    return events;
  }

  ExpeditionSummaries summaries() {
    return summaries;
  }

  /**
   * Logs an event at the sequence number the command in flight will carry (it is bumped once the
   * command is done). Call under the state's lock.
   */
  void emit(String type, Map<String, Object> data) {
    events.append(stateSeq + 1, type, data);
  }

  /**
   * Files a newly finished Expedition Summary, if there is one. Summaries are built by the state
   * when an expedition ends; this notices a new one by identity, so a summary restored from a save
   * is not filed again.
   */
  void collectSummary(GameState state) {
    var latest = state.getLastRunSummary();
    if (latest != null && latest != lastSeenSummary) {
      lastSeenSummary = latest;
      summaries.add(latest);
      events.append(stateSeq, "EXPEDITION_ENDED", Map.of("outcome", latest.outcome()));
    }
  }

  void addNotice(Notice notice) {
    notices.add(notice);
  }

  /**
   * The session facts for the next frame; drains the pending notices. {@code held} is whether the
   * Party is Held right now.
   */
  SessionMeta takeMeta(boolean held) {
    return takeMeta(held, null);
  }

  /** As {@link #takeMeta(boolean)}, with the Succession's view while the Campaign is in OFFER. */
  SessionMeta takeMeta(boolean held, SuccessionView succession) {
    var pending = new ArrayList<Notice>();
    for (Notice n; (n = notices.poll()) != null; ) {
      pending.add(n);
    }
    return new SessionMeta(resumed, savedAt, schema, List.copyOf(pending), held, succession);
  }

  /** Attaches a connection, newest wins: the previous one is told it was replaced. */
  synchronized void attach(Connection next) {
    var previous = connection;
    connection = next;
    if (previous != null) {
      previous.replaced().tryEmitEmpty();
    }
  }

  /**
   * Detaches only if {@code wsId} is still the attached connection (compare-and-detach). Returns
   * whether it detached.
   */
  synchronized boolean detach(String wsId, long nowNanos) {
    if (connection != null && connection.wsId().equals(wsId)) {
      connection = null;
      detachedAtNanos = nowNanos;
      return true;
    }
    return false;
  }

  /**
   * Tells the attached connection, if any, that another slot of the same Key took over: used when a
   * Key moves between its real Campaign and a Trial. The connection's own cleanup detaches it.
   */
  synchronized void displace() {
    if (connection != null) {
      connection.replaced().tryEmitEmpty();
    }
  }

  synchronized boolean isAttached() {
    return connection != null;
  }

  synchronized boolean hasLingeredFor(long lingerNanos, long nowNanos) {
    return connection == null && nowNanos - detachedAtNanos >= lingerNanos;
  }
}

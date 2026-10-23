package io.github.diogohmcruz.towerdungeon.domain.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame.Notice;
import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame.SessionMeta;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
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

  private final String playerId;
  private final SaveTracker saves = new SaveTracker();
  private final Queue<Notice> notices = new ConcurrentLinkedQueue<>();
  private Mono<GameState> loading;
  private volatile GameState state;
  private volatile boolean resumed;
  private volatile Instant savedAt;
  private volatile int schema;
  private volatile boolean held;
  private Connection connection;
  private long detachedAtNanos;

  PlayerSlot(String playerId) {
    this.playerId = playerId;
  }

  String playerId() {
    return playerId;
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
    this.state = state;
  }

  void addNotice(Notice notice) {
    notices.add(notice);
  }

  /**
   * The session facts for the next frame; drains the pending notices. {@code held} is whether the
   * Party is Held right now.
   */
  SessionMeta takeMeta(boolean held) {
    var pending = new ArrayList<Notice>();
    for (Notice n; (n = notices.poll()) != null; ) {
      pending.add(n);
    }
    return new SessionMeta(resumed, savedAt, schema, List.copyOf(pending), held);
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

  synchronized boolean isAttached() {
    return connection != null;
  }

  synchronized boolean hasLingeredFor(long lingerNanos, long nowNanos) {
    return connection == null && nowNanos - detachedAtNanos >= lingerNanos;
  }
}

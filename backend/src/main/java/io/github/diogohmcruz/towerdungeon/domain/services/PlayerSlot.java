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
  private final Queue<Notice> notices = new ConcurrentLinkedQueue<>();
  private Mono<GameState> loading;
  private volatile GameState state;
  private volatile boolean resumed;
  private volatile Instant savedAt;
  private volatile int schema;
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

  /** The session facts for the next frame; drains the pending notices. */
  SessionMeta takeMeta() {
    var pending = new ArrayList<Notice>();
    for (Notice n; (n = notices.poll()) != null; ) {
      pending.add(n);
    }
    return new SessionMeta(resumed, savedAt, schema, List.copyOf(pending), false);
  }

  /** Attaches a connection, newest wins: the previous one is told it was replaced. */
  synchronized void attach(Connection next) {
    var previous = connection;
    connection = next;
    if (previous != null) {
      previous.replaced().tryEmitEmpty();
    }
  }

  /** Detaches only if {@code wsId} is still the attached connection (compare-and-detach). */
  synchronized void detach(String wsId, long nowNanos) {
    if (connection != null && connection.wsId().equals(wsId)) {
      connection = null;
      detachedAtNanos = nowNanos;
    }
  }

  synchronized boolean isAttached() {
    return connection != null;
  }

  synchronized boolean hasLingeredFor(long lingerNanos, long nowNanos) {
    return connection == null && nowNanos - detachedAtNanos >= lingerNanos;
  }
}

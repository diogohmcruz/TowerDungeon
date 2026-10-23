package io.github.diogohmcruz.towerdungeon.agent.act;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;

/**
 * The last results of every grant, by {@code request_id}, so a retried call returns what the first
 * one did instead of acting twice (design 4.4). It keeps the last {@value #MAX_PER_GRANT} results
 * per grant for ten minutes. The wall clock is read here, in the agent layer, and never in the
 * domain. The store is in memory: after a restart a retry can run again, which is accepted.
 *
 * <p>A call is {@code begin}-ed before it acts and either {@code complete}-d with its result or
 * {@code abandon}-ed when it failed without one. A second call with the same id while the first is
 * running is told so, and does nothing.
 */
@AgentEnabled
@Component
public class IdempotencyStore {
  public static final int MAX_PER_GRANT = 64;
  public static final Duration KEEP = Duration.ofMinutes(10);

  /** What {@link #begin} found. */
  public sealed interface Begin {
    /** Nothing is recorded under the id: run the call, then complete or abandon it. */
    record Fresh() implements Begin {}

    /** The first call is still running. */
    record InFlight() implements Begin {}

    /** The id was used for different arguments or another tool. */
    record Mismatch() implements Begin {}

    /** The id was used before for the same call: this is what it returned. */
    record Replay(Map<String, Object> result) implements Begin {}
  }

  private record Entry(String digest, Map<String, Object> result, Instant at) {}

  private final Clock clock;
  private final Map<String, LinkedHashMap<String, Entry>> byGrant = new HashMap<>();

  @Autowired
  public IdempotencyStore() {
    this(Clock.systemUTC());
  }

  public IdempotencyStore(Clock clock) {
    this.clock = clock;
  }

  public synchronized Begin begin(String grantId, String requestId, String digest) {
    var entries = byGrant.computeIfAbsent(grantId, _ -> new LinkedHashMap<>());
    var now = clock.instant();
    for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
      var entry = it.next();
      if (entry.result() != null && entry.at().plus(KEEP).isBefore(now)) {
        it.remove();
      }
    }
    var found = entries.get(requestId);
    if (found == null) {
      while (entries.size() >= MAX_PER_GRANT) {
        var oldest = oldestFinished(entries);
        if (oldest.isEmpty()) {
          break;
        }
        entries.remove(oldest.get());
      }
      entries.put(requestId, new Entry(digest, null, now));
      return new Begin.Fresh();
    }
    if (found.result() == null) {
      return new Begin.InFlight();
    }
    if (!found.digest().equals(digest)) {
      return new Begin.Mismatch();
    }
    return new Begin.Replay(found.result());
  }

  /** Records what a call returned, so a repeat gets the same. */
  public synchronized void complete(
      String grantId, String requestId, String digest, Map<String, Object> result) {
    var entries = byGrant.computeIfAbsent(grantId, _ -> new LinkedHashMap<>());
    entries.remove(requestId);
    entries.put(requestId, new Entry(digest, Map.copyOf(result), clock.instant()));
  }

  /** Forgets a call that failed without a result, so a retry may run it. */
  public synchronized void abandon(String grantId, String requestId) {
    var entries = byGrant.get(grantId);
    if (entries != null) {
      var entry = entries.get(requestId);
      if (entry != null && entry.result() == null) {
        entries.remove(requestId);
      }
    }
  }

  private static Optional<String> oldestFinished(LinkedHashMap<String, Entry> entries) {
    return entries.entrySet().stream()
        .filter(e -> e.getValue().result() != null)
        .map(Map.Entry::getKey)
        .findFirst();
  }
}

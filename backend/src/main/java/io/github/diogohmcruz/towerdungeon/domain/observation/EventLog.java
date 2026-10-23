package io.github.diogohmcruz.towerdungeon.domain.observation;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A bounded ring of what happened in one Campaign, newest last. It is a developer-facing event
 * feed, not the Ledger. Entries hold only fixed type names and numbers, never caller-supplied text.
 */
public final class EventLog {
  public static final int DEFAULT_CAPACITY = 500;

  /**
   * @param id strictly increasing across the log's life, so a reader can ask for "after id"
   * @param stateSeq the state sequence at which it happened
   * @param type a fixed upper-case name
   * @param data small facts (counts, floors, enum names)
   */
  public record Event(long id, long stateSeq, String type, Map<String, Object> data) {
    public Event {
      // In key order, so that the same event reads the same in every launch of the server (an
      // immutable Map.of orders its entries differently each time the JVM starts).
      data = Collections.unmodifiableMap(new TreeMap<>(data));
    }
  }

  private final int capacity;
  private final Deque<Event> events = new ArrayDeque<>();
  private long nextId = 1;

  public EventLog() {
    this(DEFAULT_CAPACITY);
  }

  public EventLog(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
  }

  /** Appends an event, dropping the oldest when full. Returns its id. */
  public synchronized long append(long stateSeq, String type, Map<String, Object> data) {
    if (events.size() == capacity) {
      events.removeFirst();
    }
    var event = new Event(nextId++, stateSeq, type, data);
    events.addLast(event);
    return event.id();
  }

  /** The retained events with an id greater than {@code afterId}, oldest first. */
  public synchronized List<Event> since(long afterId) {
    return events.stream().filter(e -> e.id() > afterId).toList();
  }

  /** The id of the newest event, or 0 when nothing has been logged. */
  public synchronized long lastId() {
    return nextId - 1;
  }

  public synchronized int size() {
    return events.size();
  }
}

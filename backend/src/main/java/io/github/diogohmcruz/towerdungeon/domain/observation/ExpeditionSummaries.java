package io.github.diogohmcruz.towerdungeon.domain.observation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;

/** The most recent Expedition Summaries of a Campaign, capped, oldest dropped first. */
public final class ExpeditionSummaries {
  public static final int DEFAULT_CAPACITY = 50;

  private final int capacity;
  private final Deque<RunSummary> summaries = new ArrayDeque<>();

  public ExpeditionSummaries() {
    this(DEFAULT_CAPACITY);
  }

  public ExpeditionSummaries(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
  }

  public synchronized void add(RunSummary summary) {
    if (summaries.size() == capacity) {
      summaries.removeFirst();
    }
    summaries.addLast(summary);
  }

  /** The retained summaries, oldest first. */
  public synchronized List<RunSummary> all() {
    return List.copyOf(summaries);
  }

  public synchronized int size() {
    return summaries.size();
  }
}

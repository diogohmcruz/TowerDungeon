package io.github.diogohmcruz.towerdungeon.domain.services;

import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;

/**
 * What one slot's saves have done and still owe: the last sequence number, whether there are
 * unwritten changes, which writes are due and when, and how many writes have failed in a row. It
 * holds no I/O and reads no clock; every method takes the time, so the cadence is testable.
 */
final class SaveTracker {
  private static final long NONE = Long.MIN_VALUE;

  /** The cadence in nanoseconds, taken from the configuration. */
  record Cadence(
      long purchaseDebounce,
      long purchaseMaxWait,
      long autosave,
      long expeditionAutosave,
      long rotation,
      long retryInitial,
      long retryMax) {}

  /** One write in progress: what it expects and what it must put back if it fails. */
  record Attempt(
      long startedAt, long expectedSeq, boolean rotationRequested, boolean intervalRotation) {}

  private long seq = CampaignStore.INITIAL_SEQ;
  private boolean dirty;
  private boolean immediate;
  private boolean rotationRequested;
  private boolean intervalRotation;
  private long purchaseFirstAt = NONE;
  private long purchaseLastAt = NONE;
  private long lastWriteAt;
  private long lastRotationAt;
  private boolean expedition;
  private boolean inFlight;
  private int failures;
  private long retryAt = NONE;
  private boolean noticeSent;

  synchronized void opened(long loadedSeq, long now) {
    seq = loadedSeq;
    lastWriteAt = now;
    lastRotationAt = now;
  }

  /** The state changed; the autosave will write it. */
  synchronized void touch() {
    dirty = true;
  }

  /** A purchase: written after a quiet spell, or after the max wait if they keep coming. */
  synchronized void purchase(long now) {
    dirty = true;
    if (purchaseFirstAt == NONE) {
      purchaseFirstAt = now;
    }
    purchaseLastAt = now;
  }

  /** An event that must be written now, and rotated into a backup when the state is settled. */
  synchronized void event(boolean rotate) {
    dirty = true;
    immediate = true;
    rotationRequested |= rotate;
  }

  /** A final flush (disconnect, shutdown): now, rotating only by the autosave rule. */
  synchronized void flush() {
    dirty = true;
    immediate = true;
    intervalRotation = true;
  }

  synchronized void expedition(boolean active) {
    expedition = active;
  }

  /**
   * Claims the next write if one is due, clearing what it covers; empty when nothing is due, a
   * write is already in flight, or a failed write is still backing off (unless {@code force}).
   */
  synchronized Attempt begin(long now, Cadence cadence, boolean force) {
    if (inFlight || !dirty || (!force && retryAt != NONE && now < retryAt)) {
      return null;
    }
    var purchaseDue =
        purchaseFirstAt != NONE
            && now
                >= Math.min(
                    purchaseLastAt + cadence.purchaseDebounce(),
                    purchaseFirstAt + cadence.purchaseMaxWait());
    var autosaveDue =
        now - lastWriteAt >= (expedition ? cadence.expeditionAutosave() : cadence.autosave());
    if (!(immediate || purchaseDue || autosaveDue)) {
      return null;
    }
    var attempt =
        new Attempt(
            now,
            seq,
            rotationRequested,
            (intervalRotation || autosaveDue) && now - lastRotationAt >= cadence.rotation());
    inFlight = true;
    dirty = false;
    immediate = false;
    intervalRotation = false;
    purchaseFirstAt = NONE;
    purchaseLastAt = NONE;
    return attempt;
  }

  /** The write was not needed (a pristine Campaign). */
  synchronized void skipped() {
    inFlight = false;
  }

  synchronized void succeeded(Attempt attempt, long newSeq, boolean rotated) {
    inFlight = false;
    seq = newSeq;
    lastWriteAt = attempt.startedAt();
    if (rotated) {
      lastRotationAt = attempt.startedAt();
      rotationRequested = false;
    }
    failures = 0;
    retryAt = NONE;
    noticeSent = false;
  }

  /** The write failed; what it covered is owed again. Returns the failures in a row. */
  synchronized int failed(Attempt attempt, long now, Cadence cadence) {
    inFlight = false;
    dirty = true;
    immediate = true;
    intervalRotation |= attempt.intervalRotation();
    failures++;
    var delay = cadence.retryInitial();
    for (var i = 1; i < failures && delay < cadence.retryMax(); i++) {
      delay *= 2;
    }
    retryAt = now + Math.min(delay, cadence.retryMax());
    return failures;
  }

  /** Adopts the sequence number the store reported after a conflict. */
  synchronized void resync(long actualSeq) {
    seq = actualSeq;
  }

  /** True once per run of failures, when it has reached {@code threshold}. */
  synchronized boolean shouldWarn(int threshold) {
    if (failures >= threshold && !noticeSent) {
      noticeSent = true;
      return true;
    }
    return false;
  }

  synchronized boolean isClean() {
    return !dirty && !inFlight;
  }

  synchronized boolean isInFlight() {
    return inFlight;
  }

  synchronized boolean isDirty() {
    return dirty;
  }

  synchronized int failures() {
    return failures;
  }

  synchronized long seq() {
    return seq;
  }
}

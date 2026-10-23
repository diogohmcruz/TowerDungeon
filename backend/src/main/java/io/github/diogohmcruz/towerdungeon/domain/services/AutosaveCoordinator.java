package io.github.diogohmcruz.towerdungeon.domain.services;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Notice;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignConflictException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import io.github.diogohmcruz.towerdungeon.persistence.GameVersion;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Decides when each Campaign is written and writes it without ever doing I/O on a game thread. A
 * write captures the snapshot under the state's lock (a copy, nothing more) and hands it to the
 * store, which serializes and writes it on its own writer thread. The cadence is the one in {@code
 * docs/brainstorm/prestige-and-persistence.md} section 2.4.
 */
@Slf4j
@Component
public class AutosaveCoordinator {
  /** What a mutation changed, as far as saving cares. */
  record Marks(boolean expedition, GameOutcome outcome, int milestones, int shortcuts) {
    static Marks of(GameState state) {
      return new Marks(
          state.isExpeditionActive(),
          state.getGameOutcome(),
          state.getAchievedMilestones().size(),
          state.getOpenedShortcuts().size());
    }
  }

  /** The copy taken under the lock, and whether the Campaign was settled (no Expedition). */
  private record Capture(GameSnapshot snapshot, boolean settled) {}

  private final CampaignStore store;
  private final ObjectMapper mapper;
  private final GameProperties.Persistence config;
  private final String gameVersion;
  private final LongSupplier clock;
  private final SaveTracker.Cadence cadence;

  @Autowired
  public AutosaveCoordinator(
      CampaignStore store, ObjectMapper mapper, GameProperties properties, GameVersion version) {
    this(store, mapper, properties.getPersistence(), version.current(), System::nanoTime);
  }

  AutosaveCoordinator(
      CampaignStore store,
      ObjectMapper mapper,
      GameProperties.Persistence config,
      String gameVersion,
      LongSupplier clock) {
    this.store = store;
    this.mapper = mapper;
    this.config = config;
    this.gameVersion = gameVersion;
    this.clock = clock;
    this.cadence =
        new SaveTracker.Cadence(
            nanos(config.getPurchaseDebounce()),
            nanos(config.getPurchaseMaxWait()),
            nanos(config.getAutosaveInterval()),
            nanos(config.getExpeditionAutosaveInterval()),
            nanos(config.getRotationInterval()),
            nanos(config.getRetryInitial()),
            nanos(config.getRetryMax()));
  }

  private static long nanos(Duration duration) {
    return duration.toNanos();
  }

  /** The slot's Campaign is loaded (or new); {@code seq} is the sequence number it was read at. */
  void opened(PlayerSlot slot, long seq) {
    if (slot.isManual()) {
      return;
    }
    slot.saves().opened(seq, clock.getAsLong());
    slot.saves().expedition(slot.state() != null && slot.state().isExpeditionActive());
  }

  /** Something changed that autosave will pick up. */
  void touched(PlayerSlot slot) {
    if (slot.isManual()) {
      return;
    }
    slot.saves().touch();
  }

  /** A purchase: written after a short quiet spell. */
  void purchased(PlayerSlot slot) {
    if (slot.isManual()) {
      return;
    }
    slot.saves().purchase(clock.getAsLong());
  }

  /**
   * Called under the state's lock after a mutation: an Expedition set out or ended, the Campaign
   * ended, or a Milestone or Shortcut was earned, each written at once. Departure is not rotated
   * into a backup (a backup holds settled states only); the rest are.
   */
  void recorded(PlayerSlot slot, Marks before, Marks after) {
    if (slot.isManual()) {
      return;
    }
    var tracker = slot.saves();
    tracker.touch();
    tracker.expedition(after.expedition());
    if (!before.expedition() && after.expedition()) {
      tracker.event(false);
    } else if (before.expedition() != after.expedition()
        || before.outcome() != after.outcome()
        || before.milestones() != after.milestones()
        || before.shortcuts() != after.shortcuts()) {
      tracker.event(true);
    }
  }

  /** Writes at once whatever is unwritten (a disconnect). Does nothing for a clean slot. */
  void flush(PlayerSlot slot) {
    if (slot.isManual()) {
      return;
    }
    slot.saves().flush();
    attempt(slot, true);
  }

  /** Starts any write that has fallen due. Cheap when nothing has. */
  void tick(PlayerSlot slot) {
    if (slot.isManual()) {
      return;
    }
    attempt(slot, false);
  }

  /**
   * Writes every slot that is attached or has unwritten changes, then waits (at most {@code wait})
   * for the writes to finish. A slot whose write fails is not retried here.
   */
  void flushAll(Collection<PlayerSlot> slots, Duration wait) {
    var deadline = clock.getAsLong() + wait.toNanos();
    var pending = new HashSet<PlayerSlot>();
    for (var slot : slots) {
      if (!slot.isManual()
          && slot.state() != null
          && (slot.isAttached() || slot.saves().isDirty())) {
        slot.saves().flush();
        pending.add(slot);
      }
    }
    var attempted = new HashSet<PlayerSlot>();
    while (!pending.isEmpty() && clock.getAsLong() < deadline) {
      for (var slot : new HashSet<>(pending)) {
        var tracker = slot.saves();
        if (tracker.isInFlight()) {
          continue;
        }
        if (tracker.isClean() || attempted.contains(slot)) {
          pending.remove(slot);
        } else {
          attempted.add(slot);
          attempt(slot, true);
        }
      }
      if (!pending.isEmpty()) {
        pause();
      }
    }
    if (!pending.isEmpty()) {
      log.warn("Shutdown flush gave up on {} Campaign(s) after {}", pending.size(), wait);
    }
  }

  private static void pause() {
    try {
      TimeUnit.MILLISECONDS.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void attempt(PlayerSlot slot, boolean force) {
    if (slot.isManual()) {
      return;
    }
    var state = slot.state();
    if (state == null) {
      return;
    }
    var tracker = slot.saves();
    var now = clock.getAsLong();
    var attempt = tracker.begin(now, cadence, force);
    if (attempt == null) {
      return;
    }
    Capture capture;
    try {
      capture =
          state.callLocked(
              () ->
                  attempt.expectedSeq() == CampaignStore.INITIAL_SEQ && state.isPristine()
                      ? null
                      : new Capture(state.toSnapshot(), !state.isExpeditionActive()));
    } catch (Exception e) {
      log.error("Could not capture the Campaign of player {}", slot.playerId(), e);
      failed(slot, attempt, e);
      return;
    }
    if (capture == null) {
      tracker.skipped();
      return;
    }
    var rotate = capture.settled() && (attempt.rotationRequested() || attempt.intervalRotation());
    var snapshot = capture.snapshot();
    try {
      store
          .save(
              slot.playerId(),
              attempt.expectedSeq(),
              rotate ? Rotation.ROTATE : Rotation.KEEP,
              CampaignSchema.CURRENT,
              gameVersion,
              () -> mapper.writeValueAsBytes(snapshot))
          .switchIfEmpty(Mono.error(new IllegalStateException("The store saved nothing")))
          .subscribe(
              seq -> tracker.succeeded(attempt, seq, rotate), err -> failed(slot, attempt, err));
    } catch (RuntimeException e) {
      failed(slot, attempt, e);
    }
  }

  private void failed(PlayerSlot slot, SaveTracker.Attempt attempt, Throwable cause) {
    var tracker = slot.saves();
    if (cause instanceof CampaignConflictException conflict) {
      tracker.resync(conflict.getActualSeq());
    }
    var failures = tracker.failed(attempt, clock.getAsLong(), cadence);
    log.warn(
        "Saving the Campaign of player {} failed ({} in a row)", slot.playerId(), failures, cause);
    if (tracker.shouldWarn(config.getFailuresBeforeNotice())) {
      slot.addNotice(new Notice("RECORDS_NOT_KEPT", "Records are not being kept"));
    }
  }
}

package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** An awaited write takes over a save that is only waiting for the lock, and waits for the rest. */
class SaveTrackerExclusiveTest {
  private static final SaveTracker.Cadence CADENCE = new SaveTracker.Cadence(1, 1, 1, 1, 1, 1, 1);

  private final SaveTracker tracker = new SaveTracker();

  @Test
  void anIdleTrackerIsClaimedAtOnce() {
    var attempt = tracker.claimExclusive(0);

    assertThat(attempt).isNotNull();
    assertThat(tracker.isInFlight()).isTrue();
  }

  @Test
  void aSaveStillWaitingForTheLockIsTakenOver() {
    tracker.opened(5, 0);
    tracker.event(false);
    var waiting = tracker.begin(10, CADENCE, true);
    assertThat(waiting).isNotNull();

    var exclusive = tracker.claimExclusive(11);

    assertThat(exclusive).isNotNull();
    assertThat(exclusive.expectedSeq()).isEqualTo(5);
    assertThat(tracker.captured(waiting)).isFalse();
    assertThat(tracker.owns(waiting)).isFalse();
    // Whatever the stale save reports is ignored.
    tracker.succeeded(waiting, 6, false);
    assertThat(tracker.isInFlight()).isTrue();
    assertThat(tracker.seq()).isEqualTo(5);
  }

  @Test
  void aSaveAlreadyOnItsWayToTheStoreIsWaitedFor() {
    tracker.opened(5, 0);
    tracker.event(false);
    var writing = tracker.begin(10, CADENCE, true);
    assertThat(tracker.captured(writing)).isTrue();

    assertThat(tracker.claimExclusive(11)).isNull();

    tracker.succeeded(writing, 6, false);
    var exclusive = tracker.claimExclusive(12);
    assertThat(exclusive).isNotNull();
    assertThat(exclusive.expectedSeq()).isEqualTo(6);
  }

  @Test
  void anAbandonedExclusiveWriteIsOwedAgain() {
    tracker.opened(5, 0);
    var exclusive = tracker.claimExclusive(1);

    tracker.abandon(exclusive);

    assertThat(tracker.isInFlight()).isFalse();
    assertThat(tracker.isDirty()).isTrue();
    assertThat(tracker.seq()).isEqualTo(5);
  }
}

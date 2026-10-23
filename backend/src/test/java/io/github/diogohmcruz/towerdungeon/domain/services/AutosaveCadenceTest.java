package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.api.dtos.GameFrame.Notice;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** The write cadence of section 2.4, driven by a fake clock and a recording store. */
class AutosaveCadenceTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  private record Write(long atSeconds, long expectedSeq, Rotation rotation) {}

  private long now;
  private final List<Write> writes = new ArrayList<>();
  private RuntimeException failure;
  private final GameProperties config = new GameProperties();
  private GameState state;
  private PlayerSlot slot;
  private AutosaveCoordinator saves;
  private long seq;

  private final CampaignStore store =
      new CampaignStore() {
        @Override
        public Mono<Long> save(
            String playerId,
            long expectedSeq,
            Rotation rotation,
            int schema,
            String gameVersion,
            byte[] payload) {
          if (failure != null) {
            return Mono.error(failure);
          }
          writes.add(new Write(seconds(), expectedSeq, rotation));
          return Mono.just(++seq);
        }

        @Override
        public Mono<LoadResult> load(String playerId) {
          return Mono.empty();
        }
      };

  private long seconds() {
    return Duration.ofNanos(now).toSeconds();
  }

  private void at(long seconds) {
    now = Duration.ofSeconds(seconds).toNanos();
  }

  @BeforeEach
  void setUp() {
    state = new GameState(config);
    state.setCredit(99d);
    slot = new PlayerSlot(PLAYER);
    slot.loaded(state, false, null, 1);
    saves =
        new AutosaveCoordinator(
            store, JsonMapper.builder().build(), config.getPersistence(), "test", () -> now);
    saves.opened(slot, CampaignStore.INITIAL_SEQ);
  }

  private void tickAt(long seconds) {
    at(seconds);
    saves.tick(slot);
  }

  private void startExpedition(long seconds) {
    at(seconds);
    var before = AutosaveCoordinator.Marks.of(state);
    state.setExpeditionActive(true);
    saves.recorded(slot, before, AutosaveCoordinator.Marks.of(state));
  }

  private void endExpedition(long seconds) {
    at(seconds);
    var before = AutosaveCoordinator.Marks.of(state);
    state.setExpeditionActive(false);
    saves.recorded(slot, before, AutosaveCoordinator.Marks.of(state));
  }

  @Test
  void aPurchaseIsWrittenAfterTheDebounceWithoutRotation() {
    at(100);
    saves.opened(slot, CampaignStore.INITIAL_SEQ);
    saves.purchased(slot);

    tickAt(102);
    assertThat(writes).isEmpty();
    tickAt(103);
    assertThat(writes).containsExactly(new Write(103, 0, Rotation.KEEP));
  }

  @Test
  void aStreamOfPurchasesIsWrittenAtTheMaxWait() {
    at(100);
    saves.opened(slot, CampaignStore.INITIAL_SEQ);
    for (var t = 100; t <= 109; t += 2) {
      at(t);
      saves.purchased(slot);
      saves.tick(slot);
    }
    assertThat(writes).isEmpty();

    tickAt(110);

    assertThat(writes).containsExactly(new Write(110, 0, Rotation.KEEP));
  }

  @Test
  void anIdleCampaignIsAutosavedEveryThirtySecondsOnlyWhenChanged() {
    saves.touched(slot);
    tickAt(29);
    assertThat(writes).isEmpty();
    tickAt(30);
    assertThat(writes).hasSize(1);

    tickAt(100);
    assertThat(writes).as("nothing changed since").hasSize(1);
    tickAt(31);
    saves.touched(slot);
    tickAt(59);
    assertThat(writes).hasSize(1);
    tickAt(60);
    assertThat(writes).hasSize(2);
  }

  @Test
  void anExpeditionIsAutosavedEveryFiveSecondsWithoutRotation() {
    startExpedition(100);
    saves.tick(slot);
    assertThat(writes).hasSize(1);

    saves.touched(slot);
    tickAt(104);
    assertThat(writes).hasSize(1);
    tickAt(105);
    assertThat(writes).hasSize(2);
    assertThat(writes).extracting(Write::rotation).containsOnly(Rotation.KEEP);
  }

  @Test
  void departureIsWrittenAtOnceAndNotRotated() {
    startExpedition(100);

    assertThat(writes).isEmpty();
    saves.tick(slot);
    assertThat(writes).containsExactly(new Write(100, 0, Rotation.KEEP));
  }

  @Test
  void extractionIsWrittenAtOnceAndRotated() {
    startExpedition(100);
    saves.tick(slot);

    endExpedition(101);
    saves.tick(slot);

    assertThat(writes.getLast()).isEqualTo(new Write(101, 1, Rotation.ROTATE));
  }

  @Test
  void aMilestoneEarnedMidExpeditionWaitsForASettledStateToRotate() {
    startExpedition(100);
    saves.tick(slot);
    var before = AutosaveCoordinator.Marks.of(state);
    state
        .getAchievedMilestones()
        .add(io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType.values()[0]);
    saves.recorded(slot, before, AutosaveCoordinator.Marks.of(state));
    saves.tick(slot);
    assertThat(writes.getLast().rotation()).as("mid-Expedition").isEqualTo(Rotation.KEEP);

    endExpedition(110);
    saves.tick(slot);
    assertThat(writes.getLast().rotation()).isEqualTo(Rotation.ROTATE);
  }

  @Test
  void autosaveRotatesOnlyWhenSettledAndTenMinutesAfterTheLastRotation() {
    saves.touched(slot);
    tickAt(30);
    assertThat(writes.getLast().rotation()).isEqualTo(Rotation.KEEP);

    saves.touched(slot);
    tickAt(599);
    assertThat(writes).hasSize(2);
    assertThat(writes.getLast().rotation()).as("before ten minutes").isEqualTo(Rotation.KEEP);

    saves.touched(slot);
    tickAt(630);
    assertThat(writes.getLast().rotation()).isEqualTo(Rotation.ROTATE);

    saves.touched(slot);
    tickAt(660);
    assertThat(writes.getLast().rotation()).as("rotated 30 s ago").isEqualTo(Rotation.KEEP);
  }

  @Test
  void autosaveDoesNotRotateMidExpedition() {
    startExpedition(1000);
    saves.tick(slot);

    saves.touched(slot);
    tickAt(1005);

    assertThat(writes.getLast().rotation()).isEqualTo(Rotation.KEEP);
  }

  @Test
  void aFinalFlushWritesAtOnceWithTheAutosaveRotationRule() {
    at(30);
    saves.flush(slot);
    assertThat(writes).containsExactly(new Write(30, 0, Rotation.KEEP));

    at(700);
    saves.touched(slot);
    saves.flush(slot);
    assertThat(writes.getLast().rotation()).isEqualTo(Rotation.ROTATE);
  }

  @Test
  void aCampaignNobodyHasChangedIsNeverWritten() {
    var pristine = new GameState(config);
    var other = new PlayerSlot(PLAYER);
    other.loaded(pristine, false, null, 1);
    saves.opened(other, CampaignStore.INITIAL_SEQ);

    at(0);
    saves.flush(other);

    assertThat(writes).isEmpty();
  }

  @Test
  void aFailingStoreIsRetriedAtOneTwoFourThenThirtySeconds() {
    failure = new IllegalStateException("disk full");
    var attempts = new ArrayList<Long>();
    saves.flush(slot);
    attempts.add(seconds());
    for (var t = 1; t <= 120; t++) {
      var before = failureCount();
      tickAt(t);
      if (failureCount() > before) {
        attempts.add((long) t);
      }
    }

    assertThat(attempts).containsExactly(0L, 1L, 3L, 7L, 15L, 31L, 61L, 91L);
  }

  private int failureCount() {
    return slot.saves().failures();
  }

  @Test
  void theNoticeAppearsAfterThreeFailuresOnlyAndWritingResumesWhenTheStoreRecovers() {
    failure = new IllegalStateException("disk full");
    saves.flush(slot);
    assertThat(notices()).isEmpty();
    tickAt(1);
    assertThat(notices()).isEmpty();
    tickAt(3);
    assertThat(failureCount()).isEqualTo(3);
    assertThat(notices()).extracting(Notice::message).containsExactly("Records are not being kept");

    tickAt(7);
    assertThat(notices()).as("told once").isEmpty();

    failure = null;
    tickAt(15);
    assertThat(writes).hasSize(1);
    assertThat(failureCount()).isZero();
    assertThat(slot.saves().isClean()).isTrue();
  }

  private List<Notice> notices() {
    return slot.takeMeta(false).notices();
  }

  @Test
  void aSlotWithAWriteInFlightIsNotWrittenAgainUntilItLands() {
    var pending = reactor.core.publisher.Sinks.<Long>one();
    var slow =
        new CampaignStore() {
          int calls;

          @Override
          public Mono<Long> save(
              String playerId,
              long expectedSeq,
              Rotation rotation,
              int schema,
              String gameVersion,
              byte[] payload) {
            calls++;
            return pending.asMono();
          }

          @Override
          public Mono<LoadResult> load(String playerId) {
            return Mono.empty();
          }
        };
    var coordinator =
        new AutosaveCoordinator(
            slow, JsonMapper.builder().build(), config.getPersistence(), "test", () -> now);
    coordinator.opened(slot, CampaignStore.INITIAL_SEQ);

    coordinator.flush(slot);
    coordinator.flush(slot);
    assertThat(slow.calls).isEqualTo(1);

    pending.tryEmitValue(1L);
    coordinator.flush(slot);
    assertThat(slow.calls).isEqualTo(2);
  }

  @Test
  void theShutdownFlushWritesEveryAttachedSlot() {
    var second = new PlayerSlot("fedcba9876543210fedcba9876543210");
    var other = new GameState(config);
    other.setCredit(7d);
    second.loaded(other, false, null, 1);
    saves.opened(second, CampaignStore.INITIAL_SEQ);
    slot.attach(new PlayerSlot.Connection("a"));
    second.attach(new PlayerSlot.Connection("b"));

    saves.flushAll(List.of(slot, second), Duration.ofSeconds(20));

    assertThat(writes).hasSize(2);
    assertThat(slot.saves().isClean()).isTrue();
    assertThat(second.saves().isClean()).isTrue();
  }

  @Test
  void theShutdownFlushIsBoundedWhenAWriteNeverLands() {
    var never =
        new CampaignStore() {
          @Override
          public Mono<Long> save(
              String playerId,
              long expectedSeq,
              Rotation rotation,
              int schema,
              String gameVersion,
              byte[] payload) {
            return Mono.never();
          }

          @Override
          public Mono<LoadResult> load(String playerId) {
            return Mono.empty();
          }
        };
    var realClock =
        new AutosaveCoordinator(
            never, JsonMapper.builder().build(), config.getPersistence(), "test", System::nanoTime);
    realClock.opened(slot, CampaignStore.INITIAL_SEQ);
    slot.attach(new PlayerSlot.Connection("a"));

    var started = System.nanoTime();
    realClock.flushAll(List.of(slot), Duration.ofMillis(200));

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
  }
}

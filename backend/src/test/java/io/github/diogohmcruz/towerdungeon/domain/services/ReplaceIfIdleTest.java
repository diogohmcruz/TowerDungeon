package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

class ReplaceIfIdleTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private GameService service;

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    var store = new FileCampaignStore(config.getPersistence());
    service =
        new GameService(
            config,
            mapper,
            new SplittableRandom(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(new SplittableRandom(7)),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, mapper, config.getPersistence(), "test", System::nanoTime));
  }

  private GameSnapshot richer() {
    var state = service.newCampaign();
    state.setCredit(777d);
    return state.toSnapshot();
  }

  private double credit() {
    return service.snapshotOf(PLAYER).orElseThrow().campaign().credit();
  }

  @Test
  void anIdleCampaignIsReplacedAfterPersistRuns() {
    service.attach(PLAYER, "ws-1").block();
    var seen = new AtomicReference<OptionalLong>();

    boolean swapped =
        service.replaceIfIdle(
            PLAYER,
            richer(),
            liveSeq -> {
              seen.set(liveSeq);
              return 5;
            });

    assertThat(swapped).isTrue();
    assertThat(seen.get()).isPresent();
    assertThat(credit()).isEqualTo(777d);
  }

  @Test
  void aPlayerWithNoLiveSlotOnlyHasItsFileWritten() {
    var seen = new AtomicReference<OptionalLong>();

    boolean swapped =
        service.replaceIfIdle(
            PLAYER,
            richer(),
            liveSeq -> {
              seen.set(liveSeq);
              return 1;
            });

    assertThat(swapped).isTrue();
    assertThat(seen.get()).isEmpty();
    assertThat(service.hasSlot(PLAYER)).isFalse();
    assertThat(service.snapshotOf(PLAYER)).isEmpty();
  }

  @Test
  void anActiveExpeditionRefusesTheSwapAndChangesNothing() {
    service.attach(PLAYER, "ws-1").block();
    double before = credit();
    service.playerState(PLAYER).startRun();
    var persisted = new AtomicReference<Boolean>(false);

    boolean swapped =
        service.replaceIfIdle(
            PLAYER,
            richer(),
            liveSeq -> {
              persisted.set(true);
              return 1;
            });

    assertThat(swapped).isFalse();
    assertThat(persisted.get()).isFalse();
    assertThat(credit()).isEqualTo(before);
    assertThat(service.playerState(PLAYER).isExpeditionActive()).isTrue();
  }

  @Test
  void aHeldPartyRefusesTheSwap() {
    service.attach(PLAYER, "ws-1").block();
    service.playerState(PLAYER).startRun();
    service.detach(PLAYER, "ws-1");

    boolean swapped = service.replaceIfIdle(PLAYER, richer(), liveSeq -> 1);

    assertThat(swapped).isFalse();
  }

  @Test
  void aFailedPersistChangesNothing() {
    service.attach(PLAYER, "ws-1").block();
    double before = credit();

    assertThatThrownBy(
            () ->
                service.replaceIfIdle(
                    PLAYER,
                    richer(),
                    liveSeq -> {
                      throw new IllegalStateException("disk gone");
                    }))
        .hasMessage("disk gone");

    assertThat(credit()).isEqualTo(before);
  }
}

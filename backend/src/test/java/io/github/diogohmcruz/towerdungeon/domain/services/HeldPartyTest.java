package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

/** A disconnect mid-Expedition holds the Party; the Campaign survives a restart in that state. */
class HeldPartyTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private FileCampaignStore store;
  private GameService service;

  private GameService newService() {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return new GameService(
        config,
        mapper,
        SeedSource.fixed(7),
        plan,
        new PartyCombat(),
        store,
        mock(FloorBundle.class),
        new AutosaveCoordinator(store, mapper, config.getPersistence(), "test", System::nanoTime));
  }

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    store = new FileCampaignStore(config.getPersistence());
    service = newService();
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  private void departAndDisconnect() {
    service.attach(PLAYER, "ws-1").block();
    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    assertThat(service.playerState(PLAYER).isExpeditionActive()).isTrue();
    service.detach(PLAYER, "ws-1");
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    for (var i = 0; i < 400 && !condition.getAsBoolean(); i++) {
      Thread.sleep(10);
    }
    assertThat(condition.getAsBoolean()).isTrue();
  }

  private String frame() {
    return service.renderState(PLAYER).orElseThrow();
  }

  private GameSnapshot read(byte[] payload) {
    return mapper.readValue(payload, GameSnapshot.class);
  }

  @Test
  void aReturningPlayerFindsTheirPartyHeldAndStandingStill() {
    departAndDisconnect();
    var state = service.playerState(PLAYER);
    var before = state.toSnapshot();

    service.attach(PLAYER, "ws-2").block();
    service.gameLoop();
    service.invasionTimer();

    assertThat(frame()).contains("\"held\":true");
    assertThat(state.toSnapshot()).isEqualTo(before);
  }

  @Test
  void resumeLetsThePartyContinueExactlyWhereItStopped() {
    departAndDisconnect();
    var state = service.playerState(PLAYER);
    var before = state.toSnapshot();
    service.attach(PLAYER, "ws-2").block();
    assertThat(state.toSnapshot()).isEqualTo(before);

    service.execute(PLAYER, new Command.Resume());

    assertThat(frame()).contains("\"held\":false");
    service.gameLoop();
    assertThat(state.toSnapshot()).isNotEqualTo(before);
    assertThat(state.getSupplies()).isLessThan(before.expedition().supplies());
  }

  @Test
  void extractionWhileHeldBanksTheLoot() {
    departAndDisconnect();
    var state = service.playerState(PLAYER);
    state.gatherLoot(ResourceType.MATERIALS, 12d);
    service.attach(PLAYER, "ws-2").block();

    service.execute(PLAYER, new Command.Extract());

    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(12d);
    assertThat(frame()).contains("\"held\":false");
  }

  @Test
  void reinforcingIsRefusedUntilTheyResume() {
    departAndDisconnect();
    var state = service.playerState(PLAYER);
    service.attach(PLAYER, "ws-2").block();
    state.addUnits(
        UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR, new SplittableRandom(1))));

    service.execute(PLAYER, new Command.SendReinforcements(Map.of()));

    assertThat(state.getReinforcements()).isEmpty();
    assertThat(frame()).contains("PARTY_HELD");

    service.execute(PLAYER, new Command.Resume());
    service.execute(PLAYER, new Command.SendReinforcements(Map.of()));
    assertThat(state.getReinforcements()).isNotEmpty();
  }

  @Test
  void holdStopsTheVillageClockWithNoExpeditionAndResumeRestartsIt() {
    service.attach(PLAYER, "ws-1").block();
    var state = service.playerState(PLAYER);

    assertThat(service.execute(PLAYER, new Command.Hold()).outcome()).isEqualTo(Outcome.APPLIED);
    var before = state.toSnapshot();
    service.gameLoop();
    service.invasionTimer();

    assertThat(state.toSnapshot()).isEqualTo(before);
    assertThat(state.getYearTick()).isEqualTo(before.campaign().yearTick());
    assertThat(frame()).contains("\"held\":true");

    service.execute(PLAYER, new Command.Resume());
    service.gameLoop();

    assertThat(state.toSnapshot()).isNotEqualTo(before);
    assertThat(frame()).contains("\"held\":false");
  }

  @Test
  void holdAndResumeAreIdempotent() {
    service.attach(PLAYER, "ws-1").block();

    assertThat(service.execute(PLAYER, new Command.Resume()).outcome()).isEqualTo(Outcome.APPLIED);
    service.execute(PLAYER, new Command.Hold());
    assertThat(service.execute(PLAYER, new Command.Hold()).outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(frame()).contains("\"held\":true");
    service.execute(PLAYER, new Command.Resume());
    assertThat(service.execute(PLAYER, new Command.Resume()).outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(frame()).contains("\"held\":false");
  }

  @Test
  void aDeliberateHoldStopsAConnectedExpeditionAndRefusesReinforcements() {
    service.attach(PLAYER, "ws-1").block();
    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    var state = service.playerState(PLAYER);

    service.execute(PLAYER, new Command.Hold());
    var before = state.toSnapshot();
    service.gameLoop();
    service.invasionTimer();

    assertThat(state.toSnapshot()).isEqualTo(before);
    assertThat(service.execute(PLAYER, new Command.SendReinforcements(Map.of())).reason())
        .isEqualTo(Reason.PARTY_HELD);
    assertThat(service.execute(PLAYER, new Command.SellFood()).reason())
        .isNotEqualTo(Reason.PARTY_HELD);

    service.execute(PLAYER, new Command.Extract());
    assertThat(state.isExpeditionActive()).isFalse();
    service.execute(PLAYER, new Command.Hold());
    service.execute(PLAYER, new Command.Resume());
    service.gameLoop();
    assertThat(state.toSnapshot()).isNotEqualTo(before);
  }

  @Test
  void aSettledCampaignIsNotHeld() {
    service.attach(PLAYER, "ws-1").block();
    service.detach(PLAYER, "ws-1");
    service.attach(PLAYER, "ws-2").block();

    assertThat(frame()).contains("\"held\":false");
  }

  @Test
  void aSavedExpeditionComesBackHeldAfterARestart() throws Exception {
    departAndDisconnect();
    var before = service.playerState(PLAYER).toSnapshot();
    await(
        () -> {
          var saved = store.load(PLAYER).block();
          return saved != null && read(saved.payload()).expedition() != null;
        });

    var restarted = newService();
    restarted.attach(PLAYER, "ws-9").block();

    assertThat(restarted.playerState(PLAYER).toSnapshot().expedition())
        .isEqualTo(before.expedition());
    assertThat(restarted.renderState(PLAYER).orElseThrow()).contains("\"held\":true");
  }

  @Test
  void theShutdownFlushWritesAnAttachedCampaign() {
    service.attach(PLAYER, "ws-1").block();
    service.playerState(PLAYER).setCredit(4242d);
    service.execute(PLAYER, new Command.SellFood());

    service.shutdown();

    var saved = store.load(PLAYER).block();
    assertThat(saved).isNotNull();
    assertThat(read(saved.payload()).campaign().credit()).isGreaterThanOrEqualTo(4242d);
  }

  @Test
  void theGameStopsTickingOnceShutdownHasBegun() {
    service.attach(PLAYER, "ws-1").block();
    var state = service.playerState(PLAYER);
    service.shutdown();
    var before = state.toSnapshot();

    service.gameLoop();

    assertThat(state.toSnapshot()).isEqualTo(before);
  }
}

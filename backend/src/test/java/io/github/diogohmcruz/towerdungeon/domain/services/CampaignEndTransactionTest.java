package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.MetaCodec;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ending a Campaign is one atomic, awaited write: the Toll is stored once, the Chronicle and the
 * residue move with it, the Campaign waits in OFFER, and the loops leave it alone.
 */
class CampaignEndTransactionTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final AtomicBoolean failWrites = new AtomicBoolean();
  private final AtomicInteger writing = new AtomicInteger();
  private FileCampaignStore files;
  private CampaignStore store;
  private GameService service;

  /** The file store, but a write can be made to fail. */
  private final class FlakyStore implements CampaignStore {
    @Override
    public Mono<Long> save(
        String playerId,
        long expectedSeq,
        Rotation rotation,
        int schema,
        String gameVersion,
        byte[] payload) {
      return (failWrites.get()
              ? Mono.<Long>error(new IllegalStateException("disk full"))
              : files.save(playerId, expectedSeq, rotation, schema, gameVersion, payload))
          .doOnSubscribe(_ -> writing.incrementAndGet())
          .doFinally(_ -> writing.decrementAndGet());
    }

    @Override
    public Mono<Void> saveEnded(String playerId, int schema, String gameVersion, byte[] payload) {
      return (failWrites.get()
              ? Mono.<Void>error(new IllegalStateException("disk full"))
              : files.saveEnded(playerId, schema, gameVersion, payload))
          .doOnSubscribe(_ -> writing.incrementAndGet())
          .doFinally(_ -> writing.decrementAndGet());
    }

    @Override
    public Mono<LoadResult> load(String playerId) {
      return files.load(playerId);
    }
  }

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
        new AutosaveCoordinator(store, mapper, config.getPersistence(), "test", System::nanoTime),
        MetaCodec.development(mapper));
  }

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    files = new FileCampaignStore(config.getPersistence());
    store = new FlakyStore();
    service = newService();
    service.attach(PLAYER, "ws-1").block();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    // Let any write in flight finish, so the temporary directory can be removed.
    service.shutdown();
    for (var i = 0; i < 500 && writing.get() > 0; i++) {
      Thread.sleep(10);
    }
    files.close();
  }

  private GameState state() {
    return service.playerState(PLAYER);
  }

  /** A Campaign whose Toll forecast is well above the Exodus gate. */
  private void worthLeaving() {
    var state = state();
    state.setHighestFloor(40);
    state.setExpeditionsCompleted(17);
    state.getTally().addFoodGrown(60000);
    state.getTally().addMaterialsBanked(17000);
    state.getTally().addCreditsBanked(6800);
    state.getTally().addRelicsBanked(320);
  }

  private void spent() {
    var state = state();
    state.setUnits(new HashMap<>());
    state.setCredit(0d);
    state.getVillage().restore(0, 0);
  }

  // ---- Exodus

  @Test
  void exodusIsRefusedWhileAnExpeditionIsOut() {
    worthLeaving();
    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    assertThat(state().isExpeditionActive()).isTrue();

    var result = service.execute(PLAYER, new Command.Exodus());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.EXPEDITION_ACTIVE);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.PLAYING);
    assertThat(state().getPhase()).isEqualTo(CampaignPhase.ACTIVE);
  }

  @Test
  void exodusIsRefusedWhileThePartyIsHeld() {
    worthLeaving();
    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    service.detach(PLAYER, "ws-1");
    service.attach(PLAYER, "ws-2").block();

    var result = service.execute(PLAYER, new Command.Exodus());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.PLAYING);
  }

  @Test
  void exodusIsRefusedBelowThreeForecastPoints() {
    var result = service.execute(PLAYER, new Command.Exodus());

    assertThat(result.reason()).isEqualTo(Reason.TOLL_TOO_LOW);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.PLAYING);
    assertThat(service.playerMeta(PLAYER).chronicle()).isEmpty();
  }

  // ---- The Toll forecast the frame carries

  @Test
  void theFrameCarriesAForecastThatKeepsExodusClosedBelowThreePoints() throws Exception {
    var forecast = mapper.readTree(service.renderState(PLAYER).orElseThrow()).get("forecast");

    assertThat(forecast.get("canExodus").asBoolean()).isFalse();
    assertThat(forecast.get("exodusMinimum").asInt()).isEqualTo(3);
    assertThat(forecast.get("toll").get("legacy").asInt()).isLessThan(3);
    assertThat(forecast.get("nextLegacyAt").asDouble()).isEqualTo(9.0);
  }

  @Test
  void theForecastOpensExodusOnceWorthLeavingAndClosesItWhileAnExpeditionIsOut()
      throws Exception {
    worthLeaving();
    var open = mapper.readTree(service.renderState(PLAYER).orElseThrow()).get("forecast");
    assertThat(open.get("canExodus").asBoolean()).isTrue();
    assertThat(open.get("toll").get("legacy").asInt()).isGreaterThanOrEqualTo(3);

    service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));
    var out = mapper.readTree(service.renderState(PLAYER).orElseThrow()).get("forecast");
    assertThat(out.get("canExodus").asBoolean()).isFalse();
  }

  @Test
  void theForecastIsGoneOnceTheCampaignHasEnded() throws Exception {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());

    var frame = mapper.readTree(service.renderState(PLAYER).orElseThrow());

    assertThat(frame.get("forecast").isNull()).isTrue();
  }

  @Test
  void exodusEndsTheCampaignWithItsOwnOutcomeAndStoresTheToll() {
    worthLeaving();

    var result = service.execute(PLAYER, new Command.Exodus());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    var state = state();
    assertThat(state.getGameOutcome()).isEqualTo(GameOutcome.EXODUS);
    assertThat(state.getPhase()).isEqualTo(CampaignPhase.OFFER);
    var report = state.getPending();
    assertThat(report.outcome()).isEqualTo(GameOutcome.EXODUS);
    assertThat(report.legacy()).isGreaterThanOrEqualTo(3);
    assertThat(report.generation()).isEqualTo(1);

    var meta = service.playerMeta(PLAYER);
    assertThat(meta.legacy()).isEqualTo(report.legacy());
    assertThat(meta.residue()).isEqualTo(report.toll().residueNext());
    assertThat(meta.generation()).isEqualTo(1);
    assertThat(meta.lifetime().exoduses()).isEqualTo(1);
    assertThat(meta.chronicle()).hasSize(1);
    assertThat(meta.chronicle().getFirst().outcome()).isEqualTo(GameOutcome.EXODUS);
  }

  // ---- atomicity and durability

  @Test
  void theEndIsOnDiskWhenTheCommandReturns_AndTheEndedCopyIsKept() throws Exception {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());

    var restarted = newService();
    restarted.attach(PLAYER, "ws-2").block();

    var loaded = restarted.playerState(PLAYER);
    assertThat(loaded.getPhase()).isEqualTo(CampaignPhase.OFFER);
    assertThat(loaded.getGameOutcome()).isEqualTo(GameOutcome.EXODUS);
    assertThat(loaded.getPending()).isEqualTo(state().getPending());
    assertThat(restarted.playerMeta(PLAYER)).isEqualTo(service.playerMeta(PLAYER));
    try (var found = Files.walk(dir)) {
      assertThat(found.map(path -> path.getFileName().toString())).contains("ended-last.tdc");
    }
  }

  @Test
  void aFailingStoreRefusesTheExodusAndChangesNothing() {
    worthLeaving();
    var before = state().toSnapshot();
    var metaBefore = service.playerMeta(PLAYER);
    failWrites.set(true);

    var result = service.execute(PLAYER, new Command.Exodus());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.WRITE_FAILED);
    assertThat(result.notices()).isNotEmpty();
    assertThat(state().toSnapshot()).isEqualTo(before);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.PLAYING);
    assertThat(service.playerMeta(PLAYER)).isEqualTo(metaBefore);

    failWrites.set(false);
    assertThat(service.execute(PLAYER, new Command.Exodus()).outcome()).isEqualTo(Outcome.APPLIED);
  }

  // ---- the loops

  @Test
  void theLoopsDoNotTickACampaignInOffer() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    var before = state().toSnapshot();

    service.gameLoop();
    service.invasionTimer();
    service.gameLoop();

    assertThat(state().toSnapshot()).isEqualTo(before);
  }

  @Test
  void aCampaignInOfferTakesNoOrdinaryCommands() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    var before = state().toSnapshot();

    assertThat(service.execute(PLAYER, new Command.SellFood()).reason())
        .isEqualTo(Reason.CAMPAIGN_ENDED);
    assertThat(service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0)).reason())
        .isEqualTo(Reason.CAMPAIGN_ENDED);
    assertThat(service.execute(PLAYER, new Command.Exodus()).reason())
        .isEqualTo(Reason.CAMPAIGN_ENDED);
    assertThat(state().toSnapshot()).isEqualTo(before);
  }

  // ---- Victory and Defeat

  @Test
  void aVictoryTheRulesReachedIsRecordedByTheNextTick() {
    state().winCampaign();

    service.gameLoop();

    assertThat(state().getPhase()).isEqualTo(CampaignPhase.OFFER);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.VICTORY);
    assertThat(service.playerMeta(PLAYER).lifetime().victories()).isEqualTo(1);
  }

  @Test
  void aDefeatPaysNothingBelowThreeExpeditions() {
    spent();
    state().setExpeditionsCompleted(2);

    service.gameLoop();

    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.DEFEAT);
    assertThat(state().getPhase()).isEqualTo(CampaignPhase.OFFER);
    assertThat(state().getPending().legacy()).isZero();
    assertThat(state().getPending().pity()).isFalse();
  }

  @Test
  void aDefeatPaysThePityFloorWithThreeExpeditions() {
    spent();
    state().setExpeditionsCompleted(3);

    service.gameLoop();

    assertThat(state().getPending().legacy()).isEqualTo(1);
    assertThat(state().getPending().pity()).isTrue();
    assertThat(service.playerMeta(PLAYER).legacy()).isEqualTo(1);
  }

  @Test
  void aFailedWriteLeavesTheEndingToBeRetriedAndTellsThePlayerOnce() {
    state().winCampaign();
    failWrites.set(true);

    service.gameLoop();

    assertThat(state().getPhase()).isEqualTo(CampaignPhase.ACTIVE);
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.VICTORY);
    assertThat(state().getPending()).isNull();
    assertThat(service.renderState(PLAYER).orElseThrow()).contains("ENDING_NOT_RECORDED");
    assertThat(service.renderState(PLAYER).orElseThrow()).doesNotContain("ENDING_NOT_RECORDED");
  }

  // ---- the pending award is computed once

  @Test
  void aRefreshOnTheEndScreenShowsTheSameReport() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    var report = state().getPending();
    var meta = service.playerMeta(PLAYER);

    service.detach(PLAYER, "ws-1");
    service.attach(PLAYER, "ws-2").block();
    service.gameLoop();

    assertThat(state().getPending()).isSameAs(report);
    assertThat(service.playerMeta(PLAYER)).isEqualTo(meta);
  }

  // ---- beginning the next Generation

  @Test
  void beginGenerationIsRefusedWhileThePlayerIsStillPlaying() {
    var before = state().toSnapshot();

    var result = service.execute(PLAYER, new Command.BeginGeneration());

    assertThat(result.reason()).isEqualTo(Reason.NOT_ENDED);
    assertThat(state().toSnapshot()).isEqualTo(before);
  }

  @Test
  void beginGenerationStartsTheNextCampaignAndKeepsTheMeta() {
    worthLeaving();
    var generationOne = state();
    var yuva = founder(generationOne, "Yuva");
    assertThat(yuva.getHealthFactor()).isEqualTo(5.0);
    generationOne.recordArtifactTaken("some-artifact");
    service.execute(PLAYER, new Command.Exodus());
    var meta = service.playerMeta(PLAYER);

    var result = service.execute(PLAYER, new Command.BeginGeneration());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    var next = state();
    assertThat(next).isNotSameAs(generationOne);
    assertThat(next.getGameOutcome()).isEqualTo(GameOutcome.PLAYING);
    assertThat(next.getPhase()).isEqualTo(CampaignPhase.ACTIVE);
    assertThat(next.getPending()).isNull();
    assertThat(next.getArtifactsTaken()).isEmpty();
    assertThat(next.getHighestFloor()).isZero();
    assertThat(next.getTally().getFoodGrown()).isZero();
    assertThat(next.getUnits().values().stream().flatMap(List::stream).map(Unit::getName))
        .containsExactlyInAnyOrder("Yuva", "Fanro", "Bazen", "Calix");
    assertThat(founder(next, "Yuva").getHealthFactor()).isNotEqualTo(5.0);
    assertThat(founder(next, "Yuva").isFixedStrengths()).isFalse();

    var kept = service.playerMeta(PLAYER);
    assertThat(kept.generation()).isEqualTo(2);
    assertThat(kept.legacy()).isEqualTo(meta.legacy());
    assertThat(kept.residue()).isEqualTo(meta.residue());
    assertThat(kept.chronicle()).isEqualTo(meta.chronicle());
    assertThat(kept.lifetime()).isEqualTo(meta.lifetime());
    assertThat(kept.towerSecret()).isEqualTo(meta.towerSecret());
  }

  @Test
  void beginGenerationTwiceChangesNothingTheSecondTime() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    service.execute(PLAYER, new Command.BeginGeneration());
    var next = state();
    var before = next.toSnapshot();
    var meta = service.playerMeta(PLAYER);

    var again = service.execute(PLAYER, new Command.BeginGeneration());

    assertThat(again.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state()).isSameAs(next);
    assertThat(state().toSnapshot()).isEqualTo(before);
    assertThat(service.playerMeta(PLAYER)).isEqualTo(meta);
  }

  @Test
  void aFailedWriteLeavesTheEndedCampaignWaiting() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    var ended = state();
    var meta = service.playerMeta(PLAYER);
    failWrites.set(true);

    var result = service.execute(PLAYER, new Command.BeginGeneration());

    assertThat(result.reason()).isEqualTo(Reason.WRITE_FAILED);
    assertThat(state()).isSameAs(ended);
    assertThat(state().getPhase()).isEqualTo(CampaignPhase.OFFER);
    assertThat(service.playerMeta(PLAYER)).isEqualTo(meta);
  }

  @Test
  void theNextGenerationIsOnDiskWithItsMeta() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    service.execute(PLAYER, new Command.BeginGeneration());

    var restarted = newService();
    restarted.attach(PLAYER, "ws-2").block();

    assertThat(restarted.playerState(PLAYER).getPhase()).isEqualTo(CampaignPhase.ACTIVE);
    assertThat(restarted.playerMeta(PLAYER).generation()).isEqualTo(2);
    assertThat(restarted.playerMeta(PLAYER)).isEqualTo(service.playerMeta(PLAYER));
  }

  @Test
  void aGenerationAfterADefeatStartsHollowWithAWrit() {
    spent();
    service.gameLoop();
    assertThat(state().getGameOutcome()).isEqualTo(GameOutcome.DEFEAT);

    service.execute(PLAYER, new Command.BeginGeneration());

    var next = state();
    var start = config.getPrestige().getDefeatStart();
    assertThat(next.getVillage().getVillagersCount())
        .isEqualTo(config.getVillage().getStartingVillagers() - start.getVillagersLost());
    assertThat(next.getCredit()).isEqualTo(config.getStart().getCredit() + start.getWritCredits());
  }

  @Test
  void aNormalStartKeepsTheFullVillage() {
    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());
    service.execute(PLAYER, new Command.BeginGeneration());

    assertThat(state().getVillage().getVillagersCount())
        .isEqualTo(config.getVillage().getStartingVillagers());
    assertThat(state().getCredit()).isEqualTo(config.getStart().getCredit());
  }

  // ---- the Roll is not open yet

  @Test
  void theRollAndTheChoicesAreRefusedUntilTheyExist() {
    assertThat(service.execute(PLAYER, new Command.RollBuy("x")).reason())
        .isEqualTo(Reason.NOT_ENDED);

    worthLeaving();
    service.execute(PLAYER, new Command.Exodus());

    assertThat(service.execute(PLAYER, new Command.RollBuy("x")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(PLAYER, new Command.RollRefund("x")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(PLAYER, new Command.Choose("x")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
  }

  // ---- the seed

  @Test
  void theTowerSeedFollowsTheSecretAndTheGeneration() {
    var meta = service.playerMeta(PLAYER);

    assertThat(meta.seed()).isEqualTo(meta.seed());
    assertThat(meta.nextGeneration().seed()).isNotEqualTo(meta.seed());
    assertThat(PlayerMeta.first("another secret").seed()).isNotEqualTo(meta.seed());
  }

  private static Unit founder(GameState state, String name) {
    return state.getUnits().values().stream()
        .flatMap(List::stream)
        .filter(unit -> unit.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}

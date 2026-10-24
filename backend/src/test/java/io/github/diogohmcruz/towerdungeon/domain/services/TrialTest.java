package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.trial.AttemptLedger;
import io.github.diogohmcruz.towerdungeon.domain.trial.ConfigHash;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderLabel;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderStatus;
import io.github.diogohmcruz.towerdungeon.domain.trial.Terms;
import io.github.diogohmcruz.towerdungeon.domain.trial.TermsKind;
import io.github.diogohmcruz.towerdungeon.domain.trial.TermsRegistry;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialDesk;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialDesk.Refusal;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

class TrialTest {
  private static final String ALICE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String BOB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String RACE = "season-1";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private FileCampaignStore store;
  private AttemptLedger ledger;
  private GameService service;
  private TrialDesk desk;

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    store = new FileCampaignStore(config.getPersistence());
    ledger = new AttemptLedger(dir.resolve("trials"), mapper);
    build(terms(10, Duration.ofMinutes(90), 3, 20));
  }

  private Terms terms(int finishFloor, Duration limit, int attempts, int concurrent) {
    return new Terms(
        RACE,
        TermsKind.SEASON,
        ArchitecturePlan.VERSION,
        ConfigHash.of(config),
        424242L,
        finishFloor,
        null,
        null,
        limit,
        attempts,
        concurrent);
  }

  private void build(Terms... terms) {
    // Own seeds differ per Contender, as on a server with a secret; the Terms' seed is shared.
    service =
        new GameService(
            config,
            mapper,
            new SeedSource("a server secret"),
            ArchitecturePlan.generate(
                config.getArchitecture().getSeed(),
                config.getTower().getMaxFloor(),
                config.getBoss().getInterval(),
                config.getEnemies().getSurgeInterval()),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, mapper, config.getPersistence(), "test", System::nanoTime));
    desk = new TrialDesk(new TermsRegistry(List.of(terms)), ledger, config);
    service.setTrials(desk);
  }

  private String enter(String player) {
    return service.attachTrial(player, RACE, false, "ws-" + player).block().slotId();
  }

  @Test
  void aTrialStartsEveryoneOnTheSameKit() {
    var id = enter(ALICE);
    var state = service.playerState(id);

    assertThat(state.getCredit()).isEqualTo(10.0);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(20);
    assertThat(state.getVillage().getFood()).isEqualTo(100.0);
    assertThat(state.getUnits().values().stream().mapToInt(List::size).sum()).isEqualTo(4);
    assertThat(state.getAchievedMilestones()).isEmpty();
    assertThat(state.getOpenedShortcuts()).isEmpty();
    assertThat(state.getTower()).isNull();
    assertThat(state.getTrial().getStatus()).isEqualTo(ContenderStatus.RACING);
    assertThat(state.getTrial().getTicksSinceStart()).isZero();
    assertThat(state.getTrial().getFinishFloor()).isEqualTo(10);
    assertThat(id).matches("[0-9a-f]{32}").isNotEqualTo(ALICE);
  }

  @Test
  void theFrameShowsTheTrialButNeverTheSeed() {
    var id = enter(ALICE);

    var frame = service.renderState(id).orElseThrow();

    assertThat(frame)
        .contains("\"trial\":{")
        .contains("\"raceId\":\"season-1\"")
        .contains("\"status\":\"RACING\"")
        .contains("\"ticksSinceStart\":0")
        .contains("\"finishFloor\":10")
        .doesNotContain("424242")
        .doesNotContain("seed");
  }

  @Test
  void aRealCampaignFrameHasNoTrialBlock() {
    service.attach(ALICE, "ws").block();

    assertThat(service.renderState(ALICE).orElseThrow()).doesNotContain("\"trial\"");
  }

  @Test
  void everyContenderStartsWithTheIdenticalFourFoundersButOwnLuck() {
    var alice = service.playerState(enter(ALICE));
    var bob = service.playerState(enter(BOB));

    assertThat(strengths(alice)).isNotEmpty().isEqualTo(strengths(bob));
    // The luck after the start is each Contender's own.
    assertThat(alice.getRandom().combat().nextLong())
        .isNotEqualTo(bob.getRandom().combat().nextLong());
  }

  private static List<String> strengths(GameState s) {
    return s.getUnits().values().stream()
        .flatMap(List::stream)
        .map(u -> u.getName() + " " + u.getHealthFactor() + " " + u.getDamageFactor())
        .sorted()
        .toList();
  }

  /** Sets out with everyone and takes lifecycle ticks until the Party stands on {@code floor}. */
  private static List<String> garrisonOn(GameService service, String id, int floor) {
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    for (var i = 0; i < 20 && service.playerState(id).getTower().getCurrentFloor() < floor; i++) {
      service.gameLoop();
    }
    return service.playerState(id).getTower().getCurrentTowerFloor().getEnemies().stream()
        .map(TrialTest::describe)
        .toList();
  }

  private static String describe(Enemy enemy) {
    return enemy.getStats() + " " + enemy.getParts() + " " + enemy.getMaxHealth();
  }

  @Test
  void twoPlayersGetTheSameGarrisonDrawOnTheSameFloor() {
    var alice = enter(ALICE);
    var bob = enter(BOB);

    var first = garrisonOn(service, alice, 1);
    var second = garrisonOn(service, bob, 1);

    assertThat(first).isNotEmpty().isEqualTo(second);
  }

  @Test
  void aFinishFloorIsAGuardianFloorApartFromMaxFloor() {
    build(terms(7, Duration.ofMinutes(90), 3, 20));
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));

    var tower = state.getTower();
    assertThat(tower.getMaxFloor()).isEqualTo(100);
    for (var i = 0; i < 7; i++) {
      tower.moveToNextFloor();
    }
    var finish = tower.getFloors().get(7);
    assertThat(finish.isBoss()).isTrue();
    assertThat(tower.getFloors().get(6).isBoss()).isFalse();
    // Its difficulty still comes from its height on the real Tower, not from the Finish Floor.
    assertThat(finish.getDifficulty()).isEqualTo(1);
  }

  @Test
  void theClockCountsConnectedTicksOnly() {
    var id = enter(ALICE);
    var state = service.playerState(id);

    service.gameLoop();
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(2);

    service.detach(ALICE, "ws-" + ALICE);
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(2);
  }

  @Test
  void aTrialHasNoYearAndNoQuarterEffects() {
    var id = enter(ALICE);
    var state = service.playerState(id);

    for (var i = 0; i < 160; i++) {
      service.gameLoop();
    }

    assertThat(state.getYear()).isNull();
    assertThat(state.getQuarter()).isNull();
    assertThat(state.getSeaRisk()).isNull();
    assertThat(state.sanityDrainScale()).isEqualTo(1d);
    assertThat(state.attritionScale()).isEqualTo(1d);
    assertThat(state.toSnapshot().campaign().yearTick()).isZero();
  }

  @Test
  void theClockStopsOnAHeldParty() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    service.detach(ALICE, "ws-" + ALICE);
    assertThat(service.slotOf(id).isHeld()).isTrue();

    service.attachTrial(ALICE, RACE, false, "ws-2").block();
    service.gameLoop();

    assertThat(state.getTrial().getTicksSinceStart()).isZero();
    service.execute(id, new Command.Resume());
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(1);
  }

  @Test
  void aDeliberateHoldCostsNoRaceTime() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(1);

    service.execute(id, new Command.Hold());
    service.gameLoop();
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(1);

    service.execute(id, new Command.Resume());
    service.gameLoop();
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(2);
  }

  @Test
  void whenTheClockRunsOutTheTrialEndsAndTheResultFreezes() {
    build(terms(10, Duration.ofSeconds(3), 3, 20));
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));

    for (var i = 0; i < 10; i++) {
      service.gameLoop();
    }

    assertThat(state.getTrial().getStatus()).isEqualTo(ContenderStatus.TIMED_OUT);
    assertThat(state.getTrial().getTicksSinceStart()).isEqualTo(3);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(ledger.standing(RACE, ALICE).currentClosed()).isTrue();
    var frozen = state.toSnapshot();
    service.gameLoop();
    service.invasionTimer();
    assertThat(state.toSnapshot()).isEqualTo(frozen);
    assertThat(service.execute(id, new Command.SellFood()).reason()).isEqualTo(Reason.TRIAL_CLOSED);
    assertThat(service.execute(id, new Command.StartExpedition(Map.of(), 0)).reason())
        .isEqualTo(Reason.TRIAL_CLOSED);
  }

  @Test
  void slayingTheFinishFloorsGuardianEndsTheTrialWithTheParty_Extracted() {
    build(terms(1, Duration.ofMinutes(90), 3, 20));
    var id = enter(ALICE);
    var state = service.playerState(id);
    // A strong Party so the one Guardian falls.
    state.getUnits().values().stream()
        .flatMap(List::stream)
        .forEach(unit -> unit.setDamageFactor(500));
    service.execute(id, new Command.StartExpedition(Map.of(), 0));

    for (var i = 0; i < 300 && !state.isTrialClosed(); i++) {
      service.gameLoop();
      for (var j = 0; j < 100 && state.isExpeditionActive(); j++) {
        service.invasionTimer();
      }
    }

    assertThat(state.getTrial().getStatus()).isEqualTo(ContenderStatus.FINISHED);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getLastRunSummary().outcome()).isEqualTo("EXTRACTED");
    assertThat(state.getTrial().getTicksSinceStart()).isPositive();
    assertThat(state.getTower().getSlainGuardians()).contains(1);
    assertThat(ledger.standing(RACE, ALICE).currentClosed()).isTrue();
    assertThat(service.execute(id, new Command.Extract()).reason()).isEqualTo(Reason.TRIAL_CLOSED);
  }

  @Test
  void aTrialCountsAnExpeditionOnlyIfItClearedThreeFloors() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    service.execute(id, new Command.Extract());
    assertThat(state.getExpeditionsCompleted()).isZero();

    for (var i = 0; i < 8; i++) {
      service.execute(id, new Command.StartExpedition(Map.of(), 0));
      service.execute(id, new Command.Extract());
    }
    assertThat(state.getExpeditionsCompleted()).isZero();

    // The same Campaign facts outside a Trial count every Expedition.
    service.attach(ALICE, "ws-real").block();
    var real = service.playerState(ALICE);
    service.execute(ALICE, new Command.StartExpedition(Map.of(), 0));
    service.execute(ALICE, new Command.Extract());
    assertThat(real.getExpeditionsCompleted()).isEqualTo(1);
  }

  @Test
  void anExpeditionThatClearedThreeFloorsCounts() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    var tower = state.getTower();
    // Three Floors climbed, as the Expedition Summary counts them.
    for (var i = 0; i < 3; i++) {
      tower.moveToNextFloor();
    }

    service.execute(id, new Command.Extract());

    assertThat(state.getExpeditionsCompleted()).isEqualTo(1);
  }

  @Test
  void aShortcutExpeditionCountsFloorsFromWhereItSetOut() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    state.getOpenedShortcuts().add(ShortcutType.SUPPLY_LIFT);
    service.execute(id, new Command.StartExpedition(Map.of(), ShortcutType.SUPPLY_LIFT.getFloor()));
    state.getTower().moveToNextFloor();
    state.getTower().moveToNextFloor();

    service.execute(id, new Command.Extract());

    assertThat(state.getExpeditionsCompleted()).isZero();
  }

  @Test
  void attemptsAreLimitedAndEachHasAFileOfItsOwn() {
    var first = enter(ALICE);
    assertThatThrownBy(() -> service.attachTrial(ALICE, RACE, true, "ws").block())
        .isInstanceOfSatisfying(
            TrialDesk.Refused.class, e -> assertThat(e.reason()).isEqualTo(Refusal.ATTEMPT_OPEN));
    service.playerState(first).getTrial().timeOut();
    service.gameLoop();

    var second = service.attachTrial(ALICE, RACE, true, "ws").block().slotId();
    assertThat(second).isNotEqualTo(first);
    assertThat(service.playerState(second).getTrial().getAttempt()).isEqualTo(2);
    service.playerState(second).getTrial().timeOut();
    service.gameLoop();
    var third = service.attachTrial(ALICE, RACE, true, "ws").block().slotId();
    service.playerState(third).getTrial().timeOut();
    service.gameLoop();

    assertThatThrownBy(() -> service.attachTrial(ALICE, RACE, true, "ws").block())
        .isInstanceOfSatisfying(
            TrialDesk.Refused.class, e -> assertThat(e.reason()).isEqualTo(Refusal.NO_ATTEMPTS));
    // Without asking for a new one, the finished third attempt can still be looked at.
    assertThat(service.attachTrial(ALICE, RACE, false, "ws").block().slotId()).isEqualTo(third);
  }

  @Test
  void theAttemptCountLivesInTheLedgerNotInTheFile() {
    enter(ALICE);
    var reopened = new AttemptLedger(dir.resolve("trials"), mapper);

    assertThat(reopened.standing(RACE, ALICE).started()).isEqualTo(1);
    assertThat(reopened.standing(RACE, ALICE).currentClosed()).isFalse();
    assertThat(reopened.standing(RACE, BOB).started()).isZero();
  }

  @Test
  void aRefreshResumesTheSameTrialWithoutSpendingAnAttempt() {
    var id = enter(ALICE);
    service.gameLoop();
    service.gameLoop();
    service.detach(ALICE, "ws-" + ALICE);
    awaitSaved(id);
    config.getPersistence().setLinger(Duration.ZERO);
    service.evictLingering(System.nanoTime() + Duration.ofMinutes(1).toNanos());
    assertThat(service.hasSlot(id)).isFalse();

    var again = service.attachTrial(ALICE, RACE, false, "ws-2").block().slotId();

    assertThat(again).isEqualTo(id);
    assertThat(service.playerState(id).getTrial().getTicksSinceStart()).isEqualTo(2);
    assertThat(service.playerState(id).getTrial().getAttempt()).isEqualTo(1);
    assertThat(ledger.standing(RACE, ALICE).started()).isEqualTo(1);
  }

  @Test
  void aResumedTrialKeepsItsTower() {
    var id = enter(ALICE);
    var before = garrisonOn(service, id, 1);
    service.gameLoop();
    service.detach(ALICE, "ws-" + ALICE);
    awaitSaved(id);
    service.evictLingering(System.nanoTime() + Duration.ofMinutes(1).toNanos());

    service.attachTrial(ALICE, RACE, false, "ws-2").block();

    assertThat(service.playerState(id).getTower().getCurrentFloor()).isGreaterThanOrEqualTo(1);
    assertThat(before).isNotEmpty();
  }

  @Test
  void enteringATrialDetachesTheRealCampaignAndLeavingReverses() {
    var replacedReal = new AtomicInteger();
    var replacedTrial = new AtomicInteger();
    var real = service.attach(ALICE, "ws-real").block();
    real.replaced().subscribe(null, null, replacedReal::incrementAndGet);
    service.execute(ALICE, new Command.BuyVillagers());

    var trial = service.attachTrial(ALICE, RACE, false, "ws-trial").block();
    trial.replaced().subscribe(null, null, replacedTrial::incrementAndGet);

    assertThat(replacedReal.get()).isEqualTo(1);
    assertThat(trial.slotId()).isNotEqualTo(ALICE);
    assertThat(service.hasSlot(ALICE)).isTrue();
    service.detach(ALICE, "ws-real"); // what the closed connection's cleanup does
    assertThat(service.isConnected(ALICE)).isFalse();
    assertThat(service.isConnected(trial.slotId())).isTrue();

    var back = service.attach(ALICE, "ws-real-2").block();

    assertThat(back.slotId()).isEqualTo(ALICE);
    assertThat(replacedTrial.get()).isEqualTo(1);
    service.detach(ALICE, "ws-trial");
    assertThat(service.isConnected(trial.slotId())).isFalse();
    assertThat(service.isConnected(ALICE)).isTrue();
  }

  @Test
  void theTrialAndTheRealCampaignNeverShareState() {
    service.attach(ALICE, "ws-real").block();
    service.playerState(ALICE).setCredit(999.0);
    var id = enter(ALICE);

    assertThat(service.playerState(id).getCredit()).isEqualTo(10.0);
    assertThat(service.playerState(ALICE).getCredit()).isEqualTo(999.0);
  }

  @Test
  void aTrialHasNoSuccession() {
    var id = enter(ALICE);

    assertThat(service.execute(id, new Command.Exodus()).reason()).isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(id, new Command.BeginGeneration()).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.renderState(id).orElseThrow()).contains("\"trial\":{");
  }

  @Test
  void aTrialFileCannotBeExportedOrImported() {
    var id = enter(ALICE);
    var snapshot = service.playerState(id).toSnapshot();

    assertThat(service.snapshotOf(id)).isEmpty();
    assertThat(service.replaceIfIdle(id, snapshot, _ -> 1L)).isFalse();
    // Nor can the Key's own id reach it: the id of a file is not derived from the Key alone.
    assertThat(service.snapshotOf(ALICE)).isEmpty();
  }

  @Test
  void aBalanceThatDoesNotMatchTheTermsRefusesTheTrial() {
    config.getEnemies().setBaseBudget(config.getEnemies().getBaseBudget() + 1);

    assertThatThrownBy(() -> service.attachTrial(ALICE, RACE, false, "ws").block())
        .isInstanceOfSatisfying(
            TrialDesk.Refused.class,
            e -> assertThat(e.reason()).isEqualTo(Refusal.CONFIG_MISMATCH));
    assertThat(ledger.standing(RACE, ALICE).started()).isZero();
  }

  @Test
  void aRaceThatIsUnknownClosedOrASprintRefusesEntry() {
    var now = Instant.now();
    build(
        new Terms(
            "closed",
            TermsKind.SEASON,
            1,
            ConfigHash.of(config),
            1,
            10,
            null,
            now.minusSeconds(5),
            Duration.ofMinutes(5),
            3,
            20),
        new Terms(
            "later",
            TermsKind.SEASON,
            1,
            ConfigHash.of(config),
            1,
            10,
            now.plusSeconds(500),
            null,
            Duration.ofMinutes(5),
            3,
            20),
        new Terms(
            "sprint",
            TermsKind.SPRINT,
            1,
            ConfigHash.of(config),
            1,
            10,
            null,
            null,
            Duration.ofMinutes(5),
            3,
            20));

    for (var entry :
        Map.of(
                "nope", Refusal.UNKNOWN_RACE,
                "closed", Refusal.NOT_OPEN,
                "later", Refusal.NOT_OPEN,
                "sprint", Refusal.SPRINT_NOT_OPEN)
            .entrySet()) {
      assertThatThrownBy(() -> service.attachTrial(ALICE, entry.getKey(), false, "ws").block())
          .isInstanceOfSatisfying(
              TrialDesk.Refused.class, e -> assertThat(e.reason()).isEqualTo(entry.getValue()));
    }
  }

  @Test
  void aStalePlanVersionRefusesTheTrial() {
    build(
        new Terms(
            RACE,
            TermsKind.SEASON,
            ArchitecturePlan.VERSION + 1,
            ConfigHash.of(config),
            1,
            10,
            null,
            null,
            Duration.ofMinutes(5),
            3,
            20));

    assertThatThrownBy(() -> service.attachTrial(ALICE, RACE, false, "ws").block())
        .isInstanceOfSatisfying(
            TrialDesk.Refused.class, e -> assertThat(e.reason()).isEqualTo(Refusal.PLAN_MISMATCH));
  }

  @Test
  void onlyMaxConcurrentTrialsMayBeConnected() {
    build(terms(10, Duration.ofMinutes(90), 3, 1));
    enter(ALICE);

    assertThatThrownBy(() -> service.attachTrial(BOB, RACE, false, "ws").block())
        .isInstanceOfSatisfying(
            TrialDesk.Refused.class, e -> assertThat(e.reason()).isEqualTo(Refusal.FULL));
    assertThat(ledger.standing(RACE, BOB).started()).isZero();
    // The one inside may come back on a new connection.
    service.attachTrial(ALICE, RACE, false, "ws-2").block();
    service.detach(ALICE, "ws-" + ALICE);
    service.detach(ALICE, "ws-2");
    assertThat(service.attachTrial(BOB, RACE, false, "ws").block().slotId()).isNotNull();
  }

  private void awaitSaved(String id) {
    var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (store.load(id).block() == null && System.nanoTime() < deadline) {
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void aTrialIsSavedUnderItsOwnIdNeverTheKeys() throws Exception {
    var id = enter(ALICE);
    service.gameLoop();
    service.detach(ALICE, "ws-" + ALICE);
    awaitSaved(id);

    assertThat(store.load(id).block()).isNotNull();
    assertThat(store.load(ALICE).block()).isNull();
  }

  /** Wins a one-Floor race for {@code player} and returns the Trial's id. */
  private String win(String player, String alias) {
    build(terms(1, Duration.ofMinutes(90), 3, 20));
    var id =
        service
            .attachTrial(player, RACE, false, "ws-" + player, alias, ContenderLabel.HUMAN)
            .block()
            .slotId();
    var state = service.playerState(id);
    state.getUnits().values().stream()
        .flatMap(List::stream)
        .forEach(unit -> unit.setDamageFactor(500));
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    for (var i = 0; i < 300 && !state.isTrialClosed(); i++) {
      service.gameLoop();
      for (var j = 0; j < 100 && state.isExpeditionActive(); j++) {
        service.invasionTimer();
      }
    }
    service.gameLoop();
    return id;
  }

  private void awaitRaceRecord(String player) {
    var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      var meta = service.playerMeta(player);
      if (meta != null && !meta.raceRecords().isEmpty()) {
        return;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void aFinishedTrialLeavesAResultInTheStoreWithItsCostAndTimeline() {
    var id = win(ALICE, "Ada");
    ledger.drain();

    var results = ledger.results(RACE);
    assertThat(results).hasSize(1);
    var result = results.getFirst();
    assertThat(result.status()).isEqualTo(ContenderStatus.FINISHED);
    assertThat(result.alias()).isEqualTo("Ada");
    assertThat(result.playerId()).isEqualTo(ALICE);
    assertThat(result.seconds()).isEqualTo(service.playerState(id).getTrial().getTicksSinceStart());
    assertThat(result.finishFloor()).isEqualTo(1);
    assertThat(result.highestFloor()).isEqualTo(1);
    assertThat(result.cost().expeditions()).isEqualTo(1);
    assertThat(result.cost().wipes()).isZero();
    assertThat(new AttemptLedger(dir.resolve("trials"), mapper).results(RACE)).hasSize(1);
  }

  @Test
  void theCostIsCountedAsTheTrialGoesAndShownInTheFrame() {
    var id = enter(ALICE);
    var state = service.playerState(id);
    service.execute(id, new Command.StartExpedition(Map.of(), 0));
    state.buildRunSummary(RunSummary.WIPED);

    var cost = state.getTrialCost();
    assertThat(cost.expeditions()).isEqualTo(1);
    assertThat(cost.wipes()).isEqualTo(1);
    assertThat(service.renderState(id).orElseThrow())
        .contains("\"trialCost\":{\"expeditions\":1,\"wipes\":1");
    service.attach(BOB, "ws-bob").block();
    assertThat(service.renderState(BOB).orElseThrow()).doesNotContain("trialCost");
  }

  @Test
  void aHostileAliasIsCleanedBeforeItReachesTheStore() {
    win(ALICE, "<script>alert('x')</script>");
    ledger.drain();

    assertThat(ledger.results(RACE).getFirst().alias())
        .doesNotContain("<", ">", "(", ")")
        .isEqualTo("scriptalert'x'script");
  }

  @Test
  void theRaceRecordIsWrittenIntoTheRealCampaignAndSaved() {
    win(ALICE, "Ada");
    awaitRaceRecord(ALICE);

    var records = service.playerMeta(ALICE).raceRecords();
    assertThat(records).hasSize(1);
    assertThat(records.getFirst().line())
        .matches(
            "Race of \\d{4}-\\d{2}-\\d{2}\\. Floor 1\\. 1st of 1\\. \\d+:\\d{2}\\. 0 fallen\\.");
    assertThat(service.renderState(ALICE).orElseThrow())
        .contains("\"raceRecords\":[{")
        .contains(records.getFirst().line())
        .contains("\"pillarMarks\":[]");

    config.getPersistence().setLinger(Duration.ZERO);
    service.evictLingering(System.nanoTime() + Duration.ofMinutes(1).toNanos());
    awaitSaved(ALICE);
    assertThat(new String(store.load(ALICE).block().payload())).contains(records.getFirst().line());
  }

  @Test
  void thePillarMarkShowsOnlyOnADiscoveredFloor() {
    win(ALICE, "Ada");
    awaitRaceRecord(ALICE);
    assertThat(service.renderState(ALICE).orElseThrow()).contains("\"pillarMarks\":[]");

    service.playerState(ALICE).recordFloorReached(1);

    assertThat(service.renderState(ALICE).orElseThrow()).contains("\"pillarMarks\":[1]");
    // A Trial's own frame carries neither.
    var trial = service.attachTrial(BOB, RACE, false, "ws-bob").block().slotId();
    assertThat(service.renderState(trial).orElseThrow()).contains("\"pillarMarks\":[]");
  }

  @Test
  void aRaceRecordGivesNoLegacyAndChangesNoToll() {
    win(ALICE, "Ada");
    awaitRaceRecord(ALICE);

    var meta = service.playerMeta(ALICE);

    assertThat(meta.legacy()).isZero();
    assertThat(meta.chronicle()).isEmpty();
    assertThat(meta.generation()).isEqualTo(1);
  }
}

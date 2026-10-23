package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver.Stop;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class SandboxCampaignTest {
  private static final String HUMAN = "0123456789abcdef0123456789abcdef";
  private static final String GRANT = "grant-a";

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private CampaignStore store;
  private AutosaveCoordinator saves;
  private GameService service;
  private SandboxRegistry registry;

  @BeforeEach
  void setUp() {
    config.getAgent().getSandbox().setEnabled(true);
    store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    saves =
        new AutosaveCoordinator(store, mapper, config.getPersistence(), "test", System::nanoTime);
    service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            saves);
    registry = new SandboxRegistry(service, config);
  }

  private String open() {
    var opened = registry.open(GRANT, null, null);
    assertThat(opened.status()).isEqualTo(Status.OPENED);
    return opened.sandboxId();
  }

  // ---- the manual driver

  @Test
  void aSandboxIsNotAdvancedByTheRealTimeLoops() {
    var sandbox = open();
    service.attach(HUMAN, "ws").block();
    var sandboxSeq = service.stateSeq(sandbox).orElseThrow();
    var humanSeq = service.stateSeq(HUMAN).orElseThrow();

    service.gameLoop();
    service.invasionTimer();

    assertThat(service.stateSeq(sandbox).orElseThrow()).isEqualTo(sandboxSeq);
    assertThat(service.stateSeq(HUMAN).orElseThrow()).isGreaterThan(humanSeq);
  }

  @Test
  void theManualDriverRunsOneLifecycleTickPerSimulatedSecond() {
    var sandbox = open();
    var before = service.stateSeq(sandbox).orElseThrow();

    var advance = registry.advance(GRANT, sandbox, 25, null);

    assertThat(advance.stop()).isEqualTo(Stop.COMPLETED);
    assertThat(advance.ticksRun()).isEqualTo(25);
    assertThat(advance.simulatedSeconds()).isEqualTo(25);
    assertThat(service.stateSeq(sandbox).orElseThrow()).isEqualTo(before + 25);
  }

  @Test
  void twoSandboxesAdvancedTheSameWayEndInTheSameVillage() {
    var first = open();
    var second = open();

    registry.advance(GRANT, first, 300, null);
    registry.advance(GRANT, second, 300, null);

    var a = service.playerState(first);
    var b = service.playerState(second);
    assertThat(a.getVillage().getFood()).isEqualTo(b.getVillage().getFood());
    assertThat(a.getCredit()).isEqualTo(b.getCredit());
  }

  @Test
  void aConditionStopsTheAdvanceAfterTheTickThatMetIt() {
    var sandbox = open();
    var ticks = new AtomicInteger();

    var advance = registry.advance(GRANT, sandbox, 100, _ -> ticks.incrementAndGet() == 5);

    assertThat(advance.stop()).isEqualTo(Stop.CONDITION);
    assertThat(advance.ticksRun()).isEqualTo(5);
  }

  @Test
  void oneAttritionCallOfAThousandMillisecondsEqualsAHundredOfTen() {
    var sandbox = open();
    service.execute(sandbox, new Command.StartExpedition(Map.of(), 0, 0));
    var onTower = service.playerState(sandbox);
    assertThat(onTower.hasUnitsOnTower()).isTrue();
    var twin =
        GameState.restore(
            onTower.toSnapshot(), config.copy(), new RandomSource(7), mock(ArchitecturePlan.class));

    var before = health(onTower);

    service.attritionTick(onTower, 1000);
    for (var i = 0; i < 100; i++) {
      service.attritionTick(twin, 10);
    }

    assertThat(health(onTower)).isGreaterThan(0).isLessThan(before);
    assertThat(health(onTower)).isCloseTo(health(twin), org.assertj.core.data.Offset.offset(1e-6));
  }

  private static double health(GameState state) {
    return state.getUnitsOnTower().values().stream()
        .flatMap(java.util.List::stream)
        .mapToDouble(BaseUnit::getCurrentHealth)
        .sum();
  }

  // ---- caps

  @Test
  void aWaitRunsAtMostTheConfiguredTicks() {
    var sandbox = open();

    var advance = registry.advance(GRANT, sandbox, 10_000, null);

    assertThat(advance.stop()).isEqualTo(Stop.TICK_CAP);
    assertThat(advance.ticksRun()).isEqualTo(3_600);
  }

  @Test
  void aCampaignStopsAtItsSimulatedTimeCeiling() {
    var opened = registry.open(GRANT, null, 100L);
    var sandbox = opened.sandboxId();
    assertThat(opened.maxSimulatedSeconds()).isEqualTo(100);

    var first = registry.advance(GRANT, sandbox, 60, null);
    var second = registry.advance(GRANT, sandbox, 60, null);
    var third = registry.advance(GRANT, sandbox, 60, null);

    assertThat(first.stop()).isEqualTo(Stop.COMPLETED);
    assertThat(second.stop()).isEqualTo(Stop.SIMULATED_TIME_CAP);
    assertThat(second.ticksRun()).isEqualTo(40);
    assertThat(third.ticksRun()).isZero();
    assertThat(third.simulatedSeconds()).isEqualTo(100);
  }

  @Test
  void aCallCannotRaiseTheCeilingAboveTheConfiguredOne() {
    var opened = registry.open(GRANT, null, 1_000_000L);

    assertThat(opened.maxSimulatedSeconds()).isEqualTo(21_600);
  }

  @Test
  void aCallStopsWhenItsWallTimeIsSpent() {
    config.getAgent().getSandbox().setCallWallTime(Duration.ZERO);
    var sandbox = open();

    var advance = registry.advance(GRANT, sandbox, 500, null);

    assertThat(advance.stop()).isEqualTo(Stop.WALL_TIME_CAP);
    assertThat(advance.ticksRun()).isEqualTo(1);
  }

  @Test
  void aGrantMayHoldEightLiveSandboxesAndNoMore() {
    for (var i = 0; i < 8; i++) {
      open();
    }

    var ninth = registry.open(GRANT, null, null);

    assertThat(ninth.status()).isEqualTo(Status.GRANT_CAP);
    assertThat(ninth.sandboxId()).isNull();
    assertThat(registry.open("grant-b", null, null).status()).isEqualTo(Status.OPENED);
  }

  @Test
  void closingASandboxFreesItsPlaceUnderTheCap() {
    var ids = new java.util.ArrayList<String>();
    for (var i = 0; i < 8; i++) {
      ids.add(open());
    }

    assertThat(registry.close(GRANT, ids.getFirst())).isTrue();

    assertThat(registry.liveFor(GRANT)).isEqualTo(7);
    assertThat(service.hasSlot(ids.getFirst())).isFalse();
    assertThat(registry.open(GRANT, null, null).status()).isEqualTo(Status.OPENED);
  }

  @Test
  void theServerHoldsAtMostTheGlobalCapAcrossGrants() {
    config.getAgent().getSandbox().setMaxGlobal(3);
    registry.open("a", null, null);
    registry.open("b", null, null);
    registry.open("c", null, null);

    var fourth = registry.open("d", null, null);

    assertThat(fourth.status()).isEqualTo(Status.GLOBAL_CAP);
    assertThat(registry.live()).isEqualTo(3);
  }

  @Test
  void nothingOpensWhenSandboxesAreSwitchedOff() {
    config.getAgent().getSandbox().setEnabled(false);

    var opened = registry.open(GRANT, null, null);

    assertThat(opened.status()).isEqualTo(Status.DISABLED);
    assertThat(registry.live()).isZero();
  }

  @Test
  void aGrantCannotAdvanceOrCloseASandboxItDoesNotHold() {
    var sandbox = open();

    assertThat(registry.advance("grant-b", sandbox, 5, null).stop()).isEqualTo(Stop.NOT_FOUND);
    assertThat(registry.close("grant-b", sandbox)).isFalse();
    assertThat(service.hasSlot(sandbox)).isTrue();
  }

  @Test
  void closingAGrantClosesAllItsSandboxes() {
    open();
    open();
    registry.open("grant-b", null, null);

    assertThat(registry.closeAll(GRANT)).isEqualTo(2);

    assertThat(registry.live()).isEqualTo(1);
  }

  // ---- the response says what it cannot promise yet

  @Test
  void theOpeningAndEveryAdvanceSayDrawDeterminismIsNotHereYet() {
    var opened = registry.open(GRANT, null, null);
    var advance = registry.advance(GRANT, opened.sandboxId(), 1, null);

    assertThat(opened.note()).contains("Draws are seeded");
    assertThat(advance.note()).contains("Draws are seeded");
  }

  // ---- one configuration each

  @Test
  void aSandboxOverrideReachesNeitherAnotherSandboxNorTheSharedConfiguration() {
    var tuned =
        registry.open(GRANT, copy -> copy.getReward().setCreditPerFloor(99.0), null).sandboxId();
    var plain = open();
    service.attach(HUMAN, "ws").block();

    assertThat(service.playerState(tuned).getConfig().getReward().getCreditPerFloor())
        .isEqualTo(99.0);
    assertThat(service.playerState(plain).getConfig().getReward().getCreditPerFloor())
        .isEqualTo(2.0);
    assertThat(service.playerState(HUMAN).getConfig().getReward().getCreditPerFloor())
        .isEqualTo(2.0);
    assertThat(config.getReward().getCreditPerFloor()).isEqualTo(2.0);
  }

  @Test
  void everyCampaignOwnsItsConfigurationEvenWithoutAnOverride() {
    var one = open();
    var two = open();
    service.attach(HUMAN, "ws").block();

    var configs =
        java.util.List.of(
            service.playerState(one).getConfig(),
            service.playerState(two).getConfig(),
            service.playerState(HUMAN).getConfig());

    assertThat(configs).allSatisfy(c -> assertThat(c).isNotSameAs(config).isEqualTo(config));
    assertThat(configs.get(0)).isNotSameAs(configs.get(1)).isNotSameAs(configs.get(2));
    assertThat(configs.get(1)).isNotSameAs(configs.get(2));
  }

  // ---- never written, never evicted

  @Test
  void aSandboxIsNeverWrittenToTheStore() {
    var sandbox = open();
    service.execute(sandbox, new Command.Recruit(UnitStats.WARRIOR, 1));
    service.execute(sandbox, new Command.SellFood());
    registry.advance(GRANT, sandbox, 120, null);

    service.autosave();
    service.shutdown();
    saves.flush(service.slotOf(sandbox));
    saves.tick(service.slotOf(sandbox));

    verify(store, never())
        .save(anyString(), anyLong(), any(), anyInt(), anyString(), any(byte[].class));
    verify(store, never())
        .save(anyString(), anyLong(), any(), anyInt(), anyString(), any(Supplier.class));
  }

  @Test
  void aSandboxIsNotEvictedForHavingNoConnection() {
    var sandbox = open();

    service.evictLingering(Long.MAX_VALUE / 2);

    assertThat(service.hasSlot(sandbox)).isTrue();
  }

  @Test
  void commandsWorkOnASandboxThroughTheSameEntryPoint() {
    var sandbox = open();

    var result = service.execute(sandbox, new Command.Recruit(UnitStats.WARRIOR, 1));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
  }
}

package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Trade;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.observation.EventLog;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins what every {@code GameService} action does today, including the rules that look odd (the
 * strict {@code >} on Credits, Loot-first spending, an empty selection meaning everyone), and what
 * the {@code CommandResult} says about each. Where a pinned rule is a quirk and not a decision, the
 * test says so; changing it is a game-design call, not a refactor.
 */
class GameServiceCommandsTest {
  private static final String PLAYER = "player";

  private final GameProperties config = new GameProperties();
  private GameService service;
  private GameState state;

  @BeforeEach
  void setUp() {
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    service =
        new GameService(
            config,
            new ObjectMapper(),
            SeedSource.fixed(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            mock(AutosaveCoordinator.class));
    service.attach(PLAYER, "ws").block();
    state = service.playerState(PLAYER);
  }

  private static int count(Map<UnitStats, List<Unit>> units, UnitStats type) {
    return units.getOrDefault(type, List.of()).size();
  }

  private static Map<UnitStats, Integer> selection(Object... pairs) {
    var map = new LinkedHashMap<UnitStats, Integer>();
    for (var i = 0; i < pairs.length; i += 2) {
      map.put((UnitStats) pairs[i], (Integer) pairs[i + 1]);
    }
    return map;
  }

  // ---- recruiting

  @Test
  void recruitingFillsTheRequestWhileCreditsStayAboveTheCost() {
    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 3));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(result.reason()).isEqualTo(Reason.OK);
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(3 + 3);
    assertThat(state.getCredit()).isEqualTo(1.0);
  }

  @Test
  void theCreditsMustBeStrictlyGreaterThanTheCost() {
    // 10 Credits, an Archer costs 5: the first is bought (10 > 5), the second is not (5 > 5 fails).
    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.ARCHER, 2));

    assertThat(result.outcome()).isEqualTo(Outcome.PARTIAL);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_CREDITS);
    assertThat(count(state.getUnits(), UnitStats.ARCHER)).isEqualTo(1 + 1);
    assertThat(state.getCredit()).isEqualTo(5.0);
  }

  @Test
  void exactlyTheCostIsNotEnoughToRecruit() {
    state.setCredit(5.0);

    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.ARCHER, 1));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_CREDITS);
    assertThat(count(state.getUnits(), UnitStats.ARCHER)).isEqualTo(1);
    assertThat(state.getCredit()).isEqualTo(5.0);
  }

  @Test
  void aLockedUnitCannotBeRecruited() {
    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.MAGE, 1));

    assertThat(result.reason()).isEqualTo(Reason.NOT_UNLOCKED);
    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state.getCredit()).isEqualTo(10.0);
  }

  @Test
  void recruitingSpendsCarriedLootCreditsBeforeTheVault() {
    state.setCarriedCredits(4.0);

    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 1));

    // The Warrior costs 3, all of it taken from the party's purse.
    assertThat(state.getCarriedCredits()).isEqualTo(1.0);
    assertThat(state.getCredit()).isEqualTo(10.0);
  }

  @Test
  void aNullQuantityIsRefusedWhateverTheCredits() {
    // Was a NullPointerException when the Credits covered one; the agent tools needed it refused.
    var covered = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, null));
    state.setCredit(0.0);
    var broke = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, null));

    assertThat(covered.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(broke.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(3);
  }

  @Test
  void aNonPositiveQuantityRecruitsNothing() {
    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 0));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(state.getCredit()).isEqualTo(10.0);
  }

  // ---- villagers and selling

  @Test
  void buyingVillagersIsAllInAndComparesTheCountToTheVaultCreditsOnly() {
    state.setCredit(100.0);
    state.setCarriedCredits(1_000.0);

    var result = service.execute(PLAYER, new Command.BuyVillagers());

    // Villager 21 costs 21, 22 costs 22, 23 costs 23, 24 costs 24; then 24 < 10 stops it.
    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(24);
    assertThat(state.getCredit()).isEqualTo(10.0);
    assertThat(state.getCarriedCredits()).isEqualTo(1_000.0);
  }

  @Test
  void noVillagerIsBoughtWhileTheCountIsNotBelowTheCredits() {
    var result = service.execute(PLAYER, new Command.BuyVillagers());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_CREDITS);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(20);
  }

  @Test
  void sellingFoodSellsAllAboveOneAtOneCreditEach() {
    var result = service.execute(PLAYER, new Command.SellFood());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVillage().getFood()).isEqualTo(1.0);
    assertThat(state.getCredit()).isEqualTo(10.0 + 99.0);
  }

  @Test
  void sellingFoodWithNothingAboveOneChangesNothing() {
    state.getVillage().restore(1.0, 20);

    var result = service.execute(PLAYER, new Command.SellFood());

    assertThat(result.reason()).isEqualTo(Reason.NOTHING_TO_SELL);
    assertThat(state.getCredit()).isEqualTo(10.0);
  }

  @Test
  void sellingMaterialsAndRelicsSellsEverythingAtTheConfiguredPrice() {
    state.getResources().put(ResourceType.MATERIALS, 10.0);
    state.getResources().put(ResourceType.RELICS, 2.0);

    var materials = service.execute(PLAYER, new Command.SellMaterials());
    var relics = service.execute(PLAYER, new Command.SellRelics());

    assertThat(materials.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(relics.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isZero();
    assertThat(state.getResources().get(ResourceType.RELICS)).isZero();
    assertThat(state.getCredit()).isEqualTo(10.0 + 10 * 2.0 + 2 * 25.0);
  }

  @Test
  void sellingAnEmptyStockSaysThereIsNothingToSell() {
    assertThat(service.execute(PLAYER, new Command.SellMaterials()).reason())
        .isEqualTo(Reason.NOTHING_TO_SELL);
    assertThat(service.execute(PLAYER, new Command.SellRelics()).reason())
        .isEqualTo(Reason.NOTHING_TO_SELL);
  }

  // ---- upgrades

  @Test
  void anUnknownOrMissingUpgradeIdIsRejectedAsUnknown() {
    assertThat(service.execute(PLAYER, new Command.BuyUpgrade("NOT_AN_UPGRADE")).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
    assertThat(service.execute(PLAYER, new Command.BuyUpgrade(null)).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
  }

  @Test
  void anUpgradeTheVaultCannotPayForIsRejectedAsUnaffordable() {
    var result = service.execute(PLAYER, new Command.BuyUpgrade("UNLOCK_HEALER"));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.UPGRADE_UNAFFORDABLE);
    assertThat(state.isUnitUnlocked(UnitStats.HEALER)).isFalse();
  }

  @Test
  void aPaidUpgradeTakesEffectAndASecondPurchaseOfAOneOffIsMaxed() {
    state.getResources().put(ResourceType.MATERIALS, 40.0);

    var bought = service.execute(PLAYER, new Command.BuyUpgrade("UNLOCK_HEALER"));
    state.getResources().put(ResourceType.MATERIALS, 40.0);
    var again = service.execute(PLAYER, new Command.BuyUpgrade("UNLOCK_HEALER"));

    assertThat(bought.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.isUnitUnlocked(UnitStats.HEALER)).isTrue();
    assertThat(again.reason()).isEqualTo(Reason.UPGRADE_MAXED);
  }

  // ---- trades

  @Test
  void aVillagerMovesToATradeAndCannotBeSentBackWhileSettling() {
    var moved = service.execute(PLAYER, new Command.AssignTrade(Trade.BREAKER, 1));
    var back = service.execute(PLAYER, new Command.AssignTrade(Trade.BREAKER, -5));

    assertThat(moved.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(back.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(back.reason()).isEqualTo(Reason.TRADE_REFUSED);
    assertThat(back.message()).startsWith("Settling");
  }

  @Test
  void aZeroDeltaOrAMissingFieldMovesNothing() {
    var zero = service.execute(PLAYER, new Command.AssignTrade(Trade.BREAKER, 0));
    var noTrade = service.execute(PLAYER, new Command.AssignTrade(null, 1));
    var noDelta = service.execute(PLAYER, new Command.AssignTrade(Trade.SCRIBE, null));

    assertThat(zero.reason()).isEqualTo(Reason.TRADE_REFUSED);
    assertThat(noTrade.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(noDelta.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(service.stateSeq(PLAYER)).contains(0L);
  }

  // ---- expeditions

  @Test
  void anEmptySelectionSendsEveryone() {
    var result = service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), null));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.isExpeditionActive()).isTrue();
    assertThat(count(state.getUnitsOnTower(), UnitStats.WARRIOR)).isEqualTo(3);
    assertThat(count(state.getUnitsOnTower(), UnitStats.ARCHER)).isEqualTo(1);
    assertThat(state.getUnits().values().stream().mapToInt(List::size).sum()).isZero();
  }

  @Test
  void aNullSelectionIsRefusedAndNobodyMoves() {
    // A null map is not "everyone" (an empty one is); it used to be a NullPointerException.
    var result = service.execute(PLAYER, new Command.StartExpedition(null, 0));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(3);
  }

  @Test
  void anExplicitSelectionSendsOnlyThoseCounts() {
    var result =
        service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 2), 0));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(count(state.getUnitsOnTower(), UnitStats.WARRIOR)).isEqualTo(2);
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(1);
    assertThat(count(state.getUnits(), UnitStats.ARCHER)).isEqualTo(1);
  }

  @Test
  void aSelectionOfMoreThanExistsIsRefusedWithNoUnitMoved() {
    var result =
        service.execute(
            PLAYER,
            new Command.StartExpedition(selection(UnitStats.WARRIOR, 2, UnitStats.ARCHER, 5), 0));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_UNITS);
    assertThat(state.hasUnitsOnTower()).isFalse();
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(3);
  }

  @Test
  void cannotStartWhileAnExpeditionIsOut() {
    service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    var result = service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    assertThat(result.reason()).isEqualTo(Reason.EXPEDITION_ACTIVE);
  }

  @Test
  void cannotStartAfterTheCampaignEnded() {
    state.winCampaign();

    var result = service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    assertThat(result.reason()).isEqualTo(Reason.CAMPAIGN_ENDED);
    assertThat(state.isExpeditionActive()).isFalse();
  }

  @Test
  void cannotStartOnAFloorWithoutAnOpenShortcut() {
    var result = service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 7));

    assertThat(result.reason()).isEqualTo(Reason.START_FLOOR_LOCKED);
    assertThat(state.isExpeditionActive()).isFalse();
  }

  @Test
  void cannotStartWithNoUnitsAtHome() {
    state.getUnits().clear();

    var result = service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    assertThat(result.reason()).isEqualTo(Reason.NO_UNITS);
    assertThat(state.isExpeditionActive()).isFalse();
  }

  @Test
  void extractingWithNoExpeditionIsRejected() {
    assertThat(service.execute(PLAYER, new Command.Extract()).reason())
        .isEqualTo(Reason.NO_EXPEDITION);
  }

  @Test
  void extractingBanksTheLootBringsThePartyHomeAndFilesASummary() {
    service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));
    state.setCarriedCredits(7.0);
    state.gatherLoot(ResourceType.MATERIALS, 30.0);

    var result = service.execute(PLAYER, new Command.Extract());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.hasUnitsOnTower()).isFalse();
    assertThat(count(state.getUnits(), UnitStats.WARRIOR)).isEqualTo(3);
    assertThat(state.getCredit()).isEqualTo(17.0);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(30.0);
    assertThat(state.getExpeditionsCompleted()).isEqualTo(1);
    assertThat(service.expeditionSummaries(PLAYER).orElseThrow())
        .extracting(summary -> summary.outcome())
        .containsExactly("EXTRACTED");
  }

  // ---- reinforcements

  @Test
  void reinforcementsNeedAnActiveExpedition() {
    assertThat(service.execute(PLAYER, new Command.SendReinforcements(Map.of())).reason())
        .isEqualTo(Reason.NO_EXPEDITION);
  }

  @Test
  void reinforcementsAfterTheCampaignEndedAreRejected() {
    service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 1), 0));
    state.winCampaign();

    var result = service.execute(PLAYER, new Command.SendReinforcements(Map.of()));

    assertThat(result.reason()).isEqualTo(Reason.CAMPAIGN_ENDED);
  }

  @Test
  void anEmptyOrNullSelectionSendsEveryoneAtHomeAsOneWave() {
    service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 1), 0));

    var empty = service.execute(PLAYER, new Command.SendReinforcements(Map.of()));

    assertThat(empty.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getReinforcements()).hasSize(1);
    assertThat(state.getReinforcements().getFirst().size()).isEqualTo(3);
    assertThat(state.getUnits().values().stream().mapToInt(List::size).sum()).isZero();

    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 1));
    var nullMap = service.execute(PLAYER, new Command.SendReinforcements(null));
    assertThat(nullMap.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getReinforcements()).hasSize(2);
  }

  @Test
  void aReinforcementSelectionIsClampedToWhatIsAtHomeAndSaidToBe() {
    service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 1), 0));

    var result =
        service.execute(PLAYER, new Command.SendReinforcements(selection(UnitStats.WARRIOR, 9)));

    assertThat(result.outcome()).isEqualTo(Outcome.PARTIAL);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_UNITS);
    assertThat(state.getReinforcements().getFirst().size()).isEqualTo(2);
  }

  @Test
  void aWaveWithNobodyAtHomeIsRejected() {
    service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    var result = service.execute(PLAYER, new Command.SendReinforcements(Map.of()));

    assertThat(result.reason()).isEqualTo(Reason.NO_UNITS);
    assertThat(state.getReinforcements()).isEmpty();
  }

  @Test
  void resumingWhenNothingIsHeldChangesNothing() {
    var result = service.execute(PLAYER, new Command.Resume());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
  }

  // ---- the state sequence and the event log

  @Test
  void theStateSequenceAdvancesOnAppliedCommandsOnlyAndIsReportedInTheResult() {
    assertThat(service.stateSeq(PLAYER)).contains(0L);

    var applied = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 1));
    var rejected = service.execute(PLAYER, new Command.Extract());

    assertThat(applied.stateSeq()).isEqualTo(1);
    assertThat(rejected.stateSeq()).isEqualTo(1);
    assertThat(service.stateSeq(PLAYER)).contains(1L);
  }

  @Test
  void theStateSequenceAdvancesOnLifecycleTicksButNotOnAttritionSteps() {
    service.gameLoop();
    service.gameLoop();
    assertThat(service.stateSeq(PLAYER)).contains(2L);

    service.invasionTimer();
    assertThat(service.stateSeq(PLAYER)).contains(2L);
  }

  @Test
  void aDetachedCampaignDoesNotTickItsSequence() {
    service.detach(PLAYER, "ws");

    service.gameLoop();

    assertThat(service.stateSeq(PLAYER)).contains(0L);
  }

  @Test
  void appliedCommandsLeaveEventsAtTheSequenceTheyProduced() {
    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 1));
    service.execute(PLAYER, new Command.Extract());
    service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));

    var events = service.eventsSince(PLAYER, 0).orElseThrow();

    assertThat(events)
        .extracting(EventLog.Event::type)
        .containsExactly("RECRUITED", "EXPEDITION_STARTED");
    assertThat(events).extracting(EventLog.Event::stateSeq).containsExactly(1L, 2L);
    assertThat(service.eventsSince(PLAYER, events.getFirst().id()).orElseThrow()).hasSize(1);
  }

  @Test
  void aSummaryIsFiledOnceHoweverManyTicksFollow() {
    service.execute(PLAYER, new Command.StartExpedition(new HashMap<>(), 0));
    service.execute(PLAYER, new Command.Extract());
    assertThat(service.expeditionSummaries(PLAYER).orElseThrow()).hasSize(1);

    service.gameLoop();
    service.gameLoop();

    assertThat(service.expeditionSummaries(PLAYER).orElseThrow()).hasSize(1);
  }

  @Test
  void aWipeIsFiledByTheLifecycleTick() {
    service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 1), 0));
    state.getUnitsOnTower().clear();

    service.gameLoop();

    assertThat(service.expeditionSummaries(PLAYER).orElseThrow())
        .extracting(summary -> summary.outcome())
        .containsExactly("WIPED");
    assertThat(service.eventsSince(PLAYER, 0).orElseThrow())
        .extracting(EventLog.Event::type)
        .contains("EXPEDITION_STARTED", "EXPEDITION_ENDED");
  }

  @Test
  void fightingTicksLogTheAimedStrikesOfEachSideAsCountsOnly() {
    service.execute(PLAYER, new Command.StartExpedition(selection(UnitStats.WARRIOR, 1), 0));

    for (var tick = 0; tick < 5; tick++) {
      service.gameLoop();
    }

    var strikes =
        service.eventsSince(PLAYER, 0).orElseThrow().stream()
            .filter(event -> event.type().equals("AIMED_STRIKES"))
            .toList();
    assertThat(strikes).isNotEmpty();
    assertThat(strikes)
        .allSatisfy(
            event -> {
              assertThat(event.data().get("side")).isIn("PARTY", "GARRISON");
              var thrown = (Integer) event.data().get("strikes");
              var hits = (Integer) event.data().get("hits");
              var misses = (Integer) event.data().get("misses");
              assertThat(thrown).isPositive();
              assertThat(hits + misses).isGreaterThanOrEqualTo(thrown);
              assertThat(event.data()).containsKeys("aims", "partsHit", "partsMissed", "partsLost");
            });
  }

  @Test
  void aPlayerWithoutACampaignGetsNoCampaignAndNothingElseIsObservable() {
    var result = service.execute("nobody", new Command.SellFood());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.NO_CAMPAIGN);
    assertThat(service.stateSeq("nobody")).isEmpty();
    assertThat(service.project("nobody")).isEmpty();
    assertThat(service.eventsSince("nobody", 0)).isEmpty();
    assertThat(service.expeditionSummaries("nobody")).isEmpty();
    assertThat(service.hasSlot("nobody")).isFalse();
  }
}

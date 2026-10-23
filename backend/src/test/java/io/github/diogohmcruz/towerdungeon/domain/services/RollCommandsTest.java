package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEntry;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollRefusal;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionView;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.MetaCodec;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Roll at the Succession: buying, respec, the choices, the seal when the Generation begins, and
 * what each Inheritance of Courses I and II does to the next Generation.
 */
class RollCommandsTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  @TempDir Path dir;

  private final GameProperties config = new GameProperties();
  private final JsonMapper mapper = JsonMapper.builder().build();
  private FileCampaignStore files;
  private GameService service;

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    files = new FileCampaignStore(config.getPersistence());
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(7),
            plan,
            new PartyCombat(),
            files,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                files, mapper, config.getPersistence(), "test", System::nanoTime),
            MetaCodec.development(mapper));
    service.attach(PLAYER, "ws-1").block();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    service.shutdown();
    Thread.sleep(50);
    files.close();
  }

  private GameState state() {
    return service.playerState(PLAYER);
  }

  /** Ends the Campaign by Exodus, then gives the player this much Legacy to spend. */
  private void succession(int legacy) {
    var state = state();
    state.setHighestFloor(40);
    state.setExpeditionsCompleted(17);
    state.getTally().addFoodGrown(60000);
    state.getTally().addMaterialsBanked(17000);
    state.getTally().addCreditsBanked(6800);
    state.getTally().addRelicsBanked(320);
    assertThat(service.execute(PLAYER, new Command.Exodus()).outcome()).isEqualTo(Outcome.APPLIED);
    var slot = service.slotOf(PLAYER);
    slot.meta(slot.meta().withRoll(slot.meta().roll(), legacy));
  }

  /** Puts these Inheritances on the Roll at what they cost, bypassing the gates. */
  private void hold(InheritanceType... types) {
    var slot = service.slotOf(PLAYER);
    var roll = new java.util.ArrayList<>(slot.meta().roll());
    var spent = 0;
    for (var type : types) {
      var cost = config.getPrestige().getRoll().costOf(type);
      roll.add(new RollEntry(type.name(), cost));
      spent += cost;
    }
    slot.meta(slot.meta().withRoll(roll, slot.meta().legacy() - spent));
  }

  private Command.RollBuy buy(InheritanceType type) {
    return new Command.RollBuy(type.name());
  }

  private GameState begin() {
    assertThat(service.execute(PLAYER, new Command.BeginGeneration()).outcome())
        .isEqualTo(Outcome.APPLIED);
    return state();
  }

  private int legacy() {
    return service.playerMeta(PLAYER).legacy();
  }

  private static List<Unit> units(GameState state) {
    return state.getUnits().values().stream().flatMap(List::stream).toList();
  }

  // ---- buying, respec and the seal

  @Test
  void theRollIsSealedWhileTheCampaignIsPlayed() {
    var result = service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.NOT_ENDED);
    assertThat(service.playerMeta(PLAYER).roll()).isEmpty();
  }

  @Test
  void anInheritanceIsBoughtInTheSuccessionAndLegacyIsSpent() {
    succession(10);
    var before = legacy();

    var result = service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(legacy()).isEqualTo(before - 1);
    assertThat(service.playerMeta(PLAYER).roll()).containsExactly(new RollEntry("SEED_GRAIN", 1));
  }

  @Test
  void refusalsCarryTheirOwnReason() {
    succession(1);

    assertThat(service.execute(PLAYER, new Command.RollBuy("NOPE")).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
    assertThat(service.execute(PLAYER, new Command.RollBuy(null)).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
    assertThat(service.execute(PLAYER, buy(InheritanceType.READER_OF_OMENS)).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(PLAYER, buy(InheritanceType.THE_DOWRY)).reason())
        .isEqualTo(Reason.COURSE_LOCKED);
    assertThat(service.execute(PLAYER, buy(InheritanceType.HARDY_STOCK)).reason())
        .isEqualTo(Reason.INSUFFICIENT_LEGACY);
    service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN));
    assertThat(service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN)).reason())
        .isEqualTo(Reason.PURCHASE_REFUSED);
  }

  @Test
  void removingAnInheritanceRefundsExactlyWhatWasPaid() {
    succession(9);
    var before = legacy();
    service.execute(PLAYER, buy(InheritanceType.HARDY_STOCK));
    assertThat(legacy()).isEqualTo(before - 2);

    var result = service.execute(PLAYER, new Command.RollRefund("HARDY_STOCK"));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(legacy()).isEqualTo(before);
    assertThat(service.playerMeta(PLAYER).roll()).isEmpty();
  }

  @Test
  void aPriceRetunedBetweenBuyingAndRemovingDoesNotChangeTheRefund() {
    succession(9);
    var before = legacy();
    service.execute(PLAYER, buy(InheritanceType.HARDY_STOCK));
    state().getConfig().getPrestige().getRoll().getCosts().put(InheritanceType.HARDY_STOCK, 7);

    service.execute(PLAYER, new Command.RollRefund("HARDY_STOCK"));

    assertThat(legacy()).isEqualTo(before);
  }

  @Test
  void aRefundThatWouldStrandAnotherInheritanceIsRefused() {
    succession(30);
    for (var type :
        List.of(
            InheritanceType.SEED_GRAIN,
            InheritanceType.MUSTER_ROLL,
            InheritanceType.HARDY_STOCK,
            InheritanceType.WHETSTONE_HABIT,
            InheritanceType.THE_DOWRY)) {
      assertThat(service.execute(PLAYER, buy(type)).outcome()).isEqualTo(Outcome.APPLIED);
    }

    var refused = service.execute(PLAYER, new Command.RollRefund("WHETSTONE_HABIT"));

    assertThat(refused.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(service.playerMeta(PLAYER).holds(InheritanceType.WHETSTONE_HABIT)).isTrue();
    service.execute(PLAYER, new Command.RollRefund("THE_DOWRY"));
    assertThat(service.execute(PLAYER, new Command.RollRefund("WHETSTONE_HABIT")).outcome())
        .isEqualTo(Outcome.APPLIED);
  }

  @Test
  void aRefundOfWhatIsNotHeldIsRefused() {
    succession(5);

    var result = service.execute(PLAYER, new Command.RollRefund("SEED_GRAIN"));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(legacy()).isEqualTo(5);
  }

  @Test
  void theRollCannotChangeOnceTheGenerationBegins() {
    succession(10);
    service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN));
    begin();
    var meta = service.playerMeta(PLAYER);

    assertThat(service.execute(PLAYER, buy(InheritanceType.MUSTER_ROLL)).reason())
        .isEqualTo(Reason.NOT_ENDED);
    assertThat(service.execute(PLAYER, new Command.RollRefund("SEED_GRAIN")).reason())
        .isEqualTo(Reason.NOT_ENDED);
    assertThat(service.execute(PLAYER, new Command.Choose("DOWRY_RELICS")).reason())
        .isEqualTo(Reason.NOT_ENDED);
    assertThat(service.playerMeta(PLAYER)).isEqualTo(meta);
  }

  @Test
  void theRollIsKeptIntoTheNextGenerationsMeta() {
    succession(10);
    service.execute(PLAYER, buy(InheritanceType.SEED_GRAIN));

    begin();

    assertThat(service.playerMeta(PLAYER).holds(InheritanceType.SEED_GRAIN)).isTrue();
    assertThat(service.playerMeta(PLAYER).generation()).isEqualTo(2);
  }

  @Test
  void theSuccessionViewListsEveryInheritanceAndAnUnavailableOneCannotBeBought() {
    succession(30);
    var slot = service.slotOf(PLAYER);

    var view = SuccessionView.of(slot.meta(), state());

    assertThat(state().getPhase()).isEqualTo(CampaignPhase.OFFER);
    assertThat(view.inheritances()).hasSize(17);
    assertThat(view.legacy()).isEqualTo(30);
    var stair =
        view.inheritances().stream().filter(n -> n.id().equals("READER_OF_OMENS")).findFirst();
    assertThat(stair.orElseThrow().available()).isFalse();
    assertThat(stair.orElseThrow().whyNot()).isEqualTo(RollRefusal.UNAVAILABLE.name());
    var seed = view.inheritances().stream().filter(n -> n.id().equals("SEED_GRAIN")).findFirst();
    assertThat(seed.orElseThrow().whyNot()).isNull();
    assertThat(view.courses()).extracting(c -> c.open()).containsExactly(true, false, false, false);
    assertThat(view.chronicle()).hasSize(1);
    assertThat(view.chronicle().get(0).generation()).isEqualTo(view.generation());
  }

  @Test
  void thePlayedCampaignHasNoSuccessionView() {
    assertThat(SuccessionView.of(service.slotOf(PLAYER).meta(), state())).isNull();
  }

  // ---- the effects of Courses I and II at the start of the next Generation

  @Test
  void seedGrainStartsThePantryAndThePurseHigher() {
    succession(0);
    hold(InheritanceType.SEED_GRAIN);

    var next = begin();

    assertThat(next.getVillage().getFood()).isEqualTo(150.0);
    assertThat(next.getCredit()).isEqualTo(20.0);
  }

  @Test
  void hardyStockStartsWithTwentyFiveVillagers() {
    succession(0);
    hold(InheritanceType.HARDY_STOCK);

    var next = begin();

    assertThat(next.getVillage().getVillagersCount()).isEqualTo(25);
    assertThat(next.getVillage().getProductionRate()).isEqualTo(12.5);
  }

  @Test
  void whetstoneHabitAddsFivePercentPartyDamage() {
    succession(0);
    hold(InheritanceType.WHETSTONE_HABIT);

    var next = begin();

    assertThat(next.getDamageMultiplier()).isEqualTo(1.05);
  }

  @Test
  void heavierPacksAddsTwentyFiveToTheBaseCarryingCapacity() {
    succession(0);
    var plain = state().getConfig().getSupply().getBaseMax();
    hold(InheritanceType.HEAVIER_PACKS);

    var next = begin();

    assertThat(next.getConfig().getSupply().getBaseMax()).isEqualTo(plain + 25.0);
    assertThat(config.getSupply().getBaseMax())
        .as("the shared config is untouched")
        .isEqualTo(plain);
  }

  @Test
  void villageCharterStartsAtVillageTierTwo() {
    succession(0);
    hold(InheritanceType.VILLAGE_CHARTER);

    var next = begin();

    assertThat(next.getVillageTierLevel()).isEqualTo(2);
  }

  @Test
  void withoutTheRollNothingChanges() {
    succession(0);

    var next = begin();

    assertThat(next.getVillage().getFood()).isEqualTo(100.0);
    assertThat(next.getCredit()).isEqualTo(10.0);
    assertThat(next.getVillage().getVillagersCount()).isEqualTo(20);
    assertThat(next.getDamageMultiplier()).isEqualTo(1.0);
    assertThat(next.getVillageTierLevel()).isEqualTo(1);
    assertThat(units(next)).hasSize(4);
  }

  @Test
  void theDowryPaysTheChosenThingEachSuccession() {
    succession(0);
    hold(InheritanceType.THE_DOWRY);
    assertThat(service.execute(PLAYER, new Command.Choose("DOWRY_MATERIALS")).outcome())
        .isEqualTo(Outcome.APPLIED);

    var next = begin();

    assertThat(next.getResources().get(ResourceType.MATERIALS)).isEqualTo(40.0);
    assertThat(next.getResources().get(ResourceType.RELICS)).isZero();
    assertThat(next.getCredit()).isEqualTo(10.0);
    assertThat(next.getChoices()).isEmpty();
  }

  @Test
  void theDowryCanBeRelicsOrCreditsAndTheLastAnswerStands() {
    succession(0);
    hold(InheritanceType.THE_DOWRY);
    service.execute(PLAYER, new Command.Choose("DOWRY_MATERIALS"));
    service.execute(PLAYER, new Command.Choose("DOWRY_RELICS"));

    var next = begin();

    assertThat(next.getResources().get(ResourceType.RELICS)).isEqualTo(3.0);
    assertThat(next.getResources().get(ResourceType.MATERIALS)).isZero();
  }

  @Test
  void aDowryNotChosenPaysCredits() {
    succession(0);
    hold(InheritanceType.THE_DOWRY);

    var next = begin();

    assertThat(next.getCredit()).isEqualTo(90.0);
  }

  @Test
  void theHeirIsOneExtraUnitOnTopOfTheFourFoundersWithFiftyPercentMoreHealth() {
    succession(0);
    hold(InheritanceType.THE_HEIR);
    service.execute(PLAYER, new Command.Choose("HEIR_PORTER"));

    var next = begin();

    var all = units(next);
    assertThat(all).hasSize(5);
    var heir = all.stream().filter(u -> u.getName().equals("Heir")).findFirst().orElseThrow();
    assertThat(heir.getStats()).isEqualTo(UnitStats.PORTER);
    var spread = config.getRecruit().getStrengthSpread();
    var base = UnitStats.PORTER.getHealth();
    assertThat(heir.getMaxHealth())
        .isBetween(base * (1 - spread) * 1.5 - 1e-9, base * (1 + spread) * 1.5 + 1e-9);
    assertThat(heir.getMaxHealth()).isEqualTo(base * heir.getHealthFactor());
    assertThat(heir.getHealthFactor()).isGreaterThan(1 - spread);
  }

  @Test
  void anHeirNotChosenIsAWarrior() {
    succession(0);
    hold(InheritanceType.THE_HEIR);

    var next = begin();

    assertThat(units(next))
        .filteredOn(u -> u.getName().equals("Heir"))
        .hasSize(1)
        .allMatch(u -> u.getStats() == UnitStats.WARRIOR);
  }

  @Test
  void aChoiceNeedsItsInheritanceAndAnUnknownOneIsRefused() {
    succession(0);

    assertThat(service.execute(PLAYER, new Command.Choose("DOWRY_CREDITS")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(PLAYER, new Command.Choose("HEIR_DRAGON")).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
    assertThat(service.execute(PLAYER, new Command.Choose(null)).reason())
        .isEqualTo(Reason.UNKNOWN_ID);
    assertThat(state().getChoices()).isEmpty();
  }

  @Test
  void aChoiceIsForgottenWhenItsInheritanceIsGivenBack() {
    succession(10);
    hold(InheritanceType.SEED_GRAIN, InheritanceType.MUSTER_ROLL);
    hold(InheritanceType.HARDY_STOCK, InheritanceType.WHETSTONE_HABIT);
    hold(InheritanceType.THE_HEIR);
    service.execute(PLAYER, new Command.Choose("HEIR_ARCHER"));
    assertThat(state().getChoices()).containsEntry("HEIR", "HEIR_ARCHER");

    service.execute(PLAYER, new Command.RollRefund("THE_HEIR"));

    assertThat(state().getChoices()).isEmpty();
  }

  @Test
  void choicesAreKeptInTheSnapshotOfTheEndedCampaign() {
    succession(0);
    hold(InheritanceType.THE_HEIR);
    service.execute(PLAYER, new Command.Choose("HEIR_ARCHER"));

    var snapshot = state().toSnapshot();

    assertThat(snapshot.campaign().choices()).isEqualTo(Map.of("HEIR", "HEIR_ARCHER"));
  }

  @Test
  void bloodOfTheDoorNeedsTwelveLegacySpentBelowAndThenOpensTheDoor() {
    succession(6);
    assertThat(service.execute(PLAYER, buy(InheritanceType.BLOOD_OF_THE_DOOR)).reason())
        .isEqualTo(Reason.COURSE_LOCKED);
    hold(
        InheritanceType.SEED_GRAIN,
        InheritanceType.MUSTER_ROLL,
        InheritanceType.HARDY_STOCK,
        InheritanceType.WHETSTONE_HABIT,
        InheritanceType.THE_DOWRY,
        InheritanceType.HEAVIER_PACKS);
    var slot = service.slotOf(PLAYER);
    slot.meta(slot.meta().withRoll(slot.meta().roll(), 6));

    assertThat(service.execute(PLAYER, buy(InheritanceType.BLOOD_OF_THE_DOOR)).outcome())
        .isEqualTo(Outcome.APPLIED);
    var next = begin();

    assertThat(next.isDoorOpen()).isTrue();
  }

  // ---- Muster Roll

  private Command.StartExpedition party() {
    return new Command.StartExpedition(Map.of(UnitStats.WARRIOR, 2), 0);
  }

  private GameState playWithMusterRoll() {
    succession(0);
    hold(InheritanceType.MUSTER_ROLL);
    return begin();
  }

  @Test
  void musterRollResendsTheLastPartyAfterItReturns() {
    var next = playWithMusterRoll();
    assertThat(service.execute(PLAYER, party()).outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(next.getTowerPartySize()).isEqualTo(2);
    service.execute(PLAYER, new Command.Extract());
    assertThat(next.isExpeditionActive()).isFalse();

    var result = service.execute(PLAYER, new Command.MusterRoll());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(next.isExpeditionActive()).isTrue();
    assertThat(next.getTowerPartySize()).isEqualTo(2);
  }

  @Test
  void musterRollNeedsAPartyThatHasSetOut() {
    playWithMusterRoll();
    assertThat(service.execute(PLAYER, new Command.MusterRoll()).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
  }

  @Test
  void musterRollIsRefusedWithoutTheInheritance() {
    succession(0);
    begin();
    service.execute(PLAYER, party());
    service.execute(PLAYER, new Command.Extract());

    var result = service.execute(PLAYER, new Command.MusterRoll());

    assertThat(result.reason()).isEqualTo(Reason.NOT_UNLOCKED);
    assertThat(state().isExpeditionActive()).isFalse();
  }

  @Test
  void theLastPartyDoesNotCrossAGeneration() {
    playWithMusterRoll();
    service.execute(PLAYER, party());
    service.execute(PLAYER, new Command.Extract());
    state().setHighestFloor(40);
    state().setExpeditionsCompleted(17);
    state().getTally().addFoodGrown(60000);
    state().getTally().addMaterialsBanked(17000);
    state().getTally().addCreditsBanked(6800);
    state().getTally().addRelicsBanked(320);
    assertThat(service.execute(PLAYER, new Command.Exodus()).outcome()).isEqualTo(Outcome.APPLIED);
    begin();

    assertThat(service.execute(PLAYER, new Command.MusterRoll()).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
  }
}

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
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEffects;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEntry;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SavedLoadout;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.MetaCodec;
import tools.jackson.databind.json.JsonMapper;

/** Course III of the Roll: Narrow Stair, Overreach and Quartermaster's Tally. */
class CourseThreeTest {
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

  private GameState begin() {
    assertThat(service.execute(PLAYER, new Command.BeginGeneration()).outcome())
        .isEqualTo(Outcome.APPLIED);
    return state();
  }

  /** A new Generation under a Roll holding these. */
  private GameState playing(InheritanceType... types) {
    succession(0);
    hold(types);
    return begin();
  }

  private static Command.StartExpedition setOut(
      Map<UnitStats, Integer> party, int floor, boolean overreach) {
    return new Command.StartExpedition(party, floor, null, null, null, false, overreach);
  }

  // ---- the registry

  @Test
  void theCourseThreeNodesAreBoughtOnceTheCourseIsOpen() {
    succession(0);
    hold(
        InheritanceType.SEED_GRAIN,
        InheritanceType.MUSTER_ROLL,
        InheritanceType.HARDY_STOCK,
        InheritanceType.WHETSTONE_HABIT,
        InheritanceType.THE_DOWRY,
        InheritanceType.HEAVIER_PACKS);
    var slot = service.slotOf(PLAYER);
    slot.meta(slot.meta().withRoll(slot.meta().roll(), 12));

    for (var type :
        List.of(
            InheritanceType.NARROW_STAIR,
            InheritanceType.OVERREACH,
            InheritanceType.QUARTERMASTERS_TALLY)) {
      assertThat(service.execute(PLAYER, new Command.RollBuy(type.name())).outcome())
          .as(type.name())
          .isEqualTo(Outcome.APPLIED);
    }
    assertThat(service.playerMeta(PLAYER).legacy()).isZero();
  }

  // ---- Narrow Stair

  @Test
  void withoutNarrowStairFloorTwentyOneIsLocked() {
    var next = playing();

    var result = service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 21, false));

    assertThat(result.reason()).isEqualTo(Reason.START_FLOOR_LOCKED);
    assertThat(next.isExpeditionActive()).isFalse();
    assertThat(next.getShortcuts()).noneMatch(offer -> offer.id().equals("NARROW_STAIR"));
  }

  @Test
  void narrowStairOpensOneShortcutAtTheConfiguredFloor() {
    var next = playing(InheritanceType.NARROW_STAIR);

    var offer =
        next.getShortcuts().stream()
            .filter(shortcut -> shortcut.id().equals("NARROW_STAIR"))
            .findFirst()
            .orElseThrow();
    assertThat(offer.floor()).isEqualTo(21);
    assertThat(offer.maxParty()).isEqualTo(12);
    assertThat(offer.unlocked()).isTrue();

    var result = service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 21, false));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(next.isExpeditionActive()).isTrue();
    assertThat(next.getTower().getCurrentFloor()).isEqualTo(21);
    assertThat(next.getRunStartFloor()).isEqualTo(21);
    assertThat(next.getInheritedStartFloor()).isEqualTo(21);
  }

  @Test
  void theFloorAndTheLimitAreConfig() {
    config.getPrestige().getRoll().getNarrowStair().setFloor(25);
    config.getPrestige().getRoll().getNarrowStair().setMaxParty(3);
    var next = playing(InheritanceType.NARROW_STAIR);

    assertThat(service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 21, false)).reason())
        .isEqualTo(Reason.START_FLOOR_LOCKED);
    assertThat(service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 25, false)).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(next.getTower().getCurrentFloor()).isEqualTo(25);
  }

  @Test
  void aPartyOverTheLimitIsRefusedWithAStatedReason() {
    config.getPrestige().getRoll().getNarrowStair().setMaxParty(3);
    var next = playing(InheritanceType.NARROW_STAIR);
    var reserves = next.getUnits().values().stream().mapToInt(List::size).sum();
    assertThat(reserves).isGreaterThan(3);

    var everyone = service.execute(PLAYER, setOut(Map.of(), 21, false));

    assertThat(everyone.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(everyone.reason()).isEqualTo(Reason.PARTY_TOO_LARGE);
    assertThat(everyone.message()).contains("at most 3").contains(String.valueOf(reserves));
    assertThat(next.isExpeditionActive()).isFalse();
    assertThat(next.getUnits().values().stream().mapToInt(List::size).sum()).isEqualTo(reserves);
    assertThat(next.getUnitsOnTower().values().stream().mapToInt(List::size).sum()).isZero();

    var asked = service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 4), 21, false));
    assertThat(asked.reason()).isEqualTo(Reason.PARTY_TOO_LARGE);
  }

  @Test
  void theLimitDoesNotApplyFromTheBase() {
    config.getPrestige().getRoll().getNarrowStair().setMaxParty(1);
    var next = playing(InheritanceType.NARROW_STAIR);

    assertThat(service.execute(PLAYER, setOut(Map.of(), 0, false)).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(next.getInheritedStartFloor()).isZero();
  }

  @Test
  void startingFromNarrowStairLowersWhatTheTollPaysForAscent() {
    var next = playing(InheritanceType.NARROW_STAIR);
    service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 21, false));
    next.recordFloorReached(30);
    var slot = service.slotOf(PLAYER);

    var stair = next.getInheritedStartFloor();
    var ascent = config.getPrestige().getAscentMarksPerFloor();

    assertThat(stair).isEqualTo(21);
    assertThat(next.tollTally(0).highestFloor()).isEqualTo(30);
    assertThat(
            io.github.diogohmcruz.towerdungeon.domain.prestige.TollCalculator.calculate(
                    next.tollTally(0),
                    new io.github.diogohmcruz.towerdungeon.domain.prestige.TollContext(
                        0, 0, 0, 0, stair),
                    config.getPrestige())
                .ascentMarks())
        .isEqualTo(ascent * 9);
    assertThat(slot.meta()).isNotNull();
  }

  @Test
  void theBaselineSurvivesASaveAndALoad() {
    var next = playing(InheritanceType.NARROW_STAIR);
    service.execute(PLAYER, setOut(Map.of(UnitStats.WARRIOR, 2), 21, false));

    var restored = GameState.restore(next.toSnapshot(), config.copy(), new RandomSource(1), plan());

    assertThat(restored.getInheritedStartFloor()).isEqualTo(21);
  }

  // ---- Overreach

  @Test
  void overreachIsRefusedWithoutTheInheritance() {
    var next = playing();

    var result = service.execute(PLAYER, setOut(Map.of(), 0, true));

    assertThat(result.reason()).isEqualTo(Reason.NOT_UNLOCKED);
    assertThat(next.isExpeditionActive()).isFalse();
  }

  @Test
  void overreachAppearsInTheLoadoutOnlyWhenOwned() {
    assertThat(playing().getLoadout().overreach()).isNull();

    succession(0);
    hold(InheritanceType.OVERREACH);
    var next = begin();

    var overreach = next.getLoadout().overreach();
    assertThat(overreach.supplyDrainMultiplier()).isEqualTo(1.3);
    assertThat(overreach.starvationDamageMultiplier()).isEqualTo(2.0);
    assertThat(overreach.lootMultiplier()).isEqualTo(1.5);
  }

  @Test
  void overreachChangesTheThreeMultipliersForThatExpeditionOnly() {
    var next = playing(InheritanceType.OVERREACH);
    var plainDrain = config.getLoop().getSupplyDrainPerUnit();
    var plainStarvation = config.getLoop().getStarvationDamage();

    assertThat(service.execute(PLAYER, setOut(Map.of(), 0, true)).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(next.isOverreaching()).isTrue();

    var before = next.getSupplies();
    next.drainExpeditionSupplies(2);
    assertThat(before - next.getSupplies()).isCloseTo(2 * plainDrain * 1.3, within());
    assertThat(next.starvationDamage()).isCloseTo(plainStarvation * 2, within());
    next.gatherLoot(ResourceType.MATERIALS, 10d);
    next.gatherCredits(10d);
    assertThat(next.getCarriedLoot().get(ResourceType.MATERIALS)).isCloseTo(15d, within());
    assertThat(next.getCarriedCredits()).isCloseTo(15d, within());

    service.execute(PLAYER, new Command.Extract());
    assertThat(next.isOverreaching()).isFalse();
    assertThat(service.execute(PLAYER, setOut(Map.of(), 0, false)).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(next.isOverreaching()).isFalse();
    assertThat(next.starvationDamage()).isEqualTo(plainStarvation);
    assertThat(next.lootMultiplier()).isEqualTo(1d);
    assertThat(next.drainMultiplier()).isEqualTo(1d);
  }

  @Test
  void overreachLootIsStillLootBankedByExtractionAndLostByAWipe() {
    var next = playing(InheritanceType.OVERREACH);
    service.execute(PLAYER, setOut(Map.of(), 0, true));
    var vault = next.getResources().get(ResourceType.MATERIALS);
    next.gatherLoot(ResourceType.MATERIALS, 100d);
    assertThat(next.getResources().get(ResourceType.MATERIALS)).isEqualTo(vault);

    service.execute(PLAYER, new Command.Extract());

    assertThat(next.getResources().get(ResourceType.MATERIALS)).isCloseTo(vault + 150d, within());

    service.execute(PLAYER, setOut(Map.of(), 0, true));
    next.gatherLoot(ResourceType.MATERIALS, 100d);
    next.forfeitLoot();
    assertThat(next.getCarriedLoot().get(ResourceType.MATERIALS)).isZero();
  }

  @Test
  void overreachIsSavedWithTheExpeditionAndMuster() {
    var next = playing(InheritanceType.OVERREACH, InheritanceType.MUSTER_ROLL);
    service.execute(PLAYER, setOut(Map.of(), 0, true));
    var snapshot = next.toSnapshot();
    assertThat(snapshot.expedition().overreach()).isTrue();

    var restored = GameState.restore(snapshot, config.copy(), new RandomSource(1), plan());
    RollEffects.of(service.playerMeta(PLAYER).roll(), config).carry(restored);

    assertThat(restored.isOverreaching()).isTrue();
    assertThat(restored.lootMultiplier()).isEqualTo(1.5);

    service.execute(PLAYER, new Command.Extract());
    assertThat(next.isOverreaching()).isFalse();
    assertThat(service.execute(PLAYER, new Command.MusterRoll()).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(next.isOverreaching()).isTrue();
  }

  private ArchitecturePlan plan() {
    return ArchitecturePlan.generate(
        config.getArchitecture().getSeed(),
        config.getTower().getMaxFloor(),
        config.getBoss().getInterval(),
        config.getEnemies().getSurgeInterval());
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-6);
  }

  // ---- Quartermaster's Tally

  @Test
  void savedLoadoutsNeedTheTally() {
    playing();

    var result = service.execute(PLAYER, new Command.SaveLoadout(0, 5, 0, null, false, false));

    assertThat(result.reason()).isEqualTo(Reason.NOT_UNLOCKED);
    assertThat(service.playerMeta(PLAYER).savedLoadouts()).isEmpty();
    assertThat(state().getLoadout().tally()).isNull();
  }

  @Test
  void threeLoadoutsAreSavedInTheMetaAndTheFourthSlotDoesNotExist() {
    var next = playing(InheritanceType.QUARTERMASTERS_TALLY, InheritanceType.OVERREACH);

    for (var slot = 0; slot < 3; slot++) {
      assertThat(
              service
                  .execute(
                      PLAYER,
                      new Command.SaveLoadout(
                          slot, slot * 10, 2, UnitStats.PORTER, slot == 1, true))
                  .outcome())
          .isEqualTo(Outcome.APPLIED);
    }
    assertThat(
            service.execute(PLAYER, new Command.SaveLoadout(3, 1, 0, null, false, false)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(
            service.execute(PLAYER, new Command.SaveLoadout(-1, 1, 0, null, false, false)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(
            service.execute(PLAYER, new Command.SaveLoadout(0, -1, 0, null, false, false)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);

    var saved = service.playerMeta(PLAYER).savedLoadouts();
    assertThat(saved)
        .containsExactly(
            new SavedLoadout(0, 0, 2, "PORTER", false, true),
            new SavedLoadout(1, 10, 2, "PORTER", true, true),
            new SavedLoadout(2, 20, 2, "PORTER", false, true));
    assertThat(next.getLoadout().tally().saved()).isEqualTo(saved);
    assertThat(next.getLoadout().tally().slots()).isEqualTo(3);
  }

  @Test
  void savingAgainReplacesTheSlotAndClearingForgetsIt() {
    playing(InheritanceType.QUARTERMASTERS_TALLY);
    service.execute(PLAYER, new Command.SaveLoadout(1, 5, 0, null, false, false));
    service.execute(PLAYER, new Command.SaveLoadout(1, 7, 0, null, true, false));

    assertThat(service.playerMeta(PLAYER).savedLoadouts())
        .containsExactly(new SavedLoadout(1, 7, 0, null, true, false));
    assertThat(service.execute(PLAYER, new Command.ClearLoadout(1)).outcome())
        .isEqualTo(Outcome.APPLIED);
    assertThat(service.playerMeta(PLAYER).savedLoadouts()).isEmpty();
    assertThat(service.execute(PLAYER, new Command.ClearLoadout(1)).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
  }

  @Test
  void aSavedLoadoutCannotCarryOverreachTheRollDoesNotHold() {
    playing(InheritanceType.QUARTERMASTERS_TALLY);

    var result = service.execute(PLAYER, new Command.SaveLoadout(0, 0, 0, null, false, true));

    assertThat(result.reason()).isEqualTo(Reason.NOT_UNLOCKED);
  }

  @Test
  void savedLoadoutsOutliveTheCampaignAndAreInTheNextGeneration() {
    playing(InheritanceType.QUARTERMASTERS_TALLY);
    service.execute(PLAYER, new Command.SaveLoadout(2, 9, 1, UnitStats.WARRIOR, true, false));
    succession(0);
    assertThat(service.playerMeta(PLAYER).savedLoadouts()).hasSize(1);

    var next = begin();

    assertThat(next.getLoadout().tally().saved())
        .containsExactly(new SavedLoadout(2, 9, 1, "WARRIOR", true, false));
  }

  @Test
  void theMetaCodecKeepsSavedLoadouts() {
    playing(InheritanceType.QUARTERMASTERS_TALLY);
    service.execute(PLAYER, new Command.SaveLoadout(0, 4, 3, UnitStats.PORTER, true, false));
    var meta = service.playerMeta(PLAYER);
    var codec = MetaCodec.development(mapper);

    var read = codec.fromNode(codec.toNode(meta));

    assertThat(read.savedLoadouts()).isEqualTo(meta.savedLoadouts()).hasSize(1);
  }

  @Test
  void aMetaFromBeforeSavedLoadoutsReadsWithNone() {
    var codec = MetaCodec.development(mapper);
    var old = mapper.createObjectNode().put("generation", 3).put("legacy", 4);

    var read = codec.fromNode(old);

    assertThat(read.generation()).isEqualTo(3);
    assertThat(read.savedLoadouts()).isEmpty();
  }

  @Test
  void theRunwayForecastIsTheSuppliesOverTheDrain() {
    var next = playing(InheritanceType.QUARTERMASTERS_TALLY, InheritanceType.OVERREACH);

    var loadout = next.getLoadout();
    var tally = loadout.tally();

    var perTick = loadout.partySize() * loadout.drainPerUnit();
    assertThat(tally.runwayTicks()).isEqualTo((int) Math.floor(loadout.suppliesTaken() / perTick));
    assertThat(tally.overreachRunwayTicks())
        .isEqualTo((int) Math.floor(loadout.suppliesTaken() / (perTick * 1.3)))
        .isLessThan(tally.runwayTicks());
    assertThat(tally.saltedRunwayTicks()).isGreaterThanOrEqualTo(tally.runwayTicks());
  }
}

package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
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
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.OpenBreach;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollEntry;
import io.github.diogohmcruz.towerdungeon.domain.prestige.SuccessionView;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.MetaCodec;
import tools.jackson.databind.json.JsonMapper;

/** Course IV and the Epitaphs through the Succession: ending, choosing, beginning, reloading. */
class CourseIvCommandsTest {
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

  /** Ends the Campaign by Exodus after a good enough Campaign; the Roll then has this Legacy. */
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

  private void hold(InheritanceType... types) {
    var slot = service.slotOf(PLAYER);
    var roll = new ArrayList<>(slot.meta().roll());
    for (var type : types) {
      roll.add(new RollEntry(type.name(), config.getPrestige().getRoll().costOf(type)));
    }
    slot.meta(slot.meta().withRoll(roll, slot.meta().legacy()));
  }

  /** Records Guardian Floors as slain in Generation 1, as the end of a Campaign does. */
  private void remember(Map<Integer, List<Integer>> slain) {
    var m = service.slotOf(PLAYER).meta();
    service
        .slotOf(PLAYER)
        .meta(
            new PlayerMeta(
                m.generation(),
                m.legacy(),
                m.roll(),
                m.residue(),
                m.lifetime(),
                m.chronicle(),
                slain,
                m.epitaphs(),
                m.openBreach(),
                m.towerSecret()));
  }

  private GameState begin() {
    assertThat(service.execute(PLAYER, new Command.BeginGeneration()).outcome())
        .isEqualTo(Outcome.APPLIED);
    return state();
  }

  // ---- Hard-Won Habits

  @Test
  void hardWonHabitsGrantsTheFourMilestonesAtTheStartOfEveryGeneration() {
    succession(0);
    hold(InheritanceType.HARD_WON_HABITS);

    var next = begin();

    assertThat(next.getMilestones()).hasSize(4).allMatch(offer -> offer.achieved());
    var again = service.execute(PLAYER, new Command.Exodus());
    assertThat(again.outcome()).isEqualTo(Outcome.REJECTED);
  }

  @Test
  void courseIvNodesAreOnSaleNowAndTheTwoForVersionTwoAreNot() {
    succession(0);
    var view = SuccessionView.of(service.playerMeta(PLAYER), state());

    assertThat(view.inheritances())
        .filteredOn(node -> node.course() == 4)
        .extracting(SuccessionView.Node::id, SuccessionView.Node::available)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("HARD_WON_HABITS", true),
            org.assertj.core.groups.Tuple.tuple("THE_OPEN_BREACH", true),
            org.assertj.core.groups.Tuple.tuple("OFFERING_STONE", true),
            org.assertj.core.groups.Tuple.tuple("READER_OF_OMENS", false),
            org.assertj.core.groups.Tuple.tuple("SECOND_PACT", false));
  }

  // ---- Offering Stone

  @Test
  void theOfferingStoneConvertsVaultRelicsToLegacyCappedPerGeneration() {
    succession(0);
    hold(InheritanceType.OFFERING_STONE);
    var next = begin();
    var stone = next.getConfig().getPrestige().getRoll().getOfferingStone();
    next.setHighestFloor(40);
    next.setExpeditionsCompleted(17);
    next.getTally().addFoodGrown(60000);
    next.grant(ResourceType.RELICS, stone.getRelicsPerLegacy() * 10);
    var before = service.playerMeta(PLAYER).legacy();

    assertThat(service.execute(PLAYER, new Command.Exodus()).outcome()).isEqualTo(Outcome.APPLIED);

    var report = state().getPending();
    assertThat(report.offering()).isEqualTo(stone.getMaxLegacy());
    assertThat(report.legacy()).isEqualTo(report.toll().legacy() + stone.getMaxLegacy());
    assertThat(service.playerMeta(PLAYER).legacy()).isEqualTo(before + report.legacy());
  }

  @Test
  void aStoneIsPaidBelowTheCapInWholeThresholds() {
    succession(0);
    hold(InheritanceType.OFFERING_STONE);
    var next = begin();
    var per = next.getConfig().getPrestige().getRoll().getOfferingStone().getRelicsPerLegacy();
    next.setHighestFloor(40);
    next.setExpeditionsCompleted(17);
    next.grant(ResourceType.RELICS, per * 1.9);

    service.execute(PLAYER, new Command.Exodus());

    assertThat(state().getPending().offering()).isEqualTo(1);
  }

  @Test
  void noOfferingWithoutTheStone() {
    succession(0);
    var next = begin();
    next.setHighestFloor(40);
    next.setExpeditionsCompleted(17);
    next.grant(ResourceType.RELICS, 100_000);

    service.execute(PLAYER, new Command.Exodus());

    assertThat(state().getPending().offering()).isZero();
  }

  // ---- Epitaphs

  @Test
  void anEpitaphIsGrantedWhenACampaignEndsAndOpensTheNextGenerationsShortcut() {
    succession(0);

    assertThat(service.playerMeta(PLAYER).epitaphs()).contains("FIRST_FOOTING");
    var next = begin();

    assertThat(next.getShortcuts())
        .anyMatch(offer -> offer.name().equals("Vanguard Camp") && offer.unlocked());
  }

  @Test
  void anEpitaphSurvivesALaterCampaignThatEarnsNothingNew() {
    succession(0);
    begin();
    service.execute(PLAYER, new Command.Exodus());

    assertThat(service.playerMeta(PLAYER).epitaphs()).contains("FIRST_FOOTING");
    assertThat(service.playerMeta(PLAYER).epitaphs()).doesNotHaveDuplicates();
  }

  // ---- The Open Breach

  @Test
  void theSuccessionRecordsTheGuardiansSlainAndOffersThemToTheBreach() {
    var state = state();
    state.setHighestFloor(40);
    state.setExpeditionsCompleted(17);
    state.setTower(
        new io.github.diogohmcruz.towerdungeon.domain.models.Tower(
            100,
            state.getConfig().getBoss().copy(),
            state.getConfig().getEnemies().copy(),
            state.getRandom(),
            service.slotOf(PLAYER).plan()));
    state.getTower().markBossCleared(10);
    state.getTower().markBossCleared(20);
    assertThat(service.execute(PLAYER, new Command.Exodus()).outcome()).isEqualTo(Outcome.APPLIED);
    hold(InheritanceType.THE_OPEN_BREACH);

    assertThat(service.playerMeta(PLAYER).slainGuardians()).containsEntry(1, List.of(10, 20));
    var view = SuccessionView.of(service.playerMeta(PLAYER), state());
    assertThat(view.choices())
        .filteredOn(option -> option.group().equals(OpenBreach.GROUP))
        .extracting(SuccessionView.Option::id)
        .containsExactly(OpenBreach.NONE, "BREACH_10", "BREACH_20");
  }

  @Test
  void theBreachIsChosenAtSuccessionAndChangesOnlyThatFloorOfTheNextGeneration() {
    succession(0);
    hold(InheritanceType.THE_OPEN_BREACH);
    remember(Map.of(1, List.of(10, 20)));

    var chosen = service.execute(PLAYER, new Command.Choose("BREACH_20"));
    var next = begin();

    assertThat(chosen.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(service.playerMeta(PLAYER).openBreach()).isEqualTo(20);
    assertThat(next.getConfig().getBoss().getOpenBreach()).isEqualTo(20);
    assertThat(next.getChoices()).isEmpty();
  }

  @Test
  void aFloorNotSlainInAnEarlierGenerationIsRefused() {
    succession(0);
    hold(InheritanceType.THE_OPEN_BREACH);
    remember(Map.of(1, List.of(10)));

    assertThat(service.execute(PLAYER, new Command.Choose("BREACH_30")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    assertThat(service.execute(PLAYER, new Command.Choose("BREACH_junk")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);
    begin();
    assertThat(service.playerMeta(PLAYER).openBreach()).isZero();
  }

  @Test
  void theBreachNeedsTheInheritanceAndTheLastAnswerStands() {
    succession(0);
    remember(Map.of(1, List.of(10, 20)));
    assertThat(service.execute(PLAYER, new Command.Choose("BREACH_20")).reason())
        .isEqualTo(Reason.NOT_AVAILABLE);

    hold(InheritanceType.THE_OPEN_BREACH);
    service.execute(PLAYER, new Command.Choose("BREACH_20"));
    service.execute(PLAYER, new Command.Choose("BREACH_10"));
    begin();

    assertThat(service.playerMeta(PLAYER).openBreach()).isEqualTo(10);
  }

  @Test
  void theBreachIsForgottenWhenItsInheritanceIsGivenBackAndNotCarriedPastTheNextGeneration() {
    succession(30);
    service.execute(PLAYER, new Command.RollBuy("SEED_GRAIN"));
    hold(InheritanceType.THE_OPEN_BREACH);
    remember(Map.of(1, List.of(10)));
    service.execute(PLAYER, new Command.Choose("BREACH_10"));
    var slot = service.slotOf(PLAYER);
    slot.meta(
        slot.meta()
            .withRoll(
                slot.meta().roll().stream().filter(e -> !e.id().equals("THE_OPEN_BREACH")).toList(),
                0));
    // With the Inheritance gone the answer is ignored when the Generation begins.
    begin();

    assertThat(service.playerMeta(PLAYER).openBreach()).isZero();
  }

  @Test
  void aReloadedCampaignKeepsTheBreach() {
    succession(0);
    hold(InheritanceType.THE_OPEN_BREACH);
    remember(Map.of(1, List.of(20)));
    service.execute(PLAYER, new Command.Choose("BREACH_20"));
    begin();

    var meta = service.playerMeta(PLAYER);
    var restored =
        MetaCodec.development(mapper).fromNode(MetaCodec.development(mapper).toNode(meta));
    var campaignConfig = config.copy();
    io.github.diogohmcruz.towerdungeon.domain.prestige.RollEffects.of(restored, campaignConfig)
        .shape(campaignConfig);

    assertThat(restored.openBreach()).isEqualTo(20);
    assertThat(campaignConfig.getBoss().getOpenBreach()).isEqualTo(20);
  }
}

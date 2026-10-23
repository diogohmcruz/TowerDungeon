package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.Fallen;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

/** What a Wipe leaves behind: the fallen on the record and Risen on the floor where it ended. */
class WipeConsequencesTest {
  private static final String PLAYER = "0123456789abcdef0123456789abcdef";

  private final GameProperties config = new GameProperties();
  private final SplittableRandom random = new SplittableRandom(9);
  private final ArchitecturePlan plan =
      ArchitecturePlan.generate(
          config.getArchitecture().getSeed(),
          config.getTower().getMaxFloor(),
          config.getBoss().getInterval(),
          config.getEnemies().getSurgeInterval());
  @TempDir Path dir;

  private GameService service;
  private GameState state;
  private Tower tower;

  @BeforeEach
  void setUp() {
    config.getPersistence().setDataDir(dir.toString());
    var mapper = JsonMapper.builder().build();
    var store = new FileCampaignStore(config.getPersistence());
    service =
        new GameService(
            config,
            mapper,
            random,
            plan,
            new PartyCombat(random),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, mapper, config.getPersistence(), "test", System::nanoTime));
    service.attach(PLAYER, "ws-1").block();
    state = service.playerState(PLAYER);
    tower = new Tower(100, config.getBoss().copy(), config.getEnemies().copy(), random, plan);
    state.setTower(tower);
    tower.startNewRun();
    state.setExpeditionActive(true);
    state.startRun();
  }

  private void climbTo(int floor) {
    while (tower.getCurrentFloor() < floor) {
      tower.moveToNextFloor();
    }
    state.recordFloorReached(floor);
  }

  /** Puts {@code count} Warriors in the party, each named "Name n". */
  private List<Unit> party(int count) {
    var units = new ArrayList<Unit>();
    for (var i = 0; i < count; i++) {
      var unit = state.recruit(UnitStats.WARRIOR, random);
      units.add(unit);
    }
    state
        .getUnitsOnTower()
        .computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>())
        .addAll(units);
    return units;
  }

  private void lose(List<Unit> units) {
    units.forEach(state::removeUnit);
  }

  private List<String> risenOn(int floor) {
    return tower.getFloorMemory().risenOn(floor);
  }

  @Test
  void everyFallenUnitIsRecordedWithItsNameTypeAndFloor() {
    climbTo(2);
    var first = party(1).getFirst();
    state.removeUnit(first);
    climbTo(4);
    var second = state.recruit(UnitStats.ARCHER, random);
    state.getUnitsOnTower().computeIfAbsent(UnitStats.ARCHER, _ -> new ArrayList<>()).add(second);
    state.removeUnit(second);

    var expected =
        List.of(
            new Fallen(first.getName(), "WARRIOR", 2), new Fallen(second.getName(), "ARCHER", 4));
    assertThat(state.getRunFallen()).isEqualTo(expected);
    assertThat(state.getWallOfNames()).isEqualTo(expected);
  }

  @Test
  void aNewExpeditionStartsAFreshRunRecordButTheWallKeepsEveryName() {
    climbTo(2);
    lose(party(2));

    state.startRun();

    assertThat(state.getRunFallen()).isEmpty();
    assertThat(state.getWallOfNames()).hasSize(2);
  }

  @Test
  void aWipeRaisesOneThirdOfTheFallenRoundedUpCarryingTheirNames() {
    climbTo(7);
    var units = party(5);
    lose(units);

    state.raiseRisenOnWipe();

    assertThat(risenOn(7)).containsExactly(units.get(0).getName(), units.get(1).getName());
  }

  @Test
  void aSingleFallenUnitStillRaisesOne() {
    climbTo(7);
    var units = party(1);
    lose(units);

    state.raiseRisenOnWipe();

    assertThat(risenOn(7)).containsExactly(units.getFirst().getName());
  }

  @Test
  void theCapNeverGoesBelowOneOnAnEarlyFloor() {
    climbTo(2);
    lose(party(6));

    state.raiseRisenOnWipe();

    assertThat(risenOn(2)).hasSize(1);
  }

  @Test
  void theCapIsThreeOnAnOrdinaryDeepFloor() {
    climbTo(9);
    lose(party(12));

    state.raiseRisenOnWipe();

    assertThat(risenOn(9)).hasSize(3);
  }

  @Test
  void easingShrinksTheCap() {
    tower.markBossCleared(10);
    climbTo(7);
    lose(party(12));

    state.raiseRisenOnWipe();

    assertThat(risenOn(7)).hasSize(2);
  }

  @Test
  void repeatedWipesOnTheSameFloorDoNotCompound() {
    climbTo(9);
    lose(party(12));
    state.raiseRisenOnWipe();
    state.startRun();
    lose(party(12));

    state.raiseRisenOnWipe();

    assertThat(risenOn(9)).hasSize(3);
  }

  @Test
  void aGuardianFloorRaisesNothing() {
    climbTo(10);
    lose(party(3));

    state.raiseRisenOnWipe();

    assertThat(tower.getFloorMemory().risenByFloor()).isEmpty();
  }

  @Test
  void unitsLostOnEarlierFloorsRaiseNothingWhereTheWipeEnded() {
    climbTo(3);
    lose(party(2));
    climbTo(5);

    state.raiseRisenOnWipe();

    assertThat(tower.getFloorMemory().risenByFloor()).isEmpty();
  }

  @Test
  void nothingRisesAboveTheHighestFloor() {
    climbTo(6);
    lose(party(3));
    state.setHighestFloor(4);

    state.raiseRisenOnWipe();

    assertThat(tower.getFloorMemory().risenByFloor()).isEmpty();
  }

  @Test
  void theFloorIsRebuiltWithTheRisenOnTopOfItsGarrisonCarryingTheirNames() {
    climbTo(7);
    tower.getFloorMemory().raiseRisen(7, List.of("Brann", "Tomas"), 3);

    tower.startNewRunAt(7);

    var soldiers =
        tower.getCurrentTowerFloor().getEnemies().stream()
            .filter(enemy -> enemy.getName().equals("Brann") || enemy.getName().equals("Tomas"))
            .toList();
    assertThat(soldiers).hasSize(2);
    assertThat(soldiers).allMatch(enemy -> enemy.getStats() == EnemyStats.DEAD_SOLDIERS);
    assertThat(soldiers).allMatch(enemy -> enemy.getParts().size() == 6);
  }

  @Test
  void aWipeThroughTheServiceRaisesRisenSummarisesTheFallenAndWritesTheLedger() {
    climbTo(7);
    var units = party(4);
    lose(units);

    service.gameLoop();

    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(risenOn(7)).containsExactly(units.get(0).getName(), units.get(1).getName());
    var summary = state.getLastRunSummary();
    assertThat(summary.outcome()).isEqualTo(RunSummary.WIPED);
    assertThat(summary.fallen())
        .extracting(Fallen::name)
        .containsExactlyElementsOf(units.stream().map(Unit::getName).toList());
    assertThat(summary.fallen()).allMatch(fallen -> fallen.floor() == 7);
    assertThat(state.getLedger())
        .extracting(GameState.LedgerLine::text)
        .contains("They stay on the Floor.", "Floor 7 holds 2 more Dead Soldiers until cleared.");
  }

  @Test
  void anExtractionRaisesNothing() {
    climbTo(7);
    lose(party(3));
    party(1);

    service.handleExtractAction(PLAYER);

    assertThat(tower.getFloorMemory().risenByFloor()).isEmpty();
    assertThat(state.getLastRunSummary().fallen()).hasSize(3);
  }

  @Test
  void clearingTheFloorLaysTheRisenToRest() {
    climbTo(7);
    party(1);
    state.setSupplies(100d);
    tower.getFloorMemory().raiseRisen(7, List.of("Brann"), 3);
    tower.getCurrentTowerFloor().getEnemies().clear();

    service.gameLoop();

    assertThat(risenOn(7)).isEmpty();
    assertThat(tower.getCurrentFloor()).isEqualTo(8);
  }

  @Test
  void theFallenAndTheRisenSurviveASaveAndLoad() throws Exception {
    climbTo(7);
    lose(party(4));
    service.gameLoop();
    var mapper = JsonMapper.builder().build();

    var json = mapper.writeValueAsString(state.toSnapshot());
    var restored =
        GameState.restore(mapper.readValue(json, GameSnapshot.class), config, random, plan);

    assertThat(restored.getWallOfNames()).isEqualTo(state.getWallOfNames()).hasSize(4);
    assertThat(restored.getTower().getFloorMemory().risenOn(7)).isEqualTo(risenOn(7)).hasSize(2);
    assertThat(restored.getLastRunSummary().fallen()).hasSize(4);
  }

  @Test
  void aSaveMadeBeforeTheRecordExistedLoadsWithNoFallenAndNoRisen() throws Exception {
    var mapper = JsonMapper.builder().build();
    var json = mapper.writeValueAsString(state.toSnapshot());
    var old = json.replace(",\"wallOfNames\":[]", "").replace(",\"risen\":{}", "");
    assertThat(old).doesNotContain("wallOfNames").doesNotContain("\"risen\"");

    var restored =
        GameState.restore(mapper.readValue(old, GameSnapshot.class), config, random, plan);

    assertThat(restored.getWallOfNames()).isEmpty();
  }

  @Test
  void aReserveLostToStarvationJoinsTheWallAtTheVillage() {
    config.getVillage().setStartingFood(0);
    var fresh = new GameState(config);
    fresh.getVillage().restore(0, 0);
    var reserve = fresh.recruit(UnitStats.ARCHER, random);
    fresh.addUnits(UnitStats.ARCHER, List.of(reserve));

    fresh.triggerLifecycle(random);

    assertThat(fresh.getWallOfNames()).containsExactly(new Fallen(reserve.getName(), "ARCHER", 0));
  }
}

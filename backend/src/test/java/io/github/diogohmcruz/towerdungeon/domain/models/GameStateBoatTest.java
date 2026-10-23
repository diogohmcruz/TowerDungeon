package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState.BoatState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialRun;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** The Boat docks a fixed time after the Port is finished, and the Arrivals step ashore. */
class GameStateBoatTest {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private final GameProperties config = new GameProperties();

  GameStateBoatTest() {
    config.getPort().setFootingExpeditions(0);
    config.getBoat().setDockTicks(5);
  }

  private GameState withPort() {
    var state = new GameState(config, new RandomSource(3));
    state.getResources().put(ResourceType.MATERIALS, 5000d);
    state.addCredit(5000d);
    assertThat(state.buildPort()).isEmpty();
    return state;
  }

  private void tick(GameState state, int times) {
    for (var i = 0; i < times; i++) {
      state.triggerLifecycle();
    }
  }

  private static int reserves(GameState state) {
    return state.getUnits().values().stream().mapToInt(List::size).sum();
  }

  private GameState viaJson(GameState state) throws Exception {
    var json = MAPPER.writeValueAsString(state.toSnapshot());
    return GameState.restore(
        MAPPER.readValue(json, GameSnapshot.class), config, new RandomSource(3));
  }

  @Test
  void noBoatBeforeThePortIsFinished() {
    var state = new GameState(config, new RandomSource(3));

    tick(state, 20);

    assertThat(state.getBoat().state()).isEqualTo(BoatState.NOT_BUILT);
  }

  @Test
  void theBoatDocksExactlyTheConfiguredTicksAfterThePort() {
    var state = withPort();
    assertThat(state.getBoat().state()).isEqualTo(BoatState.DUE);

    tick(state, 4);
    assertThat(state.getBoat().state()).isEqualTo(BoatState.DUE);
    assertThat(state.getBoat().ticksToDock()).isEqualTo(1);

    tick(state, 1);
    assertThat(state.getBoat().state()).isEqualTo(BoatState.DOCKED);
  }

  @Test
  void theArrivalsStepIntoTheReservesByNameAndType() {
    var state = withPort();
    var before = reserves(state);

    tick(state, 5);

    assertThat(reserves(state)).isEqualTo(before + 4);
    assertThat(state.getUnits().get(UnitStats.DRACO_METAMORPH))
        .extracting(Unit::getName)
        .containsExactly("Iscale");
    assertThat(state.getUnits().get(UnitStats.MAGE)).hasSize(1);
    assertThat(state.getUnits().get(UnitStats.TANK)).hasSize(1);
    assertThat(state.getUnits().get(UnitStats.PORTER)).hasSize(1);
  }

  @Test
  void typesThatWereNotRecruitableOpenAfterwards() {
    var state = withPort();
    assertThat(state.isUnitUnlocked(UnitStats.DRACO_METAMORPH)).isFalse();
    assertThat(state.isUnitUnlocked(UnitStats.MAGE)).isFalse();
    assertThat(state.isUnitUnlocked(UnitStats.TANK)).isFalse();

    tick(state, 5);

    assertThat(state.isUnitUnlocked(UnitStats.DRACO_METAMORPH)).isTrue();
    assertThat(state.isUnitUnlocked(UnitStats.MAGE)).isTrue();
    assertThat(state.isUnitUnlocked(UnitStats.TANK)).isTrue();
    assertThat(state.isUnitUnlocked(UnitStats.HEALER)).isFalse();
  }

  @Test
  void theLedgerNamesTheBoatThenEachArrival() {
    var state = withPort();

    tick(state, 5);

    var lines = state.getLedger().stream().map(line -> line.text()).toList();
    var at = lines.indexOf("The Boat docks at the pier.");
    assertThat(at).isGreaterThanOrEqualTo(0);
    assertThat(lines.subList(at + 1, at + 5))
        .containsExactly(
            "Iscale, Draco metamorph, steps ashore.",
            "The Silent Assistant, Porter, steps ashore.",
            "The Pyromancer, Mage, steps ashore.",
            "The Battle Maiden, Tank, steps ashore.");
  }

  @Test
  void theBoatDocksOnlyOnce() {
    var state = withPort();
    tick(state, 5);
    var after = reserves(state);

    tick(state, 20);

    assertThat(reserves(state)).isEqualTo(after);
    assertThat(state.getLedger().stream().filter(l -> l.text().startsWith("The Boat")).count())
        .isEqualTo(1);
  }

  @Test
  void theCountSurvivesASaveAndLoadWhileDue() throws Exception {
    var state = withPort();
    tick(state, 3);

    var restored = viaJson(state);

    assertThat(restored.getBoat()).isEqualTo(state.getBoat());
    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
    tick(restored, 1);
    assertThat(restored.getBoat().state()).isEqualTo(BoatState.DUE);
    tick(restored, 1);
    assertThat(restored.getBoat().state()).isEqualTo(BoatState.DOCKED);
  }

  @Test
  void dockedSurvivesASaveAndLoadWithoutDockingAgainAndKeepsTheUnlocks() throws Exception {
    var state = withPort();
    tick(state, 5);

    var restored = viaJson(state);

    assertThat(restored.getBoat().state()).isEqualTo(BoatState.DOCKED);
    assertThat(restored.isUnitUnlocked(UnitStats.TANK)).isTrue();
    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
    var units = reserves(restored);
    tick(restored, 20);
    assertThat(reserves(restored)).isEqualTo(units);
  }

  @Test
  void anOlderSaveWithoutTheBoatReadsAsDueFromTheStartOfTheCount() throws Exception {
    var tree = (ObjectNode) MAPPER.valueToTree(withPort().toSnapshot());
    ((ObjectNode) tree.get("campaign")).remove("boatTicks");
    ((ObjectNode) tree.get("campaign")).remove("arrivalsCame");

    var restored =
        GameState.restore(
            MAPPER.treeToValue(tree, GameSnapshot.class), config, new RandomSource(3));

    assertThat(restored.getBoat().state()).isEqualTo(BoatState.DUE);
    assertThat(restored.getBoat().ticksToDock()).isEqualTo(5);
  }

  @Test
  void aTrialNeverHasABoat() {
    var state = new GameState(config, RandomSource.forTrial(99L, 5L));
    state.setTrial(new TrialRun("season-1", 1, 10, 5400));

    tick(state, 20);

    assertThat(state.getBoat().state()).isEqualTo(BoatState.NOT_BUILT);
  }

  @Test
  void theSameSeedAndScriptReproducesTheArrivals() {
    var first = withPort();
    var second = withPort();

    tick(first, 5);
    tick(second, 5);

    assertThat(second.toSnapshot()).isEqualTo(first.toSnapshot());
  }
}

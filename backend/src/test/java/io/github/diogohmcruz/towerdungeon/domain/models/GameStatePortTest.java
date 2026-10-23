package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState.PortState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialRun;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** The Port: its footing opens at an Expedition count and it is finished from the Vault. */
class GameStatePortTest {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private final GameProperties config = new GameProperties();

  /** A Campaign that has completed {@code expeditions}, with a Vault of the given means. */
  private GameState after(int expeditions, double materials, double credits) throws Exception {
    var state = new GameState(config);
    state.getResources().put(ResourceType.MATERIALS, materials);
    state.addCredit(credits - state.getCredit());
    return withExpeditions(state, expeditions);
  }

  private GameState withExpeditions(GameState state, int expeditions) throws Exception {
    var tree = (ObjectNode) MAPPER.valueToTree(state.toSnapshot());
    ((ObjectNode) tree.get("campaign")).put("expeditionsCompleted", expeditions);
    return restore(tree);
  }

  private GameState restore(ObjectNode tree) throws Exception {
    return GameState.restore(
        MAPPER.treeToValue(tree, GameSnapshot.class), config, new RandomSource(1));
  }

  @Test
  void theFootingOpensExactlyAtTheConfiguredExpeditionCount() throws Exception {
    assertThat(after(11, 1000, 1000).getPort().state()).isEqualTo(PortState.HIDDEN);
    assertThat(after(12, 1000, 1000).getPort().state()).isEqualTo(PortState.FOOTING);
  }

  @Test
  void aBuildBeforeTheFootingIsRefusedAndChangesNothing() throws Exception {
    var state = after(11, 1000, 1000);
    var before = state.toSnapshot();

    assertThat(state.buildPort()).isPresent();

    assertThat(state.toSnapshot()).isEqualTo(before);
  }

  @Test
  void buildingSpendsExactlyTheConfiguredCostAndWritesALedgerLine() throws Exception {
    var state = after(12, 1000, 500);

    assertThat(state.buildPort()).isEmpty();

    assertThat(state.getPort().state()).isEqualTo(PortState.BUILT);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(400.0);
    assertThat(state.getCredit()).isEqualTo(200.0);
    assertThat(state.getLedger()).anyMatch(line -> line.text().startsWith("The Port is finished"));
  }

  @Test
  void aVaultThatCannotPayLeavesTheStateUnchanged() throws Exception {
    for (var vault : new double[][] {{599, 1000}, {1000, 299}}) {
      var state = after(12, vault[0], vault[1]);
      var before = state.toSnapshot();

      assertThat(state.buildPort()).isPresent();

      assertThat(state.toSnapshot()).isEqualTo(before);
      assertThat(state.getPort().state()).isEqualTo(PortState.FOOTING);
    }
  }

  @Test
  void aPortIsFinishedOnlyOnce() throws Exception {
    var state = after(12, 2000, 2000);
    assertThat(state.buildPort()).isEmpty();

    assertThat(state.buildPort()).isPresent();
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(1400.0);
  }

  @Test
  void theStateSurvivesASaveAndLoad() throws Exception {
    var state = after(12, 1000, 1000);
    state.buildPort();

    var json = MAPPER.writeValueAsString(state.toSnapshot());
    var restored =
        GameState.restore(MAPPER.readValue(json, GameSnapshot.class), config, new RandomSource(1));

    assertThat(restored.getPort().state()).isEqualTo(PortState.BUILT);
    assertThat(restored.toSnapshot()).isEqualTo(state.toSnapshot());
  }

  @Test
  void anOlderSaveWithoutThePortReadsFromTheExpeditionCount() throws Exception {
    var tree = (ObjectNode) MAPPER.valueToTree(after(12, 0, 0).toSnapshot());
    ((ObjectNode) tree.get("campaign")).remove("portBuilt");

    assertThat(restore(tree).getPort().state()).isEqualTo(PortState.FOOTING);
  }

  @Test
  void aTrialHasNoPort() throws Exception {
    var state = new GameState(config, RandomSource.forTrial(99L, 5L));
    state.setTrial(new TrialRun("season-1", 1, 10, 5400));
    state.getResources().put(ResourceType.MATERIALS, 5000d);
    state.addCredit(5000d);
    var trial = withExpeditions(state, 20);

    assertThat(trial.getPort().state()).isEqualTo(PortState.HIDDEN);
    assertThat(trial.buildPort()).isPresent();
  }
}

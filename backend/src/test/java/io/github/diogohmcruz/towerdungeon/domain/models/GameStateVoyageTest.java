package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState.VoyageState;
import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.GameSnapshot;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The docked Boat sails one Voyage at a time, a Quarter of connected ticks, and the sea may win.
 */
class GameStateVoyageTest {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private final GameProperties config = new GameProperties();

  GameStateVoyageTest() {
    config.getPort().setFootingExpeditions(0);
    config.getBoat().setDockTicks(2);
    config.getVoyage().setTicks(6);
    config.getYear().setSeaRiskMean(0.10);
    config.getYear().setSeaRiskAmplitude(0.0);
  }

  /** A docked Boat, 100 Relics in the Vault; the combat stream always draws {@code draw}. */
  private GameState docked(long draw) {
    var state = new GameState(config, RandomSource.scripted(() -> draw));
    state.getResources().put(ResourceType.MATERIALS, 5000d);
    state.addCredit(5000d);
    assertThat(state.buildPort()).isEmpty();
    tick(state, 2);
    assertThat(state.getBoat().state()).isEqualTo(GameState.BoatState.DOCKED);
    state.getResources().put(ResourceType.RELICS, 100d);
    return state;
  }

  private static void tick(GameState state, int times) {
    for (var i = 0; i < times; i++) {
      state.triggerLifecycle();
    }
  }

  private static GameState viaJson(GameState state, GameProperties config, long draw)
      throws Exception {
    var json = MAPPER.writeValueAsString(state.toSnapshot());
    return GameState.restore(
        MAPPER.readValue(json, GameSnapshot.class), config, RandomSource.scripted(() -> draw));
  }

  @Test
  void noVoyageUntilTheBoatDocks() {
    var state = new GameState(config, new RandomSource(3));
    state.getResources().put(ResourceType.RELICS, 100d);

    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.UNAVAILABLE);
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isPresent();
  }

  @Test
  void aSafeVoyageReturnsSaltAfterExactlyOneQuarterOfTicks() {
    var state = docked(-1L);
    var saltBefore = state.getVillage().getSalt();

    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isEmpty();
    assertThat(state.vaultRelics()).isEqualTo(90d);
    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.SAILING);

    tick(state, 5);
    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.SAILING);
    assertThat(state.getVoyage().ticksLeft()).isEqualTo(1);
    assertThat(state.getVillage().getSalt()).isEqualTo(saltBefore);

    tick(state, 1);
    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.READY);
    assertThat(state.getVillage().getSalt()).isEqualTo(saltBefore + 20);
    assertThat(state.takeVoyageEnded()).isEqualTo("VOYAGE_RETURNED");
    assertThat(state.takeVoyageEnded()).isNull();
  }

  @Test
  void aSafeVoyageCanReturnSporesAtTheirRate() {
    var state = docked(-1L);
    var sporesBefore = state.getVillage().getSpores();

    assertThat(state.sendVoyage(10, VoyageGoods.SPORES)).isEmpty();
    tick(state, 6);

    assertThat(state.getVillage().getSpores()).isEqualTo(sporesBefore + 6);
  }

  @Test
  void aLostVoyageCostsTheCargoNotTheBoatAndNamesTheQuarter() {
    var state = docked(0L);
    var saltBefore = state.getVillage().getSalt();

    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isEmpty();
    tick(state, 6);

    assertThat(state.getVillage().getSalt()).isEqualTo(saltBefore);
    assertThat(state.vaultRelics()).isEqualTo(90d);
    assertThat(state.getBoat().state()).isEqualTo(GameState.BoatState.DOCKED);
    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.READY);
    assertThat(state.takeVoyageEnded()).isEqualTo("VOYAGE_LOST");
    var lost = state.getLedger().stream().filter(l -> l.text().contains("sea took")).toList();
    assertThat(lost).hasSize(1);
    assertThat(lost.getFirst().text()).contains("Spring");
    assertThat(lost.getFirst().loss()).isTrue();
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isEmpty();
  }

  @Test
  void theRiskIsTheYearsScaledByTheCargoAndCapped() {
    var state = docked(-1L);

    assertThat(state.voyageRisk(10)).isEqualTo(0.10);
    assertThat(state.voyageRisk(5)).isEqualTo(0.05);
    assertThat(state.voyageRisk(15)).isEqualTo(0.15);
    assertThat(state.voyageRisk(50)).isEqualTo(0.20);
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isEmpty();
    assertThat(state.getVoyage().riskTaken()).isEqualTo(0.10);
  }

  @Test
  void theChanceIsDrawnOnceAtDepartureAtTheRisk() {
    // 0.05 of the stream's range: lost at 10% (risk 0.10) but safe at 5 Relics (risk 0.05).
    var draw = (long) (0.07 * Math.pow(2, 53)) << 11;
    var lostState = docked(draw);
    lostState.sendVoyage(10, VoyageGoods.SALT);
    tick(lostState, 6);
    assertThat(lostState.takeVoyageEnded()).isEqualTo("VOYAGE_LOST");

    var safeState = docked(draw);
    safeState.sendVoyage(5, VoyageGoods.SALT);
    tick(safeState, 6);
    assertThat(safeState.takeVoyageEnded()).isEqualTo("VOYAGE_RETURNED");
  }

  @Test
  void oneVoyageAtATimeAndOnlyWholeLotsTheVaultHolds() {
    var state = docked(-1L);

    assertThat(state.sendVoyage(7, VoyageGoods.SALT)).isPresent();
    assertThat(state.sendVoyage(0, VoyageGoods.SALT)).isPresent();
    assertThat(state.sendVoyage(55, VoyageGoods.SALT)).isPresent();
    assertThat(state.sendVoyage(10, null)).isPresent();
    state.getResources().put(ResourceType.RELICS, 5d);
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isPresent();
    state.getResources().put(ResourceType.RELICS, 100d);
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isEmpty();
    assertThat(state.sendVoyage(10, VoyageGoods.SALT)).isPresent();
    assertThat(state.vaultRelics()).isEqualTo(90d);
  }

  @Test
  void theVoyageSurvivesASaveAndLoadAndKeepsItsDraw() throws Exception {
    var state = docked(0L);
    state.sendVoyage(10, VoyageGoods.SALT);
    tick(state, 4);

    var loaded = viaJson(state, config, -1L);

    assertThat(loaded.getVoyage().state()).isEqualTo(VoyageState.SAILING);
    assertThat(loaded.getVoyage().ticksLeft()).isEqualTo(2);
    assertThat(loaded.getVoyage().relics()).isEqualTo(10);
    tick(loaded, 2);
    assertThat(loaded.takeVoyageEnded()).isEqualTo("VOYAGE_LOST");
  }

  @Test
  void anOlderSaveHasNoVoyage() throws Exception {
    var state = docked(-1L);
    var tree = MAPPER.readTree(MAPPER.writeValueAsString(state.toSnapshot()));
    ((ObjectNode) tree.get("campaign")).remove("voyage");

    var loaded =
        GameState.restore(
            MAPPER.treeToValue(tree, GameSnapshot.class), config, RandomSource.scripted(() -> -1L));

    assertThat(loaded.getVoyage().state()).isEqualTo(VoyageState.READY);
  }

  @Test
  void theBoatTouchesNeitherFoodNorCredits() {
    var state = docked(-1L);
    var credits = state.getCredit();
    var food = state.getVillage().getFood();

    state.sendVoyage(10, VoyageGoods.SALT);

    assertThat(state.getCredit()).isEqualTo(credits);
    assertThat(state.getVillage().getFood()).isEqualTo(food);
  }
}

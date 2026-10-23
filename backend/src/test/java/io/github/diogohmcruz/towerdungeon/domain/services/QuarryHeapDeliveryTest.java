package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import tools.jackson.databind.json.JsonMapper;

/** The Quarry Heap is carried down by a Party that came home; a Wipe leaves it at the Quarry. */
class QuarryHeapDeliveryTest {
  private static final String SESSION = "s";

  private final GameProperties config = new GameProperties();
  private final SplittableRandom random = new SplittableRandom(9);
  private final ArchitecturePlan plan =
      ArchitecturePlan.generate(
          config.getArchitecture().getSeed(),
          config.getTower().getMaxFloor(),
          config.getBoss().getInterval(),
          config.getEnemies().getSurgeInterval());
  private final GameService service =
      new GameService(config, JsonMapper.builder().build(), random, plan, new PartyCombat(random));

  /** A state mid-Expedition, three Floors up from the base, with a 40-Material Heap at home. */
  private GameState expeditionThreeFloorsUp() {
    service.handleSellFoodAction(SESSION);
    var state = service.playerState(SESSION);
    state.getVillage().restoreTrades(0, 0, Map.of(), 0, 40, false, 0, 0);
    var tower =
        new Tower(
            config.getTower().getMaxFloor(),
            config.getBoss().copy(),
            config.getEnemies().copy(),
            random,
            plan);
    state.setTower(tower);
    tower.startNewRun();
    for (var i = 0; i < 3; i++) {
      tower.moveToNextFloor();
    }
    state.setExpeditionActive(true);
    return state;
  }

  @Test
  void extractionDeliversFivePerFloorClearedAndKeepsTheRestInTheHeap() {
    var state = expeditionThreeFloorsUp();

    service.handleExtractAction(SESSION);

    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(15.0);
    assertThat(state.getVillage().getQuarryHeap()).isEqualTo(25.0);
  }

  @Test
  void aWipeDeliversNothingAndTheHeapStays() {
    var state = expeditionThreeFloorsUp();
    state.setUnitsOnTower(new HashMap<>());

    service.gameLoop();

    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isZero();
    assertThat(state.getVillage().getQuarryHeap()).isEqualTo(40.0);
  }
}

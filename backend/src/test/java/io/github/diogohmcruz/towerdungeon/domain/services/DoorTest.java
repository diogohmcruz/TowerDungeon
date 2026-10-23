package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.FileCampaignStore;
import tools.jackson.databind.json.JsonMapper;

/** The sealed Door at the Floor beneath the Summit: it pays once and then it holds the Party. */
class DoorTest {
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
            SeedSource.fixed(9),
            plan,
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, mapper, config.getPersistence(), "test", System::nanoTime));
    service.attach(PLAYER, "ws-1").block();
    state = service.playerState(PLAYER);
  }

  /** The Party stands on a cleared Floor 99 of this state's Tower. */
  private void standAtTheDoor() {
    tower =
        new Tower(
            100, config.getBoss().copy(), config.getEnemies().copy(), state.getRandom(), plan);
    state.setTower(tower);
    tower.startNewRunAt(99);
    tower.getCurrentTowerFloor().getEnemies().clear();
    state.setExpeditionActive(true);
    state.startRun();
    state.setRunStartFloor(99);
    var unit = state.recruit(UnitStats.WARRIOR, random);
    state.getUnitsOnTower().computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>()).add(unit);
    state.setSupplies(500d);
  }

  private void tick() {
    service.lifecycleTick(service.slotOf(PLAYER), state);
  }

  @Test
  void floorNinetyNineIsPaidExactlyOnceWhileThePartyWaitsAtTheSealedDoor() {
    standAtTheDoor();

    tick();
    var paid = state.getCarriedCredits();
    assertThat(paid).isEqualTo(99 * config.getReward().getCreditPerFloor());
    assertThat(state.isDoorSeen()).isTrue();
    assertThat(tower.getCurrentFloor()).isEqualTo(99);
    var suppliesAfterFirst = state.getSupplies();

    for (var i = 0; i < 5; i++) {
      tick();
    }

    assertThat(state.getCarriedCredits()).isEqualTo(paid);
    assertThat(tower.getCurrentFloor()).isEqualTo(99);
    assertThat(state.getSupplies()).isLessThan(suppliesAfterFirst);
    assertThat(state.getLedger().toString()).contains("The door does not open.");
  }

  @Test
  void theDoorFlagIsSavedWithTheExpedition() {
    standAtTheDoor();
    tick();

    assertThat(state.toSnapshot().expedition().doorSeen()).isTrue();
  }

  @Test
  void withBloodOfTheDoorThePartyClimbsPastFloorNinetyNine() {
    standAtTheDoor();
    state.setDoorOpen(true);

    tick();

    assertThat(tower.getCurrentFloor()).isEqualTo(100);
    assertThat(state.isDoorSeen()).isFalse();
  }

  @Test
  void aNewExpeditionForgetsTheDoor() {
    standAtTheDoor();
    tick();
    state.startRun();

    assertThat(state.isDoorSeen()).isFalse();
  }
}

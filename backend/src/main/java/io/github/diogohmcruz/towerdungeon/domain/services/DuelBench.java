package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/**
 * The seam the balance tools use to run the real game on a bare Campaign: no connection, no store,
 * and a Party put on a Floor of the Tower directly. It adds no rule of its own: a Campaign is a
 * {@link GameState}, an Expedition is {@code startRun} on a {@link Tower}, and a second is the
 * manual driver's second (Attrition, then the lifecycle tick), so combat, Attrition, the Supplies
 * drain, Starvation, Easing and Tower Memory are the game's own code.
 *
 * <p>It is not a player feature and checks none of the rules a command checks (an open Shortcut, a
 * Party limit): a duel may start on any Floor.
 */
public final class DuelBench {
  private final GameService service;
  private final ArchitecturePlan plan;
  private final PlayerSlot slot = new PlayerSlot("duel", ClockMode.MANUAL);

  public DuelBench(GameService service, ArchitecturePlan plan) {
    this.service = service;
    this.plan = plan;
  }

  /** A Campaign with an empty roster on {@code seed}, owning {@code campaignConfig}. */
  public GameState open(GameProperties campaignConfig, long seed) {
    return new GameState(campaignConfig, new RandomSource(seed));
  }

  /**
   * Puts {@code party} on the Tower (the Reserves are left empty) and gives the Campaign its Tower,
   * on which Easing may be set before {@link #setOut}.
   */
  public Tower muster(GameState state, List<Unit> party) {
    var onTower = new EnumMap<UnitStats, List<Unit>>(UnitStats.class);
    for (var unit : party) {
      onTower.computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }
    state.setUnitsOnTower(onTower);
    var config = state.getConfig();
    var tower =
        new Tower(
            config.getTower().getMaxFloor(),
            config.getBoss().copy(),
            config.getEnemies().copy(),
            state.getRandom(),
            plan);
    state.setTower(tower);
    return tower;
  }

  /**
   * Sets the Expedition out from {@code startFloor}, as {@code startExpedition} does once its
   * checks are passed: the Floor is built from the Campaign's streams, and the Party carries what
   * its Carrying Capacity and the pantry allow.
   */
  public void setOut(GameState state, int startFloor) {
    state.getTower().startNewRunAt(startFloor);
    state.setRunStartFloor(startFloor);
    state.startRun();
    if (startFloor > 0) {
      state.recordFloorReached(startFloor);
      state.noteStartFloor(startFloor);
    }
  }

  /** One simulated second of an Expedition: the manual driver's second, nothing else. */
  public void second(GameState state) {
    service.attritionTick(state, 1000);
    service.lifecycleTick(slot, state);
  }
}

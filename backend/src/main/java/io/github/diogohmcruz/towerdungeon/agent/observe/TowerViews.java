package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService.Meta;

/**
 * The Tower as the player has met it: only Floors up to the highest reached, and for each only its
 * guardian and Surge flags, its difficulty and how many slain Guardians ease it. The current
 * Garrison is summarised for the Floor the Party stands on. No seed, no plan, no Architecture and
 * no Garrison of a Floor not yet met is reachable from here.
 */
public final class TowerViews {
  private TowerViews() {}

  public static Map<String, Object> summary(GameState state, Meta meta) {
    var tower = state.getTower();
    var config = state.getConfig();
    var highest = state.getHighestFloor();
    var guardians = new ArrayList<Integer>();
    var surges = new ArrayList<Integer>();
    for (var floor = 1; floor <= highest; floor++) {
      if (isGuardianFloor(state, floor)) {
        guardians.add(floor);
      }
      if (isSurgeFloor(state, floor)) {
        surges.add(floor);
      }
    }
    var view = new LinkedHashMap<String, Object>();
    view.put("seq", meta.seq());
    view.put("highest_floor", highest);
    view.put("max_floor", config.getTower().getMaxFloor());
    view.put(
        "expedition_floor",
        state.isExpeditionActive() && tower != null ? tower.getCurrentFloor() : null);
    view.put("guardian_floors_met", guardians);
    view.put("guardians_slain", tower == null ? List.of() : tower.getSlainGuardians());
    view.put("surge_floors_met", surges);
    view.put(
        "shortcuts",
        state.getShortcuts().stream()
            .map(
                s ->
                    Map.of(
                        "name",
                        s.name(),
                        "floor",
                        s.floor(),
                        "open",
                        s.unlocked(),
                        "trigger",
                        s.trigger()))
            .toList());
    view.put(
        "milestones",
        state.getMilestones().stream()
            .map(m -> Map.of("name", m.name(), "trigger", m.trigger(), "reached", m.achieved()))
            .toList());
    if (state.isExpeditionActive() && tower != null && tower.getCurrentTowerFloor() != null) {
      view.put("garrison", garrison(tower.getCurrentTowerFloor().getEnemies()));
    }
    view.put("hint", "Floor detail: td://tower/floors/{n}");
    return view;
  }

  /** One met Floor; empty for a Floor not yet reached or outside the Tower. */
  public static Optional<Map<String, Object>> floor(GameState state, Meta meta, int floor) {
    if (floor < 1
        || floor > state.getHighestFloor()
        || floor > state.getConfig().getTower().getMaxFloor()) {
      return Optional.empty();
    }
    var tower = state.getTower();
    var met = tower == null ? null : tower.getFloors().get(floor);
    var view = new LinkedHashMap<String, Object>();
    view.put("seq", meta.seq());
    view.put("floor", floor);
    view.put("guardian", isGuardianFloor(state, floor));
    view.put("surge", isSurgeFloor(state, floor));
    view.put("difficulty", met == null ? null : met.getDifficulty());
    var slain = tower == null ? List.<Integer>of() : tower.getSlainGuardians();
    view.put("eased_by_guardians_slain", slain.stream().filter(g -> g > floor).count());
    if (state.isExpeditionActive()
        && tower != null
        && tower.getCurrentFloor() == floor
        && met != null) {
      view.put("garrison", garrison(met.getEnemies()));
    }
    return Optional.of(view);
  }

  private static boolean isGuardianFloor(GameState state, int floor) {
    var config = state.getConfig();
    return floor % config.getBoss().getInterval() == 0 || floor == config.getTower().getMaxFloor();
  }

  private static boolean isSurgeFloor(GameState state, int floor) {
    var interval = state.getConfig().getEnemies().getSurgeInterval();
    return interval > 0 && floor % interval == 0 && !isGuardianFloor(state, floor);
  }

  private static Map<String, Object> garrison(List<Enemy> enemies) {
    var types = new TreeMap<String, Integer>();
    enemies.forEach(enemy -> types.merge(enemy.getStats().name(), 1, Integer::sum));
    var current = enemies.stream().mapToDouble(Enemy::getCurrentHealth).sum();
    var max = enemies.stream().mapToDouble(Enemy::getMaxHealth).sum();
    var garrison = new LinkedHashMap<String, Object>();
    garrison.put("remaining", enemies.size());
    garrison.put("health", Math.round(current) + "/" + Math.round(max));
    garrison.put("types", types);
    return garrison;
  }
}

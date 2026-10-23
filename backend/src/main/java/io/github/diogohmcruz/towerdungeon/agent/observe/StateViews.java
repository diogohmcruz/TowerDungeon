package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Part;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.observation.StateProjection;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService.Meta;
import tools.jackson.databind.json.JsonMapper;

/**
 * The views of a Campaign's state: {@code brief} (the projection, a few hundred tokens) and {@code
 * full} (the brief plus aggregated detail). Both aggregate per unit type and never list a unit, a
 * body part, an item or a phobia one by one, so their size does not grow with the Roster. Only what
 * the player's own screen shows is here: a phobia counts once it is known, no rolled strength
 * appears, and neither a seed nor the Tower's plan is reachable from this class.
 */
public final class StateViews {
  /**
   * The most characters a {@code brief} view may take: about 1,000 tokens, for a busy Campaign with
   * thirteen unit groups (the projection now carries each group's health per body part).
   */
  public static final int BRIEF_BUDGET = 4_000;

  /** The most characters a {@code full} view may take. */
  public static final int FULL_BUDGET = 8_000;

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private StateViews() {}

  @SuppressWarnings("unchecked")
  public static Map<String, Object> brief(GameState state, Meta meta) {
    var view = new LinkedHashMap<String, Object>();
    view.put("view", "brief");
    view.put("clock", meta.clock().name().toLowerCase());
    view.put("held", meta.held());
    var projection =
        (Map<String, Object>)
            MAPPER.convertValue(StateProjection.brief(state, meta.seq()), Map.class);
    view.putAll(projection);
    return view;
  }

  public static Map<String, Object> full(GameState state, Meta meta) {
    var view = brief(state, meta);
    view.put("view", "full");
    var extra = new LinkedHashMap<String, Object>();
    extra.put("village_tier", state.getVillageTierName());
    extra.put("trust", state.getTrustHeld());
    extra.put("unlocked_units", unlocked(state));
    extra.put("upgrades", upgrades(state));
    extra.put("milestones_reached", milestones(state));
    extra.put("shortcuts_open", shortcuts(state));
    extra.put("trades", trades(state));
    extra.put("units", unitDetail(state));
    view.put("detail", extra);
    return view;
  }

  private static List<Map<String, Object>> unlocked(GameState state) {
    var out = new ArrayList<Map<String, Object>>();
    for (var name : state.getUnlockedUnitTypes()) {
      var cost = UnitStats.valueOf(name).getCost();
      out.add(Map.of("type", name, "cost", cost));
    }
    out.sort((a, b) -> ((String) a.get("type")).compareTo((String) b.get("type")));
    return out;
  }

  private static Map<String, Integer> upgrades(GameState state) {
    var out = new TreeMap<String, Integer>();
    state
        .getPurchasedUpgrades()
        .forEach(
            (type, level) -> {
              if (level > 0) {
                out.put(type.name(), level);
              }
            });
    return out;
  }

  private static List<String> milestones(GameState state) {
    return state.getMilestones().stream()
        .filter(m -> m.achieved())
        .map(m -> m.id())
        .sorted()
        .toList();
  }

  private static List<Integer> shortcuts(GameState state) {
    return state.getShortcuts().stream()
        .filter(s -> s.unlocked())
        .map(s -> s.floor())
        .sorted()
        .toList();
  }

  private static List<Map<String, Object>> trades(GameState state) {
    return state.getTradeViews().stream()
        .map(t -> Map.<String, Object>of("trade", t.trade(), "hands", t.hands()))
        .toList();
  }

  /** Reserves and the Party together, counted by condition, never one unit at a time. */
  private static Map<String, Object> unitDetail(GameState state) {
    var all = new ArrayList<Unit>();
    state.getUnits().values().forEach(all::addAll);
    state.getUnitsOnTower().values().forEach(all::addAll);
    var sanity = new LinkedHashMap<String, Integer>();
    sanity.put("steady", 0);
    sanity.put("shaken", 0);
    sanity.put("frayed", 0);
    sanity.put("breaking", 0);
    var lostParts = new TreeMap<String, Integer>();
    var items = new TreeMap<String, Integer>();
    var phobias = new TreeMap<String, Integer>();
    for (var unit : all) {
      var share = unit.getSanity() / Math.max(1d, unit.getMaxSanity());
      var band =
          share >= 0.75
              ? "steady"
              : share >= 0.5 ? "shaken" : share >= 0.25 ? "frayed" : "breaking";
      sanity.merge(band, 1, Integer::sum);
      unit.getParts().stream()
          .filter(Part::isLost)
          .forEach(part -> lostParts.merge(part.getKind().name(), 1, Integer::sum));
      unit.getGear()
          .getSlots()
          .values()
          .forEach(item -> items.merge(item.tier().name(), 1, Integer::sum));
      unit.getPhobias().stream()
          .filter(p -> p.isKnown())
          .forEach(p -> phobias.merge(p.getRevealedType(), 1, Integer::sum));
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("count", all.size());
    out.put("sanity", sanity);
    out.put("lost_parts", lostParts);
    out.put("items_worn", items);
    out.put("known_phobias", phobias);
    return out;
  }
}

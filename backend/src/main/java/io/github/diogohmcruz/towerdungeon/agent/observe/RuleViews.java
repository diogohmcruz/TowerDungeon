package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import tools.jackson.databind.json.JsonMapper;

/**
 * The static rules, in sections, so a reader asks only for the one it needs. They are the values
 * the player's own screen shows, read from the same enums and configuration; no Campaign state and
 * nothing hidden is in them. The Enemy Type table is a section of its own that only a Sandbox
 * reader may take: a player learns an Enemy Type by meeting it.
 */
public final class RuleViews {
  public static final List<String> SECTIONS =
      List.of("units", "upgrades", "shortcuts", "milestones", "tick");

  /** The section a player-level reader may not take. */
  public static final String ENEMIES = "enemies";

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private RuleViews() {}

  public static Map<String, Object> index() {
    var view = new LinkedHashMap<String, Object>();
    view.put("sections", SECTIONS);
    view.put("hint", "Ask for one section with the 'section' argument.");
    return view;
  }

  /** One section, or null when there is no such section. */
  public static Map<String, Object> section(String name, GameProperties config, boolean enemies) {
    var view = new LinkedHashMap<String, Object>();
    view.put("section", name);
    switch (name) {
      case "units" -> view.put("units", units());
      case "upgrades" -> view.put("upgrades", upgrades());
      case "shortcuts" -> view.put("shortcuts", shortcuts(config));
      case "milestones" -> view.put("milestones", milestones());
      case "tick" -> view.put("tick", tick(config));
      case ENEMIES -> {
        if (!enemies) {
          return null;
        }
        view.put("enemies", enemyTypes());
      }
      default -> {
        return null;
      }
    }
    return view;
  }

  private static List<Map<String, Object>> units() {
    var out = new ArrayList<Map<String, Object>>();
    for (var type : UnitStats.values()) {
      var unit = new LinkedHashMap<String, Object>();
      unit.put("type", type.name());
      unit.put("health", type.getHealth());
      unit.put("damage", type.getDamage());
      unit.put("attack", type.getAttackType().name());
      unit.put("cost", type.getCost());
      unit.put("supply_bonus", type.getSupplyBonus());
      unit.put("weaknesses", byName(type.getWeaknesses()));
      out.add(unit);
    }
    return out;
  }

  private static List<Map<String, Object>> upgrades() {
    var out = new ArrayList<Map<String, Object>>();
    for (var type : UpgradeType.values()) {
      var upgrade = new LinkedHashMap<String, Object>();
      upgrade.put("id", type.name());
      upgrade.put("name", type.getDisplayName());
      upgrade.put("description", type.getDescription());
      upgrade.put("repeatable", type.isRepeatable());
      upgrade.put("unlocks", type.getUnlockUnit() == null ? null : type.getUnlockUnit().name());
      var cost = new TreeMap<String, Double>();
      type.costAtLevel(0).forEach((k, v) -> cost.put(k.name(), v));
      upgrade.put("first_cost", cost);
      out.add(upgrade);
    }
    return out;
  }

  private static List<Map<String, Object>> shortcuts(GameProperties config) {
    return Arrays.stream(ShortcutType.values())
        .map(
            s ->
                Map.<String, Object>of(
                    "id", s.name(),
                    "name", s.getDisplayName(),
                    "floor", config.getShortcuts().floorOf(s),
                    "trigger", s.getTriggerLabel()))
        .toList();
  }

  private static List<Map<String, Object>> milestones() {
    return Arrays.stream(MilestoneType.values())
        .map(
            m -> {
              var out = new LinkedHashMap<String, Object>();
              out.put("id", m.name());
              out.put("name", m.getDisplayName());
              out.put("trigger", m.getTriggerLabel());
              out.put("unlocks", m.getUnlockUnit() == null ? null : m.getUnlockUnit().name());
              return (Map<String, Object>) out;
            })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> tick(GameProperties config) {
    var out = new LinkedHashMap<String, Object>();
    out.put("loop", MAPPER.convertValue(config.getLoop(), Map.class));
    out.put("reward", MAPPER.convertValue(config.getReward(), Map.class));
    out.put("village", MAPPER.convertValue(config.getVillage(), Map.class));
    out.put("supply", MAPPER.convertValue(config.getSupply(), Map.class));
    out.put("healing", MAPPER.convertValue(config.getHealing(), Map.class));
    out.put("reinforcements", MAPPER.convertValue(config.getReinforcements(), Map.class));
    return out;
  }

  private static List<Map<String, Object>> enemyTypes() {
    var out = new ArrayList<Map<String, Object>>();
    for (var type : EnemyStats.values()) {
      var enemy = new LinkedHashMap<String, Object>();
      enemy.put("type", type.name());
      enemy.put("health", type.getHealth());
      enemy.put("damage", type.getDamage());
      enemy.put("attack", type.getAttackType().name());
      enemy.put("weight", type.getWeight());
      enemy.put("weaknesses", byName(type.getWeaknesses()));
      out.add(enemy);
    }
    return out;
  }

  private static Map<String, Double> byName(Map<? extends Enum<?>, Double> weaknesses) {
    var out = new TreeMap<String, Double>();
    weaknesses.forEach((k, v) -> out.put(k.name(), v));
    return out;
  }
}

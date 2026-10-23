package io.github.diogohmcruz.towerdungeon.agent.telemetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The per-Floor table of issue #8, computed from telemetry rows and nothing else. It is the code
 * behind {@code td_balance_report}: the prompt hands a model this table so that the model reads
 * numbers and never makes them up.
 *
 * <p>Rows are grouped by {@code (engine, config_hash)}. Two groups are two different games, so they
 * get two tables and are never averaged together.
 *
 * <p>The sizing rule is computed as issue #8 states it, and has not been re-derived: Units needed =
 * sqrt(HP / (1.25 x d)), where HP is the mean Garrison health at the start of the Floor and d the
 * damage one Unit measurably dealt per tick on that Floor (all damage dealt over all Unit-ticks
 * spent on it). It sits beside the Party that was actually there, so the rule can be judged against
 * the data. A Floor with fewer than {@code minSamples} rows is marked thin.
 */
public final class BalanceReport {
  public static final int DEFAULT_MIN_SAMPLES = 30;

  /** The 1.25 of the rule. */
  public static final double RULE_FACTOR = 1.25;

  /** One Floor of one {@code (engine, config_hash)}. */
  public record FloorLine(
      int floor,
      boolean guardian,
      boolean surge,
      int samples,
      boolean thin,
      double clearRate,
      double wipeRate,
      double meanPartySize,
      double meanTicks,
      double meanGarrisonHealth,
      double damagePerUnitTick,
      double unitsNeeded,
      double meanSuppliesDrained,
      double meanTakenCombat,
      double meanTakenAttrition,
      double meanTakenStarvation,
      double meanUnitsLost) {}

  /** One policy of one {@code (engine, config_hash)}. */
  public record PolicyLine(
      String policy,
      int campaigns,
      int expeditions,
      double meanHighestFloor,
      int medianHighestFloor,
      int p90HighestFloor,
      int maxHighestFloor,
      double wipeRate,
      double meanExpeditionTicks,
      double villageTicksPerExpeditionTick,
      double villageFoodPerVillageTick,
      Map<String, Integer> stops) {}

  /** What one game (one engine and balance) showed. */
  public record Table(
      String engine,
      String configHash,
      long floorRows,
      List<FloorLine> floors,
      List<PolicyLine> policies) {}

  private static final class FloorAcc {
    boolean guardian;
    boolean surge;
    int samples;
    int cleared;
    int wiped;
    double partySize;
    double ticks;
    double garrison;
    double damage;
    double unitTicks;
    double supplies;
    double combat;
    double attrition;
    double starvation;
    double lost;
  }

  private static final class PolicyAcc {
    final List<Integer> highest = new ArrayList<>();
    final Map<String, Integer> stops = new TreeMap<>();
    int expeditions;
    int wipes;
    double expeditionTicks;
    double campaignExpeditionTicks;
    double villageTicks;
    double villageFood;
  }

  private static final class GroupAcc {
    long floorRows;
    final Map<Integer, FloorAcc> floors = new TreeMap<>();
    final Map<String, PolicyAcc> policies = new TreeMap<>();
  }

  /** Collects rows, then builds the tables. */
  public static final class Builder {
    private final int minSamples;
    private final Map<String, GroupAcc> groups = new TreeMap<>();

    public Builder(int minSamples) {
      this.minSamples = minSamples;
    }

    /** Takes one row; rows of any kind may be passed in any order. */
    public Builder add(Map<String, Object> row) {
      var engine = String.valueOf(row.get("engine"));
      var hash = String.valueOf(row.get("config_hash"));
      var group = groups.computeIfAbsent(engine + " " + hash, _ -> new GroupAcc());
      switch (String.valueOf(row.get("row"))) {
        case "floor" -> floor(group, row);
        case "expedition" -> expedition(group, row);
        case "campaign" -> campaign(group, row);
        default -> {}
      }
      return this;
    }

    private void floor(GroupAcc group, Map<String, Object> row) {
      group.floorRows++;
      var acc = group.floors.computeIfAbsent(integer(row, "floor"), _ -> new FloorAcc());
      acc.guardian |= Boolean.TRUE.equals(row.get("guardian"));
      acc.surge |= Boolean.TRUE.equals(row.get("surge"));
      acc.samples++;
      var cleared = Boolean.TRUE.equals(row.get("cleared"));
      if (cleared) {
        acc.cleared++;
      }
      if ("WIPED".equals(row.get("end"))) {
        acc.wiped++;
      }
      var ticks = number(row, "ticks_on_floor");
      var start = partyCount(row.get("party_start"));
      var end = number(row, "party_size_end");
      var average = (start + end) / 2d;
      acc.partySize += start;
      acc.ticks += ticks;
      acc.garrison += number(row, "garrison_health_start");
      acc.damage += number(row, "damage_dealt");
      acc.unitTicks += ticks * average;
      acc.supplies += number(row, "supplies_drained");
      acc.combat += number(row, "damage_taken_combat");
      acc.attrition += number(row, "damage_taken_attrition");
      acc.starvation += number(row, "damage_taken_starvation");
      acc.lost += row.get("units_lost") instanceof List<?> list ? list.size() : 0;
    }

    private void expedition(GroupAcc group, Map<String, Object> row) {
      var acc = group.policies.computeIfAbsent(policy(row), _ -> new PolicyAcc());
      acc.expeditions++;
      if ("WIPED".equals(row.get("outcome"))) {
        acc.wipes++;
      }
      acc.expeditionTicks += number(row, "total_ticks");
    }

    private void campaign(GroupAcc group, Map<String, Object> row) {
      var acc = group.policies.computeIfAbsent(policy(row), _ -> new PolicyAcc());
      acc.highest.add(integer(row, "highest_floor"));
      acc.stops.merge(String.valueOf(row.get("stop")), 1, Integer::sum);
      acc.campaignExpeditionTicks += number(row, "expedition_ticks");
      acc.villageTicks += number(row, "village_ticks");
      acc.villageFood += number(row, "village_food_grown");
    }

    public List<Table> build() {
      var tables = new ArrayList<Table>();
      for (var entry : groups.entrySet()) {
        var parts = entry.getKey().split(" ", 2);
        var group = entry.getValue();
        var floors = new ArrayList<FloorLine>();
        group.floors.forEach((floor, acc) -> floors.add(line(floor, acc)));
        var policies = new ArrayList<PolicyLine>();
        group.policies.forEach((name, acc) -> policies.add(policyLine(name, acc)));
        tables.add(new Table(parts[0], parts[1], group.floorRows, floors, policies));
      }
      return tables;
    }

    private FloorLine line(int floor, FloorAcc a) {
      var n = a.samples;
      var d = a.unitTicks > 0 ? a.damage / a.unitTicks : 0d;
      var hp = a.garrison / n;
      var needed = d > 0 ? Math.sqrt(hp / (RULE_FACTOR * d)) : Double.NaN;
      return new FloorLine(
          floor,
          a.guardian,
          a.surge,
          n,
          n < minSamples,
          (double) a.cleared / n,
          (double) a.wiped / n,
          a.partySize / n,
          a.ticks / n,
          hp,
          d,
          needed,
          a.supplies / n,
          a.combat / n,
          a.attrition / n,
          a.starvation / n,
          a.lost / n);
    }

    private static PolicyLine policyLine(String name, PolicyAcc a) {
      var sorted = a.highest.stream().sorted().toList();
      var campaigns = sorted.size();
      return new PolicyLine(
          name,
          campaigns,
          a.expeditions,
          campaigns == 0 ? 0d : sorted.stream().mapToInt(Integer::intValue).average().orElse(0d),
          campaigns == 0 ? 0 : sorted.get(campaigns / 2),
          campaigns == 0 ? 0 : sorted.get(Math.min(campaigns - 1, (int) (campaigns * 0.9))),
          campaigns == 0 ? 0 : sorted.getLast(),
          a.expeditions == 0 ? 0d : (double) a.wipes / a.expeditions,
          a.expeditions == 0 ? 0d : a.expeditionTicks / a.expeditions,
          a.campaignExpeditionTicks == 0 ? 0d : a.villageTicks / a.campaignExpeditionTicks,
          a.villageTicks == 0 ? 0d : a.villageFood / a.villageTicks,
          a.stops);
    }

    private static String policy(Map<String, Object> row) {
      return String.valueOf(row.get("policy"));
    }

    private static double number(Map<String, Object> row, String key) {
      return row.get(key) instanceof Number n ? n.doubleValue() : 0d;
    }

    private static int integer(Map<String, Object> row, String key) {
      return row.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static int partyCount(Object party) {
      return party instanceof Map<?, ?> map
          ? map.values().stream().mapToInt(v -> v instanceof Number n ? n.intValue() : 0).sum()
          : 0;
    }
  }

  private BalanceReport() {}

  // ---------------------------------------------------------------- Markdown

  /**
   * The tables as Markdown, with the rule and its caveats stated. {@code onlyUpTo} cuts the Floor
   * table at that Floor (0 for no cut).
   */
  public static String markdown(List<Table> tables, int minSamples, int onlyUpTo) {
    var out = new StringBuilder();
    if (tables.size() > 1) {
      out.append("NOT COMPARABLE: the rows come from ")
          .append(tables.size())
          .append(
              " different games (engine and config hash). Each has its own table; do not mix"
                  + " them.\n\n");
    }
    for (var table : tables) {
      out.append("### engine ")
          .append(table.engine())
          .append(", config_hash ")
          .append(table.configHash())
          .append("\n\n");
      out.append(policies(table));
      out.append("\nPer Floor (")
          .append(table.floorRows())
          .append(" Floor rows). `thin` = fewer than ")
          .append(minSamples)
          .append(
              " samples. Units needed = sqrt(HP / (1.25 x d)), computed here, not re-derived.\n\n");
      out.append(
          "| Floor | Kind | Samples | Thin | Cleared | Wiped here | Party at start | Ticks |"
              + " Garrison HP | d (dmg/Unit/tick) | Units needed | Supplies drained | Taken combat"
              + " | Taken attrition | Taken starvation | Units lost |\n");
      out.append(
          "|---:|---|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
      for (var line : table.floors()) {
        if (onlyUpTo > 0 && line.floor() > onlyUpTo) {
          continue;
        }
        out.append(
            "| %d | %s | %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n"
                .formatted(
                    line.floor(),
                    line.floor() == 0
                        ? "Approach"
                        : line.guardian() ? "Guardian" : line.surge() ? "Surge" : "-",
                    line.samples(),
                    line.thin() ? "thin" : "",
                    pct(line.clearRate()),
                    pct(line.wipeRate()),
                    f(line.meanPartySize()),
                    f(line.meanTicks()),
                    f(line.meanGarrisonHealth()),
                    f3(line.damagePerUnitTick()),
                    Double.isNaN(line.unitsNeeded()) ? "n/a" : f(line.unitsNeeded()),
                    f(line.meanSuppliesDrained()),
                    f(line.meanTakenCombat()),
                    f(line.meanTakenAttrition()),
                    f(line.meanTakenStarvation()),
                    f(line.meanUnitsLost())));
      }
      out.append('\n');
    }
    return out.toString();
  }

  private static String policies(Table table) {
    var out = new StringBuilder();
    out.append(
        "| Policy | Campaigns | Expeditions | Highest Floor mean | median | p90 | max | Wipe rate |"
            + " Ticks per Expedition | Village ticks per Expedition tick | Food grown per Village"
            + " tick | Stops |\n");
    out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");
    for (var p : table.policies()) {
      var stops = new TreeSet<String>();
      p.stops().forEach((k, v) -> stops.add(k + "=" + v));
      out.append(
          "| %s | %d | %d | %s | %d | %d | %d | %s | %s | %s | %s | %s |\n"
              .formatted(
                  p.policy(),
                  p.campaigns(),
                  p.expeditions(),
                  f(p.meanHighestFloor()),
                  p.medianHighestFloor(),
                  p.p90HighestFloor(),
                  p.maxHighestFloor(),
                  pct(p.wipeRate()),
                  f(p.meanExpeditionTicks()),
                  f(p.villageTicksPerExpeditionTick()),
                  f(p.villageFoodPerVillageTick()),
                  String.join(", ", stops)));
    }
    return out.toString();
  }

  /** The Floor lines of every table as plain rows, for tests and tools. */
  public static List<Map<String, Object>> floorRows(Table table) {
    var out = new ArrayList<Map<String, Object>>();
    table.floors().stream()
        .sorted(Comparator.comparingInt(FloorLine::floor))
        .forEach(
            line -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("floor", line.floor());
              row.put("samples", line.samples());
              row.put("units_needed", line.unitsNeeded());
              out.add(row);
            });
    return out;
  }

  private static String f(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static String f3(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String pct(double value) {
    return String.format(Locale.ROOT, "%.1f%%", value * 100d);
  }
}

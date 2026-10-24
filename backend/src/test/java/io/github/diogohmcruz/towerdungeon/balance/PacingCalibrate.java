package io.github.diogohmcruz.towerdungeon.balance;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.diogohmcruz.towerdungeon.agent.balance.FloorDuel;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * Finds, for each Generation's reference plan, the garrison budget that its target Floor must hold
 * (and the Guardian health on the Guardian Floors of its climb) for the plan to win there at a set
 * rate, by bisection on the real combat code. The numbers it prints are what the curve's anchors
 * are fitted through. Runs only with {@code -Dpacing.calibrate=garrison|guardian}.
 *
 * <p>Settings: {@code pacing.gens} (default all), {@code pacing.runs} (100), {@code pacing.win}
 * (0.75 for garrisons, 0.6 for Guardians), {@code pacing.seed} (1), {@code pacing.floors} (a list
 * that replaces each Generation's target Floor, for garrisons).
 */
@EnabledIfSystemProperty(named = "pacing.calibrate", matches = ".+")
class PacingCalibrate {

  @Test
  void calibrate() {
    java.util.Locale.setDefault(java.util.Locale.ROOT);
    ((Logger) LoggerFactory.getLogger("io.github.diogohmcruz.towerdungeon.domain"))
        .setLevel(Level.ERROR);
    var mode = System.getProperty("pacing.calibrate");
    var table = PacingTable.load();
    var runs = Integer.getInteger("pacing.runs", 100);
    var seed = Long.getLong("pacing.seed", 1L);
    var wanted =
        Double.parseDouble(
            System.getProperty("pacing.win", mode.equals("guardian") ? "0.6" : "0.75"));
    var gens = PacingSweep.parse(System.getProperty("pacing.gens", "1-12"));
    var out = new StringBuilder();
    for (var row : table.rows()) {
      if (!gens.contains(row.generation())) {
        continue;
      }
      var plan = row.plan();
      var floors = new java.util.ArrayList<Integer>();
      if (mode.equals("guardian")) {
        for (var g = 10; g <= 100; g += 10) {
          if (g > plan.startFloor() - (row.generation() == 12 ? 1 : 0) && g <= row.highestFloor()) {
            floors.add(g);
          }
        }
      } else {
        floors.add(row.highestFloor());
      }
      for (var floor : floors) {
        var config = new GameProperties();
        var value = new double[] {1};
        DoubleConsumer set =
            mode.equals("guardian")
                ? v -> config.getBoss().setHealthCurve(anchor(floor, v))
                : v -> config.getEnemies().setBudgetCurve(anchor(floor, v));
        var duel = FloorDuel.standalone(config);
        DoubleSupplier win = () -> duel.duel(plan, floor, runs, seed).winRate();
        var lo = Math.log(mode.equals("guardian") ? 10.0 : 1.0);
        var hi = Math.log(mode.equals("guardian") ? 200000 : 4000);
        for (var step = 0; step < 14; step++) {
          var mid = (lo + hi) / 2;
          set.accept(Math.exp(mid));
          if (win.getAsDouble() >= wanted) {
            lo = mid;
          } else {
            hi = mid;
          }
        }
        value[0] = Math.exp(lo);
        set.accept(value[0]);
        var report = duel.duel(plan, floor, runs, seed);
        var line =
            "gen %d floor %d %s=%.2f win %.0f%% health %.0f party %.1f N %.1f ticks %.1f supplies %.0f%n"
                .formatted(
                    row.generation(),
                    floor,
                    mode.equals("guardian") ? "healthMultiplier" : "budget",
                    value[0],
                    report.winRate() * 100,
                    report.garrisonHealth(),
                    report.partyStart(),
                    report.unitsNeeded(),
                    report.ticksMean(),
                    report.suppliesSpent());
        System.out.print(line);
        System.out.flush();
        out.append(line);
      }
    }
  }

  private static Map<Integer, Double> anchor(int floor, double value) {
    var map = new TreeMap<Integer, Double>();
    map.put(floor, value);
    return map;
  }
}

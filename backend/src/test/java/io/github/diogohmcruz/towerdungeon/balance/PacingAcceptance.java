package io.github.diogohmcruz.towerdungeon.balance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.diogohmcruz.towerdungeon.agent.balance.FloorDuel;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * The pacing acceptance check (#118): every Generation's reference plan, run by the floor duel
 * harness on the real combat code, must win each Floor up to its target often enough, and must meet
 * a wall past it. The bars are in {@code balance/pacing-acceptance.properties}.
 *
 * <p>Differs from the issue text: that asked that the plan "stop a few Floors beyond its target on
 * ordinary Floors", which the curve does not do (ordinary Floors fall off slowly, see {@code
 * docs/research/curve-floors-6-to-100.md}); the wall is the first Guardian Floor past the target
 * instead.
 */
final class PacingAcceptance {

  /** One bar that a plan broke. */
  record Failure(int generation, int floor, String kind, double winRate, String bar, String plan) {
    @Override
    public String toString() {
      return "Generation %d, Floor %d (%s): won %.0f%%, bar %s; plan %s"
          .formatted(generation, floor, kind, winRate * 100, bar, plan);
    }
  }

  private PacingAcceptance() {}

  static Properties bars() {
    try (InputStream in =
        PacingAcceptance.class.getResourceAsStream("/balance/pacing-acceptance.properties")) {
      var props = new Properties();
      props.load(in);
      return props;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The failures as one message, empty when there are none. */
  static String describe(List<Failure> failures) {
    return "pacing bars broken:\n"
        + String.join("\n", failures.stream().map(Failure::toString).toList());
  }

  /**
   * @param mode {@code fast} or {@code full}: which runs and margin of the properties to use
   * @return the bars broken, empty when the curve holds
   */
  static List<Failure> check(String mode) {
    java.util.Locale.setDefault(java.util.Locale.ROOT);
    ((Logger) LoggerFactory.getLogger("io.github.diogohmcruz.towerdungeon.domain"))
        .setLevel(Level.ERROR);
    var bars = bars();
    var runs = Integer.parseInt(bars.getProperty(mode + ".runs"));
    var margin = Double.parseDouble(bars.getProperty(mode + ".margin"));
    var seed = Long.parseLong(bars.getProperty("seed"));
    var normalMin = Double.parseDouble(bars.getProperty("normal.min")) - margin;
    var guardianMin = Double.parseDouble(bars.getProperty("guardian.min")) - margin;
    var surgeMin = Double.parseDouble(bars.getProperty("surge.min")) - margin;
    var wallMax = Double.parseDouble(bars.getProperty("wall.max")) + margin;

    var props = new GameProperties();
    var duel = FloorDuel.standalone(props);
    var failures = new ArrayList<Failure>();
    for (var row : PacingTable.load().rows()) {
      var plan = row.plan();
      var target = row.highestFloor();
      // the wall: the first Guardian Floor past the target (none past the summit)
      var wall = -1;
      for (var f = target + 1; f <= props.getTower().getMaxFloor() && wall < 0; f++) {
        var probe = duel.duel(plan, f, 1, seed);
        if (probe.guardian()) {
          wall = f;
        }
      }
      final var wallFloor = wall;
      var reports =
          IntStream.rangeClosed(plan.startFloor(), Math.max(target, wallFloor))
              .boxed()
              .filter(f -> f <= target || f == wallFloor)
              .parallel()
              .map(f -> duel.duel(plan, f, runs, seed))
              .toList();
      for (var report : reports) {
        var win = report.winRate();
        if (report.floor() > target) {
          if (win > wallMax) {
            failures.add(
                new Failure(
                    row.generation(),
                    report.floor(),
                    report.kind() + " wall, past the target " + target,
                    win,
                    "at most %.0f%%".formatted(wallMax * 100),
                    plan.toString()));
          }
          continue;
        }
        var min = report.guardian() ? guardianMin : report.surge() ? surgeMin : normalMin;
        if (win < min) {
          failures.add(
              new Failure(
                  row.generation(),
                  report.floor(),
                  report.kind(),
                  win,
                  "at least %.0f%%".formatted(min * 100),
                  plan.toString()));
        }
      }
    }
    return failures;
  }
}

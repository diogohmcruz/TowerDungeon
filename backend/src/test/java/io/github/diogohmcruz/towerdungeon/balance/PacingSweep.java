package io.github.diogohmcruz.towerdungeon.balance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.diogohmcruz.towerdungeon.agent.balance.FloorDuel;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * The sizing sweep of the scaling pass (#114): each Generation's reference plan against every Floor
 * from its start Floor to a few past its target, on the real combat code. Not a test of the build:
 * it runs only with {@code -Dpacing.sweep=1}, and prints the table that {@code
 * docs/research/curve-floors-6-to-100.md} records.
 *
 * <p>Settings (system properties): {@code pacing.gens} (default {@code 1-12}, lists and ranges),
 * {@code pacing.runs} (60), {@code pacing.beyond} (4 Floors past the target), {@code pacing.seed}
 * (1), {@code pacing.out} (a file the table is also written to).
 */
@EnabledIfSystemProperty(named = "pacing.sweep", matches = ".+")
class PacingSweep {

  @Test
  void sweep() throws IOException {
    java.util.Locale.setDefault(java.util.Locale.ROOT);
    ((Logger) LoggerFactory.getLogger("io.github.diogohmcruz.towerdungeon.domain"))
        .setLevel(Level.ERROR);
    var props = new GameProperties();
    var duel = FloorDuel.standalone(props);
    var table = PacingTable.load();
    var runs = Integer.getInteger("pacing.runs", 60);
    var beyond = Integer.getInteger("pacing.beyond", 4);
    var seed = Long.getLong("pacing.seed", 1L);
    var gens = parse(System.getProperty("pacing.gens", "1-12"));
    var all = new StringBuilder();
    all.append(
        "| Gen | Floor | Kind | Win | Ticks | Supplies | Health | Party | Lost | N needed |\n");
    all.append("|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
    System.out.print(all);
    for (var row : table.rows()) {
      if (!gens.contains(row.generation())) {
        continue;
      }
      var plan = row.plan();
      var last = Math.min(props.getTower().getMaxFloor(), row.highestFloor() + beyond);
      var floors = IntStream.rangeClosed(plan.startFloor(), last).boxed().toList();
      var reports =
          floors.parallelStream().map(floor -> duel.duel(plan, floor, runs, seed)).toList();
      var out = new StringBuilder();
      var spent = 0.0;
      for (var report : reports) {
        var past = report.floor() > row.highestFloor();
        if (!past) {
          spent += report.suppliesSpent();
        }
        out.append(
            "| %d | %d%s | %s | %.0f%% | %.1f | %.1f | %.0f | %.1f | %.1f | %.1f |\n"
                .formatted(
                    row.generation(),
                    report.floor(),
                    past ? "+" : "",
                    report.guardian() ? "Guardian" : report.surge() ? "Surge" : "-",
                    report.winRate() * 100,
                    report.ticksMean(),
                    report.suppliesSpent(),
                    report.garrisonHealth(),
                    report.partyStart(),
                    report.unitsLost(),
                    report.unitsNeeded()));
      }
      out.append(
          "| %d | sum to %d | | | %.0f of %.0f carried | | | | | |\n"
              .formatted(row.generation(), row.highestFloor(), spent, plan.supplies()));
      System.out.print(out);
      System.out.flush();
      all.append(out);
    }
    var file = System.getProperty("pacing.out");
    if (file != null) {
      Files.writeString(Path.of(file), all.toString());
    }
  }

  static List<Integer> parse(String text) {
    var out = new ArrayList<Integer>();
    for (var part : text.split(",")) {
      var range = part.trim().split("-");
      var from = Integer.parseInt(range[0]);
      var to = range.length > 1 ? Integer.parseInt(range[1]) : from;
      for (var i = from; i <= to; i++) {
        out.add(i);
      }
    }
    return out;
  }
}

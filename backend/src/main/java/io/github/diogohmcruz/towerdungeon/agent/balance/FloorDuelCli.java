package io.github.diogohmcruz.towerdungeon.agent.balance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;

/**
 * Runs the floor duel harness from the command line and exits: the {@code duel} profile (see {@code
 * docs/research/floor-duel-harness.md}). It reads one {@link PartyPlan} and a list of Floors, duels
 * each Floor {@code duel.runs} times, and prints one table row per Floor.
 *
 * <p>Settings, all optional but {@code duel.units}: {@code duel.units} (for example {@code
 * WARRIOR:4,ARCHER:4}), {@code duel.floors} (a list of Floors and ranges, {@code 1-5,10}; default
 * {@code 1-5}), {@code duel.runs} (100), {@code duel.seed} (1), {@code duel.upgrades} (for example
 * {@code SHARPENED_ARMS:2,SUPPLY_LINES:1}), {@code duel.capacity}, {@code duel.supplies}, {@code
 * duel.salted}, {@code duel.memory} (the Tower Memory step, 0), {@code duel.easing} (the slain
 * Guardian Floors, {@code 10,20}), {@code duel.risen} (Risen standing on each Floor), {@code
 * duel.founders} (true: the first Units are the Founders) and {@code duel.report} (a file the
 * Markdown is also written to).
 */
@AgentEnabled
@Component
@ConditionalOnProperty(prefix = "duel", name = "run", havingValue = "true")
public class FloorDuelCli implements ApplicationRunner {
  private final FloorDuel duel;
  private final Environment env;
  private final ConfigurableApplicationContext context;

  public FloorDuelCli(FloorDuel duel, Environment env, ConfigurableApplicationContext context) {
    this.duel = duel;
    this.env = env;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    var code = 0;
    try {
      play();
    } catch (RuntimeException e) {
      System.err.println(e.getMessage());
      code = 1;
    }
    var exit = code;
    System.exit(SpringApplication.exit(context, () -> exit));
  }

  private void play() throws Exception {
    var plan = plan(env::getProperty);
    var floors = floors(env.getProperty("duel.floors", "1-5"));
    var runs = env.getProperty("duel.runs", Integer.class, 100);
    var seed = env.getProperty("duel.seed", Long.class, 1L);
    var reports = new ArrayList<DuelReport>();
    var started = System.nanoTime();
    for (var floor : floors) {
      reports.add(duel.duel(plan, floor, runs, seed));
    }
    var out = new StringBuilder();
    out.append("Plan: ").append(plan).append("\n\n");
    out.append("Seed ").append(seed).append(", ").append(runs).append(" runs per Floor.\n\n");
    out.append(DuelReport.markdown(reports));
    System.out.println(out);
    System.out.printf(
        Locale.ROOT, "%d Floors in %.1f s%n", floors.size(), (System.nanoTime() - started) / 1e9);
    var report = env.getProperty("duel.report");
    if (report != null && !report.isBlank()) {
      Files.writeString(Path.of(report), out.toString());
    }
  }

  /** The plan the settings describe; {@code duel.units} is required. */
  static PartyPlan plan(java.util.function.Function<String, String> setting) {
    var units = counts(setting.apply("duel.units"), UnitStats.class);
    if (units.isEmpty()) {
      throw new IllegalArgumentException("Say who goes: --duel.units=WARRIOR:4,ARCHER:4");
    }
    var upgrades = counts(setting.apply("duel.upgrades"), UpgradeType.class);
    var easing = new TreeSet<Integer>();
    for (var floor : floors(setting.apply("duel.easing"))) {
      easing.add(floor);
    }
    var memory = setting.apply("duel.memory");
    var salted = setting.apply("duel.salted");
    var risen = setting.apply("duel.risen");
    return new PartyPlan(
        units,
        upgrades,
        number(setting.apply("duel.capacity")),
        number(setting.apply("duel.supplies")),
        salted == null || salted.isBlank() ? 0d : Double.parseDouble(salted.trim()),
        memory == null || memory.isBlank() ? 0 : Integer.parseInt(memory.trim()),
        easing,
        Map.of(),
        risen == null || risen.isBlank() ? 0 : Integer.parseInt(risen.trim()),
        Boolean.parseBoolean(setting.apply("duel.founders")));
  }

  private static Double number(String text) {
    return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
  }

  private static <K extends Enum<K>> Map<K, Integer> counts(String text, Class<K> type) {
    var out = new EnumMap<K, Integer>(type);
    if (text == null || text.isBlank()) {
      return out;
    }
    for (var part : text.split(",")) {
      var pair = part.trim().split(":");
      out.put(
          Enum.valueOf(type, pair[0].trim().toUpperCase(Locale.ROOT)),
          pair.length > 1 ? Integer.parseInt(pair[1].trim()) : 1);
    }
    return out;
  }

  /** {@code 1-5,10} as 1, 2, 3, 4, 5, 10. A blank list is empty. */
  static List<Integer> floors(String text) {
    var out = new ArrayList<Integer>();
    if (text == null || text.isBlank()) {
      return out;
    }
    for (var part : text.split(",")) {
      var range = part.trim().split("-");
      var low = Integer.parseInt(range[0].trim());
      var high = range.length > 1 ? Integer.parseInt(range[1].trim()) : low;
      for (var floor = low; floor <= high; floor++) {
        out.add(floor);
      }
    }
    return out;
  }
}

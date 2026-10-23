package io.github.diogohmcruz.towerdungeon.agent.playtest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.telemetry.BalanceReport;
import io.github.diogohmcruz.towerdungeon.agent.telemetry.TelemetrySink;

/**
 * Plays a baseline from the command line and exits: the {@code playtest} profile (see {@code
 * docs/research/baseline-2026-10.md} for the exact command). It plays {@code playtest.campaigns}
 * Campaigns of each policy named in {@code playtest.policies} on seeds fixed by {@code
 * playtest.seed}, writes one telemetry batch per policy, and prints the per-Floor table computed
 * from those batches, which is also what {@code td_balance_report} gives a model.
 *
 * <p>Settings, all optional: {@code playtest.policies} (comma-separated, default {@code
 * greedy,cautious,random}), {@code playtest.campaigns} (1000), {@code playtest.seed} (1), {@code
 * playtest.max-expeditions} (150), {@code playtest.workers} (4), {@code playtest.min-samples} (30),
 * {@code playtest.report} (a file the Markdown is also written to).
 */
@AgentEnabled
@Component
@ConditionalOnProperty(prefix = "playtest", name = "run", havingValue = "true")
public class PlaytestCli implements ApplicationRunner {
  private final PlaytestRunner runner;
  private final TelemetrySink sink;
  private final Environment env;
  private final ConfigurableApplicationContext context;

  public PlaytestCli(
      PlaytestRunner runner,
      TelemetrySink sink,
      Environment env,
      ConfigurableApplicationContext context) {
    this.runner = runner;
    this.sink = sink;
    this.env = env;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    var code = 0;
    try {
      play();
    } catch (RuntimeException e) {
      e.printStackTrace(System.err);
      code = 1;
    }
    var exit = code;
    System.exit(SpringApplication.exit(context, () -> exit));
  }

  private void play() throws Exception {
    var names =
        Arrays.asList(env.getProperty("playtest.policies", "greedy,cautious,random").split(","));
    var campaigns = env.getProperty("playtest.campaigns", Integer.class, 1000);
    var seed = env.getProperty("playtest.seed", Long.class, 1L);
    var maxExpeditions = env.getProperty("playtest.max-expeditions", Integer.class, 150);
    var workers = env.getProperty("playtest.workers", Integer.class, 4);
    var minSamples =
        env.getProperty("playtest.min-samples", Integer.class, BalanceReport.DEFAULT_MIN_SAMPLES);
    var report = env.getProperty("playtest.report");

    var batches = new ArrayList<String>();
    for (var name : names) {
      var policy = Policies.all().get(name.trim());
      if (policy == null) {
        throw new IllegalArgumentException("No such policy: " + name + " of " + Policies.names());
      }
      var started = System.nanoTime();
      var batch = runner.runBatch(policy, campaigns, seed, maxExpeditions, workers);
      System.out.printf(
          "policy %s: %d Campaigns in %d s, batch %s%n",
          policy.name(),
          campaigns,
          (System.nanoTime() - started) / 1_000_000_000L,
          batch.batchId());
      batches.add(batch.batchId());
    }
    // One section for each policy: a Floor table that mixed policies would mix their Parties.
    var out = new StringBuilder();
    for (var id : batches) {
      var builder = new BalanceReport.Builder(minSamples);
      sink.forEach(id, null, builder::add);
      out.append("## Batch ").append(id).append("\n\n");
      out.append(BalanceReport.markdown(builder.build(), minSamples, 0));
    }
    var markdown = out.toString();
    System.out.println();
    System.out.println(markdown);
    if (report != null && !report.isBlank()) {
      Files.writeString(Path.of(report), markdown);
    }
  }
}

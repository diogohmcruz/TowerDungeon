package io.github.diogohmcruz.towerdungeon.agent.telemetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;

/**
 * Keeps the telemetry of the Sandboxes an agent opens: each gets a batch of its own, written as it
 * plays, and a {@link RowRecorder} that watches its ticks. A scripted run does the same through the
 * {@code PlaytestRunner}; this is the same pipe for an LLM playtester, so {@code td_get_telemetry}
 * answers for both.
 */
@AgentEnabled
@Component
public class SandboxTelemetry {
  /** The policy name an agent's Sandbox is recorded under. */
  public static final String POLICY = "agent";

  private record Entry(TelemetrySink.Batch batch, RowRecorder recorder) {}

  private final TelemetrySink sink;
  private final SandboxRegistry registry;
  private final GameService service;
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  public SandboxTelemetry(TelemetrySink sink, SandboxRegistry registry, GameService service) {
    this.sink = sink;
    this.registry = registry;
    this.service = service;
  }

  public TelemetrySink sink() {
    return sink;
  }

  /** Starts recording a Sandbox the grant holds; returns the id of its batch. */
  public String attach(String grantId, String sandboxId) {
    var batch = sink.open();
    var recorder = new RowRecorder(batch::append, POLICY, 0);
    if (!registry.watch(grantId, sandboxId, recorder)) {
      batch.close();
      throw new IllegalStateException("The Sandbox is not held by this grant");
    }
    entries.put(sandboxId, new Entry(batch, recorder));
    return batch.id();
  }

  /** The batch of a Sandbox that is being recorded, or null. */
  public String batchOf(String sandboxId) {
    var entry = entries.get(sandboxId);
    return entry == null ? null : entry.batch().id();
  }

  /** Writes the Campaign row and closes the batch of a Sandbox that is about to be closed. */
  public void finish(String grantId, String sandboxId) {
    var entry = entries.remove(sandboxId);
    if (entry == null) {
      return;
    }
    registry.watch(grantId, sandboxId, null);
    service.view(
        sandboxId,
        (state, meta) -> {
          entry.recorder().finish(state, "CLOSED");
          return Boolean.TRUE;
        });
    entry.batch().close();
  }
}

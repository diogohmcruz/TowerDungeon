package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver.Advance;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver.Stop;

/**
 * The live Sandbox Campaigns, who holds them, and the caps on them: per grant, across all grants,
 * and on simulated time. A Sandbox is a Campaign with a manual clock and its own copy of {@link
 * GameProperties}, so a balance override in one never reaches another or a human's Campaign. It is
 * never written to the store. Switched off by {@code game.agent.sandbox.enabled=false}, which is
 * what any public server must run.
 */
@Component
public class SandboxRegistry {
  /** How an attempt to open a Sandbox ended. */
  public enum Status {
    OPENED,
    DISABLED,
    GRANT_CAP,
    GLOBAL_CAP
  }

  /** The answer to opening a Sandbox; {@code sandboxId} is null unless it opened. */
  public record Opened(Status status, String sandboxId, long maxSimulatedSeconds, String note) {}

  private static final String PREFIX = "sandbox-";

  private final GameService service;
  private final GameProperties config;
  private final Map<String, String> owners = new HashMap<>();

  public SandboxRegistry(GameService service, GameProperties config) {
    this.service = service;
    this.config = config;
  }

  /**
   * Opens a Sandbox Campaign for a grant. {@code override} may retune the Campaign's own copy of
   * the configuration (null for none); {@code maxSimulatedSeconds} asks for a lower ceiling than
   * the configured one and can never raise it (null for the configured one).
   */
  public synchronized Opened open(
      String grantId, Consumer<GameProperties> override, Long maxSimulatedSeconds) {
    var rules = config.getAgent().getSandbox();
    if (!rules.isEnabled()) {
      return new Opened(Status.DISABLED, null, 0, "Sandbox Campaigns are switched off here.");
    }
    if (liveFor(grantId) >= rules.getMaxPerGrant()) {
      return new Opened(
          Status.GRANT_CAP,
          null,
          0,
          "This grant already holds " + rules.getMaxPerGrant() + " live Sandboxes. Close one.");
    }
    if (owners.size() >= rules.getMaxGlobal()) {
      return new Opened(
          Status.GLOBAL_CAP, null, 0, "The server holds as many Sandboxes as it will. Try later.");
    }
    var campaignConfig = config.copy();
    if (override != null) {
      override.accept(campaignConfig);
    }
    var ceiling = rules.getMaxSimulatedSeconds();
    var limit =
        maxSimulatedSeconds == null ? ceiling : Math.min(ceiling, Math.max(0, maxSimulatedSeconds));
    var id = PREFIX + UUID.randomUUID();
    service.openSandbox(id, campaignConfig, limit);
    owners.put(id, grantId);
    return new Opened(Status.OPENED, id, limit, ManualTickDriver.DETERMINISM_NOTE);
  }

  /** Closes a Sandbox the grant holds. False when the grant holds no such Sandbox. */
  public synchronized boolean close(String grantId, String sandboxId) {
    if (!grantId.equals(owners.get(sandboxId))) {
      return false;
    }
    owners.remove(sandboxId);
    service.closeSandbox(sandboxId);
    return true;
  }

  /** Closes every Sandbox of a grant (it ended or expired). Returns how many were closed. */
  public synchronized int closeAll(String grantId) {
    var mine = owners.entrySet().stream().filter(e -> e.getValue().equals(grantId)).toList();
    mine.forEach(e -> close(grantId, e.getKey()));
    return mine.size();
  }

  /** Live Sandboxes held by a grant. */
  public synchronized int liveFor(String grantId) {
    return (int) owners.values().stream().filter(grantId::equals).count();
  }

  /** Live Sandboxes across all grants. */
  public synchronized int live() {
    return owners.size();
  }

  /** Whether the grant holds that Sandbox. */
  public synchronized boolean holds(String grantId, String sandboxId) {
    return grantId.equals(owners.get(sandboxId));
  }

  /**
   * Advances a Sandbox the grant holds by up to {@code ticks} simulated seconds, within the wait
   * cap, the Campaign's simulated-time ceiling and the per-call wall-time budget, stopping early
   * when {@code until} holds after a tick (null for none). A Sandbox the grant does not hold is
   * {@link Stop#NOT_FOUND}.
   */
  public Advance advance(String grantId, String sandboxId, int ticks, Predicate<GameState> until) {
    if (!holds(grantId, sandboxId)) {
      return new Advance(Stop.NOT_FOUND, 0, 0, 0, ManualTickDriver.DETERMINISM_NOTE);
    }
    var rules = config.getAgent().getSandbox();
    return service
        .manualDriver()
        .advance(sandboxId, ticks, rules.getMaxTicksPerWait(), rules.getCallWallTime(), until);
  }
}

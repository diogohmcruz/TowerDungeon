package io.github.diogohmcruz.towerdungeon.agent.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.mcp.AgentHttpProperties;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.tools.Args;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.services.AutosaveCoordinator;
import io.github.diogohmcruz.towerdungeon.domain.services.FloorBundle;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.PartyCombat;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real game service, Sandbox registry, observations and action facade, with no HTTP: what the
 * facade tests and the invariants fuzz both stand on.
 */
final class ActionsFixture {
  static final Principal WHO = Principal.dev();

  final GameProperties config = new GameProperties();
  final GameService service;
  final SandboxRegistry registry;
  final IdempotencyStore store;
  final Actions actions;
  final AtomicLong millis = new AtomicLong(1_000_000);
  private int requests;

  ActionsFixture(long seed) {
    config.getAgent().getSandbox().setEnabled(true);
    var mapper = JsonMapper.builder().build();
    var campaigns = mock(CampaignStore.class);
    when(campaigns.load(any())).thenReturn(Mono.empty());
    // A Sandbox is never written, so nothing here needs a working save.
    var saves = mock(AutosaveCoordinator.class);
    service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(seed),
            ArchitecturePlan.generate(
                config.getArchitecture().getSeed(),
                config.getTower().getMaxFloor(),
                config.getBoss().getInterval(),
                config.getEnemies().getSurgeInterval()),
            new PartyCombat(),
            campaigns,
            mock(FloorBundle.class),
            saves);
    registry = new SandboxRegistry(service, config);
    var clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
          }
        };
    store = new IdempotencyStore(clock);
    var observations = new Observations(service, registry, new AgentHttpProperties(), config);
    actions = new Actions(service, observations, store);
  }

  /** Opens a Sandbox on {@code seed} and returns its handle. */
  String open(long seed) {
    var opened = registry.open(WHO.grantId(), null, null, seed);
    assertThat(opened.status()).isEqualTo(Status.OPENED);
    return opened.sandboxId();
  }

  /** The Campaign's state, for a test to set up and to read: the Sandbox has no other owner. */
  GameState state(String campaign) {
    return service.playerState(campaign);
  }

  /** A fresh request id, so each call in a test is its own. */
  String nextId() {
    return "req-%08d".formatted(++requests);
  }

  /** Arguments with a fresh request id and the Campaign, plus {@code pairs} (name, value...). */
  Map<String, Object> args(String campaign, Object... pairs) {
    var out = new HashMap<String, Object>();
    out.put("request_id", nextId());
    out.put("campaign", campaign);
    for (var i = 0; i < pairs.length; i += 2) {
      out.put((String) pairs[i], pairs[i + 1]);
    }
    return out;
  }

  /**
   * Calls a tool through the facade as a caller would see it: a refusal that is thrown (a bad
   * argument) is turned into the same envelope shape as a refusal that is returned.
   */
  Map<String, Object> call(String tool, Map<String, Object> arguments) {
    var args = new Args(arguments);
    try {
      return switch (tool) {
        case "td_recruit" -> actions.recruit(WHO, args);
        case "td_buy_villagers" -> actions.buyVillagers(WHO, args);
        case "td_sell" -> actions.sell(WHO, args);
        case "td_buy_upgrade" -> actions.buyUpgrade(WHO, args);
        case "td_start_expedition" -> actions.startExpedition(WHO, args);
        case "td_send_reinforcement_wave" -> actions.sendReinforcementWave(WHO, args);
        case "td_extract" -> actions.extract(WHO, args);
        default -> throw new IllegalArgumentException(tool);
      };
    } catch (ToolFailure failure) {
      var out = new HashMap<String, Object>();
      out.put("status", "rejected");
      out.put("code", failure.code());
      out.put("message", failure.getMessage());
      return out;
    }
  }
}

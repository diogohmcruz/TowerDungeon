package io.github.diogohmcruz.towerdungeon.agent.playtest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import io.github.diogohmcruz.towerdungeon.agent.act.Actions;
import io.github.diogohmcruz.towerdungeon.agent.act.IdempotencyStore;
import io.github.diogohmcruz.towerdungeon.agent.mcp.AgentHttpProperties;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.telemetry.TelemetrySink;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.domain.services.AutosaveCoordinator;
import io.github.diogohmcruz.towerdungeon.domain.services.FloorBundle;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.PartyCombat;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real game service, Sandbox registry, action facade, telemetry sink and playtest runner, with no
 * HTTP: what the telemetry and bot tests stand on. The Floors' finds use the real {@link
 * FloorBundle}, as the game does.
 */
final class PlaytestFixture {
  final GameProperties config = new GameProperties();
  final GameService service;
  final SandboxRegistry registry;
  final TelemetrySink sink;
  final PlaytestRunner runner;

  PlaytestFixture(Path telemetryDir) {
    config.getAgent().getSandbox().setEnabled(true);
    var mapper = JsonMapper.builder().build();
    var campaigns = mock(CampaignStore.class);
    when(campaigns.load(any())).thenReturn(Mono.empty());
    service =
        new GameService(
            config,
            mapper,
            SeedSource.fixed(1),
            ArchitecturePlan.generate(
                config.getArchitecture().getSeed(),
                config.getTower().getMaxFloor(),
                config.getBoss().getInterval(),
                config.getEnemies().getSurgeInterval()),
            new PartyCombat(),
            campaigns,
            new FloorBundle(config, new LeftoverGenerator(config)),
            mock(AutosaveCoordinator.class));
    registry = new SandboxRegistry(service, config);
    var observations = new Observations(service, registry, new AgentHttpProperties(), config);
    var actions = new Actions(service, observations, new IdempotencyStore());
    sink = new TelemetrySink(telemetryDir, 500);
    runner = new PlaytestRunner(actions, registry, service, sink);
  }
}

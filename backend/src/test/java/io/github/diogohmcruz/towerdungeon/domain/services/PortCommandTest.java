package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState.PortState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** Building the Port as a command. */
class PortCommandTest {
  private final GameProperties config = new GameProperties();

  private GameService service() {
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var service =
        new GameService(
            config,
            new ObjectMapper(),
            SeedSource.fixed(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, new ObjectMapper(), config.getPersistence(), "test", System::nanoTime));
    service.attach("session", "ws").block();
    return service;
  }

  @Test
  void aBuildBeforeTheFootingIsRejectedWithAReason() {
    var service = service();

    var result = service.execute("session", new Command.BuildPort());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.PURCHASE_REFUSED);
    assertThat(service.playerState("session").getPort().state()).isEqualTo(PortState.HIDDEN);
  }

  @Test
  void aFundedBuildOnTheFootingIsApplied() {
    config.getPort().setFootingExpeditions(0);
    var service = service();
    var state = service.playerState("session");
    state.getResources().put(ResourceType.MATERIALS, 700d);
    state.addCredit(400d);

    var result = service.execute("session", new Command.BuildPort());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getPort().state()).isEqualTo(PortState.BUILT);
  }

  @Test
  void anUnfundedBuildOnTheFootingIsRejectedAndSpendsNothing() {
    config.getPort().setFootingExpeditions(0);
    var service = service();
    var state = service.playerState("session");
    state.getResources().put(ResourceType.MATERIALS, 10d);

    var result = service.execute("session", new Command.BuildPort());

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(10d);
    assertThat(state.getPort().state()).isEqualTo(PortState.FOOTING);
  }
}

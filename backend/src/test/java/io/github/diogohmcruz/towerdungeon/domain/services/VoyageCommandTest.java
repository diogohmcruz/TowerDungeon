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
import io.github.diogohmcruz.towerdungeon.domain.models.GameState.VoyageState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.VoyageGoods;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** Sending the Boat on a Voyage as a command. */
class VoyageCommandTest {
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
  void aVoyageBeforeTheBoatDocksIsRejected() {
    var service = service();
    service.playerState("session").getResources().put(ResourceType.RELICS, 50d);

    var result = service.execute("session", new Command.SendVoyage(10, VoyageGoods.SALT));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.PURCHASE_REFUSED);
  }

  @Test
  void aMissingLoadOrGoodsIsRejectedAsInvalid() {
    var service = service();

    var result = service.execute("session", new Command.SendVoyage(null, VoyageGoods.SALT));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
  }

  @Test
  void aVoyageFromTheDockedBoatSailsAndTakesTheRelics() {
    config.getPort().setFootingExpeditions(0);
    config.getBoat().setDockTicks(1);
    var service = service();
    var state = service.playerState("session");
    state.getResources().put(ResourceType.MATERIALS, 700d);
    state.addCredit(400d);
    state.buildPort();
    state.triggerLifecycle();
    state.getResources().put(ResourceType.RELICS, 50d);

    var result = service.execute("session", new Command.SendVoyage(10, VoyageGoods.SPORES));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVoyage().state()).isEqualTo(VoyageState.SAILING);
    assertThat(state.vaultRelics()).isEqualTo(40d);
    var again = service.execute("session", new Command.SendVoyage(10, VoyageGoods.SPORES));
    assertThat(again.outcome()).isEqualTo(Outcome.REJECTED);
  }
}

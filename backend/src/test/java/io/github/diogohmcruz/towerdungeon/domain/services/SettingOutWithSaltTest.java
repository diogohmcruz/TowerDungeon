package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** Setting out from the Loadout: the Salt the player chose, and the one-click default. */
class SettingOutWithSaltTest {
  private final GameProperties config = new GameProperties();

  private GameService service() {
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var service =
        new GameService(
            config,
            new ObjectMapper(),
            new SplittableRandom(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(new SplittableRandom(7)),
            store,
            mock(FloorBundle.class),
            new AutosaveCoordinator(
                store, new ObjectMapper(), config.getPersistence(), "test", System::nanoTime));
    service.attach("session", "ws").block();
    return service;
  }

  @Test
  void oneClickWithTheDefaultsSaltsNothingAndSpendsNoSalt() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);

    service.handleMessage("session", new InvadeActionDTO(new HashMap<>(), null));

    assertThat(state.isExpeditionActive()).isTrue();
    assertThat(state.getSaltedSupplies()).isZero();
    assertThat(state.getVillage().getSalt()).isEqualTo(6.0);
  }

  @Test
  void theSaltTheLoadoutChoseSaltsTheSupplies() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);

    service.handleMessage("session", new InvadeActionDTO(new HashMap<>(), null, 4));

    assertThat(state.getSaltedSupplies()).isEqualTo(40.0);
    assertThat(state.getVillage().getSalt()).isEqualTo(2.0);
  }

  @Test
  void aRefusedSettingOutSpendsNoSalt() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);
    state.setExpeditionActive(true);

    service.handleMessage("session", new InvadeActionDTO(new HashMap<>(), null, 4));

    assertThat(state.getVillage().getSalt()).isEqualTo(6.0);
  }
}

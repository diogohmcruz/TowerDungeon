package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
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

    service.execute("session", new Command.StartExpedition(new HashMap<>(), null));

    assertThat(state.isExpeditionActive()).isTrue();
    assertThat(state.getSaltedSupplies()).isZero();
    assertThat(state.getVillage().getSalt()).isEqualTo(6.0);
  }

  @Test
  void theSaltTheLoadoutChoseSaltsTheSupplies() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);

    service.execute("session", new Command.StartExpedition(new HashMap<>(), null, 4));

    assertThat(state.getSaltedSupplies()).isEqualTo(40.0);
    assertThat(state.getVillage().getSalt()).isEqualTo(2.0);
  }

  @Test
  void aRefusedSettingOutSpendsNoSalt() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);
    state.setExpeditionActive(true);

    service.execute("session", new Command.StartExpedition(new HashMap<>(), null, 4));

    assertThat(state.getVillage().getSalt()).isEqualTo(6.0);
  }

  @Test
  void aSaltPackSetsOutOnTheBearerTypeAndTakesItsSaltAndCapacity() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);
    state.addUnits(
        io.github.diogohmcruz.towerdungeon.domain.models.UnitStats.TANK,
        java.util.List.of(
            new io.github.diogohmcruz.towerdungeon.domain.models.Unit(
                io.github.diogohmcruz.towerdungeon.domain.models.UnitStats.TANK, "Brann")));

    var result =
        service.execute(
            "session",
            new Command.StartExpedition(
                new HashMap<>(),
                null,
                null,
                4,
                io.github.diogohmcruz.towerdungeon.domain.models.UnitStats.TANK));

    assertThat(result.outcome())
        .isEqualTo(
            io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome.APPLIED);
    assertThat(state.getSaltPack()).isEqualTo(4);
    assertThat(state.saltBearer()).isPresent();
    assertThat(state.getVillage().getSalt()).isEqualTo(2.0);
  }

  @Test
  void aSaltPackWithoutABearerInThePartyIsRefusedAndSpendsNothing() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 6);

    var result =
        service.execute(
            "session",
            new Command.StartExpedition(
                new HashMap<>(),
                null,
                null,
                4,
                io.github.diogohmcruz.towerdungeon.domain.models.UnitStats.TANK));

    assertThat(result.outcome())
        .isEqualTo(
            io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome.REJECTED);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getVillage().getSalt()).isEqualTo(6.0);
  }

  @Test
  void surplusSaltSellsThroughTheSellSaltCommand() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSalt(0, 3);
    var before = state.getCredit();

    service.execute("session", new Command.SellSalt());

    assertThat(state.getCredit()).isEqualTo(before + 6.0);
    assertThat(state.getVillage().getSalt()).isZero();
  }
}

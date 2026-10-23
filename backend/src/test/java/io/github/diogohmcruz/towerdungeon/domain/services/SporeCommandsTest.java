package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** The Remedy, the Spore Dose and the Panacea as commands. */
class SporeCommandsTest {
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

  private static Unit wounded(GameService service) {
    var state = service.playerState("session");
    var unit = new Unit(UnitStats.WARRIOR, "Tomas");
    state.addUnits(UnitStats.WARRIOR, List.of(unit));
    unit.damagePart(BodyPart.TORSO, 4.0);
    return unit;
  }

  @Test
  void aRemedyCommandHealsTheUnitAndSpendsASpore() {
    var service = service();
    var unit = wounded(service);
    var state = service.playerState("session");
    state.getVillage().restoreSpores(0, 2);

    var result = service.execute("session", new Command.GiveRemedy(unit.getId()));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(unit.isWounded()).isFalse();
    assertThat(state.getVillage().getSpores()).isEqualTo(1.0);
  }

  @Test
  void aRemedyWithNoSporesIsRefusedAndChangesNothing() {
    var service = service();
    var unit = wounded(service);

    var result = service.execute("session", new Command.GiveRemedy(unit.getId()));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(unit.isWounded()).isTrue();
  }

  @Test
  void aReattachCommandBringsBackALostLegAtAQuarter() {
    var service = service();
    var unit = wounded(service);
    var state = service.playerState("session");
    state.getVillage().restoreSpores(0, 1);
    unit.damagePart(BodyPart.LEFT_LEG, 100);

    var result =
        service.execute("session", new Command.ReattachPart(unit.getId(), BodyPart.LEFT_LEG));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(unit.hasPart(BodyPart.LEFT_LEG)).isTrue();
    assertThat(state.getVillage().getSpores()).isZero();
  }

  @Test
  void anIncompleteReattachIsRejected() {
    var service = service();

    var result = service.execute("session", new Command.ReattachPart(null, null));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
  }

  @Test
  void settingOutWithAPanaceaSpendsItsSporesAndMaterials() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSpores(0, 5);
    state.getResources().put(ResourceType.MATERIALS, 50d);

    service.execute(
        "session", new Command.StartExpedition(new HashMap<>(), null, null, null, null, true));

    assertThat(state.isExpeditionActive()).isTrue();
    assertThat(state.isPanaceaCarried()).isTrue();
    assertThat(state.getVillage().getSpores()).isEqualTo(1.0);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(10.0);
  }

  @Test
  void aPanaceaTheVillageCannotMakeRefusesToSetOutAndSpendsNothing() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSpores(0, 3);

    var result =
        service.execute(
            "session", new Command.StartExpedition(new HashMap<>(), null, null, null, null, true));

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getVillage().getSpores()).isEqualTo(3.0);
  }

  @Test
  void oneClickWithTheDefaultsCarriesNoPanacea() {
    var service = service();
    var state = service.playerState("session");
    state.getVillage().restoreSpores(0, 5);
    state.getResources().put(ResourceType.MATERIALS, 50d);

    service.execute("session", new Command.StartExpedition(new HashMap<>(), null));

    assertThat(state.isPanaceaCarried()).isFalse();
    assertThat(state.getVillage().getSpores()).isEqualTo(5.0);
  }
}

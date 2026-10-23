package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.api.dtos.InvadeActionDTO;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import tools.jackson.databind.ObjectMapper;

class FoundersTest {
  private final GameProperties config = new GameProperties();

  private GameService service() {
    return new GameService(
        config,
        new ObjectMapper(),
        new SplittableRandom(7),
        mock(ArchitecturePlan.class),
        new PartyCombat(new SplittableRandom(7)));
  }

  private static List<Unit> reserves(GameState state) {
    return state.getUnits().values().stream().flatMap(List::stream).toList();
  }

  @Test
  void aNewCampaignStartsWithTheFourFoundersAndTheStartingResources() {
    var state = service().newCampaign();

    assertThat(reserves(state))
        .extracting(Unit::getName)
        .containsExactlyInAnyOrder("Yuva", "Fanro", "Bazen", "Calix");
    assertThat(state.getUnits().get(UnitStats.WARRIOR)).hasSize(3);
    assertThat(state.getUnits().get(UnitStats.ARCHER)).hasSize(1);
    assertThat(state.getCredit()).isEqualTo(10.0);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(20);
    assertThat(state.getVillage().getFood()).isEqualTo(100.0);
  }

  @Test
  void yuvaIsFiveTimesAWarriorAndFixed() {
    var yuva =
        reserves(service().newCampaign()).stream()
            .filter(unit -> unit.getName().equals("Yuva"))
            .findFirst()
            .orElseThrow();

    assertThat(yuva.isFixedStrengths()).isTrue();
    assertThat(yuva.getMaxHealth()).isEqualTo(UnitStats.WARRIOR.getHealth() * 5);
    assertThat(yuva.getCurrentHealth()).isEqualTo(100.0);
    assertThat(yuva.getDamage()).isEqualTo(UnitStats.WARRIOR.getDamage() * 5);
  }

  @Test
  void theOtherFoundersAreRolledWithinTheSpread() {
    var spread = config.getRecruit().getStrengthSpread();

    for (var unit : reserves(service().newCampaign())) {
      if (unit.getName().equals("Yuva")) {
        continue;
      }
      var base = unit.getStats().getHealth();
      assertThat(unit.isFixedStrengths()).isFalse();
      assertThat(unit.getMaxHealth()).isBetween(base * (1 - spread), base * (1 + spread));
    }
  }

  @Test
  void theFounderListIsConfigurable() {
    config.getStart().setFounders(List.of());

    assertThat(reserves(service().newCampaign())).isEmpty();
  }

  @Test
  void aDirectGameStateStartsEmpty() {
    assertThat(reserves(new GameState(config))).isEmpty();
  }

  @Test
  void anEmptyInvadeSendsAllFourFoundersToTheTower() {
    var service = service();
    service.renderState("session");

    service.handleMessage("session", new InvadeActionDTO(new HashMap<>(), null));

    var state = service.playerState("session");
    assertThat(state.isExpeditionActive()).isTrue();
    assertThat(state.getUnitsOnTower().values().stream().flatMap(List::stream))
        .extracting(Unit::getName)
        .containsExactlyInAnyOrder("Yuva", "Fanro", "Bazen", "Calix");
    assertThat(reserves(state)).isEmpty();
  }
}

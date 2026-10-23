package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * What the commands gained for the agent's tools: a count of Villagers, an amount to sell with a
 * guard against starving the Reserves, explicit selections, and a null that is refused rather than
 * a {@code NullPointerException}. The screen's own forms of the commands are unchanged.
 */
class ActTweaksTest {
  private static final String PLAYER = "player";

  private GameService service;
  private GameState state;

  @BeforeEach
  void setUp() {
    var store = mock(CampaignStore.class);
    when(store.load(any())).thenReturn(Mono.empty());
    var config = new GameProperties();
    service =
        new GameService(
            config,
            new ObjectMapper(),
            SeedSource.fixed(7),
            mock(ArchitecturePlan.class),
            new PartyCombat(),
            store,
            mock(FloorBundle.class),
            mock(AutosaveCoordinator.class));
    service.attach(PLAYER, "ws").block();
    state = service.playerState(PLAYER);
  }

  @Test
  void aNullQuantityIsRefusedNotAnException() {
    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, null));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
  }

  @Test
  void aNonPositiveQuantityChangesNothing() {
    var before = state.getCredit();

    var result = service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 0));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(state.getCredit()).isEqualTo(before);
  }

  @Test
  void aNullPartyIsRefusedAndNobodyMoves() {
    var result = service.execute(PLAYER, new Command.StartExpedition(null, 0));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(state.isExpeditionActive()).isFalse();
    assertThat(state.getUnitsOnTower().values().stream().mapToInt(java.util.List::size).sum())
        .isZero();
  }

  @Test
  void anEmptyPartyStillSendsEveryoneAsTheScreenDoes() {
    var result = service.execute(PLAYER, new Command.StartExpedition(Map.of(), 0));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
  }

  @Test
  void villagersAreHiredUpToTheCount() {
    state.setCredit(1000d);
    var before = state.getVillage().getVillagersCount();

    var result = service.execute(PLAYER, new Command.BuyVillagers(3));

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVillage().getVillagersCount()).isEqualTo(before + 3);
  }

  @Test
  void aShortVaultHiresFewerThanAskedAndSaysSo() {
    state.setCredit(1000d);
    var before = state.getVillage().getVillagersCount();

    var result = service.execute(PLAYER, new Command.BuyVillagers(5000));

    assertThat(result.outcome()).isEqualTo(Outcome.PARTIAL);
    assertThat(result.reason()).isEqualTo(Reason.INSUFFICIENT_CREDITS);
    assertThat(state.getVillage().getVillagersCount()).isGreaterThan(before);
  }

  @Test
  void theScreensVillagerPurchaseStillHiresAsManyAsTheVaultAllows() {
    state.setCredit(100d);

    var result = service.execute(PLAYER, new Command.BuyVillagers());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVillage().getVillagersCount()).isGreaterThan(10);
  }

  @Test
  void aCountOfZeroVillagersIsRefused() {
    var result = service.execute(PLAYER, new Command.BuyVillagers(0));

    assertThat(result.reason()).isEqualTo(Reason.INVALID_ARGUMENT);
  }

  @Test
  void foodIsSoldByTheAmountAndTheScreensFormSellsAll() {
    state.getVillage().takeFood(1_000_000d);
    state.getVillage().addFood(40d);

    service.execute(PLAYER, new Command.SellFood(10, false));
    assertThat(state.getVillage().getFood()).isEqualTo(30d);

    service.execute(PLAYER, new Command.SellFood());
    assertThat(state.getVillage().getFood()).isLessThanOrEqualTo(1d);
  }

  @Test
  void aSaleThatLeavesThePantryUnableToFeedTheReservesIsRefusedUnlessForced() {
    state.setCredit(1_000_000d);
    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 100));
    state.getVillage().takeFood(1_000_000d);
    state.getVillage().addFood(30d);
    var credits = state.getCredit();

    var refused = service.execute(PLAYER, new Command.SellFood(5, false));
    assertThat(refused.reason()).isEqualTo(Reason.WOULD_STARVE);
    assertThat(refused.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(state.getCredit()).isEqualTo(credits);
    assertThat(state.getVillage().getFood()).isEqualTo(30d);

    var forced = service.execute(PLAYER, new Command.SellFood(5, true));
    assertThat(forced.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getVillage().getFood()).isEqualTo(25d);
  }

  @Test
  void theScreensSaleOfAllFoodIsNotGuarded() {
    state.setCredit(1_000_000d);
    service.execute(PLAYER, new Command.Recruit(UnitStats.WARRIOR, 100));
    state.getVillage().takeFood(1_000_000d);
    state.getVillage().addFood(30d);

    var result = service.execute(PLAYER, new Command.SellFood());

    assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
  }

  @Test
  void aNonPositiveAmountIsRefused() {
    assertThat(service.execute(PLAYER, new Command.SellFood(0, false)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(service.execute(PLAYER, new Command.SellMaterials(-1d)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
    assertThat(service.execute(PLAYER, new Command.SellRelics(0d)).reason())
        .isEqualTo(Reason.INVALID_ARGUMENT);
  }

  @Test
  void materialsAndRelicsAreSoldByAmountAndTheRestStays() {
    state.getResources().put(ResourceType.MATERIALS, 10d);
    state.getResources().put(ResourceType.RELICS, 3d);

    var materials = service.execute(PLAYER, new Command.SellMaterials(4d));
    var relics = service.execute(PLAYER, new Command.SellRelics(9d));

    assertThat(materials.outcome()).isEqualTo(Outcome.APPLIED);
    assertThat(state.getResources().get(ResourceType.MATERIALS)).isEqualTo(6d);
    assertThat(relics.outcome()).isEqualTo(Outcome.PARTIAL);
    assertThat(state.getResources().get(ResourceType.RELICS)).isZero();
  }
}

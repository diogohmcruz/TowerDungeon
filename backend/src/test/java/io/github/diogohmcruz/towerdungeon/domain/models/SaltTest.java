package io.github.diogohmcruz.towerdungeon.domain.models;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/** Salt Pans, salted Supplies and the Loadout's preview. */
class SaltTest {
  private final GameProperties config = new GameProperties();

  private GameState state(double food, int villagers, int warriors, int highestFloor) {
    return state(food, villagers, warriors, highestFloor, VillageTier.SHELTERED);
  }

  private GameState state(
      double food, int villagers, int warriors, int highestFloor, VillageTier tier) {
    config.getVillage().setStartingFood(food);
    config.getVillage().setStartingVillagers(villagers);
    var state = new GameState(config);
    for (var i = 0; i < warriors; i++) {
      state.addUnits(UnitStats.WARRIOR, List.of(new Unit(UnitStats.WARRIOR, "Tomas", cosmetic())));
    }
    state.recordFloorReached(highestFloor);
    state.setVillageTier(tier);
    return state;
  }

  /** Carrying Capacity with nobody carrying extra: the base plus whatever the Floors granted. */
  private double cap(GameState state) {
    return config.getSupply().getBaseMax() + state.getSupplyCapacityBonus();
  }

  private static void assignSalters(GameState state, int count) {
    for (var i = 0; i < count; i++) {
      assertThat(state.assignTrade(Trade.SALTER, 1)).isEmpty();
    }
  }

  @Test
  void theSaltPansStayClosedUntilVillageTierTwoAndOpenAtIt() {
    var closed = state(100, 20, 0, 9, VillageTier.HUDDLED);
    var open = state(100, 20, 0, 5, VillageTier.SHELTERED);

    assertThat(closed.isSaltPansOpen()).isFalse();
    assertThat(closed.assignTrade(Trade.SALTER, 1))
        .hasValueSatisfying(r -> assertThat(r).isNotBlank());
    assertThat(closed.getVillage().hands(Trade.SALTER)).isZero();
    var preview =
        closed.getTradePreviews().stream()
            .filter(p -> p.trade().equals("SALTER") && p.delta() == 1)
            .findFirst()
            .orElseThrow();
    assertThat(preview.blocked()).contains("Village Tier II");

    assertThat(open.isSaltPansOpen()).isTrue();
    assertThat(open.assignTrade(Trade.SALTER, 1)).isEmpty();
    assertThat(open.getVillage().hands(Trade.SALTER)).isEqualTo(1);
  }

  @Test
  void fourSaltersMakeSixTenthsOfASaltPerTickIntoTheStockroom() {
    var state = state(100, 20, 0, 5);
    assignSalters(state, 4);

    state.getVillage().triggerLifecycle(0, 5);

    assertThat(state.getVillage().getSalt()).isCloseTo(0.6, within(1e-9));
    assertThat(state.getVillage().saltPerTick()).isCloseTo(0.6, within(1e-9));
  }

  @Test
  void saltStopsAtTheStockroomCap() {
    var state = state(100, 20, 0, 5);
    assignSalters(state, 4);
    var cap = state.getStockroomCap();
    state.getVillage().restoreSalt(4, cap - 0.2);

    state.getVillage().triggerLifecycle(0, 5);

    assertThat(state.getVillage().getSalt()).isEqualTo(cap);
  }

  @Test
  void saltersAreTakenFromTheFieldsAndCountAsPostsAndStarvation() {
    var state = state(100, 20, 0, 5);
    assignSalters(state, 2);

    assertThat(state.getVillage().hands(Trade.TILLER)).isEqualTo(18);
    assertThat(state.getVillage().posts(Trade.SALTER)).isEqualTo(10);
    assertThat(state.getTradeViews())
        .anySatisfy(
            view -> {
              assertThat(view.trade()).isEqualTo("SALTER");
              assertThat(view.workplace()).isEqualTo("Salt Pans");
              assertThat(view.hands()).isEqualTo(2);
            });
  }

  @Test
  void settingOutWithSaltSaltsTenSuppliesPerSaltAndSpendsTheSalt() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 5);

    state.startRun(2);

    assertThat(state.getSupplies()).isEqualTo(cap(state));
    assertThat(state.getSaltedSupplies()).isEqualTo(20.0);
    assertThat(state.getVillage().getSalt()).isEqualTo(3.0);
  }

  @Test
  void settingOutWithNoSaltOrTheDefaultSaltsNothing() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 5);

    state.startRun();

    assertThat(state.getSaltedSupplies()).isZero();
    assertThat(state.getVillage().getSalt()).isEqualTo(5.0);
  }

  @Test
  void youCannotSaltMoreThanYouHoldOrMoreThanYouCarry() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 2.9);

    state.startRun(50);

    assertThat(state.getSaltedSupplies()).isEqualTo(20.0);
    assertThat(state.getVillage().getSalt()).isCloseTo(0.9, within(1e-9));

    var small = state(5, 20, 6, 5);
    small.getVillage().restoreSalt(0, 9);
    small.startRun(9);
    assertThat(small.getSupplies()).isEqualTo(5.0);
    assertThat(small.getSaltedSupplies()).isEqualTo(5.0);
    assertThat(small.getVillage().getSalt()).isEqualTo(8.0);
  }

  @Test
  void fullySaltedSuppliesDrainAtOneAndAQuarterPerUnit() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 10);
    state.startRun(10);
    var carried = cap(state);
    assertThat(state.getSaltedSupplies()).isEqualTo(carried);

    state.drainExpeditionSupplies(6);

    assertThat(state.getSupplies()).isCloseTo(carried - 6 * 1.25, within(1e-9));
    assertThat(state.getSaltedSupplies()).isCloseTo(carried - 6 * 1.25, within(1e-9));
  }

  @Test
  void plainSuppliesDrainAtOneAndAHalfPerUnit() {
    var state = state(500, 20, 6, 5);
    state.startRun();
    var carried = state.getSupplies();

    state.drainExpeditionSupplies(6);

    assertThat(state.getSupplies()).isCloseTo(carried - 6 * 1.5, within(1e-9));
  }

  @Test
  void partlySaltedSuppliesDrainInProportion() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 5);
    state.startRun(5);
    var carried = state.getSupplies();
    var salted = state.getSaltedSupplies();
    assertThat(salted).isEqualTo(50.0);

    state.drainExpeditionSupplies(6);

    var perUnit = 1.5 - 0.25 * (salted / carried);
    assertThat(state.getSupplies()).isCloseTo(carried - 6 * perUnit, within(1e-9));
    assertThat(state.getSaltedSupplies())
        .isCloseTo(salted - 6 * perUnit * (salted / carried), within(1e-9));
  }

  @Test
  void leftoverSuppliesComeHomeUnsalted() {
    var state = state(500, 20, 6, 5);
    state.getVillage().restoreSalt(0, 10);
    state.startRun(10);

    state.returnLeftoverSuppliesToVillage();

    assertThat(state.getSaltedSupplies()).isZero();
  }

  @Test
  void theLoadoutStatesCapacityAndWhatSettingOutLeavesInThePantry() {
    var state = state(140, 20, 6, 5);
    state.addUnits(UnitStats.PORTER, List.of(new Unit(UnitStats.PORTER, "Hale", cosmetic())));

    var loadout = state.getLoadout();

    var porter = UnitStats.PORTER.getSupplyBonus();
    assertThat(loadout.carryingCapacity()).isEqualTo(cap(state) + porter);
    assertThat(loadout.suppliesTaken()).isEqualTo(140.0);
    assertThat(loadout.pantryAfter()).isZero();
    assertThat(loadout.partySize()).isEqualTo(7);
    assertThat(loadout.carryingCapacity()).isGreaterThan(140.0);
    assertThat(loadout.drainPerUnit()).isEqualTo(1.5);
    assertThat(loadout.saltedDrainPerUnit()).isEqualTo(1.25);
    assertThat(loadout.suppliesPerSalt()).isEqualTo(10.0);
    assertThat(loadout.saltUseful()).isEqualTo(14);
    assertThat(loadout.saltHeld()).isZero();
  }

  @Test
  void thePantryHoldAfterSettingOutCountsTheReservesLeftBehindIsNoneWhenEveryoneGoes() {
    var state = state(140, 20, 6, 5);

    var loadout = state.getLoadout();

    assertThat(loadout.suppliesTaken()).isEqualTo(cap(state));
    assertThat(loadout.pantryAfter()).isEqualTo(140.0 - cap(state));
    assertThat(loadout.reservesStaying()).isZero();
    assertThat(loadout.pantryHoldSeconds()).isNull();
  }

  @Test
  void saltIsInTheSnapshotAndComesBack() {
    var original = state(500, 20, 6, 5);
    assignSalters(original, 3);
    original.getVillage().restoreSalt(3, 7.5);
    original.startRun(3);

    var snapshot = original.toSnapshot();
    var restored =
        GameState.restore(
            snapshot,
            config,
            new RandomSource(1),
            ArchitecturePlan.generate(
                config.getArchitecture().getSeed(),
                config.getTower().getMaxFloor(),
                config.getBoss().getInterval(),
                config.getEnemies().getSurgeInterval()));

    assertThat(restored.getVillage().getSalters()).isEqualTo(3);
    assertThat(restored.getVillage().getSalt()).isEqualTo(4.5);
    assertThat(restored.getVillage().getSettling().get(Trade.SALTER)).containsExactly(10, 10, 10);
    assertThat(restored.getSaltedSupplies()).isEqualTo(30.0);
  }
}

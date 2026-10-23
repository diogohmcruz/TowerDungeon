package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;

class FloorBundleTest {
  private final GameProperties config = new GameProperties();

  private FloorBundle bundle(RandomGenerator random) {
    return new FloorBundle(random, config, new LeftoverGenerator(random, config));
  }

  private static Item weapon(String id, double power) {
    return new Item(
        id, id, ItemTier.LEFTOVER, ItemCategory.WEAPON, Map.of(ItemStat.POWER, power), null);
  }

  private GameState partyOf(int size, Item... waiting) {
    var state = new GameState(config);
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    state.setTower(
        new Tower(100, config.getBoss(), config.getEnemies(), new SplittableRandom(0), plan));
    state.dropGear(3, List.of(waiting));
    for (var i = 0; i < size; i++) {
      state
          .getUnitsOnTower()
          .computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>())
          .add(new Unit(UnitStats.WARRIOR, "W" + i));
    }
    return state;
  }

  private static List<Unit> members(GameState state) {
    return state.getUnitsOnTower().values().stream().flatMap(List::stream).toList();
  }

  /** What still waits on floor 3 after the Bundle has been shared out. */
  private static List<Item> waiting(GameState state) {
    return state.getTower().getFloorMemory().gearByFloor().getOrDefault(3, List.of());
  }

  private static int worn(Unit unit) {
    return unit.dropAll().size();
  }

  @Test
  void aGuardianFloorNeverGeneratesALeftover() {
    config.getItems().getBundle().setLeftoverChance(1.0);
    var random = new SplittableRandom(1);
    var generator = spy(new LeftoverGenerator(random, config));
    var bundle = new FloorBundle(random, config, generator);

    for (var i = 0; i < 50; i++) {
      bundle.resolve(partyOf(1), 10, true);
    }

    verifyNoInteractions(generator);
  }

  @Test
  void aClearedFloorGeneratesALeftoverAboutTwentyPercentOfTheTime() {
    var random = new SplittableRandom(2);
    var generator = spy(new LeftoverGenerator(random, config));
    var bundle = new FloorBundle(random, config, generator);

    var clears = 10_000;
    for (var i = 0; i < clears; i++) {
      bundle.resolve(partyOf(0), 3, false);
    }

    var generated =
        mockingDetails(generator).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("generate"))
            .count();
    assertThat(generated / (double) clears).isCloseTo(0.20, within(0.02));
  }

  @Test
  void aFoundGeneratedItemIsWornByTheFinder() {
    config.getItems().getBundle().setLeftoverChance(1.0);
    config.getItems().getBundle().setFindChance(1.0);
    var state = partyOf(1);

    bundle(new SplittableRandom(3)).resolve(state, 4, false);

    assertThat(worn(members(state).getFirst())).isEqualTo(1);
  }

  @Test
  void everyMemberRollsOnceAtTheConfiguredChance() {
    var random = new SplittableRandom(4);
    var bundle = bundle(random);

    var clears = 10_000;
    var found = 0;
    for (var i = 0; i < clears; i++) {
      var state = partyOf(1, weapon("w", 1.0));
      bundle.resolve(state, 3, true);
      found += waiting(state).isEmpty() ? 1 : 0;
    }

    assertThat(found / (double) clears).isCloseTo(0.15, within(0.02));
  }

  @Test
  void aMemberCannotTakeAnItemAlreadyTaken() {
    config.getItems().getBundle().setFindChance(1.0);
    var state = partyOf(3, weapon("a", 1.0), weapon("b", 1.0));

    bundle(new SplittableRandom(5)).resolve(state, 3, true);

    var wornCounts = members(state).stream().map(FloorBundleTest::worn).toList();
    assertThat(wornCounts).containsExactlyInAnyOrder(1, 1, 0);
    assertThat(waiting(state)).isEmpty();
  }

  @Test
  void membersRollInAShuffledOrder() {
    config.getItems().getBundle().setFindChance(1.0);
    var winners = new HashSet<String>();
    for (var seed = 0; seed < 40; seed++) {
      var state = partyOf(3, weapon("a", 1.0));
      bundle(new SplittableRandom(seed)).resolve(state, 3, true);
      members(state).stream()
          .filter(unit -> worn(unit) == 1)
          .forEach(unit -> winners.add(unit.getName()));
    }

    assertThat(winners).containsExactlyInAnyOrder("W0", "W1", "W2");
  }

  @Test
  void theDeadDoNotRoll() {
    config.getItems().getBundle().setFindChance(1.0);
    var state = partyOf(2, weapon("a", 1.0), weapon("b", 1.0));
    var dead = members(state).getFirst();
    dead.receiveAttack(10_000d, null);
    assertThat(dead.isDead()).isTrue();

    bundle(new SplittableRandom(6)).resolve(state, 3, true);

    assertThat(worn(dead)).isZero();
    assertThat(waiting(state)).hasSize(1);
  }

  @Test
  void unclaimedStoredGearGoesBackAndUnclaimedGeneratedItemsVanish() {
    config.getItems().getBundle().setFindChance(0.0);
    config.getItems().getBundle().setLeftoverChance(1.0);
    var kept = weapon("kept", 1.0);
    var state = partyOf(2, kept);

    bundle(new SplittableRandom(7)).resolve(state, 3, false);

    assertThat(waiting(state)).containsExactly(kept);
  }

  @Test
  void anItemThatDoesNotEquipIsCountedAsSurplusPerTier() {
    config.getItems().getBundle().setFindChance(1.0);
    var state = partyOf(1);
    var unit = members(state).getFirst();
    var bundle = bundle(new SplittableRandom(8));

    var clears = 20;
    for (var i = 0; i < clears; i++) {
      state.dropGear(3, List.of(weapon("w", 1.0)));
      bundle.resolve(state, 3, true);
    }

    var surplus = state.getSurplus().getOrDefault(ItemTier.LEFTOVER, 0);
    assertThat(surplus).isPositive();
    assertThat(surplus + worn(unit)).isEqualTo(clears);
  }

  @Test
  void findingAnItemRestoresTenSanity() {
    config.getItems().getBundle().setFindChance(1.0);
    var state = partyOf(1, weapon("w", 1.0));
    var unit = members(state).getFirst();
    unit.changeSanity(-30);
    var before = unit.getSanity();

    bundle(new SplittableRandom(9)).resolve(state, 3, true);

    assertThat(unit.getSanity()).isCloseTo(before + 10, within(1e-9));
  }
}

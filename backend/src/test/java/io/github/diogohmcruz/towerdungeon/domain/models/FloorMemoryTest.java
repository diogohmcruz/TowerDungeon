package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;
import io.github.diogohmcruz.towerdungeon.domain.services.PartyCombat;

class FloorMemoryTest {
  private final GameProperties config = new GameProperties();
  private final ItemRating rating = new ItemRating(config.getItems().getWeights());
  private GameState state;
  private Tower tower;

  @BeforeEach
  void setUp() {
    state = new GameState(config);
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    tower = new Tower(100, config.getBoss(), config.getEnemies(), new RandomSource(1), plan);
    state.setTower(tower);
    for (var i = 0; i < 3; i++) {
      tower.moveToNextFloor();
    }
  }

  private static Item weapon(String id, double power) {
    return new Item(
        id, id, ItemTier.LEFTOVER, ItemCategory.WEAPON, Map.of(ItemStat.POWER, power), null);
  }

  private static Item charm(String id) {
    return new Item(
        id, id, ItemTier.LEFTOVER, ItemCategory.TRINKET, Map.of(ItemStat.SANITY, 5.0), null);
  }

  private Unit equipped(Item... items) {
    var unit = state.recruit(UnitStats.WARRIOR, new SplittableRandom(2));
    for (var item : items) {
      unit.equip(item, rating);
    }
    state.getUnitsOnTower().put(UnitStats.WARRIOR, new ArrayList<>(List.of(unit)));
    return unit;
  }

  private List<Item> onFloor(int floor) {
    return tower.getFloorMemory().gearByFloor().getOrDefault(floor, List.of());
  }

  @Test
  void aUnitRemovedOnFloorNLeavesAllItsItemsThere() {
    var sword = weapon("sword", 2.0);
    var trinket = charm("charm");
    var unit = equipped(sword, trinket);

    state.removeUnit(unit);

    assertThat(onFloor(3)).containsExactlyInAnyOrder(sword, trinket);
    assertThat(onFloor(2)).isEmpty();
  }

  @Test
  void aUnitWornDownByTimeLeavesItsGearOnTheCurrentFloor() {
    var sword = weapon("sword", 2.0);
    var unit = equipped(sword);

    state.passingTime(1_000_000L);

    assertThat(unit.isDead()).isTrue();
    assertThat(onFloor(3)).containsExactly(sword);
  }

  @Test
  void aLostPartLeavesOnlyItsItem() {
    var sword = weapon("sword", 2.0);
    var trinket = charm("charm");
    var unit = equipped(sword, trinket);

    var dropped = new ArrayList<Item>();
    dropped.addAll(unit.damagePart(BodyPart.LEFT_ARM, 10_000));
    dropped.addAll(unit.damagePart(BodyPart.RIGHT_ARM, 10_000));
    state.dropGearOnCurrentFloor(dropped);

    assertThat(unit.isDead()).isFalse();
    assertThat(onFloor(3)).containsExactly(sword);
  }

  @Test
  void aKillingStrikeStoresTheItemsOnTheFloor() {
    var sword = weapon("sword", 2.0);
    var unit = equipped(sword);
    unit.setCurrentHealth(0.001);
    var enemies =
        new ArrayList<>(
            List.of(new Enemy(EnemyStats.SLIME, new SplittableRandom(3), config.getEnemies())));
    var combat = new PartyCombat();

    for (var i = 0; i < 50 && !unit.isDead(); i++) {
      state.dropGearOnCurrentFloor(combat.enemiesAttack(state, enemies, state.getUnitsOnTower()));
    }

    assertThat(unit.isDead()).isTrue();
    assertThat(onFloor(3)).containsExactly(sword);
  }

  @Test
  void aFloorKeepsTwelveItemsAndLosesTheLowestRatedFirst() {
    var items = new ArrayList<Item>();
    for (var i = 1; i <= 15; i++) {
      items.add(weapon("w" + i, i));
    }

    state.dropGear(3, items);

    assertThat(onFloor(3)).hasSize(12).extracting(Item::id).doesNotContain("w1", "w2", "w3");
  }

  @Test
  void theCapIsConfigurable() {
    config.getItems().setFloorGearCap(2);

    state.dropGear(3, List.of(weapon("a", 1), weapon("b", 3), weapon("c", 2)));

    assertThat(onFloor(3)).extracting(Item::id).containsExactlyInAnyOrder("b", "c");
  }

  @Test
  void gearSurvivesTheFloorBeingRebuilt() {
    var sword = weapon("sword", 2.0);
    state.dropGear(3, List.of(sword));

    tower.startNewRunAt(3);
    tower.startNewRun();
    tower.moveToNextFloor();
    tower.moveToNextFloor();
    tower.moveToNextFloor();

    assertThat(onFloor(3)).containsExactly(sword);
  }

  @Test
  void gearDroppedOnTheBaseIsLost() {
    state.dropGear(0, List.of(weapon("w", 1)));

    assertThat(tower.getFloorMemory().gearByFloor()).isEmpty();
  }

  @Test
  void aWipeLeavesDroppedGearWhereItFell() {
    var sword = weapon("sword", 2.0);
    state.dropGear(3, List.of(sword));
    equipped(charm("carried"));

    state.getUnitsOnTower().clear();

    assertThat(onFloor(3)).containsExactly(sword);
  }

  @Test
  void storedGearIsInTheSnapshot() {
    var sword = weapon("sword", 2.0);
    state.dropGear(3, List.of(sword));

    assertThat(state.toSnapshot().campaign().tower().floorGear())
        .isEqualTo(Map.of(3, List.of(sword)));
  }
}

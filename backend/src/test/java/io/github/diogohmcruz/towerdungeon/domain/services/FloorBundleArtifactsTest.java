package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class FloorBundleArtifactsTest {
  private final GameProperties config = new GameProperties();

  private FloorBundle bundle(long seed) {
    var random = new SplittableRandom(seed);
    return new FloorBundle(random, config, new LeftoverGenerator(random, config));
  }

  private static GameState partyOfOne() {
    var state = new GameState(new GameProperties());
    var unit = new Unit(UnitStats.WARRIOR, "W");
    state.getUnitsOnTower().computeIfAbsent(UnitStats.WARRIOR, _ -> new ArrayList<>()).add(unit);
    return state;
  }

  private static List<Item> worn(GameState state) {
    return state.getUnitsOnTower().values().stream()
        .flatMap(List::stream)
        .flatMap(unit -> unit.getGear().getSlots().values().stream())
        .toList();
  }

  @Test
  void anArtifactIsTakenAtItsHomeFloorEvenOnAGuardianFloorAndNotAgainThisGeneration() {
    config.getItems().getBundle().setFindChance(1.0);
    config.getItems().getBundle().setLeftoverChance(0.0);
    var bundle = bundle(1);
    var state = partyOfOne();

    bundle.resolve(state, 40, true);

    assertThat(worn(state)).extracting(Item::id).containsExactly("sargans-mask");
    assertThat(state.getArtifactsTaken()).containsExactly("sargans-mask");

    var second = partyOfOne();
    state.getArtifactsTaken().forEach(second::recordArtifactTaken);
    bundle.resolve(second, 40, true);
    assertThat(worn(second)).isEmpty();
  }

  @Test
  void anUntakenArtifactIsOfferedAgainOnTheNextClear() {
    config.getItems().getBundle().setFindChance(0.0);
    config.getItems().getBundle().setLeftoverChance(0.0);
    var bundle = bundle(2);
    var state = partyOfOne();

    bundle.resolve(state, 20, true);
    assertThat(state.getArtifactsTaken()).isEmpty();

    config.getItems().getBundle().setFindChance(1.0);
    bundle.resolve(state, 20, true);
    assertThat(worn(state)).extracting(Item::id).containsExactly("yuvas-salt-satchel");
  }

  @Test
  void otherFloorsOfferNoArtifacts() {
    config.getItems().getBundle().setFindChance(1.0);
    config.getItems().getBundle().setLeftoverChance(0.0);
    var state = partyOfOne();

    bundle(3).resolve(state, 21, false);

    assertThat(worn(state)).isEmpty();
  }

  @Test
  void generatedFindsAreSometimesTalismansAtTheConfiguredRate() {
    config.getItems().getBundle().setLeftoverChance(1.0);
    config.getItems().getBundle().setFindChance(1.0);
    config.getItems().getDropRates().setTalisman(1.0);
    config.getItems().getDropRates().setLeftover(0.0);
    var state = partyOfOne();

    bundle(4).resolve(state, 5, false);

    assertThat(worn(state)).extracting(Item::tier).containsExactly(ItemTier.TALISMAN);
  }
}

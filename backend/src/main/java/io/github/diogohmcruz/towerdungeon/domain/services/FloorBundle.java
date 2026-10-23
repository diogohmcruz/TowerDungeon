package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;

/**
 * Builds the Bundle of a cleared floor (a generated Leftover, plus the gear waiting there) and lets
 * each living party member roll once to find something in it.
 */
@Component
public class FloorBundle {
  private final RandomGenerator random;
  private final GameProperties.Items.Bundle config;
  private final LeftoverGenerator generator;
  private final ItemRating rating;

  public FloorBundle(
      RandomGenerator random, GameProperties properties, LeftoverGenerator generator) {
    this.random = random;
    this.config = properties.getItems().getBundle();
    this.generator = generator;
    this.rating = new ItemRating(properties.getItems().getWeights());
  }

  /**
   * Resolves the finds of one cleared floor. Items nobody claims vanish if generated and go back to
   * the store if they were waiting there.
   */
  public void resolve(GameState gameState, int floor, boolean guardian) {
    var store = gameState.floorGear();
    var stored = new ArrayList<>(store.takeAll(floor));
    var pool = new ArrayList<>(stored);
    if (!guardian && random.nextDouble() < config.getLeftoverChance()) {
      pool.add(generator.generate(floor));
    }

    var finders =
        new ArrayList<>(
            gameState.getUnitsOnTower().values().stream()
                .flatMap(List::stream)
                .filter(unit -> !unit.isDead())
                .toList());
    Collections.shuffle(finders, random);
    for (var finder : finders) {
      var found = random.nextDouble() < config.getFindChance() * findMultiplier(finder);
      if (found && !pool.isEmpty()) {
        give(gameState, finder, pool.remove(random.nextInt(pool.size())));
      }
    }

    var unclaimed = pool.stream().filter(stored::contains).toList();
    if (!unclaimed.isEmpty()) {
      store.putBack(floor, unclaimed);
    }
  }

  private void give(GameState gameState, Unit finder, Item item) {
    var result = finder.equip(item, rating);
    if (result.hasSurplus()) {
      gameState.addSurplus(result.surplus().tier());
    }
    finder.changeSanity(config.getSanityGain());
  }

  /** Multiplies a unit's find chance; 1.0 until affinities exist. */
  double findMultiplier(Unit unit) {
    return 1.0;
  }
}

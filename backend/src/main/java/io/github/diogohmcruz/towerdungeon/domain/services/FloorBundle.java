package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.ArtifactProvider;
import io.github.diogohmcruz.towerdungeon.domain.items.HomeFloorArtifacts;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.items.TalismanGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;

/**
 * Builds the Bundle of a cleared floor (a generated Leftover, plus the gear waiting there) and lets
 * each living party member roll once to find something in it.
 */
@Component
public class FloorBundle {
  private final LeftoverGenerator generator;
  private final TalismanGenerator talismans;
  private final ArtifactProvider artifacts;

  public FloorBundle(GameProperties properties, LeftoverGenerator generator) {
    this(
        properties,
        generator,
        new TalismanGenerator(properties),
        new HomeFloorArtifacts(properties));
  }

  @Autowired
  public FloorBundle(
      GameProperties properties,
      LeftoverGenerator generator,
      TalismanGenerator talismans,
      ArtifactProvider artifacts) {
    this.talismans = talismans;
    this.artifacts = artifacts;
    this.generator = generator;
  }

  /**
   * Resolves the finds of one cleared floor. Items nobody claims vanish if generated and go back to
   * the store if they were waiting there.
   */
  public void resolve(GameState gameState, int floor, boolean guardian) {
    var random = gameState.getRandom();
    resolve(gameState, floor, guardian, random.combat(), random.yield(floor));
  }

  /** As {@link #resolve(GameState, int, boolean)}, with every draw taken from {@code random}. */
  public void resolve(GameState gameState, int floor, boolean guardian, RandomGenerator random) {
    resolve(gameState, floor, guardian, random, random);
  }

  /**
   * As above, with what the Floor offers (a Leftover, its tier and make) drawn from {@code offer}
   * and who finds what from {@code random}. A Trial fixes the first by its Terms and not the
   * second.
   */
  private void resolve(
      GameState gameState,
      int floor,
      boolean guardian,
      RandomGenerator random,
      RandomGenerator offer) {
    // The Campaign's own copy of the configuration, so a Sandbox override of items.* takes effect.
    var items = gameState.getConfig().getItems();
    var config = items.getBundle();
    var affinity = items.getAffinity();
    var rating = new ItemRating(items.getWeights(), affinity.getRatingMultiplier());
    var store = gameState.floorGear();
    var stored = new ArrayList<>(store.takeAll(floor));
    var pool = new ArrayList<>(stored);
    if (!guardian && offer.nextDouble() < config.getLeftoverChance()) {
      pool.add(
          talismans.rollTier(offer) == ItemTier.TALISMAN
              ? talismans.generate(offer)
              : generator.generate(floor, offer));
    }
    // An Artifact comes back to its home floor, guardian or not, until it is taken.
    pool.addAll(artifacts.artifactsFor(floor, gameState.getArtifactsTaken()));

    var finders =
        new ArrayList<>(
            gameState.getUnitsOnTower().values().stream()
                .flatMap(List::stream)
                .filter(unit -> !unit.isDead())
                .toList());
    Collections.shuffle(finders, random);
    for (var finder : finders) {
      var roll = random.nextDouble();
      if (pool.isEmpty()) {
        continue;
      }
      if (roll < config.getFindChance()) {
        give(gameState, finder, pool.remove(random.nextInt(pool.size())), rating, config);
      } else if (roll < config.getFindChance() * affinity.getFindMultiplier()) {
        // Only the unit's affinity makes this find possible, so it can only be of that category.
        var liked = pool.stream().filter(item -> item.category() == finder.getAffinity()).toList();
        if (!liked.isEmpty()) {
          var item = liked.get(random.nextInt(liked.size()));
          pool.remove(item);
          give(gameState, finder, item, rating, config);
        }
      }
    }

    var unclaimed = pool.stream().filter(stored::contains).toList();
    if (!unclaimed.isEmpty()) {
      store.putBack(floor, unclaimed);
    }
  }

  private void give(
      GameState gameState,
      Unit finder,
      Item item,
      ItemRating rating,
      GameProperties.Items.Bundle config) {
    if (item.tier() == ItemTier.ARTIFACT) {
      gameState.recordArtifactTaken(item.id());
    }
    gameState.getMeter().itemFound();
    var result = finder.equip(item, rating);
    if (result.hasSurplus()) {
      gameState.addSurplus(result.surplus().tier());
    }
    finder.changeSanity(config.getSanityGain());
  }
}

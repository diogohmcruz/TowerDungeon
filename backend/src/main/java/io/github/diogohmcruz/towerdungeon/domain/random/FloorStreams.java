package io.github.diogohmcruz.towerdungeon.domain.random;

import java.util.random.RandomGenerator;

/**
 * The two streams one building of a Floor draws from, both fixed by the Campaign seed, the Floor
 * and the visit index alone.
 *
 * @param generation the Garrison: budget swing, which enemies, how many parts and which limbs
 * @param cosmetic the Garrison's names and ids, kept apart so they never move the draws that decide
 *     its strength
 */
public record FloorStreams(RandomGenerator generation, RandomGenerator cosmetic) {

  /** One generator for both, for a test that scripts or seeds a Floor directly. */
  public static FloorStreams of(RandomGenerator both) {
    return new FloorStreams(both, both);
  }
}

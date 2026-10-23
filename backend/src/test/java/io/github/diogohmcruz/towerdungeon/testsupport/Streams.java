package io.github.diogohmcruz.towerdungeon.testsupport;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Streams for tests that need a Unit or Enemy but do not care what it is called or numbered. */
public final class Streams {
  private Streams() {}

  /**
   * A cosmetic stream that is not seeded: the names and ids of the units it makes are arbitrary.
   */
  public static RandomGenerator cosmetic() {
    return ThreadLocalRandom.current();
  }
}

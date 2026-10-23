package io.github.diogohmcruz.towerdungeon.domain.random;

import java.util.Random;
import java.util.random.RandomGenerator;

import net.datafaker.Faker;

/**
 * Datafaker names drawn from a cosmetic stream, so the same stream position gives the same name.
 */
public final class Names {
  private Names() {}

  /** A Faker whose every choice follows from one draw of {@code cosmetic}. */
  public static Faker faker(RandomGenerator cosmetic) {
    return new Faker(new Random(cosmetic.nextLong()));
  }

  /** The name of a Unit. */
  public static String philosopher(RandomGenerator cosmetic) {
    return faker(cosmetic).greekPhilosopher().name();
  }
}

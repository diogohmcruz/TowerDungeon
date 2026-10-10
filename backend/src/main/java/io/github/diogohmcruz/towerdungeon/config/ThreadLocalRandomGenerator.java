package io.github.diogohmcruz.towerdungeon.config;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The production random source: every draw is delegated to the calling thread's {@link
 * ThreadLocalRandom}, so it is safe to share between the game loops and behaves exactly like the
 * direct {@code ThreadLocalRandom.current()} calls it replaces. Tests swap in a seeded generator.
 */
public final class ThreadLocalRandomGenerator implements RandomGenerator {
  @Override
  public long nextLong() {
    return ThreadLocalRandom.current().nextLong();
  }

  @Override
  public int nextInt(int bound) {
    return ThreadLocalRandom.current().nextInt(bound);
  }

  @Override
  public double nextDouble() {
    return ThreadLocalRandom.current().nextDouble();
  }

  @Override
  public double nextDouble(double bound) {
    return ThreadLocalRandom.current().nextDouble(bound);
  }

  @Override
  public double nextDouble(double origin, double bound) {
    return ThreadLocalRandom.current().nextDouble(origin, bound);
  }
}

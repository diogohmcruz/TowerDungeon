package io.github.diogohmcruz.towerdungeon.domain.random;

import java.util.random.RandomGenerator;

/**
 * One stream of draws: the SplitMix64 sequence, which is exactly what {@code new
 * SplittableRandom(seed)} produces for its {@code nextLong()} (checked by a test). It is written
 * out because the Campaign must save where a stream stands, and {@code SplittableRandom} does not
 * let its state be read. Every other draw ({@code nextInt}, {@code nextDouble}, shuffles) derives
 * from {@link #nextLong()}, so the position is the single {@link #state()} number.
 *
 * <p>Not thread-safe: a stream is only used under its Campaign's lock.
 */
public final class SplitMixStream implements RandomGenerator {
  private static final long GAMMA = 0x9e3779b97f4a7c15L;

  private long state;

  /** A stream that starts at {@code seed}, like {@code new SplittableRandom(seed)}. */
  public SplitMixStream(long seed) {
    this.state = seed;
  }

  /**
   * The SplitMix64 finaliser: spreads the bits of {@code z} so near inputs give distant outputs.
   */
  static long mix(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  @Override
  public long nextLong() {
    state += GAMMA;
    return mix(state);
  }

  /** Where the stream stands: restoring it with {@code new SplitMixStream(state)} resumes it. */
  public long state() {
    return state;
  }
}

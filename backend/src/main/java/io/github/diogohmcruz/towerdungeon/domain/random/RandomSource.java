package io.github.diogohmcruz.towerdungeon.domain.random;

import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.RandomSnapshot;

/**
 * Every random draw of one Campaign, from one seed, in three independent streams:
 *
 * <ul>
 *   <li><b>Tower generation</b>: a fresh stream per building of a Floor, derived from {@code (seed,
 *       floor, visit index)}. A Floor's Garrison therefore does not depend on how many draws
 *       happened anywhere else, so recruiting an extra Unit leaves the next Floor unchanged.
 *   <li><b>Combat and lifecycle</b>: target picks, the Starvation victim, shuffles, strength rolls,
 *       phobias, affinity, miss rolls, Aim picks, Bundle and find rolls.
 *   <li><b>Cosmetic</b>: names and ids of Units. (A Floor's enemy names and ids come from a
 *       cosmetic stream of their own, derived like the generation stream.)
 * </ul>
 *
 * Any new random draw in the game takes its stream from here and never from {@code
 * ThreadLocalRandom}. Not thread-safe: use it under the Campaign's lock.
 *
 * <p>A human Campaign's seed comes from {@link SeedSource} and is never accepted from a client;
 * only a Sandbox is given a chosen one.
 */
public final class RandomSource {
  private static final long COMBAT = 1;
  private static final long COSMETIC = 2;
  private static final long TOWER = 3;
  private static final long TOWER_COSMETIC = 4;
  private static final long FOUNDERS = 5;
  private static final long YIELD = 6;

  private final long seed;

  /**
   * The seed the Tower and what it yields derive from. It is {@link #seed} for a Campaign; a Trial
   * takes it from its Terms, so every Contender meets the same Garrisons, the same finds on each
   * Floor and the same four Founders, while combat and everything after the start stay their own.
   */
  private final long towerSeed;

  private final boolean pinned;
  private final SplitMixStream combatStream;
  private final RandomGenerator combat;
  private final SplitMixStream cosmetic;
  private final Map<Integer, Integer> floorVisits = new TreeMap<>();

  /** A source whose every stream follows from {@code seed}. */
  public RandomSource(long seed) {
    this(
        seed,
        seed,
        false,
        new SplitMixStream(derive(seed, COMBAT)),
        null,
        new SplitMixStream(derive(seed, COSMETIC)));
  }

  /**
   * A Trial's source: the Tower, its yield and the Founders follow from {@code termsSeed}, the
   * Terms' server-only seed; combat, Aim, finds and names follow from {@code ownSeed}, so equal
   * Garrisons do not mean equal luck.
   */
  public static RandomSource forTrial(long termsSeed, long ownSeed) {
    return new RandomSource(
        ownSeed,
        termsSeed,
        true,
        new SplitMixStream(derive(ownSeed, COMBAT)),
        null,
        new SplitMixStream(derive(ownSeed, COSMETIC)));
  }

  private RandomSource(
      long seed,
      long towerSeed,
      boolean pinned,
      SplitMixStream combatStream,
      RandomGenerator scripted,
      SplitMixStream cosmetic) {
    this.seed = seed;
    this.towerSeed = towerSeed;
    this.pinned = pinned;
    this.combatStream = combatStream;
    this.combat = scripted == null ? combatStream : scripted;
    this.cosmetic = cosmetic;
  }

  /** Resumes a saved source exactly where it stood. */
  public static RandomSource restore(RandomSnapshot saved) {
    var source =
        new RandomSource(
            saved.seed(),
            saved.towerSeed() == null ? saved.seed() : saved.towerSeed(),
            saved.towerSeed() != null,
            new SplitMixStream(saved.combatState()),
            null,
            new SplitMixStream(saved.cosmeticState()));
    source.floorVisits.putAll(saved.floorVisits());
    return source;
  }

  /** A source whose combat stream is {@code combat}, for a test that scripts the draws. */
  public static RandomSource scripted(RandomGenerator combat) {
    return new RandomSource(
        0, 0, false, new SplitMixStream(0), combat, new SplitMixStream(derive(0, COSMETIC)));
  }

  /** The combat and lifecycle stream. */
  public RandomGenerator combat() {
    return combat;
  }

  /** The cosmetic stream: Unit names and ids. */
  public RandomGenerator cosmetic() {
    return cosmetic;
  }

  /**
   * Opens the streams for building {@code floor} once more. The visit index counts the buildings of
   * that Floor so far, so each building has its own Garrison, the same in every replay.
   */
  public FloorStreams openFloor(int floor) {
    var visit = floorVisits.merge(floor, 1, Integer::sum) - 1;
    return new FloorStreams(
        new SplitMixStream(derive(towerSeed, TOWER, floor, visit)),
        new SplitMixStream(derive(seed, TOWER_COSMETIC, floor, visit)));
  }

  /**
   * The draws that rolled the Founders' strengths. In a Trial a fresh stream fixed by the Terms, so
   * every Contender starts with the same four; otherwise the combat stream, as ever. Take it once
   * and draw every Founder from it, in order.
   */
  public RandomGenerator founders() {
    return pinned ? new SplitMixStream(derive(towerSeed, FOUNDERS)) : combat;
  }

  /**
   * The draws that decide what a cleared {@code floor} yields to find (whether a Leftover waits,
   * its tier and its make). In a Trial a fresh stream fixed by the Terms, the Floor and how often
   * it was built, so the k-th clearing of a Floor offers the same finds to every Contender; who
   * finds them stays with the combat stream. Otherwise the combat stream, as ever.
   */
  public RandomGenerator yield(int floor) {
    if (!pinned) {
      return combat;
    }
    var visit = Math.max(0, floorVisits.getOrDefault(floor, 1) - 1);
    return new SplitMixStream(derive(towerSeed, YIELD, floor, visit));
  }

  /** Whether the Tower, its yield and the Founders are fixed by Terms rather than by the seed. */
  public boolean isPinned() {
    return pinned;
  }

  /** What to save so the Campaign carries on from here. */
  public RandomSnapshot snapshot() {
    return new RandomSnapshot(
        seed,
        combatStream.state(),
        cosmetic.state(),
        Map.copyOf(floorVisits),
        pinned ? towerSeed : null);
  }

  /** Mixes the seed with a list of numbers into a new, unrelated stream seed. */
  private static long derive(long seed, long... parts) {
    var h = SplitMixStream.mix(seed);
    for (var part : parts) {
      h = SplitMixStream.mix(h ^ SplitMixStream.mix(part + 0x9e3779b97f4a7c15L));
    }
    return h;
  }
}

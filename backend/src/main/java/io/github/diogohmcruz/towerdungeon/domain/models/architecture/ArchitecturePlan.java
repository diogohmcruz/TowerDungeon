package io.github.diogohmcruz.towerdungeon.domain.models.architecture;

import static io.github.diogohmcruz.towerdungeon.domain.models.architecture.Architecture.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Which {@link Architecture} every Floor of the Tower has: a pure, versioned function of the Tower
 * seed and the Floor number, plus 14 pinned Floors that hold in every seed so the Floors the manga
 * attests stay recognisable.
 *
 * <p>The plan is built once, sequentially, so its constraints can see their neighbours: no
 * Architecture twice in a row except Colonnade, at most one Warren per Reach, never more than three
 * consecutive Floors that carry a Cost, Statuary only from Floor 41. Pinned Floors and the Guardian
 * Floors are settled first, so an ordinary Floor next to one already accounts for it. A pin is
 * ignored when its Floor is the wrong kind for it (for example under a different Guardian
 * interval), so the plan never puts a guardian form on an ordinary Floor or the reverse.
 *
 * <p>The plan lives on the backend. Only the Floors a Party has discovered are ever sent to the
 * frontend.
 */
public final class ArchitecturePlan {
  /** Bump when the plan function changes in a way that moves existing Floors. */
  public static final int VERSION = 1;

  private static final int REACH_LENGTH = 10;
  private static final int MAX_COST_RUN = 3;
  private static final int MAX_WELLS = 3;
  private static final int FIRST_STATUARY_FLOOR = 41;
  private static final int LAST_WARREN_REACH = 6;
  private static final int FIRST_EXTRA_LOOP_REACH = 6;
  private static final int LAST_EXTRA_LOOP_REACH = 7;
  private static final int NEVER_WELL_FLOOR = 40;
  private static final long VARIANT_SALT = 0x5DEECE66DL;

  private static final Map<Integer, Architecture> PINS =
      Map.ofEntries(
          Map.entry(1, COLONNADE),
          Map.entry(3, STAIR_SHAFT),
          Map.entry(5, COLONNADE),
          Map.entry(10, GATE),
          Map.entry(14, LOOP_ROOMS),
          Map.entry(15, WATERWAY),
          Map.entry(20, WELL),
          Map.entry(25, PILLAR_MAZE),
          Map.entry(40, GATE),
          Map.entry(50, GATE),
          Map.entry(59, STATUARY),
          Map.entry(60, GATE),
          Map.entry(80, EXPANSE),
          Map.entry(100, GATE));

  /** Relative weight of each ordinary Architecture in Reaches 1 to 10 (0 means not offered). */
  private static final Map<Architecture, int[]> REACH_WEIGHTS = reachWeights();

  private final Architecture[] architectures;
  private final int[] variants;

  private ArchitecturePlan(Architecture[] architectures, int[] variants) {
    this.architectures = architectures;
    this.variants = variants;
  }

  /**
   * Plans floors 1 to {@code maxFloor}.
   *
   * @param seed the Tower seed; a shipped constant in Generation 1
   * @param guardianInterval every Nth Floor, and the top Floor, is a Guardian Floor
   * @param surgeInterval every Nth ordinary Floor is a Surge Floor (0 for none)
   */
  public static ArchitecturePlan generate(
      long seed, int maxFloor, int guardianInterval, int surgeInterval) {
    var plan = new Architecture[maxFloor + 2];
    var builder = new Builder(seed, maxFloor, guardianInterval, surgeInterval, plan);
    builder.pinFloors();
    builder.seedGuardianFloors();
    builder.seedOrdinaryFloors();
    var variants = new int[maxFloor + 2];
    for (var floor = 1; floor <= maxFloor; floor++) {
      variants[floor] = (int) (mix(seed ^ VARIANT_SALT, floor) & 3);
    }
    return new ArchitecturePlan(plan, variants);
  }

  /** The Architecture of {@code floor}, which must lie between 1 and the top Floor. */
  public Architecture architectureOf(int floor) {
    return architectures[floor];
  }

  /** One of four cosmetic variants of the Architecture, so neighbouring Floors differ. */
  public int variantOf(int floor) {
    return variants[floor];
  }

  /** SplitMix64 finaliser over a seed and a floor, so every Floor has its own stream. */
  private static long mix(long seed, int floor) {
    var z = seed + floor * 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  private static int reachOf(int floor) {
    return (floor - 1) / REACH_LENGTH + 1;
  }

  private static Map<Architecture, int[]> reachWeights() {
    var weights = new EnumMap<Architecture, int[]>(Architecture.class);
    weights.put(COLONNADE, new int[] {45, 40, 36, 32, 30, 28, 26, 24, 22, 20});
    weights.put(NAVE, new int[] {4, 6, 8, 10, 12, 14, 16, 18, 20, 22});
    weights.put(PILLAR_MAZE, new int[] {0, 0, 8, 10, 12, 14, 16, 18, 20, 22});
    weights.put(STAIR_SHAFT, new int[] {14, 14, 12, 10, 0, 0, 8, 0, 0, 0});
    weights.put(SPAN, new int[] {4, 6, 8, 10, 12, 14, 16, 18, 20, 22});
    weights.put(WATERWAY, new int[] {8, 8, 8, 8, 8, 8, 8, 8, 8, 8});
    weights.put(LOOP_ROOMS, new int[] {0, 0, 0, 0, 0, 3, 3, 0, 0, 0});
    weights.put(STATUARY, new int[] {0, 0, 0, 0, 6, 8, 10, 12, 14, 16});
    weights.put(WARREN, new int[] {12, 12, 10, 8, 6, 4, 0, 0, 0, 0});
    return weights;
  }

  private static final class Builder {
    private final long seed;
    private final int maxFloor;
    private final int guardianInterval;
    private final int surgeInterval;
    private final Architecture[] plan;

    Builder(long seed, int maxFloor, int guardianInterval, int surgeInterval, Architecture[] plan) {
      this.seed = seed;
      this.maxFloor = maxFloor;
      this.guardianInterval = guardianInterval;
      this.surgeInterval = surgeInterval;
      this.plan = plan;
    }

    private boolean isGuardianFloor(int floor) {
      return floor == maxFloor || (guardianInterval > 0 && floor % guardianInterval == 0);
    }

    private boolean isSurgeFloor(int floor) {
      return surgeInterval > 0 && floor % surgeInterval == 0 && !isGuardianFloor(floor);
    }

    void pinFloors() {
      PINS.forEach(
          (floor, architecture) -> {
            if (floor <= maxFloor
                && architecture.guardianArchitecture() == isGuardianFloor(floor)) {
              plan[floor] = architecture;
            }
          });
    }

    /** Guardian Floors the pins leave open are a Gate, or a Well while fewer than three exist. */
    void seedGuardianFloors() {
      var wells = (int) java.util.Arrays.stream(plan).filter(a -> a == WELL).count();
      for (var floor = 1; floor <= maxFloor; floor++) {
        if (!isGuardianFloor(floor) || plan[floor] != null) {
          continue;
        }
        var well =
            wells < MAX_WELLS
                && floor != NEVER_WELL_FLOOR
                && new SplittableRandom(mix(seed, floor)).nextBoolean();
        plan[floor] = well ? WELL : GATE;
        if (well) {
          wells++;
        }
      }
    }

    void seedOrdinaryFloors() {
      var warrenReaches = new boolean[reachOf(maxFloor) + 1];
      var extraLoops = 0;
      for (var floor = 1; floor <= maxFloor; floor++) {
        if (plan[floor] == null) {
          var chosen = pick(floor, warrenReaches, extraLoops);
          plan[floor] = chosen;
          if (chosen == WARREN) {
            warrenReaches[reachOf(floor)] = true;
          } else if (chosen == LOOP_ROOMS) {
            extraLoops++;
          }
        } else if (plan[floor] == WARREN) {
          warrenReaches[reachOf(floor)] = true;
        }
      }
    }

    private Architecture pick(int floor, boolean[] warrenReaches, int extraLoops) {
      var reach = Math.min(reachOf(floor), REACH_LENGTH);
      var candidates = new java.util.ArrayList<Architecture>();
      var weights = new java.util.ArrayList<Integer>();
      var total = 0;
      for (var architecture : ordinaryArchitectures()) {
        var weight = REACH_WEIGHTS.get(architecture)[reach - 1];
        if (weight > 0 && allowed(architecture, floor, warrenReaches, extraLoops)) {
          candidates.add(architecture);
          weights.add(weight);
          total += weight;
        }
      }
      if (total == 0) {
        return COLONNADE;
      }
      var roll = new SplittableRandom(mix(seed, floor)).nextInt(total);
      for (var i = 0; i < candidates.size(); i++) {
        roll -= weights.get(i);
        if (roll < 0) {
          return candidates.get(i);
        }
      }
      return COLONNADE;
    }

    private static List<Architecture> ordinaryArchitectures() {
      return java.util.Arrays.stream(values()).filter(a -> !a.guardianArchitecture()).toList();
    }

    private boolean allowed(
        Architecture candidate, int floor, boolean[] warrenReaches, int extraLoops) {
      if (candidate != COLONNADE
          && (plan[floor - 1] == candidate || plan[floor + 1] == candidate)) {
        return false;
      }
      if (isSurgeFloor(floor) && !candidate.surgeEligible()) {
        return false;
      }
      if (candidate == STATUARY && floor < FIRST_STATUARY_FLOOR) {
        return false;
      }
      if (candidate == WARREN
          && (warrenReaches[reachOf(floor)] || reachOf(floor) > LAST_WARREN_REACH)) {
        return false;
      }
      if (candidate == LOOP_ROOMS
          && (extraLoops >= 1
              || reachOf(floor) < FIRST_EXTRA_LOOP_REACH
              || reachOf(floor) > LAST_EXTRA_LOOP_REACH)) {
        return false;
      }
      return !candidate.carriesCost() || costRunWith(floor) <= MAX_COST_RUN;
    }

    /** Length of the run of Cost Floors that {@code floor} would join or create. */
    private int costRunWith(int floor) {
      var run = 1;
      for (var f = floor - 1; f >= 1 && plan[f] != null && plan[f].carriesCost(); f--) {
        run++;
      }
      for (var f = floor + 1; f <= maxFloor && plan[f] != null && plan[f].carriesCost(); f++) {
        run++;
      }
      return run;
    }
  }
}

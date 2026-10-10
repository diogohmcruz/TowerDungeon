package io.github.diogohmcruz.towerdungeon.domain.models.architecture;

import static io.github.diogohmcruz.towerdungeon.domain.models.architecture.Architecture.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class ArchitecturePlanTest {
  private static final int MAX_FLOOR = 100;
  private static final int GUARDIAN_INTERVAL = 10;
  private static final int SURGE_INTERVAL = 5;
  private static final int SEEDS = 400;

  private static ArchitecturePlan plan(long seed) {
    return ArchitecturePlan.generate(seed, MAX_FLOOR, GUARDIAN_INTERVAL, SURGE_INTERVAL);
  }

  private static boolean isGuardianFloor(int floor) {
    return floor % GUARDIAN_INTERVAL == 0 || floor == MAX_FLOOR;
  }

  private static int reach(int floor) {
    return (floor - 1) / 10 + 1;
  }

  private static LongStream seeds() {
    return LongStream.range(0, SEEDS);
  }

  @Test
  void sameSeedGivesTheSamePlan() {
    for (var floor = 1; floor <= MAX_FLOOR; floor++) {
      assertThat(plan(7).architectureOf(floor)).isEqualTo(plan(7).architectureOf(floor));
      assertThat(plan(7).variantOf(floor)).isEqualTo(plan(7).variantOf(floor));
    }
  }

  @Test
  void differentSeedsGiveDifferentPlans() {
    var distinct =
        seeds()
            .mapToObj(
                seed ->
                    IntStream.rangeClosed(1, MAX_FLOOR)
                        .mapToObj(plan(seed)::architectureOf)
                        .toList())
            .distinct()
            .count();

    assertThat(distinct).isGreaterThan(SEEDS * 9L / 10);
  }

  @Test
  void theFourteenPinnedFloorsHoldInEverySeed() {
    var pins =
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
    assertThat(pins).hasSize(14);

    seeds()
        .forEach(
            seed ->
                pins.forEach(
                    (floor, expected) ->
                        assertThat(plan(seed).architectureOf(floor))
                            .as("seed %d floor %d", seed, floor)
                            .isEqualTo(expected)));
  }

  @Test
  void guardianFloorsHoldGuardianArchitecturesAndOrdinaryFloorsDoNot() {
    seeds()
        .forEach(
            seed ->
                IntStream.rangeClosed(1, MAX_FLOOR)
                    .forEach(
                        floor ->
                            assertThat(plan(seed).architectureOf(floor).guardianArchitecture())
                                .as("seed %d floor %d", seed, floor)
                                .isEqualTo(isGuardianFloor(floor))));
  }

  @Test
  void noArchitectureRepeatsOnNeighbouringFloorsExceptColonnade() {
    seeds()
        .forEach(
            seed ->
                IntStream.range(1, MAX_FLOOR)
                    .forEach(
                        floor -> {
                          var here = plan(seed).architectureOf(floor);
                          if (here != COLONNADE) {
                            assertThat(plan(seed).architectureOf(floor + 1))
                                .as("seed %d floor %d", seed, floor)
                                .isNotEqualTo(here);
                          }
                        }));
  }

  @Test
  void atMostOneWarrenPerReachAndOnlyInReachesOneToSix() {
    seeds()
        .forEach(
            seed -> {
              var perReach = new int[11];
              IntStream.rangeClosed(1, MAX_FLOOR)
                  .filter(floor -> plan(seed).architectureOf(floor) == WARREN)
                  .forEach(
                      floor -> {
                        assertThat(reach(floor)).isLessThanOrEqualTo(6);
                        assertThat(++perReach[reach(floor)]).as("seed %d", seed).isEqualTo(1);
                      });
            });
  }

  @Test
  void noMoreThanThreeConsecutiveFloorsCarryACost() {
    seeds()
        .forEach(
            seed -> {
              var run = 0;
              for (var floor = 1; floor <= MAX_FLOOR; floor++) {
                run = plan(seed).architectureOf(floor).carriesCost() ? run + 1 : 0;
                assertThat(run).as("seed %d floor %d", seed, floor).isLessThanOrEqualTo(3);
              }
            });
  }

  @Test
  void reachAndFloorRestrictionsHold() {
    seeds()
        .forEach(
            seed ->
                IntStream.rangeClosed(1, MAX_FLOOR)
                    .forEach(
                        floor -> {
                          var architecture = plan(seed).architectureOf(floor);
                          var message = "seed %d floor %d".formatted(seed, floor);
                          if (architecture == STATUARY) {
                            assertThat(floor).as(message).isGreaterThanOrEqualTo(41);
                          }
                          if (architecture == PILLAR_MAZE) {
                            assertThat(reach(floor)).as(message).isGreaterThanOrEqualTo(3);
                          }
                          if (architecture == STAIR_SHAFT) {
                            assertThat(reach(floor)).as(message).isIn(1, 2, 3, 4, 7);
                          }
                          if (architecture == EXPANSE) {
                            assertThat(floor).as(message).isEqualTo(80);
                          }
                        }));
  }

  @Test
  void surgeFloorsOnlyHoldWideArchitectures() {
    seeds()
        .forEach(
            seed ->
                IntStream.rangeClosed(1, MAX_FLOOR)
                    .filter(floor -> floor % SURGE_INTERVAL == 0 && !isGuardianFloor(floor))
                    .forEach(
                        floor ->
                            assertThat(plan(seed).architectureOf(floor).surgeEligible())
                                .as("seed %d floor %d", seed, floor)
                                .isTrue()));
  }

  @Test
  void wellsAreCappedAtThreeAndNeverOnFloorForty() {
    seeds()
        .forEach(
            seed -> {
              var wells =
                  IntStream.rangeClosed(1, MAX_FLOOR)
                      .filter(floor -> plan(seed).architectureOf(floor) == WELL)
                      .boxed()
                      .toList();
              assertThat(wells).as("seed %d", seed).hasSizeLessThanOrEqualTo(3).doesNotContain(40);
              assertThat(wells).contains(20);
            });
  }

  @Test
  void theSeededGuardianFloorsAreGateOrWell() {
    seeds()
        .forEach(
            seed ->
                assertThat(java.util.List.of(30, 70, 90))
                    .allSatisfy(
                        floor -> assertThat(plan(seed).architectureOf(floor)).isIn(GATE, WELL)));
  }

  @Test
  void atMostOneExtraLoopRoomsAndOnlyInReachesSixAndSeven() {
    seeds()
        .forEach(
            seed -> {
              var loops =
                  IntStream.rangeClosed(1, MAX_FLOOR)
                      .filter(floor -> plan(seed).architectureOf(floor) == LOOP_ROOMS)
                      .boxed()
                      .toList();
              assertThat(loops).as("seed %d", seed).contains(14).hasSizeLessThanOrEqualTo(2);
              loops.stream()
                  .filter(floor -> floor != 14)
                  .forEach(floor -> assertThat(reach(floor)).isIn(6, 7));
            });
  }

  @Test
  void variantsStayInRangeAndVary() {
    var seen = new int[4];
    seeds()
        .forEach(
            seed ->
                IntStream.rangeClosed(1, MAX_FLOOR)
                    .forEach(
                        floor -> {
                          var variant = plan(seed).variantOf(floor);
                          assertThat(variant).isBetween(0, 3);
                          seen[variant]++;
                        }));

    assertThat(seen).doesNotContain(0);
  }

  @Test
  void colonnadeIsAboutAThirdOfUnpinnedOrdinaryFloors() {
    var pinned = java.util.Set.of(1, 3, 5, 14, 15, 25, 59);
    var counts = new EnumMap<Architecture, Integer>(Architecture.class);
    var total = 0;
    for (var seed = 0L; seed < SEEDS; seed++) {
      for (var floor = 1; floor <= MAX_FLOOR; floor++) {
        if (isGuardianFloor(floor) || pinned.contains(floor)) {
          continue;
        }
        counts.merge(plan(seed).architectureOf(floor), 1, Integer::sum);
        total++;
      }
    }

    assertThat(counts.getOrDefault(COLONNADE, 0) / (double) total).isBetween(0.25, 0.40);
    for (var architecture : values()) {
      if (!architecture.guardianArchitecture()) {
        assertThat(counts).as(architecture.name()).containsKey(architecture);
      }
    }
  }

  @Test
  void upperReachesFillWithTheHeavyArchitectures() {
    var heavy = java.util.Set.of(NAVE, PILLAR_MAZE, SPAN, STATUARY);
    long lower = 0;
    long upper = 0;
    for (var seed = 0L; seed < SEEDS; seed++) {
      var plan = plan(seed);
      for (var floor = 1; floor <= MAX_FLOOR; floor++) {
        if (heavy.contains(plan.architectureOf(floor))) {
          if (reach(floor) <= 3) {
            lower++;
          } else if (reach(floor) >= 8) {
            upper++;
          }
        }
      }
    }

    assertThat(upper).isGreaterThan(lower * 2);
  }

  @Test
  void aPinOnTheWrongKindOfFloorIsIgnored() {
    var plan = ArchitecturePlan.generate(1, 100, 7, 5);

    assertThat(plan.architectureOf(10).guardianArchitecture()).isFalse();
    assertThat(plan.architectureOf(7).guardianArchitecture()).isTrue();
    assertThat(plan.architectureOf(100)).isEqualTo(GATE);
  }

  @Test
  void theTowerCanBeShorterThanTheLastPins() {
    var plan = ArchitecturePlan.generate(1, 12, 10, 5);

    assertThat(plan.architectureOf(10)).isEqualTo(GATE);
    assertThat(plan.architectureOf(12)).isIn(GATE, WELL);
  }
}

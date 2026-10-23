package io.github.diogohmcruz.towerdungeon.domain.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

class LeftoverGeneratorTest {
  private final GameProperties config = new GameProperties();

  private LeftoverGenerator generator(long seed) {
    return new LeftoverGenerator(new SplittableRandom(seed), config);
  }

  @Test
  void sameSeedMakesTheSameItems() {
    var a = generator(5);
    var b = generator(5);

    for (var floor = 1; floor <= 20; floor++) {
      assertThat(a.generate(floor)).isEqualTo(b.generate(floor));
    }
  }

  @Test
  void everyLeftoverDrawsOneOrTwoStatsFromItsCategoryTable() {
    var generator = generator(11);
    var seen = new java.util.HashSet<ItemCategory>();

    for (var i = 0; i < 500; i++) {
      var item = generator.generate(10);
      seen.add(item.category());
      assertThat(item.tier()).isEqualTo(ItemTier.LEFTOVER);
      assertThat(item.hasEffect()).isFalse();
      assertThat(item.stats().keySet()).isSubsetOf(item.category().statTable());
      assertThat(item.stats()).hasSizeBetween(1, 2);
    }
    assertThat(seen).containsExactlyInAnyOrder(ItemCategory.values());
  }

  @Test
  void valuesAreBaseTimesFloorScalingTimesAHalfToOneAndAHalfRoll() {
    var generator = generator(3);
    var floor = 20;
    var scale = 1 + 0.05 * floor;

    for (var i = 0; i < 500; i++) {
      var item = generator.generate(floor);
      item.stats()
          .forEach(
              (stat, value) -> {
                var base =
                    switch (stat) {
                      case POWER, ARMOUR -> 0.5;
                      default -> 5.0;
                    };
                assertThat(value).isBetween(base * scale * 0.5, base * scale * 1.5);
              });
    }
  }

  @Test
  void deeperFloorsRollBiggerOnAverage() {
    double shallow = 0;
    double deep = 0;
    var a = generator(9);
    var b = generator(9);

    for (var i = 0; i < 300; i++) {
      shallow += a.generate(1).stats().values().stream().mapToDouble(d -> d).sum();
      deep += b.generate(60).stats().values().stream().mapToDouble(d -> d).sum();
    }

    assertThat(deep / shallow).isCloseTo((1 + 0.05 * 60) / (1 + 0.05), within(0.01));
  }
}

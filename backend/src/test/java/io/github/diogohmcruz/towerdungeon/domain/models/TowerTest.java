package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

class TowerTest {
  private static final int FLOORS = 30;

  private static List<List<String>> climb(RandomGenerator random) {
    var config = new GameProperties();
    var tower = new Tower(100, config.getBoss(), config.getEnemies(), random);
    return IntStream.range(0, FLOORS)
        .<List<String>>mapToObj(
            _ -> {
              tower.moveToNextFloor();
              return tower.getCurrentTowerFloor().getEnemies().stream()
                  .map(enemy -> enemy.getStats().name())
                  .toList();
            })
        .toList();
  }

  @Test
  void sameSeedBuildsTheSameGarrisons() {
    assertThat(climb(new SplittableRandom(42))).isEqualTo(climb(new SplittableRandom(42)));
  }

  @Test
  void differentSeedsBuildDifferentGarrisons() {
    assertThat(climb(new SplittableRandom(1))).isNotEqualTo(climb(new SplittableRandom(2)));
  }
}

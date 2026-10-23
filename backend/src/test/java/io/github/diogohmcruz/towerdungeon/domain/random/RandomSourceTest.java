package io.github.diogohmcruz.towerdungeon.domain.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.RandomSnapshot;

class RandomSourceTest {

  private static List<Long> draws(java.util.random.RandomGenerator stream, int count) {
    return LongStream.range(0, count).mapToObj(_ -> stream.nextLong()).toList();
  }

  @Test
  void aStreamIsExactlyTheSequenceOfASplittableRandomWithThatSeed() {
    var mine = new SplitMixStream(123_456_789L);
    var reference = new SplittableRandom(123_456_789L);

    for (var i = 0; i < 1000; i++) {
      assertThat(mine.nextLong()).isEqualTo(reference.nextLong());
    }
    assertThat(mine.nextInt(10, 20)).isBetween(10, 19);
  }

  @Test
  void aStreamResumesFromItsSavedState() {
    var stream = new SplitMixStream(5);
    draws(stream, 17);

    var resumed = new SplitMixStream(stream.state());

    assertThat(draws(resumed, 10)).isEqualTo(draws(stream, 10));
  }

  @Test
  void theSameSeedGivesTheSameStreamsAndAnotherSeedDoesNot() {
    assertThat(draws(new RandomSource(9).combat(), 8))
        .isEqualTo(draws(new RandomSource(9).combat(), 8))
        .isNotEqualTo(draws(new RandomSource(10).combat(), 8));
    assertThat(draws(new RandomSource(9).cosmetic(), 8))
        .isEqualTo(draws(new RandomSource(9).cosmetic(), 8));
  }

  @Test
  void theThreeStreamsOfASeedAreDifferentSequences() {
    var source = new RandomSource(3);
    var floor = source.openFloor(1);

    var firsts =
        Set.of(
            source.combat().nextLong(),
            source.cosmetic().nextLong(),
            floor.generation().nextLong(),
            floor.cosmetic().nextLong());

    assertThat(firsts).hasSize(4);
  }

  @Test
  void aFloorsStreamsDoNotDependOnWhatTheOtherStreamsHaveDrawn() {
    var quiet = new RandomSource(21);
    var busy = new RandomSource(21);
    draws(busy.combat(), 500);
    draws(busy.cosmetic(), 500);
    busy.openFloor(7);
    draws(busy.openFloor(2).generation(), 40);

    var expected = quiet.openFloor(3);
    var actual = busy.openFloor(3);

    assertThat(draws(actual.generation(), 20)).isEqualTo(draws(expected.generation(), 20));
    assertThat(draws(actual.cosmetic(), 20)).isEqualTo(draws(expected.cosmetic(), 20));
  }

  @Test
  void eachVisitToAFloorHasItsOwnStreamsAndEveryReplayRepeatsThem() {
    var source = new RandomSource(4);
    var replay = new RandomSource(4);

    var firstVisit = draws(source.openFloor(5).generation(), 6);
    var secondVisit = draws(source.openFloor(5).generation(), 6);
    var otherFloor = draws(source.openFloor(6).generation(), 6);

    assertThat(secondVisit).isNotEqualTo(firstVisit);
    assertThat(otherFloor).isNotEqualTo(firstVisit);
    assertThat(draws(replay.openFloor(5).generation(), 6)).isEqualTo(firstVisit);
    assertThat(draws(replay.openFloor(5).generation(), 6)).isEqualTo(secondVisit);
  }

  @Test
  void aSavedSourceCarriesOnWhereItStood() {
    var source = new RandomSource(77);
    draws(source.combat(), 31);
    draws(source.cosmetic(), 12);
    source.openFloor(1);
    source.openFloor(1);
    source.openFloor(4);

    var restored = RandomSource.restore(source.snapshot());

    assertThat(restored.snapshot()).isEqualTo(source.snapshot());
    assertThat(draws(restored.combat(), 10)).isEqualTo(draws(source.combat(), 10));
    assertThat(draws(restored.cosmetic(), 10)).isEqualTo(draws(source.cosmetic(), 10));
    // The third visit to Floor 1 is the same one the original would open.
    assertThat(draws(restored.openFloor(1).generation(), 5))
        .isEqualTo(draws(source.openFloor(1).generation(), 5));
  }

  @Test
  void theSnapshotCarriesEveryFieldOfTheSource() {
    var components =
        Arrays.stream(RandomSnapshot.class.getRecordComponents())
            .map(c -> c.getName())
            .collect(Collectors.toSet());

    // seed -> seed; combatStream -> combatState; cosmetic -> cosmeticState; floorVisits.
    // "combat" is the same stream as combatStream unless a test scripts it.
    var mapped = Set.of("seed", "combatStream", "combat", "cosmetic", "floorVisits");
    var fields =
        Arrays.stream(RandomSource.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(f -> f.getName())
            .toList();

    assertThat(mapped).containsAll(fields);
    assertThat(components)
        .containsExactlyInAnyOrder("seed", "combatState", "cosmeticState", "floorVisits");
  }

  @Test
  void aShuffleOfTheCombatStreamRepeatsForTheSameSeed() {
    var a = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8));
    var b = new ArrayList<>(a);

    Collections.shuffle(a, new RandomSource(5).combat());
    Collections.shuffle(b, new RandomSource(5).combat());

    assertThat(a).isEqualTo(b);
  }
}

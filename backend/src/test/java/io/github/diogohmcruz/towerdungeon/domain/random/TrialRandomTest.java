package io.github.diogohmcruz.towerdungeon.domain.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

/** What a Trial's Terms seed fixes, and what stays each Contender's own luck. */
class TrialRandomTest {
  private static final long TERMS = 0xC0FFEEL;

  private static List<Long> draws(RandomGenerator generator) {
    var out = new ArrayList<Long>();
    for (var i = 0; i < 8; i++) {
      out.add(generator.nextLong());
    }
    return out;
  }

  /**
   * The Garrison draws of the first {@code floors} Floors, each arrived at {@code arrivals} times.
   */
  private static List<List<Long>> garrisons(RandomSource source, int floors, int arrivals) {
    var out = new ArrayList<List<Long>>();
    for (var arrival = 0; arrival < arrivals; arrival++) {
      for (var floor = 1; floor <= floors; floor++) {
        out.add(draws(source.openFloor(floor).generation()));
      }
    }
    return out;
  }

  @Test
  void theKthArrivalAtAFloorDrawsTheSameForEveryContender() {
    var alice = RandomSource.forTrial(TERMS, 1);
    var bob = RandomSource.forTrial(TERMS, 2);

    assertThat(garrisons(alice, 30, 3)).isEqualTo(garrisons(bob, 30, 3));
  }

  @Test
  void aReArrivalRerollsTheSameWayForAll() {
    var alice = RandomSource.forTrial(TERMS, 1);
    var bob = RandomSource.forTrial(TERMS, 2);
    var firstA = draws(alice.openFloor(5).generation());
    var secondA = draws(alice.openFloor(5).generation());
    var firstB = draws(bob.openFloor(5).generation());
    var secondB = draws(bob.openFloor(5).generation());

    assertThat(secondA).isNotEqualTo(firstA).isEqualTo(secondB);
    assertThat(firstA).isEqualTo(firstB);
  }

  @Test
  void anotherRaceIsAnotherTower() {
    var one = RandomSource.forTrial(TERMS, 1);
    var other = RandomSource.forTrial(TERMS + 1, 1);

    assertThat(garrisons(one, 10, 1)).isNotEqualTo(garrisons(other, 10, 1));
  }

  @Test
  void whatTheTowerYieldsOnAFloorIsFixedByTheTermsToo() {
    var alice = RandomSource.forTrial(TERMS, 1);
    var bob = RandomSource.forTrial(TERMS, 2);
    alice.openFloor(4);
    bob.openFloor(4);

    assertThat(draws(alice.yield(4))).isEqualTo(draws(bob.yield(4)));
    assertThat(draws(alice.yield(4))).isNotEqualTo(draws(alice.yield(5)));
    // Built a second time, the Floor offers other finds, the same ones to all who build it twice.
    alice.openFloor(4);
    assertThat(draws(alice.yield(4))).isNotEqualTo(draws(bob.yield(4)));
    bob.openFloor(4);
    assertThat(draws(alice.yield(4))).isEqualTo(draws(bob.yield(4)));
  }

  @Test
  void theFoundersAreRolledFromTheTermsAndTheLuckAfterThemIsNot() {
    var alice = RandomSource.forTrial(TERMS, 1);
    var bob = RandomSource.forTrial(TERMS, 2);

    assertThat(draws(alice.founders())).isEqualTo(draws(bob.founders()));
    assertThat(draws(alice.combat())).isNotEqualTo(draws(bob.combat()));
    assertThat(draws(alice.cosmetic())).isNotEqualTo(draws(bob.cosmetic()));
  }

  @Test
  void anOrdinaryCampaignDrawsFoundersAndFindsFromItsCombatStream() {
    var source = new RandomSource(7);

    assertThat(source.isPinned()).isFalse();
    assertThat(source.founders()).isSameAs(source.combat());
    assertThat(source.yield(3)).isSameAs(source.combat());
  }

  @Test
  void aTrialsTowerIsTheTowerOfACampaignWithTheTermsSeedAsItsSeed() {
    var trial = RandomSource.forTrial(TERMS, 99);
    var campaign = new RandomSource(TERMS);

    assertThat(garrisons(trial, 20, 2)).isEqualTo(garrisons(campaign, 20, 2));
  }

  @Test
  void aSavedTrialResumesTheSameTowerAndLuck() {
    var source = RandomSource.forTrial(TERMS, 5);
    source.openFloor(1);
    source.combat().nextLong();

    var restored = RandomSource.restore(source.snapshot());

    assertThat(restored.isPinned()).isTrue();
    assertThat(draws(restored.openFloor(1).generation()))
        .isEqualTo(draws(source.openFloor(1).generation()));
    assertThat(draws(restored.combat())).isEqualTo(draws(source.combat()));
    assertThat(draws(restored.yield(1))).isEqualTo(draws(source.yield(1)));
  }

  @Test
  void aCampaignSaveHasNoTowerSeedOfItsOwn() {
    assertThat(new RandomSource(7).snapshot().towerSeed()).isNull();
    assertThat(RandomSource.forTrial(TERMS, 7).snapshot().towerSeed()).isEqualTo(TERMS);
  }
}

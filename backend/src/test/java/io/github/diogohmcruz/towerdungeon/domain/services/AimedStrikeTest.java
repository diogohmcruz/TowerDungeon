package io.github.diogohmcruz.towerdungeon.domain.services;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class AimedStrikeTest {
  private final GameProperties.Aiming config = new GameProperties().getAiming();

  /** A random source that replays the given doubles in order. */
  private static RandomGenerator script(double... values) {
    return new RandomGenerator() {
      private int next;

      @Override
      public long nextLong() {
        throw new UnsupportedOperationException();
      }

      @Override
      public double nextDouble() {
        return values[next++];
      }
    };
  }

  private static Map<Aim, Double> only(Aim aim) {
    var weights = new EnumMap<Aim, Double>(Aim.class);
    weights.put(aim, 1.0);
    return weights;
  }

  /** Health the part has lost from its maximum. */
  private static double lost(Unit unit, BodyPart kind) {
    return unit.getParts().stream()
        .filter(part -> part.getKind() == kind)
        .findFirst()
        .orElseThrow()
        .missing();
  }

  /** A unit with no weakness to a null attack type, so damage numbers read straight off. */
  private static Unit tank() {
    return new Unit(UnitStats.TANK, cosmetic());
  }

  @Test
  void missChanceStartsAtTheBaseValuesAndShiftsWithAccuracyAndEvasion() {
    var strike = new AimedStrike(script(), config);

    assertThat(strike.missChance(BodyPart.TORSO, 0, 0)).isEqualTo(10);
    assertThat(strike.missChance(BodyPart.LEFT_ARM, 0, 0)).isEqualTo(30);
    assertThat(strike.missChance(BodyPart.RIGHT_LEG, 0, 0)).isEqualTo(30);
    assertThat(strike.missChance(BodyPart.HEAD, 0, 0)).isEqualTo(50);
    assertThat(strike.missChance(BodyPart.HEAD, 10, 0)).isEqualTo(40);
    assertThat(strike.missChance(BodyPart.HEAD, 10, 10)).isEqualTo(50);
  }

  @Test
  void aStrikeBuiltOnACampaignCopyOfTheAimingFollowsItsOverrideAndNotAnotherCampaigns() {
    var campaign = new GameProperties();
    var sandbox = campaign.copy();
    sandbox.getAiming().setHeadMiss(70);
    var other = campaign.copy();

    assertThat(new AimedStrike(script(), sandbox.getAiming()).missChance(BodyPart.HEAD, 0, 0))
        .isEqualTo(70);
    assertThat(new AimedStrike(script(), other.getAiming()).missChance(BodyPart.HEAD, 0, 0))
        .isEqualTo(50);
    assertThat(campaign.getAiming().getHeadMiss()).isEqualTo(50);
  }

  @Test
  void missChanceIsClampedBetweenFiveAndNinety() {
    var strike = new AimedStrike(script(), config);

    assertThat(strike.missChance(BodyPart.TORSO, 100, 0)).isEqualTo(5);
    assertThat(strike.missChance(BodyPart.HEAD, 0, 100)).isEqualTo(90);
  }

  @Test
  void aSinglePartHitPutsAllTheDamageOnThatPart() {
    var target = tank();
    // Aim roll, part roll (0.0 picks the torso), then a miss roll of 0.99 clears its 10%.
    new AimedStrike(script(0.0, 0.0, 0.99), config)
        .strike(target, 4.0, null, only(Aim.PART), 0, 1.0);

    assertThat(lost(target, BodyPart.TORSO)).isCloseTo(4.0, within(1e-9));
    assertThat(target.getMaxHealth() - target.getCurrentHealth()).isCloseTo(4.0, within(1e-9));
  }

  @Test
  void aMissedSinglePartHitDoesNothing() {
    var target = tank();
    new AimedStrike(script(0.0, 0.0, 0.05), config)
        .strike(target, 4.0, null, only(Aim.PART), 0, 1.0);

    assertThat(target.isWounded()).isFalse();
  }

  @Test
  void theVerticalLineSplitsEvenlyOverHeadTorsoAndBothLegs() {
    var target = tank();
    // Aim roll, then one miss roll per part, all clear of every miss chance.
    new AimedStrike(script(0.0, 0.99, 0.99, 0.99, 0.99), config)
        .strike(target, 8.0, null, only(Aim.LINE_V), 0, 1.0);

    for (var kind :
        new BodyPart[] {BodyPart.HEAD, BodyPart.TORSO, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
      assertThat(lost(target, kind)).isCloseTo(2.0, within(1e-9));
    }
    assertThat(lost(target, BodyPart.LEFT_ARM)).isZero();
    assertThat(lost(target, BodyPart.RIGHT_ARM)).isZero();
  }

  @Test
  void theHorizontalLineSplitsEvenlyOverTorsoAndBothArms() {
    var target = tank();
    new AimedStrike(script(0.0, 0.99, 0.99, 0.99), config)
        .strike(target, 6.0, null, only(Aim.LINE_H), 0, 1.0);

    assertThat(lost(target, BodyPart.TORSO)).isCloseTo(2.0, within(1e-9));
    assertThat(lost(target, BodyPart.LEFT_ARM)).isCloseTo(2.0, within(1e-9));
    assertThat(lost(target, BodyPart.RIGHT_ARM)).isCloseTo(2.0, within(1e-9));
    assertThat(lost(target, BodyPart.HEAD)).isZero();
  }

  @Test
  void theBodyAimSplitsEvenlyOverAllSixPartsAndConservesTheDamage() {
    var target = tank();
    new AimedStrike(script(0.0, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99), config)
        .strike(target, 6.0, null, only(Aim.BODY), 0, 1.0);

    for (var part : target.getParts()) {
      assertThat(part.missing()).isCloseTo(1.0, within(1e-9));
    }
    assertThat(target.getMaxHealth() - target.getCurrentHealth()).isCloseTo(6.0, within(1e-9));
  }

  @Test
  void eachShareOfALineRollsItsOwnMiss() {
    var target = tank();
    // The vertical line is head, torso, left leg, right leg: a roll of 0.0 misses, 0.99 hits.
    new AimedStrike(script(0.0, 0.0, 0.99, 0.0, 0.99), config)
        .strike(target, 8.0, null, only(Aim.LINE_V), 0, 1.0);

    assertThat(lost(target, BodyPart.HEAD)).isZero();
    assertThat(lost(target, BodyPart.TORSO)).isCloseTo(2.0, within(1e-9));
    assertThat(lost(target, BodyPart.LEFT_LEG)).isZero();
    assertThat(lost(target, BodyPart.RIGHT_LEG)).isCloseTo(2.0, within(1e-9));
  }

  @Test
  void aLineWithALostPartSplitsOverTheRemainingPartsOfThatAim() {
    var target = tank();
    target.damagePart(BodyPart.LEFT_LEG, 1000);
    // Three parts remain, so three miss rolls. The lost leg also makes the target take 1.25 times.
    new AimedStrike(script(0.0, 0.99, 0.99, 0.99), config)
        .strike(target, 9.0, null, only(Aim.LINE_V), 0, 1.0);

    var share = 9.0 * 1.25 / 3;
    assertThat(lost(target, BodyPart.HEAD)).isCloseTo(share, within(1e-9));
    assertThat(lost(target, BodyPart.TORSO)).isCloseTo(share, within(1e-9));
    assertThat(lost(target, BodyPart.RIGHT_LEG)).isCloseTo(share, within(1e-9));
  }

  @Test
  void aSinglePartHitNeverTriesALostPart() {
    var target = tank();
    target.damagePart(BodyPart.LEFT_ARM, 1000);
    var armsBefore = target.getMaxHealth();
    var strike = new AimedStrike(new SplittableRandom(3), config);
    for (var i = 0; i < 200; i++) {
      strike.strike(target, 0.01, null, only(Aim.PART), 0, 1.0);
    }

    assertThat(target.getMaxHealth()).isEqualTo(armsBefore);
    assertThat(target.isWounded()).isTrue();
  }

  @Test
  void weaknessScalesTheWholeHitBeforeItIsSplit() {
    var warrior = new Unit(UnitStats.WARRIOR, cosmetic());
    // A Warrior takes half from melee.
    new AimedStrike(script(0.0, 0.99, 0.99, 0.99, 0.99, 0.99, 0.99), config)
        .strike(warrior, 12.0, AttackType.MELEE, only(Aim.BODY), 0, 1.0);

    assertThat(warrior.getMaxHealth() - warrior.getCurrentHealth()).isCloseTo(6.0, within(1e-9));
  }

  @Test
  void theHeadBiasWeightsASinglePartAimTowardsTheHead() {
    var strike = new AimedStrike(new SplittableRandom(11), config);
    var headHits = 0;
    var runs = 8000;
    for (var i = 0; i < runs; i++) {
      var target = tank();
      // Accuracy high enough that only the 5% floor can miss; the head weighs 3 against 5 others.
      strike.strike(target, 1.0, null, only(Aim.PART), 100, 3.0);
      if (lost(target, BodyPart.HEAD) > 0) {
        headHits++;
      }
    }

    assertThat(headHits / (double) runs).isCloseTo(3.0 / 8 * 0.95, within(0.03));
  }

  @Test
  void aSeededRunReproducesTheProfileDistribution() {
    var strike = new AimedStrike(new SplittableRandom(5), config);
    var profile = config.profileOf(EnemyStats.DEAD_SOLDIERS); // 50 / 0 / 30 / 20
    var runs = 20000;
    var perPart = new EnumMap<BodyPart, Double>(BodyPart.class);
    for (var i = 0; i < runs; i++) {
      var target = tank();
      strike.strike(target, 1.0, null, profile, 100, 1.0);
      for (var part : target.getParts()) {
        perPart.merge(part.getKind(), part.missing(), Double::sum);
      }
    }

    // Nearly every share lands (5% floor). Per hit: PART 1/6 each, LINE_H 1/3 on torso and arms,
    // BODY 1/6 each.
    var expectedTorso = (0.5 / 6 + 0.3 / 3 + 0.2 / 6) * 0.95;
    var expectedHead = (0.5 / 6 + 0.2 / 6) * 0.95;
    assertThat(perPart.get(BodyPart.TORSO) / runs).isCloseTo(expectedTorso, within(0.01));
    assertThat(perPart.get(BodyPart.HEAD) / runs).isCloseTo(expectedHead, within(0.01));
  }

  @Test
  void everyUnitAndEnemyTypeHasAProfile() {
    for (var type : UnitStats.values()) {
      assertThat(config.getUnitProfiles()).containsKey(type);
    }
    for (var type : EnemyStats.values()) {
      assertThat(config.getEnemyProfiles()).containsKey(type);
    }
  }

  @Test
  void baseAccuracyAndEvasionFollowTheType() {
    assertThat(new Unit(UnitStats.ARCHER, cosmetic()).getAccuracy()).isEqualTo(10);
    assertThat(new Unit(UnitStats.ROGUE, cosmetic()).getAccuracy()).isEqualTo(10);
    assertThat(new Unit(UnitStats.ROGUE, cosmetic()).getEvasion()).isEqualTo(10);
    assertThat(new Unit(UnitStats.WARRIOR, cosmetic()).getAccuracy()).isZero();
    assertThat(new Unit(UnitStats.WARRIOR, cosmetic()).getEvasion()).isZero();
  }

  @Test
  void resolveReportsTheAimAndHowEachPartFared() {
    var target = tank();
    // Line (all of torso and both arms): roll 0.0 picks it; torso hits, left arm misses, right
    // arm hits.
    var result =
        new AimedStrike(script(0.0, 0.99, 0.0, 0.99), config)
            .resolve(target, 3.0, null, only(Aim.LINE_H), 0, 1.0);

    assertThat(result.aim()).isEqualTo(Aim.LINE_H);
    assertThat(result.parts())
        .extracting(AimedStrike.PartOutcome::part, AimedStrike.PartOutcome::hit)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(BodyPart.TORSO, true),
            org.assertj.core.groups.Tuple.tuple(BodyPart.LEFT_ARM, false),
            org.assertj.core.groups.Tuple.tuple(BodyPart.RIGHT_ARM, true));
  }

  @Test
  void aHealIsNotAnAimedStrike() {
    var target = tank();
    var result =
        new AimedStrike(script(), config).resolve(target, -1.0, null, only(Aim.PART), 0, 1.0);

    assertThat(result.aim()).isNull();
    assertThat(result.parts()).isEmpty();
  }
}

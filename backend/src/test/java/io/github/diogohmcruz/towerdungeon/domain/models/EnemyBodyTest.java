package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

class EnemyBodyTest {
  private static final double EPS = 1e-9;
  private static final int ROLLS = 400;

  private final GameProperties config = new GameProperties();

  private Enemy enemy(EnemyStats stats, long seed) {
    return new Enemy(stats, new SplittableRandom(seed), config.getEnemies());
  }

  private static List<Part> present(Enemy enemy) {
    return enemy.getParts().stream().filter(part -> !part.isLost()).toList();
  }

  private static Part part(Enemy enemy, BodyPart kind) {
    return enemy.getParts().stream().filter(p -> p.getKind() == kind).findFirst().orElseThrow();
  }

  /** An enemy of the type with exactly the given limbs (plus torso and head). */
  private Enemy withLimbs(EnemyStats stats, BodyPart... limbs) {
    for (var seed = 0; seed < 5000; seed++) {
      var candidate = new Enemy(stats, new SplittableRandom(seed), config.getEnemies());
      var kinds = present(candidate).stream().map(Part::getKind).toList();
      if (kinds.size() == 2 + limbs.length && kinds.containsAll(List.of(limbs))) {
        return candidate;
      }
    }
    throw new IllegalStateException("no seed gave the requested layout");
  }

  private void assertPartCountsStayInRange(EnemyStats stats, int min, int max) {
    var seen = new java.util.TreeSet<Integer>();
    for (var seed = 0; seed < ROLLS; seed++) {
      var enemy = enemy(stats, seed);
      var parts = present(enemy);
      seen.add(parts.size());
      assertThat(parts.size()).isBetween(min, max);
      assertThat(enemy.hasPart(BodyPart.TORSO)).isTrue();
      assertThat(enemy.hasPart(BodyPart.HEAD)).isEqualTo(parts.size() >= 2);
    }
    assertThat(seen).containsExactlyElementsOf(IntStream.rangeClosed(min, max).boxed().toList());
  }

  @Test
  void slimesAndChymusInsectsAreASingleBody() {
    assertPartCountsStayInRange(EnemyStats.SLIME, 1, 1);
    assertPartCountsStayInRange(EnemyStats.CHYMUS_INSECT, 1, 1);
    assertThat(enemy(EnemyStats.SLIME, 1).getParts()).hasSize(1);
  }

  @Test
  void eachTypeRollsItsConfiguredNumberOfParts() {
    assertPartCountsStayInRange(EnemyStats.DEAD_SOLDIERS, 2, 6);
    assertPartCountsStayInRange(EnemyStats.TOWER_BEAST, 3, 6);
    assertPartCountsStayInRange(EnemyStats.CHIMERA, 4, 6);
    assertPartCountsStayInRange(EnemyStats.NECROMANCER, 6, 6);
    assertPartCountsStayInRange(EnemyStats.STONE_GOLEM, 6, 6);
    assertPartCountsStayInRange(EnemyStats.DEAD_DRAGON, 6, 6);
    assertPartCountsStayInRange(EnemyStats.DRAGON, 6, 6);
  }

  @Test
  void theRangesComeFromConfig() {
    config.getEnemies().getParts().put(EnemyStats.SLIME, new GameProperties.PartRange(3, 3));

    assertThat(present(enemy(EnemyStats.SLIME, 1))).hasSize(3);
  }

  @Test
  void theSameSeedLaysTheSameBodyOut() {
    for (var seed = 0; seed < 50; seed++) {
      assertThat(enemy(EnemyStats.DEAD_SOLDIERS, seed).getParts())
          .isEqualTo(enemy(EnemyStats.DEAD_SOLDIERS, seed).getParts());
    }
  }

  @Test
  void everyLimbSlotIsEventuallyChosen() {
    var limbsSeen = new java.util.HashSet<BodyPart>();
    for (var seed = 0; seed < ROLLS; seed++) {
      present(enemy(EnemyStats.TOWER_BEAST, seed)).forEach(p -> limbsSeen.add(p.getKind()));
    }

    assertThat(limbsSeen).containsExactlyInAnyOrder(BodyPart.values());
  }

  @Test
  void aBossAlwaysHasAllSixPartsWhateverItsArchetype() {
    for (var stats : EnemyStats.values()) {
      for (var seed = 0; seed < 50; seed++) {
        var boss =
            new Enemy(stats, 3.0, 1.25, true, new SplittableRandom(seed), config.getEnemies());
        assertThat(present(boss)).hasSize(6);
      }
    }
  }

  @Test
  void totalHealthIsConservedWhateverTheLayoutAndMultiplier() {
    for (var stats : EnemyStats.values()) {
      for (var seed = 0; seed < 100; seed++) {
        var enemy =
            new Enemy(stats, 3.5, 1.0, false, new SplittableRandom(seed), config.getEnemies());
        assertThat(enemy.getMaxHealth()).isCloseTo(stats.getHealth() * 3.5, within(1e-6));
        assertThat(enemy.getCurrentHealth()).isCloseTo(enemy.getMaxHealth(), within(1e-6));
      }
    }
  }

  @Test
  void aFullBodyIsDividedByTheUnitWeights() {
    var enemy = enemy(EnemyStats.STONE_GOLEM, 1);
    var total = EnemyStats.STONE_GOLEM.getHealth();

    assertThat(part(enemy, BodyPart.TORSO).getMaxHealth()).isCloseTo(total * 0.50, within(EPS));
    assertThat(part(enemy, BodyPart.HEAD).getMaxHealth()).isCloseTo(total * 0.10, within(EPS));
    assertThat(part(enemy, BodyPart.LEFT_ARM).getMaxHealth()).isCloseTo(total * 0.08, within(EPS));
    assertThat(part(enemy, BodyPart.RIGHT_LEG).getMaxHealth()).isCloseTo(total * 0.12, within(EPS));
  }

  @Test
  void theWeightsAreRenormalisedOverThePartsItHas() {
    var enemy = withLimbs(EnemyStats.DEAD_SOLDIERS, BodyPart.LEFT_ARM);
    var total = EnemyStats.DEAD_SOLDIERS.getHealth();
    var weights = 0.50 + 0.10 + 0.08;

    assertThat(part(enemy, BodyPart.TORSO).getMaxHealth())
        .isCloseTo(total * 0.50 / weights, within(EPS));
    assertThat(part(enemy, BodyPart.HEAD).getMaxHealth())
        .isCloseTo(total * 0.10 / weights, within(EPS));
    assertThat(part(enemy, BodyPart.LEFT_ARM).getMaxHealth())
        .isCloseTo(total * 0.08 / weights, within(EPS));
    assertThat(enemy.getMaxHealth()).isCloseTo(total, within(EPS));
  }

  @Test
  void aSingleBodyHoldsAllTheHealthAndDiesAtZero() {
    var slime = enemy(EnemyStats.SLIME, 1);

    assertThat(part(slime, BodyPart.TORSO).getMaxHealth()).isCloseTo(8.0, within(EPS));
    slime.damagePart(BodyPart.TORSO, 7.9);
    assertThat(slime.isDead()).isFalse();
    slime.damagePart(BodyPart.TORSO, 0.1);
    assertThat(slime.isDead()).isTrue();
  }

  @Test
  void aMissingPartIsBornMaimedAndNeverTakesDamage() {
    var soldiers = withLimbs(EnemyStats.DEAD_SOLDIERS);

    assertThat(soldiers.hasPart(BodyPart.LEFT_ARM)).isFalse();
    soldiers.damagePart(BodyPart.LEFT_ARM, 100);
    assertThat(soldiers.getCurrentHealth()).isEqualTo(soldiers.getMaxHealth());
  }

  @Test
  void torsoOrHeadAtZeroKills() {
    var torso = enemy(EnemyStats.STONE_GOLEM, 1);
    torso.damagePart(BodyPart.TORSO, 1e9);
    var head = enemy(EnemyStats.STONE_GOLEM, 1);
    head.damagePart(BodyPart.HEAD, 1e9);
    var leg = enemy(EnemyStats.STONE_GOLEM, 1);
    leg.damagePart(BodyPart.LEFT_LEG, 1e9);

    assertThat(torso.isDead()).isTrue();
    assertThat(head.isDead()).isTrue();
    assertThat(leg.isDead()).isFalse();
  }

  @Test
  void aLimbAtZeroIsLostAndTheTotalsShrink() {
    var golem = enemy(EnemyStats.STONE_GOLEM, 1);
    var before = golem.getMaxHealth();

    golem.damagePart(BodyPart.LEFT_ARM, 1e9);

    assertThat(golem.hasPart(BodyPart.LEFT_ARM)).isFalse();
    assertThat(golem.getMaxHealth()).isCloseTo(before * (1 - 0.08), within(EPS));
  }

  @Test
  void aLostArmHalvesMeleeDamageButNotACastersAndALostLegIncreasesDamageTaken() {
    var golem = enemy(EnemyStats.STONE_GOLEM, 1);
    var full = golem.getEffectiveDamage();
    golem.damagePart(BodyPart.LEFT_ARM, 1e9);
    assertThat(golem.getEffectiveDamage()).isCloseTo(full * 0.5, within(EPS));
    golem.damagePart(BodyPart.RIGHT_ARM, 1e9);
    assertThat(golem.getEffectiveDamage()).isCloseTo(full * 0.25, within(EPS));

    var necromancer = enemy(EnemyStats.NECROMANCER, 1);
    necromancer.damagePart(BodyPart.LEFT_ARM, 1e9);
    assertThat(necromancer.getEffectiveDamage()).isCloseTo(100.0, within(EPS));

    assertThat(golem.lostLegsDamageTakenFactor()).isEqualTo(1.0);
    golem.damagePart(BodyPart.LEFT_LEG, 1e9);
    assertThat(golem.lostLegsDamageTakenFactor()).isCloseTo(1.25, within(EPS));
  }

  @Test
  void aBornMaimedArmAlsoWeakensTheAttack() {
    var soldiers = withLimbs(EnemyStats.DEAD_SOLDIERS, BodyPart.LEFT_LEG);

    assertThat(soldiers.getEffectiveDamage()).isCloseTo(5.0 * 0.5 * 0.5, within(EPS));
    assertThat(soldiers.lostLegsDamageTakenFactor()).isCloseTo(1.25, within(EPS));
  }

  @Test
  void aRestoredEnemyKeepsItsPartsAsTheyWere() {
    var soldiers = withLimbs(EnemyStats.DEAD_SOLDIERS, BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG);
    soldiers.damagePart(BodyPart.TORSO, 1.5);
    soldiers.damagePart(BodyPart.LEFT_ARM, 1e9);

    var restored =
        Enemy.restore(
            soldiers.getId(),
            soldiers.getName(),
            soldiers.getStats(),
            soldiers.healthMultiplier(),
            soldiers.damageMultiplier(),
            soldiers.isBoss(),
            soldiers.getParts().stream()
                .map(
                    p -> {
                      var copy = new Part(p.getKind(), p.getMaxHealth());
                      copy.setCurrentHealth(p.getCurrentHealth());
                      copy.setLost(p.isLost());
                      return copy;
                    })
                .toList(),
            soldiers.isSalted());

    assertThat(restored).isEqualTo(soldiers);
    assertThat(restored.getCurrentHealth()).isEqualTo(soldiers.getCurrentHealth());
    assertThat(restored.getEffectiveDamage()).isEqualTo(soldiers.getEffectiveDamage());
  }

  @Test
  void healingNeverPassesAPartsMaximum() {
    var golem = enemy(EnemyStats.STONE_GOLEM, 1);
    golem.damagePart(BodyPart.TORSO, 10);

    assertThat(golem.heal(1000)).isCloseTo(10, within(EPS));
    assertThat(golem.getCurrentHealth()).isEqualTo(golem.getMaxHealth());
  }
}

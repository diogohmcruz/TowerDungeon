package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import tools.jackson.databind.json.JsonMapper;

class SanityAndPhobiaTest {
  private final GameProperties config = new GameProperties();
  private final GameState state = new GameState(config);

  @Test
  void aRecruitStartsAtFullSanity() {
    var unit = state.recruit(UnitStats.WARRIOR, new SplittableRandom(1));

    assertThat(unit.getMaxSanity()).isEqualTo(100.0);
    assertThat(unit.getSanity()).isEqualTo(100.0);
  }

  @Test
  void sanityIsClampedAtZero() {
    var unit = new Unit(UnitStats.WARRIOR);

    unit.changeSanity(-250);

    assertThat(unit.getSanity()).isZero();
  }

  @Test
  void aGainBeyondFullHealsTheLowestRatioPart() {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.damagePart(BodyPart.TORSO, 10);
    var wounded = unit.getCurrentHealth();
    unit.changeSanity(-3);

    unit.changeSanity(8);

    assertThat(unit.getSanity()).isEqualTo(100.0);
    assertThat(unit.getCurrentHealth()).isEqualTo(wounded + 5, within(1e-9));
  }

  @Test
  void severityRunsFromAQuarterAtFullSanityToOneAtZero() {
    var unit = new Unit(UnitStats.WARRIOR);
    assertThat(unit.getSeverity()).isEqualTo(0.25, within(1e-9));

    unit.changeSanity(-50);
    assertThat(unit.getSeverity()).isEqualTo(0.625, within(1e-9));

    unit.changeSanity(-50);
    assertThat(unit.getSeverity()).isEqualTo(1.0, within(1e-9));
  }

  @Test
  void aPhobicUnitScalesDamageAndTakenOnlyAgainstItsFearedType() {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.addPhobia(EnemyStats.SLIME);

    assertThat(unit.damageFactorAgainst(EnemyStats.SLIME)).isEqualTo(0.875, within(1e-9));
    assertThat(unit.takenFactorFrom(EnemyStats.SLIME)).isEqualTo(1.125, within(1e-9));
    assertThat(unit.damageFactorAgainst(EnemyStats.CHIMERA)).isEqualTo(1.0);
    assertThat(unit.takenFactorFrom(EnemyStats.CHIMERA)).isEqualTo(1.0);

    unit.changeSanity(-100);

    assertThat(unit.damageFactorAgainst(EnemyStats.SLIME)).isEqualTo(0.5, within(1e-9));
    assertThat(unit.takenFactorFrom(EnemyStats.SLIME)).isEqualTo(1.5, within(1e-9));
  }

  @Test
  void aSeededRecruitDrawsTheSamePhobiasEveryTime() {
    var first = state.recruit(UnitStats.WARRIOR, new SplittableRandom(99));
    var second = state.recruit(UnitStats.WARRIOR, new SplittableRandom(99));

    assertThat(first.getPhobias()).isEqualTo(second.getPhobias());
  }

  @Test
  void phobiaCountsFollowFortyFortyTwentyAndTypesAreDistinct() {
    var random = new SplittableRandom(5);
    var counts = new int[3];
    var runs = 6000;
    for (var i = 0; i < runs; i++) {
      var phobias = state.recruit(UnitStats.ARCHER, random).getPhobias();
      counts[phobias.size()]++;
      var types = new HashSet<EnemyStats>();
      phobias.forEach(phobia -> types.add(phobia.getFeared()));
      assertThat(types).hasSize(phobias.size());
      assertThat(phobias).allSatisfy(phobia -> assertThat(phobia.isKnown()).isFalse());
    }

    assertThat(counts[0] / (double) runs).isCloseTo(0.4, within(0.03));
    assertThat(counts[1] / (double) runs).isCloseTo(0.4, within(0.03));
    assertThat(counts[2] / (double) runs).isCloseTo(0.2, within(0.03));
  }

  @Test
  void aFixedStrengthUnitAndTheFoundersDrawNoPhobias() {
    var fixed = new Unit(UnitStats.WARRIOR);
    fixed.setFixedStrengths(true);
    for (var seed = 0; seed < 200; seed++) {
      fixed.rollPhobias(new SplittableRandom(seed));
      assertThat(fixed.getPhobias()).isEmpty();
    }
    for (var founder : config.getStart().getFounders()) {
      for (var seed = 0; seed < 50; seed++) {
        var unit = state.recruitFounder(founder, new SplittableRandom(seed));
        assertThat(unit.getPhobias()).isEmpty();
        assertThat(unit.getSanity()).isEqualTo(100.0);
      }
    }
  }

  @Test
  void aPhobiaShowsNoTypeUntilItIsKnown() throws Exception {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.addPhobia(EnemyStats.CHIMERA);
    var mapper = JsonMapper.builder().build();

    var hidden = mapper.readTree(mapper.writeValueAsString(unit));
    assertThat(hidden.get("sanity").asDouble()).isEqualTo(100.0);
    assertThat(hidden.get("maxSanity").asDouble()).isEqualTo(100.0);
    var phobia = hidden.get("phobias").get(0);
    assertThat(phobia.get("known").asBoolean()).isFalse();
    assertThat(phobia.get("enemyType").isNull()).isTrue();

    unit.getPhobias().getFirst().reveal();

    var shown = mapper.readTree(mapper.writeValueAsString(unit)).get("phobias").get(0);
    assertThat(shown.get("known").asBoolean()).isTrue();
    assertThat(shown.get("enemyType").asString()).isEqualTo("CHIMERA");
  }
}

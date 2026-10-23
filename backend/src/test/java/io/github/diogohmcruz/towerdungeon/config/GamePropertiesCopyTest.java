package io.github.diogohmcruz.towerdungeon.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class GamePropertiesCopyTest {
  private final GameProperties original = new GameProperties();

  @Test
  void aCopyHasEveryValueOfTheOriginal() {
    original.getReward().setCreditPerFloor(7.5);
    original.getAgent().getSandbox().setCallWallTime(Duration.ofMillis(300));

    var copy = original.copy();

    assertThat(copy).isNotSameAs(original).isEqualTo(original);
    assertThat(copy.getAgent().getSandbox().getCallWallTime()).isEqualTo(Duration.ofMillis(300));
  }

  @Test
  void changingTheCopyLeavesTheOriginalAlone() {
    var copy = original.copy();

    copy.getReward().setCreditPerFloor(99.0);
    copy.getLoop().setStarvationDamage(50.0);
    copy.getBoss().setInterval(3);
    copy.getEnemies().setBaseBudget(40.0);
    copy.getEnemies().getParts().get(EnemyStats.SLIME).setMax(5);
    copy.getStart().getFounders().clear();
    copy.getAiming().getHeadBias().put(UnitStats.ARCHER, 9.0);
    copy.getSanity().getPhobiaCountChances().set(0, 1.0);

    assertThat(original).isEqualTo(new GameProperties());
    assertThat(original.getStart().getFounders()).isNotEmpty();
  }

  @Test
  void twoCopiesShareNothingMutable() {
    var one = original.copy();
    var two = original.copy();

    assertThat(one.getReward()).isNotSameAs(two.getReward());
    assertThat(one.getEnemies().getParts()).isNotSameAs(two.getEnemies().getParts());
    assertThat(one.getStart().getFounders()).isNotSameAs(two.getStart().getFounders());
    assertThat(one.getAgent().getSandbox()).isNotSameAs(two.getAgent().getSandbox());
  }

  @Test
  void theBodyWeightsTheEnemiesShareWithTheUnitsStayShared() {
    assertThat(original.getEnemies().getWeights()).isSameAs(original.getBody().getWeights());

    var copy = original.copy();

    assertThat(copy.getEnemies().getWeights()).isSameAs(copy.getBody().getWeights());
    assertThat(copy.getBody().getWeights()).isNotSameAs(original.getBody().getWeights());
  }
}

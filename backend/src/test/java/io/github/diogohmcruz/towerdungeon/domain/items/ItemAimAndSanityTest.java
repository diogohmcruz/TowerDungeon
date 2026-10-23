package io.github.diogohmcruz.towerdungeon.domain.items;

import static io.github.diogohmcruz.towerdungeon.testsupport.Streams.cosmetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.AimedStrike;

class ItemAimAndSanityTest {
  private final GameProperties config = new GameProperties();
  private final ItemRating rating = new ItemRating(config.getItems().getWeights());

  private static Item item(ItemCategory category, ItemStat stat, double value) {
    return new Item("i-" + stat, "i", ItemTier.LEFTOVER, category, Map.of(stat, value), null);
  }

  private Unit wearing(Item item) {
    var unit = new Unit(UnitStats.TANK, cosmetic());
    unit.equip(item, rating);
    return unit;
  }

  /** Strikes the head of a fresh target {@code trials} times; returns how many strikes hit. */
  private int headHits(Unit striker, Supplier<Unit> target, int trials) {
    var strike = new AimedStrike(new SplittableRandom(7), config.getAiming());
    var hits = 0;
    for (var i = 0; i < trials; i++) {
      var victim = target.get();
      var before = victim.getCurrentHealth();
      // A huge head bias makes the single-part aim pick the head every time.
      strike.strike(victim, 1.0, null, Map.of(Aim.PART, 1.0), striker.getAccuracy(), 1e9);
      if (victim.getCurrentHealth() < before) {
        hits++;
      }
    }
    return hits;
  }

  @Test
  void anAccuracyItemAddsItsPointsToAccuracy() {
    var bare = new Unit(UnitStats.TANK, cosmetic());
    var armed = wearing(item(ItemCategory.WEAPON, ItemStat.ACCURACY, 10));
    assertThat(armed.getAccuracy()).isEqualTo(bare.getAccuracy() + 10);
    assertThat(armed.getEvasion()).isEqualTo(bare.getEvasion());
  }

  @Test
  void anEvasionItemAddsItsPointsToEvasion() {
    var bare = new Unit(UnitStats.TANK, cosmetic());
    var helmed = wearing(item(ItemCategory.HELM, ItemStat.EVASION, 10));
    assertThat(helmed.getEvasion()).isEqualTo(bare.getEvasion() + 10);
    assertThat(helmed.getAccuracy()).isEqualTo(bare.getAccuracy());
  }

  @Test
  void accuracyAndEvasionLeaveWithTheDroppedItem() {
    var unit = wearing(item(ItemCategory.WEAPON, ItemStat.ACCURACY, 10));
    unit.dropAll();
    assertThat(unit.getAccuracy()).isEqualTo(new Unit(UnitStats.TANK, cosmetic()).getAccuracy());
  }

  @Test
  void theSanityModifierIsZeroAtFullSanityAndLinearDownToTheConfiguredPenalty() {
    var unit = new Unit(UnitStats.TANK, cosmetic());
    assertThat(unit.getSanityAccuracyModifier()).isZero();
    unit.changeSanity(-50);
    assertThat(unit.getSanityAccuracyModifier()).isCloseTo(-10.0, within(1e-9));
    unit.changeSanity(-50);
    assertThat(unit.getSanityAccuracyModifier()).isCloseTo(-20.0, within(1e-9));
    assertThat(unit.getAccuracy()).isCloseTo(-20.0, within(1e-9));
  }

  @Test
  void theAccuracyPenaltyFollowsTheConfiguration() {
    var rules = new GameProperties.Sanity();
    rules.setAccuracyPenalty(40.0);
    var unit = new Unit(UnitStats.TANK, cosmetic());
    unit.setSanityRules(rules);
    unit.changeSanity(-50);
    assertThat(unit.getSanityAccuracyModifier()).isCloseTo(-20.0, within(1e-9));
  }

  @Test
  void anAccuracyItemLandsMoreSeededHeadshots() {
    var trials = 4000;
    var bare = new Unit(UnitStats.TANK, cosmetic());
    var armed = wearing(item(ItemCategory.WEAPON, ItemStat.ACCURACY, 10));
    var bareHits = headHits(bare, () -> new Unit(UnitStats.TANK, cosmetic()), trials);
    var armedHits = headHits(armed, () -> new Unit(UnitStats.TANK, cosmetic()), trials);
    // Head miss chance is 50, so +10 accuracy lifts the hit rate from 50% to 60%.
    assertThat(bareHits / (double) trials).isCloseTo(0.5, within(0.03));
    assertThat(armedHits / (double) trials).isCloseTo(0.6, within(0.03));
  }

  @Test
  void anEvasionItemMakesEnemyStrikesMissMore() {
    var trials = 4000;
    var bareHits =
        headHits(
            new Unit(UnitStats.TANK, cosmetic()),
            () -> new Unit(UnitStats.TANK, cosmetic()),
            trials);
    var helmed =
        headHits(
            new Unit(UnitStats.TANK, cosmetic()),
            () -> wearing(item(ItemCategory.HELM, ItemStat.EVASION, 10)),
            trials);
    assertThat(bareHits / (double) trials).isCloseTo(0.5, within(0.03));
    assertThat(helmed / (double) trials).isCloseTo(0.4, within(0.03));
  }
}

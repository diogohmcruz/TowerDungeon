package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;

class AffinityTest {
  private final GameProperties config = new GameProperties();
  private final GameState state = new GameState(config);

  private static Item item(ItemCategory category, ItemStat stat, double value) {
    return new Item("i", "i", ItemTier.LEFTOVER, category, Map.of(stat, value), null);
  }

  @Test
  void defaultsAreHalfTheTimeFiveQuarterRatingAndThreeHalvesFinds() {
    var affinity = config.getItems().getAffinity();
    assertThat(affinity.getChance()).isEqualTo(0.5);
    assertThat(affinity.getRatingMultiplier()).isEqualTo(1.25);
    assertThat(affinity.getFindMultiplier()).isEqualTo(1.5);
  }

  @Test
  void aSeededRecruitRollsItsAffinityAfterStrengthsAndPhobias() {
    for (var seed = 0L; seed < 20; seed++) {
      var reference = new SplittableRandom(seed);
      var unit = new Unit(UnitStats.WARRIOR);
      unit.rollStrengths(reference, config.getRecruit().getStrengthSpread());
      unit.setSanityRules(config.getSanity());
      unit.rollPhobias(reference);
      var has = reference.nextDouble() < 0.5;
      var category = has ? ItemCategory.values()[reference.nextInt(5)] : null;

      var recruit = state.recruit(UnitStats.WARRIOR, new SplittableRandom(seed));

      assertThat(recruit.getAffinity()).isEqualTo(category);
    }
  }

  @Test
  void aboutHalfOfRecruitsHaveOneSpreadEvenlyOverTheFiveCategories() {
    var random = new SplittableRandom(11);
    var counts = new EnumMap<ItemCategory, Integer>(ItemCategory.class);
    var none = 0;
    var recruits = 20_000;
    for (var i = 0; i < recruits; i++) {
      var affinity = state.recruit(UnitStats.ARCHER, random).getAffinity();
      if (affinity == null) {
        none++;
      } else {
        counts.merge(affinity, 1, Integer::sum);
      }
    }

    assertThat(none / (double) recruits).isCloseTo(0.5, within(0.02));
    assertThat(counts).hasSize(5);
    counts.values().forEach(n -> assertThat(n / (double) recruits).isCloseTo(0.1, within(0.015)));
  }

  @Test
  void aUnitWithFixedStrengthsGetsNoAffinityAndDrawsNothing() {
    var unit = new Unit(UnitStats.WARRIOR);
    unit.setFixedStrengths(true);
    var random = new SplittableRandom(3);

    unit.rollAffinity(random, 1.0);

    assertThat(unit.getAffinity()).isNull();
    assertThat(random.nextLong()).isEqualTo(new SplittableRandom(3).nextLong());
  }

  @Test
  void foundersHaveNoAffinity() {
    var yuva = new GameProperties.Founder("Yuva", UnitStats.WARRIOR, 1.0, true);
    var plain = new GameProperties.Founder("Kai", UnitStats.WARRIOR, 1.0, false);
    for (var seed = 0; seed < 50; seed++) {
      assertThat(state.recruitFounder(yuva, new SplittableRandom(seed)).getAffinity()).isNull();
      assertThat(state.recruitFounder(plain, new SplittableRandom(seed)).getAffinity()).isNull();
    }
  }

  @Test
  void anAffinityRaisesThatCategorysRatingByAQuarterForThatUnitOnly() {
    var rating = new ItemRating(config.getItems().getWeights());
    var sword = item(ItemCategory.WEAPON, ItemStat.POWER, 4.0);
    var charm = item(ItemCategory.TRINKET, ItemStat.SANITY, 5.0);

    var forWeaponLover = rating.forAffinity(ItemCategory.WEAPON);

    assertThat(forWeaponLover.of(sword)).isCloseTo(rating.of(sword) * 1.25, within(1e-9));
    assertThat(forWeaponLover.of(charm)).isCloseTo(rating.of(charm), within(1e-9));
    assertThat(rating.forAffinity(null).of(sword)).isEqualTo(rating.of(sword));
    assertThat(rating.of(sword)).isEqualTo(4.0);
  }

  @Test
  void aUnitRatesItemsWithItsOwnAffinity() {
    var rating = new ItemRating(config.getItems().getWeights());
    var unit = new Unit(UnitStats.WARRIOR);
    unit.setAffinity(ItemCategory.WEAPON);

    assertThat(unit.ratingOf(item(ItemCategory.WEAPON, ItemStat.POWER, 4.0), rating))
        .isCloseTo(5.0, within(1e-9));
    assertThat(unit.ratingOf(item(ItemCategory.HELM, ItemStat.ARMOUR, 5.0), rating))
        .isCloseTo(4.0, within(1e-9));
  }
}

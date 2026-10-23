package io.github.diogohmcruz.towerdungeon.domain.prestige;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** The Roll's registry, its gates, purchases and exact refunds. */
class RollRulesTest {
  private final GameProperties.Prestige.Roll config = new GameProperties().getPrestige().getRoll();

  private PlayerMeta withLegacy(int legacy) {
    return PlayerMeta.first("secret").withRoll(List.of(), legacy);
  }

  private PlayerMeta buy(PlayerMeta meta, InheritanceType... types) {
    for (var type : types) {
      assertThat(RollRules.whyNotBuy(meta, type, config)).as(type.name()).isNull();
      meta = RollRules.buy(meta, type, config);
    }
    return meta;
  }

  @Test
  void theRegistryHoldsAllSeventeenIdsForSixtyFourLegacy() {
    assertThat(InheritanceType.values()).hasSize(17);
    assertThat(Arrays.stream(InheritanceType.values()).mapToInt(config::costOf).sum())
        .isEqualTo(64);
  }

  @Test
  void onlyTheInheritancesWithAnEffectAreAvailable() {
    var available =
        Arrays.stream(InheritanceType.values()).filter(InheritanceType::isAvailable).toList();
    assertThat(available)
        .containsExactlyInAnyOrder(
            InheritanceType.SEED_GRAIN,
            InheritanceType.MUSTER_ROLL,
            InheritanceType.HARDY_STOCK,
            InheritanceType.WHETSTONE_HABIT,
            InheritanceType.THE_DOWRY,
            InheritanceType.HEAVIER_PACKS,
            InheritanceType.VILLAGE_CHARTER,
            InheritanceType.THE_HEIR,
            InheritanceType.BLOOD_OF_THE_DOOR,
            InheritanceType.HARD_WON_HABITS,
            InheritanceType.THE_OPEN_BREACH,
            InheritanceType.OFFERING_STONE,
            InheritanceType.NARROW_STAIR,
            InheritanceType.OVERREACH,
            InheritanceType.QUARTERMASTERS_TALLY);
  }

  @Test
  void aPurchaseSpendsLegacyAndRecordsWhatWasPaid() {
    var meta = buy(withLegacy(10), InheritanceType.SEED_GRAIN);

    assertThat(meta.legacy()).isEqualTo(9);
    assertThat(meta.roll()).containsExactly(new RollEntry("SEED_GRAIN", 1));
    assertThat(meta.holds(InheritanceType.SEED_GRAIN)).isTrue();
    assertThat(meta.spent()).isEqualTo(1);
  }

  @Test
  void anUnavailableInheritanceIsListedButNeverBought() {
    assertThat(RollRules.whyNotBuy(withLegacy(99), InheritanceType.READER_OF_OMENS, config))
        .isEqualTo(RollRefusal.UNAVAILABLE);
    assertThat(RollRules.whyNotBuy(withLegacy(99), InheritanceType.SECOND_PACT, config))
        .isEqualTo(RollRefusal.UNAVAILABLE);
  }

  @Test
  void youCannotBuyWhatYouCannotAffordOrAlreadyHold() {
    assertThat(RollRules.whyNotBuy(withLegacy(0), InheritanceType.SEED_GRAIN, config))
        .isEqualTo(RollRefusal.INSUFFICIENT_LEGACY);
    var meta = buy(withLegacy(5), InheritanceType.SEED_GRAIN);
    assertThat(RollRules.whyNotBuy(meta, InheritanceType.SEED_GRAIN, config))
        .isEqualTo(RollRefusal.ALREADY_HELD);
  }

  @Test
  void aCourseOpensWhenEnoughLegacyIsAlreadySpentBelowIt() {
    var meta = withLegacy(40);
    assertThat(RollRules.whyNotBuy(meta, InheritanceType.THE_DOWRY, config))
        .isEqualTo(RollRefusal.COURSE_CLOSED);

    meta = buy(meta, InheritanceType.SEED_GRAIN, InheritanceType.MUSTER_ROLL);
    meta = buy(meta, InheritanceType.HARDY_STOCK);
    assertThat(RollRules.spentBelow(meta.roll(), 2)).isEqualTo(4);
    assertThat(RollRules.whyNotBuy(meta, InheritanceType.THE_DOWRY, config))
        .isEqualTo(RollRefusal.COURSE_CLOSED);

    meta = buy(meta, InheritanceType.WHETSTONE_HABIT);
    assertThat(RollRules.spentBelow(meta.roll(), 2)).isEqualTo(6);
    assertThat(RollRules.whyNotBuy(meta, InheritanceType.THE_DOWRY, config)).isNull();
  }

  @Test
  void spendingInTheSameCourseDoesNotOpenIt() {
    var meta = buy(withLegacy(40), InheritanceType.SEED_GRAIN, InheritanceType.MUSTER_ROLL);
    meta = buy(meta, InheritanceType.HARDY_STOCK, InheritanceType.WHETSTONE_HABIT);
    meta = buy(meta, InheritanceType.THE_DOWRY);
    assertThat(RollRules.spentBelow(meta.roll(), 2)).isEqualTo(6);
  }

  @Test
  void removingAnInheritanceRefundsExactlyWhatWasPaid() {
    var meta = buy(withLegacy(10), InheritanceType.HARDY_STOCK);
    assertThat(meta.legacy()).isEqualTo(8);

    assertThat(RollRules.whyNotRefund(meta, "HARDY_STOCK", config)).isNull();
    var refunded = RollRules.refund(meta, "HARDY_STOCK");

    assertThat(refunded.legacy()).isEqualTo(10);
    assertThat(refunded.roll()).isEmpty();
    assertThat(refunded.holds(InheritanceType.HARDY_STOCK)).isFalse();
  }

  @Test
  void aRetunedPriceRefundsWhatWasPaidNotWhatItCostsNow() {
    var meta = buy(withLegacy(10), InheritanceType.HARDY_STOCK);
    config.getCosts().put(InheritanceType.HARDY_STOCK, 5);

    var refunded = RollRules.refund(meta, "HARDY_STOCK");

    assertThat(refunded.legacy()).isEqualTo(10);
  }

  @Test
  void anInheritanceTheRegistryNoLongerKnowsStillRefundsWhatWasPaid() {
    var meta = withLegacy(0).withRoll(List.of(new RollEntry("LONG_GONE", 4)), 1);

    assertThat(RollRules.whyNotRefund(meta, "LONG_GONE", config)).isNull();
    assertThat(RollRules.refund(meta, "LONG_GONE").legacy()).isEqualTo(5);
  }

  @Test
  void onlyAHeldInheritanceCanBeGivenBack() {
    assertThat(RollRules.whyNotRefund(withLegacy(5), "SEED_GRAIN", config))
        .isEqualTo(RollRefusal.NOT_HELD);
  }

  @Test
  void aRefundThatWouldCloseACourseOthersStandOnIsRefusedUntilTheyAreGivenBack() {
    var meta =
        buy(
            withLegacy(40),
            InheritanceType.SEED_GRAIN,
            InheritanceType.MUSTER_ROLL,
            InheritanceType.HARDY_STOCK,
            InheritanceType.WHETSTONE_HABIT,
            InheritanceType.THE_DOWRY);

    assertThat(RollRules.whyNotRefund(meta, "WHETSTONE_HABIT", config))
        .isEqualTo(RollRefusal.HELD_UP);

    meta = RollRules.refund(meta, "THE_DOWRY");
    assertThat(RollRules.whyNotRefund(meta, "WHETSTONE_HABIT", config)).isNull();
    meta = RollRules.refund(meta, "WHETSTONE_HABIT");
    assertThat(meta.legacy()).isEqualTo(40 - 1 - 1 - 2);
  }

  @Test
  void aRefundInCourseTwoCannotCloseCourseThreeEither() {
    config.getGates().set(2, 7);
    var meta =
        buy(
            withLegacy(40),
            InheritanceType.SEED_GRAIN,
            InheritanceType.HARDY_STOCK,
            InheritanceType.MUSTER_ROLL,
            InheritanceType.WHETSTONE_HABIT,
            InheritanceType.THE_DOWRY);
    // A held Course III entry, as a build where it could be had would have left it.
    meta = meta.withRoll(append(meta, new RollEntry("NARROW_STAIR", 5)), meta.legacy());
    assertThat(RollRules.spentBelow(meta.roll(), 3)).isEqualTo(9);

    assertThat(RollRules.whyNotRefund(meta, "THE_DOWRY", config)).isEqualTo(RollRefusal.HELD_UP);
    assertThat(RollRules.whyNotRefund(meta, "SEED_GRAIN", config)).isNull();
  }

  private static List<RollEntry> append(PlayerMeta meta, RollEntry entry) {
    var all = new java.util.ArrayList<>(meta.roll());
    all.add(entry);
    return all;
  }
}

package io.github.diogohmcruz.towerdungeon.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollRules;

/**
 * The pacing table ({@code balance/pacing-table.json}) is complete, climbs and earns monotonically,
 * follows the shape of 3.12, and each reference plan is something the economy of its own Generation
 * pays for and the Roll of its own Generation allows.
 */
class PacingTableTest {
  private final GameProperties props = new GameProperties();
  private final PacingTable table = PacingTable.load();

  @Test
  void holdsOneRowPerGenerationOneToTwelveWithARationaleAndAPlan() {
    assertThat(table.rows()).hasSize(12);
    for (var i = 0; i < 12; i++) {
      var row = table.rows().get(i);
      assertThat(row.generation()).isEqualTo(i + 1);
      assertThat(row.rationale()).as("rationale of %d", i + 1).isNotBlank();
      assertThat(row.plan()).isNotNull();
      assertThat(row.plan().units()).isNotEmpty();
      assertThat(row.plan().aim()).isNotNull();
    }
  }

  @Test
  void theFirstGenerationReachesFloorFiveToSevenAndTheLastTheSummit() {
    assertThat(table.rows().getFirst().highestFloor()).isBetween(5, 7);
    assertThat(table.rows().getLast().highestFloor()).isEqualTo(props.getTower().getMaxFloor());
  }

  @Test
  void floorsRiseStrictlyAndLegacyNeverFalls() {
    for (var i = 1; i < 12; i++) {
      var before = table.rows().get(i - 1);
      var row = table.rows().get(i);
      assertThat(row.highestFloor())
          .as("floor %d", row.generation())
          .isGreaterThan(before.highestFloor());
      assertThat(row.legacy())
          .as("legacy %d", row.generation())
          .isGreaterThanOrEqualTo(before.legacy());
    }
  }

  @Test
  void plansNeverShrinkFromOneGenerationToTheNext() {
    for (var i = 1; i < 12; i++) {
      var before = table.rows().get(i - 1).plan();
      var plan = table.rows().get(i).plan();
      assertThat(plan.partySize()).as("party %d", i + 1).isGreaterThanOrEqualTo(before.partySize());
      assertThat(plan.carryingCapacity())
          .as("capacity %d", i + 1)
          .isGreaterThanOrEqualTo(before.carryingCapacity());
      assertThat(plan.startFloor())
          .as("start %d", i + 1)
          .isGreaterThanOrEqualTo(before.startFloor());
      assertThat(plan.towerMemoryStep())
          .as("memory %d", i + 1)
          .isGreaterThanOrEqualTo(before.towerMemoryStep());
      assertThat(plan.count(UnitStats.PORTER))
          .as("porters %d", i + 1)
          .isGreaterThanOrEqualTo(before.count(UnitStats.PORTER));
      for (var type : UpgradeType.values()) {
        assertThat(plan.level(type))
            .as("%s %d", type, i + 1)
            .isGreaterThanOrEqualTo(before.level(type));
      }
    }
  }

  @Test
  void legacySumsToTheRollsSixtyFourByAboutGenerationNine() {
    var cumulative = 0;
    var reached = 0;
    for (var row : table.rows()) {
      cumulative += row.legacy();
      if (reached == 0 && cumulative >= 64) {
        reached = row.generation();
      }
    }
    assertThat(reached).isBetween(8, 10);
    var upToNine = table.rows().stream().limit(9).mapToInt(PacingTable.Row::legacy).sum();
    assertThat(upToNine).isGreaterThanOrEqualTo(64);
  }

  @Test
  void theRollIsBoughtLegallyWithTheLegacyEarnedAndBloodOfTheDoorFallsInGenerationSixOrSeven() {
    var roll = props.getPrestige().getRoll();
    var meta = PlayerMeta.first("secret");
    var doorGeneration = 0;
    for (var row : table.rows()) {
      if (row.generation() > 1) {
        meta =
            meta.withRoll(
                meta.roll(), meta.legacy() + table.rows().get(row.generation() - 2).legacy());
      }
      for (var type : row.rollBuysAtStart()) {
        assertThat(RollRules.whyNotBuy(meta, type, roll))
            .as("%s in generation %d", type, row.generation())
            .isNull();
        meta = RollRules.buy(meta, type, roll);
        if (type == InheritanceType.BLOOD_OF_THE_DOOR) {
          doorGeneration = row.generation();
        }
      }
      var held =
          new ArrayList<>(meta.roll().stream().map(e -> InheritanceType.valueOf(e.id())).toList());
      assertThat(row.plan().inheritances())
          .as("held in %d", row.generation())
          .containsExactlyElementsOf(held);
    }
    assertThat(doorGeneration).isBetween(6, 7);
    assertThat(table.rows().getLast().plan().inheritances())
        .contains(InheritanceType.BLOOD_OF_THE_DOOR);
    var availableCost =
        java.util.Arrays.stream(InheritanceType.values())
            .filter(InheritanceType::isAvailable)
            .mapToInt(roll::costOf)
            .sum();
    assertThat(meta.roll())
        .hasSize(
            (int)
                java.util.Arrays.stream(InheritanceType.values())
                    .filter(InheritanceType::isAvailable)
                    .count());
    assertThat(meta.roll().stream().mapToInt(e -> e.paid()).sum()).isEqualTo(availableCost);
  }

  @Test
  void towerMemoryStepFollowsTheGenerationUpToTheConfiguredCap() {
    var cap = props.getPrestige().getMemory().getMaxSteps();
    for (var row : table.rows()) {
      assertThat(row.plan().towerMemoryStep()).isEqualTo(Math.min(row.generation() - 1, cap));
    }
  }

  @Test
  void carryingCapacityIsWhatThePlanProvidesAndSuppliesFitInIt() {
    for (var row : table.rows()) {
      var plan = row.plan();
      var expected =
          props.getSupply().getBaseMax()
              + 60 * plan.count(UnitStats.PORTER)
              + 25 * plan.level(UpgradeType.SUPPLY_LINES)
              + table.assumptions().relicLoreCapacity()
              + (plan.inheritances().contains(InheritanceType.HEAVIER_PACKS)
                  ? props.getPrestige().getRoll().getHeavierPacks().getCapacity()
                  : 0);
      assertThat(plan.carryingCapacity()).as("capacity %d", row.generation()).isEqualTo(expected);
      assertThat(plan.supplies()).isLessThanOrEqualTo(plan.carryingCapacity());
    }
  }

  @Test
  void everyPlanKeepsTheFoundersUnlocksItsTypesAndCanBePaidForByItsOwnExpeditions() {
    for (var row : table.rows()) {
      var plan = row.plan();
      var generation = row.generation();
      assertThat(plan.count(UnitStats.WARRIOR) + 0)
          .as("founder warriors %d", generation)
          .isGreaterThanOrEqualTo(3);
      assertThat(plan.count(UnitStats.ARCHER))
          .as("founder archer %d", generation)
          .isGreaterThanOrEqualTo(1);
      for (var unlock : UpgradeType.values()) {
        if (unlock.getUnlockUnit() != null && plan.count(unlock.getUnlockUnit()) > 0) {
          assertThat(plan.level(unlock)).as("%s unlocked in %d", unlock, generation).isEqualTo(1);
        }
      }
      var earned = table.earned(row, props);
      var cost = table.cost(row, props);
      assertThat(cost.materials())
          .as("materials %d", generation)
          .isLessThanOrEqualTo(earned.materials());
      assertThat(cost.relics()).as("relics %d", generation).isLessThanOrEqualTo(earned.relics());
      var sold =
          (earned.materials() - cost.materials()) * props.getReward().getMaterialsSellPrice();
      assertThat(cost.credits())
          .as(
              "credits %d (earned %.0f, plus %.0f from unspent Materials)",
              generation, earned.credits(), sold)
          .isLessThanOrEqualTo(earned.credits() + sold);
    }
  }

  @Test
  void theSpendingOrderOfTheRollHoldsEachRowsInheritancesAndNothingElse() {
    List<InheritanceType> held = new ArrayList<>();
    for (var row : table.rows()) {
      held.addAll(row.rollBuysAtStart());
      assertThat(row.plan().inheritances()).containsExactlyElementsOf(held);
    }
  }
}

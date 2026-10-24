package io.github.diogohmcruz.towerdungeon.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.prestige.InheritanceType;
import io.github.diogohmcruz.towerdungeon.domain.prestige.PlayerMeta;
import io.github.diogohmcruz.towerdungeon.domain.prestige.RollRules;
import io.github.diogohmcruz.towerdungeon.domain.prestige.Tally;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollCalculator;
import io.github.diogohmcruz.towerdungeon.domain.prestige.TollContext;

/**
 * The Toll of each Generation's reference run is the Legacy of the pacing table, within one point
 * (#117): the Tally a Generation reaches is modelled from its plan as {@code PacingTable} models
 * its economy, and read by the real {@link TollCalculator} on the real configuration. The fit and
 * how to rerun it are in {@code docs/research/toll-and-roll-fit.md}.
 */
class PacingTollTest {
  /** 3.10: 4 to 6 seconds a Floor. */
  private static final int SECONDS_PER_FLOOR = 5;

  /** 3.10: 130 to 200 seconds between Expeditions. */
  private static final int SECONDS_BETWEEN_EXPEDITIONS = 165;

  private final GameProperties props = new GameProperties();
  private final PacingTable table = PacingTable.load();

  /**
   * What the Campaign has done when its final push ends at {@code highestFloor}: the Expeditions
   * before it (loot at the config prices, Relics none), the Food its Villagers grew over the
   * Expeditions and the waits between them, and the Guardian Floors from the start Floor to the
   * Highest Floor, all slain in this Campaign.
   */
  private Tally tally(PacingTable.Row row, int highestFloor) {
    var reached =
        new PacingTable.Row(
            row.generation(),
            highestFloor,
            row.legacy(),
            row.rationale(),
            row.rollBuysAtStart(),
            row.plan());
    var found = table.found(reached, props);
    var expeditions = table.assumptions().expeditionsPerGeneration();
    var start = row.plan().startFloor();
    var span = Math.max(1, highestFloor - start);
    var seconds = (long) SECONDS_PER_FLOOR * span; // the final push
    for (var i = 1; i <= expeditions; i++) {
      var cleared = (int) Math.ceil(span * (double) i / (expeditions + 1));
      seconds += (long) SECONDS_PER_FLOOR * cleared + SECONDS_BETWEEN_EXPEDITIONS;
    }
    var villagers =
        row.plan().inheritances().contains(InheritanceType.HARDY_STOCK)
            ? props.getPrestige().getRoll().getHardyStock().getVillagers()
            : props.getVillage().getStartingVillagers();
    var food = villagers * props.getVillage().getVillagerFoodProduction() * seconds;
    var guardians = 0;
    var interval = props.getBoss().getInterval();
    for (var floor = Math.max(start, interval); floor <= highestFloor; floor++) {
      if (floor % interval == 0) {
        guardians++;
      }
    }
    return new Tally(
        food,
        found.materials(),
        found.credits(),
        0.0,
        expeditions,
        highestFloor,
        Math.min(10, guardians));
  }

  /** The Legacy each Generation's reference run pays, residue and New Ground carried along. */
  private List<Integer> legacyPerGeneration() {
    var legacy = new ArrayList<Integer>();
    var residue = 0.0;
    var best = 0;
    for (var row : table.rows()) {
      var tally = tally(row, row.highestFloor());
      var toll =
          TollCalculator.calculate(
              tally, new TollContext(0, 0, residue, best, 0), props.getPrestige());
      legacy.add(toll.legacy());
      residue = toll.residueNext();
      best = Math.max(best, tally.guardiansSlain());
    }
    return legacy;
  }

  @Test
  void eachGenerationsReferenceRunPaysTheTablesLegacyWithinOnePoint() {
    var paid = legacyPerGeneration();
    for (var row : table.rows()) {
      assertThat(paid.get(row.generation() - 1))
          .as("Legacy of generation %d (table %d)", row.generation(), row.legacy())
          .isBetween(row.legacy() - 1, row.legacy() + 1);
    }
  }

  @Test
  void theFirstSevenGenerationsAreTheLegacyOfTheDesignedPacing() {
    var designed = List.of(3, 4, 5, 6, 8, 10, 10);
    for (var i = 0; i < designed.size(); i++) {
      assertThat(table.rows().get(i).legacy())
          .as("generation %d", i + 1)
          .isEqualTo(designed.get(i));
    }
  }

  @Test
  void theFirstGenerationReachingFloorFiveToSevenCanExodus() {
    var first = table.rows().getFirst();
    var minimum = props.getPrestige().getMinPointsToExodus();
    for (var floor = 5; floor <= 7; floor++) {
      var toll =
          TollCalculator.calculate(tally(first, floor), TollContext.NONE, props.getPrestige());
      assertThat(toll.legacy()).as("forecast at Floor %d", floor).isGreaterThanOrEqualTo(minimum);
      assertThat(toll.legacy()).as("forecast at Floor %d", floor).isLessThanOrEqualTo(minimum + 1);
    }
  }

  @Test
  void aCampaignThatNeverClimbsPaysNothingForAscentAndStaysUnderTheExodusGate() {
    var first = table.rows().getFirst();
    var shallow = tally(first, 2);
    var toll = TollCalculator.calculate(shallow, TollContext.NONE, props.getPrestige());
    assertThat(toll.legacy()).isLessThan(props.getPrestige().getMinPointsToExodus());
  }

  @Test
  void theRollIsBoughtInTheTablesOrderOnTheLegacyTheTollPaysBloodOfTheDoorInGenerationSixOrSeven() {
    var roll = props.getPrestige().getRoll();
    var order = table.rows().stream().flatMap(row -> row.rollBuysAtStart().stream()).toList();
    var paid = legacyPerGeneration();
    var meta = PlayerMeta.first("secret");
    var next = 0;
    var doorGeneration = 0;
    var doneGeneration = 0;
    for (var row : table.rows()) {
      if (row.generation() > 1) {
        meta = meta.withRoll(meta.roll(), meta.legacy() + paid.get(row.generation() - 2));
      }
      while (next < order.size() && RollRules.whyNotBuy(meta, order.get(next), roll) == null) {
        if (order.get(next) == InheritanceType.BLOOD_OF_THE_DOOR) {
          doorGeneration = row.generation();
        }
        meta = RollRules.buy(meta, order.get(next), roll);
        next++;
        if (next == order.size()) {
          doneGeneration = row.generation();
        }
      }
      // What the reference plan of the Generation holds is held on the Legacy the Toll paid.
      var held = meta.roll().stream().map(e -> InheritanceType.valueOf(e.id())).toList();
      assertThat(held)
          .as("held in generation %d", row.generation())
          .containsAll(row.plan().inheritances());
    }
    assertThat(doorGeneration).isBetween(6, 7);
    assertThat(doneGeneration).as("the whole available Roll bought").isBetween(8, 9);
  }

  /** Prints the Tally and the Toll of each Generation: {@code -Dpacing.toll=1}. */
  @Test
  @EnabledIfSystemProperty(named = "pacing.toll", matches = ".+")
  void printTheTollOfEachGeneration() {
    var paid = legacyPerGeneration();
    var residue = 0.0;
    var best = 0;
    for (var row : table.rows()) {
      var tally = tally(row, row.highestFloor());
      var toll =
          TollCalculator.calculate(
              tally, new TollContext(0, 0, residue, best, 0), props.getPrestige());
      residue = toll.residueNext();
      best = Math.max(best, tally.guardiansSlain());
      System.out.printf(
          "toll gen %2d floor %3d food %6.0f materials %6.0f credits %6.0f guardians %d | marks"
              + " food %.1f mat %.1f cred %.1f exp %.1f ascent %.1f guard %.1f = %.1f | legacy %d"
              + " (table %d)%n",
          row.generation(),
          row.highestFloor(),
          tally.foodGrown(),
          tally.materialsBanked(),
          tally.creditsBanked(),
          tally.guardiansSlain(),
          toll.foodMarks(),
          toll.materialsMarks(),
          toll.creditsMarks(),
          toll.expeditionsMarks(),
          toll.ascentMarks(),
          toll.guardianMarks(),
          toll.marks(),
          paid.get(row.generation() - 1),
          row.legacy());
    }
    var first = table.rows().getFirst();
    for (var floor = 5; floor <= 7; floor++) {
      var toll =
          TollCalculator.calculate(tally(first, floor), TollContext.NONE, props.getPrestige());
      System.out.printf(
          "toll gen 1 at floor %d: %.1f marks, forecast %d Legacy%n",
          floor, toll.toll(), toll.legacy());
    }
  }
}

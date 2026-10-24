package io.github.diogohmcruz.towerdungeon.balance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * Prints what each Generation's Expeditions bank and what its plan costs: {@code -Dpacing.slack=1}.
 */
@EnabledIfSystemProperty(named = "pacing.slack", matches = ".+")
class PacingSlack {
  @Test
  void slack() {
    var props = new GameProperties();
    var table = PacingTable.load();
    for (var row : table.rows()) {
      var earned = table.earned(row, props);
      var cost = table.cost(row, props);
      var sold =
          (earned.materials() - cost.materials()) * props.getReward().getMaterialsSellPrice();
      System.out.printf(
          "slack gen %d credits earned %.0f + sold %.0f, cost %.0f | materials earned %.0f cost"
              + " %.0f | relics earned %.0f cost %.0f | size %d%n",
          row.generation(),
          earned.credits(),
          sold,
          cost.credits(),
          earned.materials(),
          cost.materials(),
          earned.relics(),
          cost.relics(),
          row.plan().size());
    }
  }
}

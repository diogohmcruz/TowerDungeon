package io.github.diogohmcruz.towerdungeon.domain.models.healing;

import java.util.List;

import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.Village;

/**
 * Heals one wounded unit per tick, cycling through the party in order so everyone gets a fair turn.
 * The cursor persists between ticks, so after a unit is serviced the next tick continues from the
 * following unit.
 */
public class RoundRobinFoodDistributionStrategy implements FoodDistributionStrategy {

  private int cursor = 0;

  @Override
  public double distribute(List<Unit> homeUnits, Village village, FoodHealingConfig config) {
    if (homeUnits.isEmpty() || village.getFood() <= 0 || config.hpBudgetPerTick() <= 0) {
      return 0d;
    }
    var count = homeUnits.size();
    for (var offset = 0; offset < count; offset++) {
      var index = (cursor + offset) % count;
      var unit = homeUnits.get(index);
      if (!unit.isWounded()) {
        continue;
      }
      var affordableHp = village.getFood() / config.costPerHp();
      var budget = Math.min(config.hpBudgetPerTick(), affordableHp);
      if (budget <= 0) {
        return 0d;
      }
      // The unit puts the budget on its lowest-ratio part and never past a part's maximum.
      var heal = unit.heal(budget);
      var spent = heal * config.costPerHp();
      village.takeFood(spent);
      cursor = (index + 1) % count;
      return spent;
    }
    return 0d;
  }
}

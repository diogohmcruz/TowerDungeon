package io.github.diogohmcruz.towerdungeon.domain.models.healing;

import java.util.List;

import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.Village;

/**
 * Decides how the village's food is spent to heal the home party each lifecycle tick.
 *
 * <p>Implementations own whatever cursor/state they need to spread food across ticks (e.g. a
 * round-robin index). Swap the strategy on {@code GameState} to change the distribution policy —
 * proportional, most-wounded-first, even-split, etc. — without touching the game loop.
 */
public interface FoodDistributionStrategy {

  /**
   * Heals wounded units in {@code homeUnits}, drawing food from the {@code village} pantry.
   *
   * @return the amount of food consumed during this tick.
   */
  double distribute(List<Unit> homeUnits, Village village, FoodHealingConfig config);
}

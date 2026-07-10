package io.github.diogohmcruz.towerdungeon.domain.models.healing;

/**
 * Tuning parameters handed to a {@link FoodDistributionStrategy} on every lifecycle tick. Kept as a
 * dedicated value object so new parameters can be added without touching every strategy signature.
 *
 * @param costPerHp food consumed for each point of health restored.
 * @param hpBudgetPerTick maximum health a strategy may restore during a single tick.
 */
public record FoodHealingConfig(double costPerHp, double hpBudgetPerTick) {}

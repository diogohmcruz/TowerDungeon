package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.random.RandomGenerator;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseUnit {
  private final UnitStats stats;

  /** Personal mana reserve (only casters carry one; non-casters keep 0). */
  private double maxMana = 0.0;

  private double currentMana = 0.0;

  /** This unit's health relative to its type's baseline (1.0 = average); rolled at recruit. */
  private double healthFactor = 1.0;

  /** This unit's damage relative to its type's baseline (1.0 = average); rolled at recruit. */
  private double damageFactor = 1.0;

  /**
   * When set, recruiting never rolls this unit: it keeps whatever factors its creator preset
   * (default 1.0). Used for scripted units such as the Founders.
   */
  private boolean fixedStrengths = false;

  public Unit(UnitStats stats) {
    super();
    this.stats = stats;
    this.setMaxHealth(stats.getHealth());
    this.setCurrentHealth(stats.getHealth());
  }

  public Unit(UnitStats stats, String name) {
    super(name);
    this.stats = stats;
    this.setMaxHealth(stats.getHealth());
    this.setCurrentHealth(stats.getHealth());
  }

  /**
   * Sets the health factor and resets maximum and current health to the type's health scaled by it.
   * Meant for a freshly created unit, not one already wounded.
   */
  public void setHealthFactor(double healthFactor) {
    this.healthFactor = healthFactor;
    var scaled = stats.getHealth() * healthFactor;
    this.setMaxHealth(scaled);
    this.setCurrentHealth(scaled);
  }

  /** Damage of one strike before milestone multipliers: the type's damage scaled by the roll. */
  public double getDamage() {
    return stats.getDamage() * damageFactor;
  }

  /**
   * Rolls health and damage factors independently and uniformly in {@code [1 - spread, 1 +
   * spread]}, health first. Does nothing for units with {@link #fixedStrengths} or a non-positive
   * spread.
   */
  public void rollStrengths(RandomGenerator random, double spread) {
    if (fixedStrengths || spread <= 0) {
      return;
    }
    setHealthFactor(random.nextDouble(1 - spread, 1 + spread));
    this.damageFactor = random.nextDouble(1 - spread, 1 + spread);
  }

  /** Whether this unit casts magic (offensive spells or heals) and therefore draws on mana. */
  public boolean isCaster() {
    var type = stats.getAttackType();
    return type == AttackType.MAGIC || type == AttackType.HEAL;
  }

  /**
   * Fills the mana reserve to {@code max} — used when the unit is freshly raised at the village.
   */
  public void chargeToFull(double max) {
    this.maxMana = max;
    this.currentMana = max;
  }

  /** Whether the unit is a caster with enough mana left to pay for one cast. */
  public boolean canCast(double cost) {
    return isCaster() && currentMana >= cost;
  }

  /** Spends {@code cost} mana for a cast, never dropping below empty. */
  public void spendMana(double cost) {
    this.currentMana = Math.max(0, this.currentMana - cost);
  }

  /** Regains {@code amount} mana while recharging at home, capped at the reserve's maximum. */
  public void rechargeMana(double amount) {
    if (maxMana <= 0) {
      return;
    }
    this.currentMana = Math.min(maxMana, this.currentMana + Math.max(0, amount));
  }

  public void receiveAttack(Double attack, AttackType attackType) {
    var attackTypeOrTrueDamage =
        attackType == null ? 1.0 : this.getStats().getWeaknesses().getOrDefault(attackType, 1.0);
    var attackTotal = attack * attackTypeOrTrueDamage;
    super.receiveAttack(attackTotal);
  }

  @Override
  public String toString() {
    return String.format(
        "%s[%s][%.1f/%.1f]",
        this.getName(), this.stats.name(), this.getCurrentHealth(), this.getMaxHealth());
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseUnit {
  private final UnitStats stats;

  /** Personal mana reserve (only casters carry one; non-casters keep 0). */
  private double maxMana = 0.0;

  private double currentMana = 0.0;

  public Unit(UnitStats stats) {
    super();
    this.stats = stats;
    this.setMaxHealth(stats.getHealth());
    this.setCurrentHealth(stats.getHealth());
  }

  /** Whether this unit casts magic (offensive spells or heals) and therefore draws on mana. */
  public boolean isCaster() {
    var type = stats.getAttackType();
    return type == AttackType.MAGIC || type == AttackType.HEAL;
  }

  /** Fills the mana reserve to {@code max} — used when the unit is freshly raised at the village. */
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
        this.getName(), this.stats.name(), this.getCurrentHealth(), this.getStats().getHealth());
  }
}

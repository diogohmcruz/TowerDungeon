package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum UnitStats {
  WARRIOR(4, 20.0, 1.0, AttackType.MELEE),
  ARCHER(8, 10.0, 1.2, AttackType.RANGED),
  MAGE(10, 10.0, 1.5, AttackType.MAGIC),
  HEALER(12, 10.0, 1.5, AttackType.HEAL),
  CAPTAIN(15, 20.0, 2.0, AttackType.MELEE),
  TANK(16, 50.0, 1.0, AttackType.MELEE),
  ROGUE(20, 10.0, 2.0, AttackType.MELEE),
  NECROMANCER(24, 10.0, 2.5, AttackType.MAGIC),
  DRACO_METAMORPH(30, 100.0, 3.0, AttackType.MAGIC);

  private final Integer cost;
  private final Double health;
  private final Double damage;
  private final AttackType attackType;
}

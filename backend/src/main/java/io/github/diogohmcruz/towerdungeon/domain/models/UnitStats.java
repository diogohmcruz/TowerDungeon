package io.github.diogohmcruz.towerdungeon.domain.models;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
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

  Integer cost;
  Double health;
  Double damage;
  AttackType attackType;
}

package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.Map;

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
  WARRIOR(
      20.0,
      1.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 0.5,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 1.0,
          AttackType.HEAL, -1.5),
      3,
      0.0),
  ARCHER(
      10.0,
      1.5,
      AttackType.RANGED,
      Map.of(
          AttackType.MELEE, 1.0,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 1.0,
          AttackType.HEAL, -1.0),
      5,
      0.0),
  MAGE(
      10.0,
      1.5,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 1.0,
          AttackType.RANGED, 1.0,
          AttackType.MAGIC, 0.5,
          AttackType.HEAL, -1.0),
      9,
      0.0),
  HEALER(
      30.0,
      1.5,
      AttackType.HEAL,
      Map.of(
          AttackType.MELEE, 1.5,
          AttackType.RANGED, 1.5,
          AttackType.MAGIC, 1.5,
          AttackType.HEAL, -1.0),
      7,
      0.0),
  CAPTAIN(
      30.0,
      3.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 0.5,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 0.5,
          AttackType.HEAL, -1.0),
      10,
      0.0),
  TANK(
      50.0,
      1.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 0.25,
          AttackType.RANGED, 0.25,
          AttackType.MAGIC, 0.5,
          AttackType.HEAL, -2.0),
      11,
      0.0),
  ROGUE(
      10.0,
      5.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 0.25,
          AttackType.RANGED, 0.25,
          AttackType.MAGIC, 0.25,
          AttackType.HEAL, -5.0),
      12,
      0.0),
  NECROMANCER(
      20.0,
      3.5,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 1.0,
          AttackType.RANGED, 0.25,
          AttackType.MAGIC, 0.0,
          AttackType.HEAL, -1.0),
      15,
      0.0),
  DRACO_METAMORPH(
      100.0,
      10.0,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 0.25,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 0.5,
          AttackType.HEAL, -0.5),
      30,
      0.0),
  PORTER(
      25.0,
      0.5,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 1.0,
          AttackType.RANGED, 1.0,
          AttackType.MAGIC, 1.0,
          AttackType.HEAL, -1.0),
      6,
      60.0);

  Double health;
  Double damage;
  AttackType attackType;
  Map<AttackType, Double> weaknesses;
  Integer cost;
  Double supplyBonus;

  public String getType() {
    return name();
  }
}

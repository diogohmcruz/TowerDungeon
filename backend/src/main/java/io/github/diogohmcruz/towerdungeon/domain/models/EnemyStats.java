package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum EnemyStats {
  SLIME(
      10.0,
      5.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 0.0,
          AttackType.MAGIC, 1.5,
          AttackType.RANGED, 0.5),
      1.0),
  DEAD_SOLDIERS(
      15.0,
      5.0,
      AttackType.MELEE,
      Map.of(
          AttackType.MELEE, 1.5,
          AttackType.MAGIC, 0.5,
          AttackType.RANGED, 1.0),
      1.0),
  CHYMUS_INSECT(
      100.0,
      100.0,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 0.5,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 0.5),
      10.0),
  NECROMANCER(
      200.0,
      100.0,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 0.5,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 0.5),
      20.0),
  DEAD_DRAGON(
      2000.0,
      200.0,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 2.0,
          AttackType.RANGED, 2.0,
          AttackType.MAGIC, 1.0),
      50.0),
  DRAGON(
      5000.0,
      500.0,
      AttackType.MAGIC,
      Map.of(
          AttackType.MELEE, 0.5,
          AttackType.RANGED, 0.5,
          AttackType.MAGIC, 0.5),
      90.0);

  private final Double health;
  private final Double damage;
  private final AttackType attackType;
  private final Map<AttackType, Double> weaknesses;
  private final Double weight;
}

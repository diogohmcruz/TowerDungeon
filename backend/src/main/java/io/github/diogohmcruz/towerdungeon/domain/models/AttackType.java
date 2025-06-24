package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public enum AttackType {
  MELEE(1.0),
  RANGED(1.2),
  MAGIC(1.5),
  HEAL(0.5);

  private final Double multiplier;
}

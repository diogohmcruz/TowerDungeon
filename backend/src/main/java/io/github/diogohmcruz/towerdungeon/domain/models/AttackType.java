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
public enum AttackType {
  MELEE(1.0),
  RANGED(1.2),
  MAGIC(1.5),
  HEAL(0.5);

  Double multiplier;
}

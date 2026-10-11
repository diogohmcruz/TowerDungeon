package io.github.diogohmcruz.towerdungeon.domain.models;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.STRING)
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public enum AttackType {
  MELEE,
  RANGED,
  MAGIC,
  HEAL;
}

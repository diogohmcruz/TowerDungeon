package io.github.diogohmcruz.towerdungeon.domain.models;

/** The six parts of a unit's body. Head and torso are vital; arms and legs can be lost. */
public enum BodyPart {
  TORSO,
  HEAD,
  LEFT_ARM,
  RIGHT_ARM,
  LEFT_LEG,
  RIGHT_LEG;

  /** Whether a unit dies when this part reaches zero. */
  public boolean isVital() {
    return this == TORSO || this == HEAD;
  }

  public boolean isArm() {
    return this == LEFT_ARM || this == RIGHT_ARM;
  }

  public boolean isLeg() {
    return this == LEFT_LEG || this == RIGHT_LEG;
  }
}

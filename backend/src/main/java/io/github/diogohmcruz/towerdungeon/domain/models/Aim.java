package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.List;

/**
 * How an attack is spread over its target's body parts. A {@link #PART} aim picks one of the parts
 * listed; the other aims share the damage evenly over the parts they list.
 */
public enum Aim {
  /** One part takes all the damage. */
  PART(BodyPart.values()),
  /** Head, torso and both legs. */
  LINE_V(BodyPart.HEAD, BodyPart.TORSO, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG),
  /** Torso and both arms. */
  LINE_H(BodyPart.TORSO, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM),
  /** All six parts. */
  BODY(BodyPart.values());

  private final List<BodyPart> parts;

  Aim(BodyPart... parts) {
    this.parts = List.of(parts);
  }

  /** The parts this aim can reach, lost or not. */
  public List<BodyPart> parts() {
    return parts;
  }
}

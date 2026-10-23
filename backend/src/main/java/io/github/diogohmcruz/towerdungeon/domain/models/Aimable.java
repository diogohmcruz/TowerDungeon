package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.List;

import io.github.diogohmcruz.towerdungeon.domain.items.Item;

/**
 * A body that {@link io.github.diogohmcruz.towerdungeon.domain.services.AimedStrike} can aim at: a
 * unit or an enemy. It answers which parts it still has, takes damage on one part and heals.
 */
public interface Aimable {
  /** The attack scaled by this body's weakness to the attack type; negative means it heals. */
  double scaledByWeakness(double attack, AttackType attackType);

  /** The damage-taken multiplier from the legs lost so far. */
  double lostLegsDamageTakenFactor();

  /** Whether the part of this kind is still attached. */
  boolean hasPart(BodyPart kind);

  /**
   * Takes {@code amount} straight off one part.
   *
   * @return the items lost with the part if this blow took it off; an enemy never carries any
   */
  List<Item> damagePart(BodyPart kind, double amount);

  /**
   * Restores up to {@code amount} health to the most hurt parts.
   *
   * @return the health actually restored
   */
  double heal(double amount);

  /** How well this body avoids hits, in percentage points on the attacker's miss chance. */
  double getEvasion();
}

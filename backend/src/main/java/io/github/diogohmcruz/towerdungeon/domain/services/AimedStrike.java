package io.github.diogohmcruz.towerdungeon.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.Aimable;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;

/**
 * Resolves one hit on a unit's body: picks an aim from the attacker's profile, spreads the damage
 * over the parts that aim reaches, and rolls each part's share against its miss chance.
 *
 * <p>The weakness multiplier applies to the whole hit first. A single-part aim that misses does
 * nothing; a line or body aim splits the damage evenly over the parts of that aim still attached
 * and rolls each share on its own. A part already lost is never aimed at: the hit goes to a
 * remaining part, or is split over the remaining parts of the aim, so it cannot whiff for that
 * reason. Environmental damage does not come through here.
 */
public class AimedStrike {
  private final RandomGenerator random;
  private final GameProperties.Aiming config;

  public AimedStrike(RandomGenerator random, GameProperties.Aiming config) {
    this.random = random;
    this.config = config;
  }

  /**
   * The chance, in percent, that a hit on a part of this kind misses: its base value, less the
   * attacker's accuracy, plus the target's evasion, kept within the configured bounds.
   */
  public double missChance(BodyPart kind, double accuracy, double evasion) {
    var chance = config.baseMiss(kind) - accuracy + evasion;
    return Math.min(config.getMaxMiss(), Math.max(config.getMinMiss(), chance));
  }

  /**
   * Strikes {@code target} with {@code damage} of the given type.
   *
   * @param profile weights over the aims; the attacker picks one in proportion
   * @param accuracy the attacker's accuracy in percentage points
   * @param headBias how much likelier the head is than any other part when aiming at one part
   * @return the items the target lost with any part this hit took off
   */
  public List<Item> strike(
      Aimable target,
      double damage,
      AttackType type,
      Map<Aim, Double> profile,
      double accuracy,
      double headBias) {
    var total = target.scaledByWeakness(damage, type);
    if (total < 0) {
      target.heal(-total);
      return List.of();
    }
    total *= target.lostLegsDamageTakenFactor();
    if (total <= 0) {
      return List.of();
    }
    var dropped = new ArrayList<Item>();
    var aim = chooseAim(profile);
    var reachable = aim.parts().stream().filter(target::hasPart).toList();
    if (aim == Aim.PART) {
      var part = choosePart(reachable, headBias);
      land(target, part, total, accuracy, dropped);
      return dropped;
    }
    var share = total / reachable.size();
    for (var part : reachable) {
      land(target, part, share, accuracy, dropped);
    }
    return dropped;
  }

  private void land(
      Aimable target, BodyPart part, double amount, double accuracy, List<Item> dropped) {
    var missChance = missChance(part, accuracy, target.getEvasion());
    if (random.nextDouble() * 100 < missChance) {
      return;
    }
    dropped.addAll(target.damagePart(part, amount));
  }

  private Aim chooseAim(Map<Aim, Double> profile) {
    var sum = 0.0;
    for (var aim : Aim.values()) {
      sum += profile.getOrDefault(aim, 0.0);
    }
    if (sum <= 0) {
      return Aim.PART;
    }
    var roll = random.nextDouble() * sum;
    Aim last = Aim.PART;
    for (var aim : Aim.values()) {
      var weight = profile.getOrDefault(aim, 0.0);
      if (weight <= 0) {
        continue;
      }
      last = aim;
      if (roll < weight) {
        return aim;
      }
      roll -= weight;
    }
    return last;
  }

  private BodyPart choosePart(List<BodyPart> candidates, double headBias) {
    var sum = 0.0;
    for (var part : candidates) {
      sum += part == BodyPart.HEAD ? headBias : 1.0;
    }
    var roll = random.nextDouble() * sum;
    for (var part : candidates) {
      var weight = part == BodyPart.HEAD ? headBias : 1.0;
      if (roll < weight) {
        return part;
      }
      roll -= weight;
    }
    return candidates.getLast();
  }
}

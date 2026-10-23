package io.github.diogohmcruz.towerdungeon.domain.prestige;

import java.util.ArrayList;
import java.util.List;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/**
 * What the Roll lets a player do with Legacy: buy an Inheritance, give one back. Pure functions of
 * the meta and the config, so the service only chooses when to ask.
 *
 * <p>A Course opens when the Legacy already <em>spent</em> in the Courses below it reaches its
 * gate. Spending therefore never hurts, and giving one back is refused when it would close a Course
 * that other held Inheritances stand on.
 */
public final class RollRules {
  private RollRules() {}

  /** Legacy paid for the held Inheritances in Courses strictly below {@code course}. */
  public static int spentBelow(List<RollEntry> roll, int course) {
    var spent = 0;
    for (var entry : roll) {
      var held = InheritanceType.find(entry.id());
      if (held.isPresent() && held.get().course() < course) {
        spent += entry.paid();
      }
    }
    return spent;
  }

  /** Whether the Courses below have been paid into enough for {@code course} to be open. */
  public static boolean courseOpen(
      List<RollEntry> roll, int course, GameProperties.Prestige.Roll config) {
    return spentBelow(roll, course) >= config.gateOf(course);
  }

  /** Why {@code type} cannot be bought now; null when it can. */
  public static RollRefusal whyNotBuy(
      PlayerMeta meta, InheritanceType type, GameProperties.Prestige.Roll config) {
    if (!type.isAvailable()) {
      return RollRefusal.UNAVAILABLE;
    }
    if (meta.holds(type)) {
      return RollRefusal.ALREADY_HELD;
    }
    if (!courseOpen(meta.roll(), type.course(), config)) {
      return RollRefusal.COURSE_CLOSED;
    }
    if (meta.legacy() < config.costOf(type)) {
      return RollRefusal.INSUFFICIENT_LEGACY;
    }
    return null;
  }

  /** The meta after buying {@code type}, which {@link #whyNotBuy} has cleared. */
  public static PlayerMeta buy(
      PlayerMeta meta, InheritanceType type, GameProperties.Prestige.Roll config) {
    var cost = config.costOf(type);
    var roll = new ArrayList<>(meta.roll());
    roll.add(new RollEntry(type.name(), cost));
    return meta.withRoll(roll, meta.legacy() - cost);
  }

  /** Why the held Inheritance {@code id} cannot be given back now; null when it can. */
  public static RollRefusal whyNotRefund(
      PlayerMeta meta, String id, GameProperties.Prestige.Roll config) {
    var held = meta.roll().stream().filter(entry -> entry.id().equals(id)).findFirst();
    if (held.isEmpty()) {
      return RollRefusal.NOT_HELD;
    }
    var remaining = new ArrayList<>(meta.roll());
    remaining.remove(held.get());
    for (var entry : remaining) {
      var type = InheritanceType.find(entry.id());
      if (type.isEmpty()) {
        continue;
      }
      var course = type.get().course();
      if (courseOpen(meta.roll(), course, config) && !courseOpen(remaining, course, config)) {
        return RollRefusal.HELD_UP;
      }
    }
    return null;
  }

  /**
   * The meta after giving {@code id} back, which {@link #whyNotRefund} has cleared: exactly what
   * was paid returns to Legacy, whatever the price is now.
   */
  public static PlayerMeta refund(PlayerMeta meta, String id) {
    var roll = new ArrayList<>(meta.roll());
    var paid = 0;
    for (var entry : meta.roll()) {
      if (entry.id().equals(id)) {
        paid = entry.paid();
        roll.remove(entry);
        break;
      }
    }
    return meta.withRoll(roll, meta.legacy() + paid);
  }
}

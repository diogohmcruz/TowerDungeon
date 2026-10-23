package io.github.diogohmcruz.towerdungeon.domain.observation;

import java.util.ArrayList;
import java.util.List;

/**
 * Running totals of what an Expedition did, for the balance telemetry: damage dealt and taken (by
 * cause), mana spent, Supplies drained, Units and Parts lost, items found. It only counts and
 * decides nothing, is never saved, and every counter only rises, so a reader takes a {@link
 * #snapshot()} before and after a stretch of play and subtracts. Touched only under the Campaign's
 * lock.
 */
public final class ExpeditionMeter {
  /** What was doing the damage when a Unit took it or fell. */
  public enum Cause {
    COMBAT,
    ATTRITION,
    STARVATION,
    OTHER
  }

  /** One Unit lost, with what took it. */
  public record Loss(String type, Cause cause) {}

  /** The totals at one moment. */
  public record Reading(
      double damageDealt,
      double takenCombat,
      double takenAttrition,
      double takenStarvation,
      double manaSpent,
      double suppliesDrained,
      int partsLost,
      int itemsFound,
      int surplusItems,
      int unitsLost) {}

  private double damageDealt;
  private double takenCombat;
  private double takenAttrition;
  private double takenStarvation;
  private double manaSpent;
  private double suppliesDrained;
  private int partsLost;
  private int itemsFound;
  private int surplusItems;
  private final List<Loss> losses = new ArrayList<>();
  private Cause cause = Cause.OTHER;

  /** Damage the Party did to the Garrison. */
  public void dealt(double amount) {
    damageDealt += Math.max(0d, amount);
  }

  /** Health a Unit lost to {@code from}. */
  public void taken(Cause from, double amount) {
    var lost = Math.max(0d, amount);
    switch (from) {
      case COMBAT -> takenCombat += lost;
      case ATTRITION -> takenAttrition += lost;
      case STARVATION -> takenStarvation += lost;
      case OTHER -> {}
    }
  }

  public void manaSpent(double amount) {
    manaSpent += Math.max(0d, amount);
  }

  public void suppliesDrained(double amount) {
    suppliesDrained += Math.max(0d, amount);
  }

  public void partsLost(int count) {
    partsLost += Math.max(0, count);
  }

  public void itemFound() {
    itemsFound++;
  }

  public void surplusItem() {
    surplusItems++;
  }

  /** What the Units that fall from now on are put down to; set around the code that hurts them. */
  public void causeIs(Cause now) {
    this.cause = now;
  }

  public void unitLost(String type) {
    losses.add(new Loss(type, cause));
  }

  public Reading snapshot() {
    return new Reading(
        damageDealt,
        takenCombat,
        takenAttrition,
        takenStarvation,
        manaSpent,
        suppliesDrained,
        partsLost,
        itemsFound,
        surplusItems,
        losses.size());
  }

  /** The Units lost after the first {@code from} losses (the {@code unitsLost} of a reading). */
  public List<Loss> lossesSince(int from) {
    return List.copyOf(losses.subList(Math.min(from, losses.size()), losses.size()));
  }
}

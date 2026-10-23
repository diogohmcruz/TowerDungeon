package io.github.diogohmcruz.towerdungeon.domain.models;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.TallySnapshot;
import lombok.Getter;

/**
 * The Village's Tally: totals that spending never reduces. Every method only ever raises a counter,
 * and non-positive amounts are ignored, so a counter cannot go down by any route.
 */
@Getter
public class CampaignTally {
  private double foodGrown;
  private double creditsBanked;
  private double materialsBanked;
  private double relicsBanked;
  private int floorsCleared;
  private int peakVillagers;
  private int inheritedStartFloor;

  public CampaignTally(int villagers) {
    this.peakVillagers = Math.max(0, villagers);
  }

  public void addFoodGrown(double amount) {
    foodGrown += Math.max(0, amount);
  }

  public void addCreditsBanked(double amount) {
    creditsBanked += Math.max(0, amount);
  }

  public void addMaterialsBanked(double amount) {
    materialsBanked += Math.max(0, amount);
  }

  public void addRelicsBanked(double amount) {
    relicsBanked += Math.max(0, amount);
  }

  public void addFloorsCleared(int floors) {
    floorsCleared += Math.max(0, floors);
  }

  public void notePeakVillagers(int villagers) {
    peakVillagers = Math.max(peakVillagers, villagers);
  }

  /** Notes an Expedition that set out from a Floor the Roll gave; only ever raises it. */
  public void noteInheritedStartFloor(int floor) {
    inheritedStartFloor = Math.max(inheritedStartFloor, floor);
  }

  public TallySnapshot toSnapshot() {
    return new TallySnapshot(
        foodGrown,
        creditsBanked,
        materialsBanked,
        relicsBanked,
        floorsCleared,
        peakVillagers,
        inheritedStartFloor);
  }

  /** Restores saved counters; they only ever rise from here. */
  public void restore(TallySnapshot saved) {
    foodGrown = Math.max(0, saved.foodGrown());
    creditsBanked = Math.max(0, saved.creditsBanked());
    materialsBanked = Math.max(0, saved.materialsBanked());
    relicsBanked = Math.max(0, saved.relicsBanked());
    floorsCleared = Math.max(0, saved.floorsCleared());
    peakVillagers = Math.max(peakVillagers, saved.peakVillagers());
    inheritedStartFloor = Math.max(0, saved.inheritedStartFloor());
  }
}

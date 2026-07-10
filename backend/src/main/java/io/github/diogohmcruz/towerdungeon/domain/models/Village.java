package io.github.diogohmcruz.towerdungeon.domain.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Village {
  @Setter(AccessLevel.NONE)
  private Double food;

  @Setter(AccessLevel.NONE)
  private Integer villagersCount;

  @JsonIgnore private double villagerFoodProduction;
  @JsonIgnore private double unitFoodConsumption;

  public Village(GameProperties.Village config) {
    this.food = config.getStartingFood();
    this.villagersCount = config.getStartingVillagers();
    this.villagerFoodProduction = config.getVillagerFoodProduction();
    this.unitFoodConsumption = config.getUnitFoodConsumption();
  }

  private void setFood(Double food) {
    this.food = Math.max(0, food);
  }

  private void setVillagersCount(Integer villagersCount) {
    this.villagersCount = Math.max(0, villagersCount);
  }

  public boolean triggerLifecycle(Integer unitCount) {
    this.setFood(Math.max(0, this.food + getProductionRate() - upkeepFor(unitCount)));
    return checkStarvation();
  }

  /** Food the villagers grow each tick. */
  public double getProductionRate() {
    return villagersCount * villagerFoodProduction;
  }

  /** Food eaten each tick by {@code unitCount} idle (standby) units back home. */
  public double upkeepFor(int unitCount) {
    return unitCount * unitFoodConsumption;
  }

  /** Removes up to {@code amount} of food from the pantry for an expedition to carry. */
  public double takeFood(double amount) {
    var taken = Math.min(this.food, Math.max(0d, amount));
    setFood(this.food - taken);
    return taken;
  }

  /** Pours food (e.g. an expedition's leftovers) back into the pantry. */
  public void addFood(double amount) {
    setFood(this.food + Math.max(0d, amount));
  }

  public Double buyVillager(Double credits) {
    this.villagersCount++;
    credits -= this.villagersCount;
    return credits;
  }

  public boolean sellFood() {
    if (food > 1) {
      setFood(food - 1);
      return true;
    }
    return false;
  }

  private boolean checkStarvation() {
    return this.food <= 0;
  }

  public void starve() {
    this.setVillagersCount(Math.max(0, this.villagersCount - 1));
  }
}

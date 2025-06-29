package io.github.diogohmcruz.towerdungeon.domain.models;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Village {
  @Setter(AccessLevel.NONE)
  private Double food = 0.1d;

  @Setter(AccessLevel.NONE)
  private Integer villagersCount = 1;

  private final Double VILLAGER_FOOD_PRODUCTION = 0.01;
  private final Double FOOD_CONSUMPTION = 0.01;

  private void setFood(Double food) {
    this.food = Math.max(0, food);
  }

  private void setVillagersCount(Integer villagersCount) {
    this.villagersCount = Math.max(0, villagersCount);
  }

  public boolean triggerLifecycle(Integer unitCount) {
    this.setFood(
        Math.max(
            0,
            this.food
                + (villagersCount * VILLAGER_FOOD_PRODUCTION)
                - (unitCount * VILLAGER_FOOD_PRODUCTION)));
    return checkStarvation();
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

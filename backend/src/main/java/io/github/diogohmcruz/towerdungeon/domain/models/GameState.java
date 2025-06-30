package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class GameState {
  private Double mana = 0d;
  private Double manaPerSecond = 1d;
  private Double credit = 10d;
  private Map<UnitStats, List<Unit>> units = new HashMap<>();
  private Map<UnitStats, List<Unit>> unitsOnTower = new HashMap<>();
  private Tower tower;
  private Village village = new Village();
  private List<String> upgrades = new ArrayList<>();
  private Integer prestigePoints = 0;

  public void triggerLifecycle() {
    mana += manaPerSecond;
    var allUnits = units.values().stream().flatMap(List::stream).toList();
    if (allUnits.isEmpty() && village.getVillagersCount() <= 0) {
      log.error("GAME OVER!");
      return;
    }
    var isStarving = village.triggerLifecycle(allUnits.size());
    if (isStarving) {
      village.starve();
      var randomIndex = ThreadLocalRandom.current().nextInt(allUnits.size());
      var unitToRemove = allUnits.get(randomIndex);
      units.get(unitToRemove.getStats()).remove(unitToRemove);
      log.warn("Village is starving! Unit {} starved!", unitToRemove);
    }
  }

  public void addCredit(Double amount) {
    credit += amount;
  }

  public void addUnits(UnitStats unitStats, List<Unit> newUnits) {
    units.computeIfAbsent(unitStats, _ -> new ArrayList<>()).addAll(newUnits);
  }

  public Integer buyVillager() {
    var villagersCount = village.getVillagersCount();
    while (village.getVillagersCount() < credit) {
      credit = village.buyVillager(credit);
    }
    return villagersCount;
  }

  public Double sellFood() {
    var profit = 0d;
    while (village.sellFood()) {
      profit += 1;
    }
    this.credit += profit;
    return credit;
  }

  public void enlistRandom(UnitStats unitStats) {
    var currentUnits = this.units.getOrDefault(unitStats, new ArrayList<>());
    var randomIndex = ThreadLocalRandom.current().nextInt(currentUnits.size());
    var unitToEnlist = currentUnits.get(randomIndex);
    currentUnits.remove(unitToEnlist);
    units.put(unitStats, currentUnits);
    var currentUnitsOnTower = unitsOnTower.getOrDefault(unitStats, new ArrayList<>());
    currentUnitsOnTower.add(unitToEnlist);
    unitsOnTower.put(unitStats, currentUnitsOnTower);
  }

  public void removeUnit(Unit targetUnit) {
    var currentUnits = unitsOnTower.get(targetUnit.getStats());
    var isRemoved = currentUnits.remove(targetUnit);
    if (isRemoved) {
      unitsOnTower.put(targetUnit.getStats(), currentUnits);
      if (currentUnits.isEmpty()) {
        unitsOnTower.remove(targetUnit.getStats());
      }
    } else {
      log.warn("Unit {} not found on tower. Failed to remove.", targetUnit);
    }
  }

  public boolean hasUnitsOnTower() {
    return unitsOnTower.values().stream().anyMatch(Predicate.not(List::isEmpty));
  }

  public void passingTime(Long interval) {
    Double attack = Double.valueOf(interval) / 1000;
    var unitsToRemove =
        unitsOnTower.values().stream()
            .flatMap(List::stream)
            .peek(unit -> unit.receiveAttack(attack, null))
            .filter(BaseUnit::isDead)
            .toList();
    unitsToRemove.forEach(this::removeUnit);
  }
}

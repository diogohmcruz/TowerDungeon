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
  private Double credit = 0d;
  private Map<UnitStats, List<Unit>> units = new HashMap<>();
  private Map<UnitStats, List<Unit>> unitsOnTower = new HashMap<>();
  private Tower tower;
  private List<String> upgrades = new ArrayList<>();
  private Integer prestigePoints = 0;

  public void updateMana() {
    mana += manaPerSecond;
  }

  public void addCredit(Double amount) {
    credit += amount;
  }

  public void addUnits(UnitStats unitStats, List<Unit> newUnits) {
    units.computeIfAbsent(unitStats, k -> new ArrayList<>()).addAll(newUnits);
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
    unitsOnTower
        .get(targetUnit.getStats())
        .removeIf(unit -> unit.getId().equals(targetUnit.getId()));
  }

  public boolean hasUnitsOnTower() {
    return unitsOnTower.values().stream().anyMatch(Predicate.not(List::isEmpty));
  }

  public void passingTime(Long interval) {
    Double attack = Double.valueOf(interval) / 1000;
    unitsOnTower.forEach(
        (unitStats, unitList) -> {
          var unitsToRemove = new ArrayList<Unit>();
          unitList.forEach(
              unit -> {
                if (unit.getCurrentHealth() <= 0) {
                  log.info("Unit {} has passed away.", unit);
                  unitsToRemove.add(unit);
                } else {
                  unit.receiveAttack(attack);
                }
              });
          unitsToRemove.forEach(this::removeUnit);
        });
  }
}

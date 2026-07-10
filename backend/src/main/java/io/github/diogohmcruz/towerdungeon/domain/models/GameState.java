package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.domain.models.healing.FoodDistributionStrategy;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.FoodHealingConfig;
import io.github.diogohmcruz.towerdungeon.domain.models.healing.RoundRobinFoodDistributionStrategy;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneOffer;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeOffer;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
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
  private Map<ResourceType, Double> resources = ResourceType.emptyWallet();
  private Map<ResourceType, Double> carriedLoot = ResourceType.emptyWallet();
  private Double supplies = 0d;
  private Double maxSupplies = BASE_MAX_SUPPLIES;
  private Double lastFoodReturned = 0d;

  @JsonIgnore
  private Set<UnitStats> unlockedUnits =
      EnumSet.of(UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.PORTER);

  @JsonIgnore private Map<UpgradeType, Integer> purchasedUpgrades = new EnumMap<>(UpgradeType.class);

  private int deepestFloor = 0;
  private int expeditionsCompleted = 0;

  @JsonIgnore
  private Set<MilestoneType> achievedMilestones = EnumSet.noneOf(MilestoneType.class);

  @JsonIgnore
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private FoodDistributionStrategy foodDistributionStrategy =
      new RoundRobinFoodDistributionStrategy();

  private static final double BASE_MAX_SUPPLIES = 100d;
  private static final double HEAL_SUPPLY_COST_PER_HP = 1.0;
  private static final double FOOD_HEAL_HP_PER_TICK = 5.0;

  /**
   * Starts an expedition. Food is a single shared resource: the party draws food out of the village
   * pantry (up to its carrying capacity, which the PORTER unit increases) and carries it as supplies.
   * On extraction the leftovers are poured back into the pantry and the survivors are then healed
   * using the village's food.
   */
  public void startRun() {
    this.maxSupplies = computeSupplyCapacity();
    this.supplies = village.takeFood(this.maxSupplies);
    this.carriedLoot = ResourceType.emptyWallet();
    this.lastFoodReturned = 0d;
  }

  /**
   * Carrying capacity of the party currently on the tower: a base amount plus the supply bonus of
   * every PORTER in the party. Because it is derived from the live party, capacity shrinks as soon as
   * a porter dies.
   */
  private double computeSupplyCapacity() {
    var supplyBonus =
        unitsOnTower.entrySet().stream()
            .mapToDouble(entry -> entry.getKey().getSupplyBonus() * entry.getValue().size())
            .sum();
    return BASE_MAX_SUPPLIES + getSupplyCapacityBonus() + supplyBonus;
  }

  /**
   * Recomputes the carrying capacity from the surviving party (e.g. after a porter dies) and spills
   * any food that can no longer be carried — the dead porter's load is lost with them.
   */
  public void recalculateSupplyCapacity() {
    if (tower == null) {
      return;
    }
    this.maxSupplies = computeSupplyCapacity();
    if (this.supplies > this.maxSupplies) {
      this.supplies = this.maxSupplies;
    }
  }

  /** Number of units in the party currently on the tower. */
  public int getTowerPartySize() {
    return (int) unitsOnTower.values().stream().flatMap(List::stream).count();
  }

  /** Pours any food the party still carries back into the village pantry after a successful run. */
  public void returnLeftoverSuppliesToVillage() {
    this.lastFoodReturned = this.supplies;
    village.addFood(this.supplies);
    this.supplies = 0d;
  }

  /**
   * Heals the wounded units resting at home, spending the village's food. Runs every lifecycle tick
   * and delegates the actual "who gets fed" decision to the configured {@link
   * FoodDistributionStrategy}, so different policies (round-robin, proportional, most-wounded-first,
   * …) can be swapped in without changing the game loop.
   */
  public void healHomePartyWithFood() {
    var homeUnits = units.values().stream().flatMap(List::stream).toList();
    foodDistributionStrategy.distribute(
        homeUnits, village, new FoodHealingConfig(HEAL_SUPPLY_COST_PER_HP, FOOD_HEAL_HP_PER_TICK));
  }

  /** Total missing health across the units resting at home (the ones the village feeds/heals). */
  public double getHomePartyWounds() {
    double total = 0d;
    for (var entry : units.entrySet()) {
      var maxHealth = entry.getKey().getHealth();
      for (Unit unit : entry.getValue()) {
        var missing = maxHealth - unit.getCurrentHealth();
        if (missing > 0) {
          total += missing;
        }
      }
    }
    return total;
  }

  /** Health restored per lifecycle tick when the village has food to spare. */
  public double getFoodHealRate() {
    return FOOD_HEAL_HP_PER_TICK;
  }

  /** Number of idle units back home (excludes those on an expedition). */
  public int getHomeUnitCount() {
    return (int) units.values().stream().flatMap(List::stream).count();
  }

  /** Food eaten per tick by idle units standing by at home. */
  public double getFoodUpkeep() {
    return village.upkeepFor(getHomeUnitCount());
  }

  /** Net food change per tick: villager production minus standby unit upkeep. */
  public double getNetFoodPerSecond() {
    return village.getProductionRate() - getFoodUpkeep();
  }

  public void gatherLoot(ResourceType type, Double amount) {
    carriedLoot.merge(type, amount, Double::sum);
  }

  public void drainSupplies(Double amount) {
    this.supplies = Math.max(0d, this.supplies - amount);
  }

  public boolean hasSupplies() {
    return supplies > 0;
  }

  public void bankLoot() {
    carriedLoot.forEach((type, amount) -> resources.merge(type, amount, Double::sum));
    this.carriedLoot = ResourceType.emptyWallet();
  }

  public void forfeitLoot() {
    this.carriedLoot = ResourceType.emptyWallet();
  }

  public void returnPartyHome() {
    unitsOnTower.forEach(
        (stats, survivors) ->
            units.computeIfAbsent(stats, _ -> new ArrayList<>()).addAll(survivors));
    unitsOnTower.clear();
  }

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
    healHomePartyWithFood();
  }

  public void addCredit(Double amount) {
    credit += amount;
  }

  public void addUnits(UnitStats unitStats, List<Unit> newUnits) {
    units.computeIfAbsent(unitStats, _ -> new ArrayList<>()).addAll(newUnits);
  }

  /** Whether the given unit type has been unlocked and can be recruited. */
  public boolean isUnitUnlocked(UnitStats unitStats) {
    return unlockedUnits.contains(unitStats);
  }

  /** Names of the unit types the player has unlocked so far, for the frontend hire list. */
  public List<String> getUnlockedUnitTypes() {
    return unlockedUnits.stream().map(UnitStats::name).sorted().toList();
  }

  private int upgradeLevel(UpgradeType type) {
    return purchasedUpgrades.getOrDefault(type, 0);
  }

  /** Extra carrying capacity granted by the SUPPLY_LINES upgrade and any capacity milestones. */
  public double getSupplyCapacityBonus() {
    var upgradeBonus =
        upgradeLevel(UpgradeType.SUPPLY_LINES) * UpgradeType.SUPPLY_LINES.getCapacityPerLevel();
    var milestoneBonus =
        achievedMilestones.stream().mapToDouble(MilestoneType::getCapacityBonus).sum();
    return upgradeBonus + milestoneBonus;
  }

  /**
   * Multiplier applied to party damage, from the SHARPENED_ARMS upgrade and any damage milestones
   * (1.0 = no bonus).
   */
  public double getDamageMultiplier() {
    var upgradeBonus =
        upgradeLevel(UpgradeType.SHARPENED_ARMS)
            * UpgradeType.SHARPENED_ARMS.getDamageBonusPerLevel();
    var milestoneBonus =
        achievedMilestones.stream().mapToDouble(MilestoneType::getDamageBonus).sum();
    return 1.0 + upgradeBonus + milestoneBonus;
  }

  private boolean canAfford(Map<ResourceType, Double> cost) {
    return cost.entrySet().stream()
        .allMatch(entry -> resources.getOrDefault(entry.getKey(), 0d) >= entry.getValue());
  }

  private void spend(Map<ResourceType, Double> cost) {
    cost.forEach((type, amount) -> resources.merge(type, -amount, Double::sum));
  }

  /**
   * Attempts to buy the next level of an upgrade using banked resources. Returns {@code true} on
   * success. Unit-unlock upgrades add the unit to the roster; repeatable upgrades bump their level
   * (and their bonuses) and grow more expensive.
   */
  public boolean applyUpgrade(UpgradeType type) {
    var level = upgradeLevel(type);
    if (!type.isAvailableAt(level)) {
      return false;
    }
    var cost = type.costAtLevel(level);
    if (!canAfford(cost)) {
      return false;
    }
    spend(cost);
    purchasedUpgrades.put(type, level + 1);
    if (type.getUnlockUnit() != null) {
      unlockedUnits.add(type.getUnlockUnit());
    }
    if (tower != null) {
      recalculateSupplyCapacity();
    } else {
      this.maxSupplies = computeSupplyCapacity();
    }
    return true;
  }

  /** Live catalog of upgrades with current level and next-purchase cost, streamed to the client. */
  public List<UpgradeOffer> getAvailableUpgrades() {
    var offers = new ArrayList<UpgradeOffer>();
    for (UpgradeType type : UpgradeType.values()) {
      var level = upgradeLevel(type);
      var maxed = !type.isAvailableAt(level);
      offers.add(
          new UpgradeOffer(
              type.name(),
              type.getDisplayName(),
              type.getDescription(),
              level,
              maxed,
              type.isRepeatable(),
              type.getUnlockUnit() == null ? null : type.getUnlockUnit().name(),
              maxed ? Map.of() : type.costAtLevel(level)));
    }
    return offers;
  }

  /** Records the deepest floor the party has reached and grants any newly earned milestones. */
  public void recordFloorReached(int floor) {
    if (floor > deepestFloor) {
      deepestFloor = floor;
    }
    checkMilestones();
  }

  /** Marks an expedition as finished (extract or wipe) and grants any newly earned milestones. */
  public void completeExpedition() {
    expeditionsCompleted++;
    checkMilestones();
  }

  /**
   * Grants every milestone whose condition is now satisfied. Story-unit milestones add the companion
   * to the roster; buff milestones take effect immediately via the capacity/damage getters.
   */
  private void checkMilestones() {
    for (MilestoneType milestone : MilestoneType.values()) {
      if (achievedMilestones.contains(milestone)) {
        continue;
      }
      if (milestone.isSatisfied(deepestFloor, expeditionsCompleted)) {
        achievedMilestones.add(milestone);
        if (milestone.getUnlockUnit() != null) {
          unlockedUnits.add(milestone.getUnlockUnit());
        }
        log.info("Milestone reached: {}", milestone.getDisplayName());
      }
    }
    if (tower != null) {
      recalculateSupplyCapacity();
    }
  }

  /** Story milestones with their unlock condition and whether they have been achieved. */
  public List<MilestoneOffer> getMilestones() {
    var offers = new ArrayList<MilestoneOffer>();
    for (MilestoneType milestone : MilestoneType.values()) {
      offers.add(
          new MilestoneOffer(
              milestone.name(),
              milestone.getDisplayName(),
              milestone.getDescription(),
              milestone.getTriggerLabel(),
              achievedMilestones.contains(milestone)));
    }
    return offers;
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
      recalculateSupplyCapacity();
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

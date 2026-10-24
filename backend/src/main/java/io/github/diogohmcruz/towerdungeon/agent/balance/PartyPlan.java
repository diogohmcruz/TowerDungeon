package io.github.diogohmcruz.towerdungeon.agent.balance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.github.diogohmcruz.towerdungeon.domain.models.Aim;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;

/**
 * Everything a balance tool says about a Party, with nothing of the Village behind it: who goes,
 * what Upgrades the Campaign holds, what the Party carries, and what the Tower has become by
 * Generation and by Guardians slain. The floor duel harness ({@link FloorDuel}) and the pacing
 * table both read it, so each reference plan of a Generation is one value.
 *
 * <p>Every field is optional in the sense that its default is the game as a first Generation finds
 * it: no Upgrades, full Supplies, the Carrying Capacity the Party and Upgrades give, no Tower
 * Memory, no Easing and the Units' own Aim.
 *
 * @param units how many of each Unit type set out; each is raised as a recruit of the Campaign
 * @param upgrades levels held by Upgrade (a Unit-unlock Upgrade is 1 or absent); they only act
 *     through what the game derives from them (Supply Lines, Sharpened Arms)
 * @param carryingCapacity the Party's Carrying Capacity at the start, or null for what the base,
 *     Supply Lines and the Porters give. Fixed by moving the base capacity, so a Porter that falls
 *     still takes its share away
 * @param supplies the Supplies carried at the start (capped by the Carrying Capacity), or null for
 *     a full load
 * @param saltedSupplies how many of the Supplies are salted (the Loadout's Salt), at most all
 * @param towerMemoryStep the Generation's Tower Memory step {@code m}: 0 for Generation 1, one more
 *     for each Generation after it, up to the configured maximum (as {@code min(generation - 1,
 *     maxSteps)} is)
 * @param easing the Guardian Floors slain so far in the Campaign, each thinning every Floor below
 *     it as in the game (the Easing of the Campaign, which is forgotten each Generation)
 * @param aim Aim weights that replace the configured ones of a Unit type, or none
 * @param founders whether the Party's first Warriors and Archer are the Campaign's Founders (the
 *     configured start kit, Generation 1's stronger Yuva among them) and not ordinary recruits; the
 *     rest of each type are recruits
 * @param risen Risen already standing on the Floor dueled, as a Wipe there leaves them (the
 *     garrison of a Floor the Party has failed on before)
 */
public record PartyPlan(
    Map<UnitStats, Integer> units,
    Map<UpgradeType, Integer> upgrades,
    Double carryingCapacity,
    Double supplies,
    double saltedSupplies,
    int towerMemoryStep,
    Set<Integer> easing,
    Map<UnitStats, Map<Aim, Double>> aim,
    int risen,
    boolean founders) {

  public PartyPlan {
    units = ordered(units, UnitStats.class);
    upgrades = ordered(upgrades, UpgradeType.class);
    easing = easing == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(easing));
    var aims = new EnumMap<UnitStats, Map<Aim, Double>>(UnitStats.class);
    if (aim != null) {
      aim.forEach((type, weights) -> aims.put(type, Map.copyOf(weights)));
    }
    aim = Collections.unmodifiableMap(aims);
    if (towerMemoryStep < 0 || saltedSupplies < 0 || risen < 0) {
      throw new IllegalArgumentException("A step or a quantity of Salt cannot be negative");
    }
  }

  /** A first-Generation Party of these Units, with nothing else. */
  public static PartyPlan of(Map<UnitStats, Integer> units) {
    return new PartyPlan(units, Map.of(), null, null, 0, 0, Set.of(), Map.of(), 0, false);
  }

  /** A Party of {@code count} Units of one type. */
  public static PartyPlan of(UnitStats type, int count) {
    return of(Map.of(type, count));
  }

  /** The Party's size. */
  public int size() {
    return units.values().stream().mapToInt(Integer::intValue).sum();
  }

  public PartyPlan withUnits(Map<UnitStats, Integer> now) {
    return copy(
        now,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withUpgrades(Map<UpgradeType, Integer> now) {
    return copy(
        units,
        now,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withCarryingCapacity(Double now) {
    return copy(
        units,
        upgrades,
        now,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withSupplies(Double now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        now,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withSaltedSupplies(double now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        now,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withTowerMemoryStep(int now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        now,
        easing,
        aim,
        risen,
        founders);
  }

  public PartyPlan withEasing(Set<Integer> now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        now,
        aim,
        risen,
        founders);
  }

  public PartyPlan withAim(Map<UnitStats, Map<Aim, Double>> now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        now,
        risen,
        founders);
  }

  public PartyPlan withRisen(int now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        now,
        founders);
  }

  public PartyPlan withFounders(boolean now) {
    return copy(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        now);
  }

  private static PartyPlan copy(
      Map<UnitStats, Integer> units,
      Map<UpgradeType, Integer> upgrades,
      Double carryingCapacity,
      Double supplies,
      double saltedSupplies,
      int towerMemoryStep,
      Set<Integer> easing,
      Map<UnitStats, Map<Aim, Double>> aim,
      int risen,
      boolean founders) {
    return new PartyPlan(
        units,
        upgrades,
        carryingCapacity,
        supplies,
        saltedSupplies,
        towerMemoryStep,
        easing,
        aim,
        risen,
        founders);
  }

  private static <K extends Enum<K>> Map<K, Integer> ordered(Map<K, Integer> in, Class<K> type) {
    var out = new EnumMap<K, Integer>(type);
    if (in != null) {
      in.forEach(
          (key, value) -> {
            if (value != null && value < 0) {
              throw new IllegalArgumentException("A count or a level cannot be negative");
            }
            if (value != null && value > 0) {
              out.put(key, value);
            }
          });
    }
    return Collections.unmodifiableMap(out);
  }
}

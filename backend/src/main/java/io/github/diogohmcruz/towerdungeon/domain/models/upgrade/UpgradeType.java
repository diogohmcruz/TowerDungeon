package io.github.diogohmcruz.towerdungeon.domain.models.upgrade;

import java.util.EnumMap;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import lombok.Getter;

/**
 * Permanent, resource-funded upgrades bought with loot banked from tower runs. Unit-unlock upgrades
 * add a new recruit to the roster ("a new character joins the story"); repeatable upgrades scale a
 * numeric bonus and grow in cost each level.
 */
@Getter
public enum UpgradeType {
  UNLOCK_HEALER(
      "Field Medic joins",
      "Recruit the Healer to mend the party mid-delve.",
      Map.of(ResourceType.MATERIALS, 40d),
      UnitStats.HEALER,
      false,
      1.0,
      0d,
      0d),
  UNLOCK_TANK(
      "Ironclad joins",
      "Recruit the Tank to soak the tower's blows.",
      Map.of(ResourceType.MATERIALS, 85d),
      UnitStats.TANK,
      false,
      1.0,
      0d,
      0d),
  UNLOCK_ROGUE(
      "Shadow joins",
      "Recruit the Rogue, a glass-cannon assassin.",
      Map.of(ResourceType.MATERIALS, 110d),
      UnitStats.ROGUE,
      false,
      1.0,
      0d,
      0d),
  UNLOCK_NECROMANCER(
      "The Necromancer joins",
      "Bind the Necromancer with a deep-tower relic.",
      Map.of(ResourceType.RELICS, 3d),
      UnitStats.NECROMANCER,
      false,
      1.0,
      0d,
      0d),
  UNLOCK_DRACO_METAMORPH(
      "The Draco-Metamorph joins",
      "Awaken the Draco-Metamorph, the tower's own power turned against it.",
      Map.of(ResourceType.RELICS, 8d),
      UnitStats.DRACO_METAMORPH,
      false,
      1.0,
      0d,
      0d),
  SUPPLY_LINES(
      "Supply Lines",
      "Raise base carrying capacity by 25 food per level.",
      Map.of(ResourceType.MATERIALS, 30d),
      null,
      true,
      1.6,
      25d,
      0d),
  SHARPENED_ARMS(
      "Sharpened Arms",
      "Increase party damage by 10% per level.",
      Map.of(ResourceType.MATERIALS, 35d),
      null,
      true,
      1.7,
      0d,
      0.10);

  private final String displayName;
  private final String description;
  private final Map<ResourceType, Double> baseCost;
  private final UnitStats unlockUnit;
  private final boolean repeatable;
  private final double costGrowth;
  private final double capacityPerLevel;
  private final double damageBonusPerLevel;

  UpgradeType(
      String displayName,
      String description,
      Map<ResourceType, Double> baseCost,
      UnitStats unlockUnit,
      boolean repeatable,
      double costGrowth,
      double capacityPerLevel,
      double damageBonusPerLevel) {
    this.displayName = displayName;
    this.description = description;
    this.baseCost = baseCost;
    this.unlockUnit = unlockUnit;
    this.repeatable = repeatable;
    this.costGrowth = costGrowth;
    this.capacityPerLevel = capacityPerLevel;
    this.damageBonusPerLevel = damageBonusPerLevel;
  }

  /**
   * Cost to buy the next level given how many levels are already owned. Non-repeatable upgrades
   * keep a flat cost; repeatable ones scale by {@link #costGrowth} per owned level.
   */
  public Map<ResourceType, Double> costAtLevel(int ownedLevel) {
    var factor = repeatable ? Math.pow(costGrowth, ownedLevel) : 1.0;
    var cost = new EnumMap<ResourceType, Double>(ResourceType.class);
    baseCost.forEach((type, amount) -> cost.put(type, Math.ceil(amount * factor)));
    return cost;
  }

  /** Whether this upgrade can still be bought given how many levels are already owned. */
  public boolean isAvailableAt(int ownedLevel) {
    return repeatable || ownedLevel < 1;
  }
}

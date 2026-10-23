package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.EquipResult;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemEffect;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.Loadout;
import io.github.diogohmcruz.towerdungeon.domain.items.Slot;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseUnit implements Aimable {
  private final UnitStats stats;

  /** Personal mana reserve (only casters carry one; non-casters keep 0), before items add. */
  @Getter(AccessLevel.NONE)
  private double maxMana = 0.0;

  @Getter(AccessLevel.NONE)
  private double currentMana = 0.0;

  /** This unit's health relative to its type's baseline (1.0 = average); rolled at recruit. */
  private double healthFactor = 1.0;

  /** This unit's damage relative to its type's baseline (1.0 = average); rolled at recruit. */
  private double damageFactor = 1.0;

  /**
   * When set, recruiting never rolls this unit: it keeps whatever factors its creator preset
   * (default 1.0). Used for scripted units such as the Founders.
   */
  private boolean fixedStrengths = false;

  /** The item category this unit has a knack for; rolled at recruit, {@code null} for none. */
  private ItemCategory affinity;

  /** The Party's Panacea, while it is carried and unspent; shared with the other Units. */
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  private Panacea panacea;

  /** Health below this counts as zero, absorbing floating-point drift from proportional hits. */
  static final double EPSILON = 1e-9;

  /** Damage multiplier for each arm lost (casters are unaffected). */
  static final double LOST_ARM_DAMAGE_FACTOR = 0.5;

  /** Damage-taken multiplier for each leg lost. */
  static final double LOST_LEG_DAMAGE_TAKEN_FACTOR = 1.25;

  /** Share of a hit that always lands on a part, however much armour it wears. */
  private static final double ARMOUR_MIN_HIT_SHARE = 0.2;

  /** What the unit wears. Items come in through {@link #equip} and leave only as drops. */
  private final Loadout gear = new Loadout();

  /**
   * The unit's six body parts. Their present members' health sums are the unit's {@link
   * #getCurrentHealth()} and {@link #getMaxHealth()}.
   */
  @Getter(AccessLevel.NONE)
  private final List<Part> parts = new ArrayList<>();

  /** How the rolled total is divided among the parts; kept so a re-roll lays the body out again. */
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  private GameProperties.Body.Weights bodyWeights = new GameProperties.Body.Weights();

  /** How sanity gains, severity and phobia draws are tuned; supplied from config at recruit. */
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  private GameProperties.Sanity sanityRules = new GameProperties.Sanity();

  /** The numbers behind the special effects of the items this unit wears; supplied from config. */
  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  private GameProperties.Items.Effects itemEffects = new GameProperties.Items.Effects();

  /** The most sanity this unit has of its own, before what it wears adds to it. */
  @Getter(AccessLevel.NONE)
  private double maxSanity = sanityRules.getMax();

  /** Current sanity as last set; reads are held to the current maximum. */
  @Getter(AccessLevel.NONE)
  private double sanity = maxSanity;

  /** What this unit fears; hidden from the player until first met. */
  @Getter(AccessLevel.NONE)
  private final List<Phobia> phobias = new ArrayList<>();

  /** The floor on which a feared enemy last cost this unit sanity (-1 for none). */
  @JsonIgnore private int fearFloor = -1;

  public Unit(UnitStats stats) {
    super();
    this.stats = stats;
    layOutBody();
  }

  /**
   * Offers an item found by this unit: it goes into an empty fitting slot, else replaces the
   * lowest-rated fitting item if it rates higher, else is surplus. Slots on lost parts are not
   * fitting.
   */
  public EquipResult equip(Item item, ItemRating rating) {
    return gear.equip(item, rating.forAffinity(affinity), this::isUsable);
  }

  /** How this unit rates an item: its affinity category scores higher. */
  public double ratingOf(Item item, ItemRating rating) {
    return rating.forAffinity(affinity).of(item);
  }

  private boolean isUsable(Slot slot) {
    return slot.bodyPart().map(this::hasPart).orElse(true);
  }

  /** Takes off and returns everything the unit wears, trinkets included: what a death drops. */
  public List<Item> dropAll() {
    return gear.releaseAll();
  }

  /** How many of the worn items carry {@code effect}. */
  private int worn(ItemEffect effect) {
    return gear.countOf(effect);
  }

  /** Supplies the numbers behind the effects of worn items. */
  public void setItemEffects(GameProperties.Items.Effects effects) {
    this.itemEffects = effects;
  }

  /** The unit's body parts, in the order torso, head, arms, legs; lost parts stay listed. */
  public List<Part> getParts() {
    return Collections.unmodifiableList(parts);
  }

  public Unit(UnitStats stats, String name) {
    super(name);
    this.stats = stats;
    this.setMaxHealth(stats.getHealth());
    this.setCurrentHealth(stats.getHealth());
  }

  /** Restores a persisted unit with its identity; the caller reapplies strengths and health. */
  private Unit(UnitStats stats, Integer id, String name) {
    super(id, name);
    this.stats = stats;
    layOutBody();
  }

  /**
   * Rebuilds a unit from its persisted facts. The rolled factors and the body parts are reapplied
   * as they were, never re-rolled or re-laid-out; health totals derive from the parts.
   */
  public static Unit restore(
      UnitStats stats,
      Integer id,
      String name,
      double healthFactor,
      double damageFactor,
      boolean fixedStrengths,
      double maxMana,
      double currentMana,
      List<Part> savedParts,
      Map<Slot, Item> savedGear,
      ItemCategory affinity,
      GameProperties.Body.Weights bodyWeights) {
    var unit = new Unit(stats, id, name);
    unit.healthFactor = healthFactor;
    unit.damageFactor = damageFactor;
    unit.fixedStrengths = fixedStrengths;
    unit.maxMana = maxMana;
    unit.currentMana = currentMana;
    unit.bodyWeights = bodyWeights;
    unit.parts.clear();
    unit.parts.addAll(savedParts);
    unit.gear.restore(savedGear);
    unit.affinity = affinity;
    return unit;
  }

  /**
   * Sets the health factor and resets every part to its full share of the type's health scaled by
   * it. Meant for a freshly created unit, not one already wounded.
   */
  public void setHealthFactor(double healthFactor) {
    this.healthFactor = healthFactor;
    layOutBody();
  }

  /**
   * Divides the unit's rolled health total by these weights and heals every part fully. Meant for a
   * freshly created unit, not one already wounded.
   */
  public void setBodyWeights(GameProperties.Body.Weights weights) {
    this.bodyWeights = weights;
    layOutBody();
  }

  /** Lays the body out afresh at full health: the rolled total times each part's weight. */
  private void layOutBody() {
    var total = stats.getHealth() * healthFactor;
    parts.clear();
    for (var kind : BodyPart.values()) {
      parts.add(new Part(kind, total * weightOf(kind)));
    }
  }

  private double weightOf(BodyPart kind) {
    return switch (kind) {
      case TORSO -> bodyWeights.getTorso();
      case HEAD -> bodyWeights.getHead();
      case LEFT_ARM, RIGHT_ARM -> bodyWeights.getArm();
      case LEFT_LEG, RIGHT_LEG -> bodyWeights.getLeg();
    };
  }

  private Stream<Part> presentParts() {
    return parts.stream().filter(part -> !part.isLost());
  }

  /** Total health of the parts still attached. */
  @Override
  public Double getCurrentHealth() {
    return presentParts().mapToDouble(Part::getCurrentHealth).sum();
  }

  /** Total maximum health of the parts still attached. */
  @Override
  public Double getMaxHealth() {
    return presentParts().mapToDouble(Part::getMaxHealth).sum();
  }

  /**
   * Sets the total to {@code health}, spread so every present part holds the same share of its
   * maximum. A total of zero or less takes every part to zero.
   */
  @Override
  public void setCurrentHealth(Double health) {
    var max = getMaxHealth();
    var ratio = max > 0 ? Math.min(1.0, Math.max(0.0, health / max)) : 0.0;
    presentParts()
        .toList()
        .forEach(
            part -> {
              part.setCurrentHealth(part.getMaxHealth() * ratio);
              settle(part, null);
            });
  }

  /** Rescales the whole body to {@code health} at full strength, keeping the part weights. */
  @Override
  public void setMaxHealth(Double health) {
    healthFactor = health / stats.getHealth();
    layOutBody();
  }

  /** A unit dies when its head or torso reaches zero. */
  @Override
  public boolean isDead() {
    return parts.stream().anyMatch(part -> part.getKind().isVital() && isEmpty(part));
  }

  /** Whether any part still attached is below its maximum. */
  public boolean isWounded() {
    return presentParts().anyMatch(part -> part.missing() > EPSILON);
  }

  /**
   * Takes {@code amount} straight off one part. A head or torso at zero kills the unit; an arm or
   * leg at zero is lost for good. Does nothing to a part already lost.
   *
   * <p>The Armour worn on the part takes that much off the blow, but at least {@link
   * #ARMOUR_MIN_HIT_SHARE} of it still lands.
   *
   * @return the item worn on the part if this blow cost the unit that part, else empty
   */
  public List<Item> damagePart(BodyPart kind, double amount) {
    var dropped = new ArrayList<Item>();
    var armour = gear.stat(Slot.on(kind), ItemStat.ARMOUR);
    var landed = Math.max(amount * ARMOUR_MIN_HIT_SHARE, amount - armour);
    parts.stream()
        .filter(part -> part.getKind() == kind && !part.isLost())
        .forEach(
            part -> {
              part.setCurrentHealth(Math.max(0, part.getCurrentHealth() - landed));
              settle(part, dropped);
            });
    savedByPanacea();
    return dropped;
  }

  /** Gives this Unit the Party's Panacea, or takes it away with {@code null}. */
  public void setPanacea(Panacea panacea) {
    this.panacea = panacea;
  }

  /**
   * After a hit: if exactly one vital part has run out and the Party's Panacea is unspent, leaves
   * that part at its share of maximum and spends the Panacea. A hit that takes both vital parts to
   * zero cannot be saved.
   */
  private void savedByPanacea() {
    if (panacea == null || !panacea.isArmed()) {
      return;
    }
    var down = parts.stream().filter(part -> part.getKind().isVital() && isEmpty(part)).toList();
    if (down.size() == 1) {
      var part = down.getFirst();
      part.setCurrentHealth(part.getMaxHealth() * panacea.saveShare());
      panacea.spend();
    }
  }

  /**
   * Reattaches a lost arm or leg at {@code share} of that part's maximum health. Head and torso are
   * never lost.
   *
   * @return whether a part was reattached
   */
  public boolean reattach(BodyPart kind, double share) {
    var lost = parts.stream().filter(part -> part.getKind() == kind && part.isLost()).findFirst();
    if (lost.isEmpty()) {
      return false;
    }
    lost.get().setLost(false);
    lost.get().setCurrentHealth(lost.get().getMaxHealth() * share);
    return true;
  }

  /** The arms and legs this unit has lost, for a Spore Dose to bring back. */
  public List<BodyPart> lostParts() {
    return parts.stream().filter(Part::isLost).map(Part::getKind).toList();
  }

  /**
   * Restores up to {@code amount} health, always to the present part with the lowest share of its
   * maximum, never past a part's maximum; what a full part cannot take spills to the next lowest.
   *
   * @return the health actually restored
   */
  public double heal(double amount) {
    var remaining = amount;
    while (remaining > EPSILON) {
      var lowest =
          presentParts()
              .filter(part -> part.missing() > EPSILON)
              .min(Comparator.comparingDouble(Part::ratio));
      if (lowest.isEmpty()) {
        break;
      }
      var part = lowest.get();
      var restored = Math.min(remaining, part.missing());
      part.setCurrentHealth(
          restored >= part.missing() ? part.getMaxHealth() : part.getCurrentHealth() + restored);
      remaining -= restored;
    }
    return amount - remaining;
  }

  /**
   * Marks a part that has run out: an arm or leg is lost, a vital part is simply at zero. The item
   * on a lost part is added to {@code dropped}; with no list it stays worn until the unit dies.
   */
  private void settle(Part part, List<Item> dropped) {
    if (part.getCurrentHealth() <= EPSILON) {
      part.setCurrentHealth(0);
      if (!part.getKind().isVital()) {
        part.setLost(true);
        if (dropped != null) {
          var item = gear.release(Slot.on(part.getKind()));
          if (item != null) {
            dropped.add(item);
          }
        }
      }
    }
  }

  private static boolean isEmpty(Part part) {
    return part.getCurrentHealth() <= EPSILON;
  }

  private long lostCount(Predicate<BodyPart> kind) {
    return parts.stream().filter(part -> part.isLost() && kind.test(part.getKind())).count();
  }

  /**
   * Damage of one strike before milestone multipliers: the type's damage scaled by the roll, halved
   * for each lost arm unless the unit casts, plus the Power of what it wears.
   */
  public double getDamage() {
    var damage = stats.getDamage() * damageFactor;
    var scaled =
        isCaster() ? damage : damage * Math.pow(LOST_ARM_DAMAGE_FACTOR, lostCount(BodyPart::isArm));
    var ember =
        stats.getAttackType() == AttackType.MAGIC
            ? worn(ItemEffect.EMBER) * itemEffects.getEmberMagicDamage()
            : 0.0;
    return scaled + gear.total(ItemStat.POWER) + ember;
  }

  /**
   * Rolls health and damage factors independently and uniformly in {@code [1 - spread, 1 +
   * spread]}, health first. Does nothing for units with {@link #fixedStrengths} or a non-positive
   * spread.
   */
  public void rollStrengths(RandomGenerator random, double spread) {
    if (fixedStrengths || spread <= 0) {
      return;
    }
    setHealthFactor(random.nextDouble(1 - spread, 1 + spread));
    this.damageFactor = random.nextDouble(1 - spread, 1 + spread);
  }

  /** Whether this unit casts magic (offensive spells or heals) and therefore draws on mana. */
  public boolean isCaster() {
    var type = stats.getAttackType();
    return type == AttackType.MAGIC || type == AttackType.HEAL;
  }

  /**
   * Fills the mana reserve to {@code max} — used when the unit is freshly raised at the village.
   */
  public void chargeToFull(double max) {
    this.maxMana = max;
    this.currentMana = max;
  }

  /** Whether the unit is a caster with enough mana left to pay for one cast. */
  public boolean canCast(double cost) {
    return isCaster() && getCurrentMana() >= cost;
  }

  /** The most mana the unit can hold: its own reserve, plus Lilicen's Mana Vessel for a caster. */
  public double getMaxMana() {
    var vessel =
        isCaster()
            ? worn(ItemEffect.LILICENS_MANA_VESSEL) * itemEffects.getManaVesselMaxMana()
            : 0.0;
    return maxMana + vessel;
  }

  /** The unit's own mana reserve, before what it wears adds to it. */
  @JsonIgnore
  public double getBaseMaxMana() {
    return maxMana;
  }

  /** The mana left, never above the current maximum. */
  public double getCurrentMana() {
    return Math.min(currentMana, getMaxMana());
  }

  /** Spends {@code cost} mana for a cast, never dropping below empty. */
  public void spendMana(double cost) {
    this.currentMana = Math.max(0, getCurrentMana() - cost);
  }

  /** Regains {@code amount} mana while recharging at home, capped at the reserve's maximum. */
  public void rechargeMana(double amount) {
    var max = getMaxMana();
    if (max <= 0) {
      return;
    }
    this.currentMana = Math.min(max, getCurrentMana() + Math.max(0, amount));
  }

  /**
   * Applies the rules that govern this unit's mind and brings it to full sanity. Meant for a
   * freshly created unit.
   */
  public void setSanityRules(GameProperties.Sanity rules) {
    this.sanityRules = rules;
    this.maxSanity = rules.getMax();
    this.sanity = maxSanity;
  }

  /** The most sanity the unit can hold now: its own, plus what it wears. */
  public double getMaxSanity() {
    return maxSanity
        + gear.total(ItemStat.SANITY)
        + worn(ItemEffect.STEADFAST) * itemEffects.getSteadfastMaxSanity();
  }

  /** The unit's own maximum sanity, before what it wears adds to it. */
  @JsonIgnore
  public double getBaseMaxSanity() {
    return maxSanity;
  }

  /** Current sanity, from 0 to {@link #getMaxSanity()}. */
  public double getSanity() {
    return Math.min(sanity, getMaxSanity());
  }

  /** Restores a persisted mind as it was; nothing is re-rolled. */
  public void restoreMind(
      double sanity,
      double maxSanity,
      List<Phobia> savedPhobias,
      int fearFloor,
      GameProperties.Sanity rules) {
    this.sanityRules = rules;
    this.maxSanity = maxSanity;
    this.sanity = sanity;
    this.phobias.clear();
    this.phobias.addAll(savedPhobias);
    this.fearFloor = fearFloor;
  }

  /** The unit's phobias, hidden or revealed. */
  public List<Phobia> getPhobias() {
    return Collections.unmodifiableList(phobias);
  }

  /** Gives the unit a phobia of {@code enemyType} unless it already has one. */
  public void addPhobia(EnemyStats enemyType) {
    if (phobias.stream().noneMatch(phobia -> phobia.getFeared() == enemyType)) {
      phobias.add(new Phobia(enemyType));
    }
  }

  /**
   * Draws this unit's phobias: how many (by the configured chances) and which enemy types, all
   * distinct, from {@code random}. Units with {@link #fixedStrengths} have none.
   */
  public void rollPhobias(RandomGenerator random) {
    phobias.clear();
    if (fixedStrengths) {
      return;
    }
    var chances = sanityRules.getPhobiaCountChances();
    var roll = random.nextDouble();
    var count = chances.size() - 1;
    var cumulative = 0.0;
    for (var i = 0; i < chances.size(); i++) {
      cumulative += chances.get(i);
      if (roll < cumulative) {
        count = i;
        break;
      }
    }
    var pool = new ArrayList<>(List.of(EnemyStats.values()));
    for (var i = 0; i < count && !pool.isEmpty(); i++) {
      addPhobia(pool.remove(random.nextInt(pool.size())));
    }
  }

  /**
   * Draws this unit's affinity from {@code random}: whether it has one (by {@code chance}) and, if
   * so, which of the item categories, uniformly. Units with {@link #fixedStrengths} have none and
   * draw nothing.
   */
  public void rollAffinity(RandomGenerator random, double chance) {
    affinity = null;
    if (fixedStrengths) {
      return;
    }
    if (random.nextDouble() < chance) {
      var categories = ItemCategory.values();
      affinity = categories[random.nextInt(categories.length)];
    }
  }

  /**
   * Moves sanity by {@code delta}, never below zero. Gain beyond the maximum is not lost: it heals
   * the unit instead, one health per point.
   */
  public void changeSanity(double delta) {
    var max = getMaxSanity();
    var target = getSanity() + delta;
    if (target > max) {
      var excess = target - max;
      sanity = max;
      heal(excess);
    } else {
      sanity = Math.max(0, target);
    }
  }

  /**
   * How hard phobias bite right now: the configured base at full sanity, rising linearly to 1.0 at
   * zero.
   */
  @JsonIgnore
  public double getSeverity() {
    var base = sanityRules.getSeverityAtFullSanity();
    var max = getMaxSanity();
    var missing = max > 0 ? 1 - getSanity() / max : 1.0;
    return base + (1 - base) * missing;
  }

  /** Whether a phobia of {@code enemyType} bites: held, and not warded off by Sargan's Mask. */
  private boolean fears(EnemyStats enemyType) {
    return !isImmuneToPhobias()
        && phobias.stream().anyMatch(phobia -> phobia.getFeared() == enemyType);
  }

  /** Whether phobias have no effect on this unit: it wears Sargan's Mask. */
  public boolean isImmuneToPhobias() {
    return worn(ItemEffect.SARGANS_MASK) > 0;
  }

  /**
   * What this unit's damage is multiplied by against {@code enemyType}: down if it fears it,
   * doubled against dragons by the Dragon-Slayer's Blade.
   */
  public double damageFactorAgainst(EnemyStats enemyType) {
    var fear = fears(enemyType) ? 1 - sanityRules.getPhobiaEffect() * getSeverity() : 1.0;
    var slaying =
        enemyType == EnemyStats.DRAGON || enemyType == EnemyStats.DEAD_DRAGON
            ? Math.pow(
                itemEffects.getDragonSlayerMultiplier(), worn(ItemEffect.DRAGON_SLAYERS_BLADE))
            : 1.0;
    return fear * slaying;
  }

  /**
   * What damage from {@code enemyType} is multiplied by: up if this unit fears it, down by Gloom
   * against Dead Soldiers and Necromancers and by Salted against Slimes.
   */
  public double takenFactorFrom(EnemyStats enemyType) {
    var fear = fears(enemyType) ? 1 + sanityRules.getPhobiaEffect() * getSeverity() : 1.0;
    var warding =
        switch (enemyType) {
          case DEAD_SOLDIERS, NECROMANCER ->
              reduction(worn(ItemEffect.GLOOM), itemEffects.getGloomTakenReduction());
          case SLIME -> reduction(worn(ItemEffect.SALTED), itemEffects.getSaltedTakenReduction());
          default -> 1.0;
        };
    return fear * warding;
  }

  private static double reduction(int count, double share) {
    return Math.max(0.0, 1 - count * share);
  }

  /** Damage this unit returns to a melee attacker that hurts it, from Thorn. */
  public double thornDamage() {
    return worn(ItemEffect.THORN) * itemEffects.getThornDamage();
  }

  /** Sanity this unit gains on top of the usual when the party climbs a floor, from Lantern. */
  public double floorClimbedSanityBonus() {
    return worn(ItemEffect.LANTERN) * itemEffects.getLanternSanityPerFloor();
  }

  /**
   * Attrition: steady wear from being on the tower, not aimed and not armoured. Yuva's Salt Satchel
   * cuts it.
   *
   * @return the items worn on parts this cost the unit
   */
  public List<Item> receiveAttrition(double amount) {
    var factor =
        Math.pow(itemEffects.getSaltSatchelAttritionFactor(), worn(ItemEffect.YUVAS_SALT_SATCHEL));
    return receiveAttack(amount * factor, null);
  }

  /**
   * Notices which enemy types stand on floor {@code floorId}: every phobia of a type present is
   * revealed, and the first such meeting on this floor costs the configured sanity.
   */
  public void meetEnemies(int floorId, Collection<EnemyStats> present) {
    if (isImmuneToPhobias()) {
      return;
    }
    var met = phobias.stream().filter(phobia -> present.contains(phobia.getFeared())).toList();
    if (met.isEmpty()) {
      return;
    }
    met.forEach(Phobia::reveal);
    if (fearFloor != floorId) {
      fearFloor = floorId;
      changeSanity(-sanityRules.getFearMeetDrain());
    }
  }

  /** Forgets which floor last frightened the unit, so a new climb can frighten it again. */
  public void forgetFearFloor() {
    this.fearFloor = -1;
  }

  /**
   * Low sanity dulling aim: percentage points added to {@link #getAccuracy()}, zero at full sanity
   * and falling linearly to minus the configured penalty at zero sanity.
   */
  @JsonIgnore
  public double getSanityAccuracyModifier() {
    var max = getMaxSanity();
    var missing = max > 0 ? 1 - getSanity() / max : 1.0;
    return -sanityRules.getAccuracyPenalty() * missing;
  }

  /** The attack scaled by this unit's weakness to the attack type; negative means it heals. */
  public double scaledByWeakness(double attack, AttackType attackType) {
    var multiplier =
        attackType == null ? 1.0 : this.getStats().getWeaknesses().getOrDefault(attackType, 1.0);
    return attack * multiplier;
  }

  /** The damage-taken multiplier from the legs lost so far. */
  public double lostLegsDamageTakenFactor() {
    return Math.pow(LOST_LEG_DAMAGE_TAKEN_FACTOR, lostCount(BodyPart::isLeg));
  }

  /**
   * How well this unit lands hits on the parts it aims at, in percentage points off the miss
   * chance: its base, the sanity modifier, the worn items' Accuracy and the Keen Talismans.
   */
  @JsonIgnore
  public double getAccuracy() {
    return stats.baseAccuracy()
        + getSanityAccuracyModifier()
        + gear.total(ItemStat.ACCURACY)
        + worn(ItemEffect.KEEN) * itemEffects.getKeenAccuracy();
  }

  /** How well this unit avoids hits, in percentage points on the attacker's miss chance. */
  @JsonIgnore
  public double getEvasion() {
    return stats.baseEvasion()
        + gear.total(ItemStat.EVASION)
        + worn(ItemEffect.WARY) * itemEffects.getWaryEvasion();
  }

  /** Whether the part with this kind is still attached. */
  public boolean hasPart(BodyPart kind) {
    return parts.stream().anyMatch(part -> part.getKind() == kind && !part.isLost());
  }

  /**
   * Environmental damage: not aimed and not armoured, spread over every present part in proportion
   * to its size.
   *
   * @return the items worn on parts this cost the unit; empty for a heal
   */
  public List<Item> receiveAttack(Double attack, AttackType attackType) {
    var attackTotal = scaledByWeakness(attack, attackType);
    if (attackTotal < 0) {
      heal(-attackTotal);
      return List.of();
    }
    var taken = attackTotal * lostLegsDamageTakenFactor();
    var max = getMaxHealth();
    if (taken <= 0 || max <= 0) {
      return List.of();
    }
    var dropped = new ArrayList<Item>();
    // Every part drains in proportion to its maximum, so all lose the same share of it.
    presentParts()
        .toList()
        .forEach(
            part -> {
              part.setCurrentHealth(
                  Math.max(0, part.getCurrentHealth() - taken * part.getMaxHealth() / max));
              settle(part, dropped);
            });
    savedByPanacea();
    return dropped;
  }

  @Override
  public String toString() {
    return String.format(
        "%s[%s][%.1f/%.1f]",
        this.getName(), this.stats.name(), this.getCurrentHealth(), this.getMaxHealth());
  }
}

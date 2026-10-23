package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseUnit {
  private final UnitStats stats;

  /** Personal mana reserve (only casters carry one; non-casters keep 0). */
  private double maxMana = 0.0;

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

  /** Health below this counts as zero, absorbing floating-point drift from proportional hits. */
  private static final double EPSILON = 1e-9;

  /** Damage multiplier for each arm lost (casters are unaffected). */
  private static final double LOST_ARM_DAMAGE_FACTOR = 0.5;

  /** Damage-taken multiplier for each leg lost. */
  private static final double LOST_LEG_DAMAGE_TAKEN_FACTOR = 1.25;

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

  /** The most sanity this unit can hold. */
  private double maxSanity = sanityRules.getMax();

  /** Current sanity, from 0 to {@link #maxSanity}. */
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
              settle(part);
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
   */
  public void damagePart(BodyPart kind, double amount) {
    parts.stream()
        .filter(part -> part.getKind() == kind && !part.isLost())
        .forEach(
            part -> {
              part.setCurrentHealth(Math.max(0, part.getCurrentHealth() - amount));
              settle(part);
            });
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

  /** Marks a part that has run out: an arm or leg is lost, a vital part is simply at zero. */
  private void settle(Part part) {
    if (part.getCurrentHealth() <= EPSILON) {
      part.setCurrentHealth(0);
      if (!part.getKind().isVital()) {
        part.setLost(true);
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
   * for each lost arm unless the unit casts.
   */
  public double getDamage() {
    var damage = stats.getDamage() * damageFactor;
    return isCaster()
        ? damage
        : damage * Math.pow(LOST_ARM_DAMAGE_FACTOR, lostCount(BodyPart::isArm));
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
    return isCaster() && currentMana >= cost;
  }

  /** Spends {@code cost} mana for a cast, never dropping below empty. */
  public void spendMana(double cost) {
    this.currentMana = Math.max(0, this.currentMana - cost);
  }

  /** Regains {@code amount} mana while recharging at home, capped at the reserve's maximum. */
  public void rechargeMana(double amount) {
    if (maxMana <= 0) {
      return;
    }
    this.currentMana = Math.min(maxMana, this.currentMana + Math.max(0, amount));
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
   * Moves sanity by {@code delta}, never below zero. Gain beyond the maximum is not lost: it heals
   * the unit instead, one health per point.
   */
  public void changeSanity(double delta) {
    var target = sanity + delta;
    if (target > maxSanity) {
      var excess = target - maxSanity;
      sanity = maxSanity;
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
    var missing = maxSanity > 0 ? 1 - sanity / maxSanity : 1.0;
    return base + (1 - base) * missing;
  }

  private boolean fears(EnemyStats enemyType) {
    return phobias.stream().anyMatch(phobia -> phobia.getFeared() == enemyType);
  }

  /** What this unit's damage is multiplied by against {@code enemyType}; 1 unless it fears it. */
  public double damageFactorAgainst(EnemyStats enemyType) {
    return fears(enemyType) ? 1 - sanityRules.getPhobiaEffect() * getSeverity() : 1.0;
  }

  /** What damage from {@code enemyType} is multiplied by; 1 unless this unit fears it. */
  public double takenFactorFrom(EnemyStats enemyType) {
    return fears(enemyType) ? 1 + sanityRules.getPhobiaEffect() * getSeverity() : 1.0;
  }

  /**
   * Notices which enemy types stand on floor {@code floorId}: every phobia of a type present is
   * revealed, and the first such meeting on this floor costs the configured sanity.
   */
  public void meetEnemies(int floorId, Collection<EnemyStats> present) {
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
   * Seam for low sanity dulling aim: percentage points added to {@link #getAccuracy()} (zero for
   * now, so sanity does not touch accuracy yet). Item sanity gains (#48) and the loss from maiming
   * (#45) arrive through {@link #changeSanity(double)}.
   */
  @JsonIgnore
  public double getSanityAccuracyModifier() {
    return 0.0;
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
   * chance. Items and sanity add to this later by overriding or wrapping it.
   */
  @JsonIgnore
  public double getAccuracy() {
    return stats.baseAccuracy() + getSanityAccuracyModifier();
  }

  /** How well this unit avoids hits, in percentage points on the attacker's miss chance. */
  @JsonIgnore
  public double getEvasion() {
    return stats.baseEvasion();
  }

  /** Whether the part with this kind is still attached. */
  public boolean hasPart(BodyPart kind) {
    return parts.stream().anyMatch(part -> part.getKind() == kind && !part.isLost());
  }

  /** Environmental damage: not aimed, spread over every present part in proportion to its size. */
  public void receiveAttack(Double attack, AttackType attackType) {
    var attackTotal = scaledByWeakness(attack, attackType);
    if (attackTotal < 0) {
      heal(-attackTotal);
      return;
    }
    var taken = attackTotal * lostLegsDamageTakenFactor();
    var max = getMaxHealth();
    if (taken <= 0 || max <= 0) {
      return;
    }
    // Every part drains in proportion to its maximum, so all lose the same share of it.
    presentParts()
        .toList()
        .forEach(
            part -> {
              part.setCurrentHealth(
                  Math.max(0, part.getCurrentHealth() - taken * part.getMaxHealth() / max));
              settle(part);
            });
  }

  @Override
  public String toString() {
    return String.format(
        "%s[%s][%.1f/%.1f]",
        this.getName(), this.stats.name(), this.getCurrentHealth(), this.getMaxHealth());
  }
}

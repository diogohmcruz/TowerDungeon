package io.github.diogohmcruz.towerdungeon.domain.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.random.FloorStreams;
import io.github.diogohmcruz.towerdungeon.domain.random.Names;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = true)
public class Enemy extends BaseUnit implements Aimable {
  private static final BodyPart[] LIMBS = {
    BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG
  };

  private final EnemyStats stats;

  @Getter(AccessLevel.NONE)
  private final double healthMultiplier;

  @Getter(AccessLevel.NONE)
  private final double damageMultiplier;

  private final boolean boss;

  /** Salted by the Party's Salt Pack: it takes the Pack's extra damage. */
  private boolean salted;

  /**
   * The enemy's body. A creature with a single part holds just the torso; any other has all six
   * slots, those it was born without flagged lost with no health.
   */
  @Getter(AccessLevel.NONE)
  private final List<Part> parts = new ArrayList<>();

  /**
   * A garrison enemy: its parts are rolled from the type's configured range, or all six for a
   * guardian, and its total health is divided over them by the configured weights.
   */
  public Enemy(
      EnemyStats stats,
      double healthMultiplier,
      double damageMultiplier,
      boolean boss,
      FloorStreams streams,
      GameProperties.Enemies config) {
    this(
        nameFor(stats, streams.cosmetic()),
        stats,
        healthMultiplier,
        damageMultiplier,
        boss,
        streams,
        config);
  }

  /** As above, drawing the body and the name and id from the one generator. */
  public Enemy(
      EnemyStats stats,
      double healthMultiplier,
      double damageMultiplier,
      boolean boss,
      RandomGenerator random,
      GameProperties.Enemies config) {
    this(stats, healthMultiplier, damageMultiplier, boss, FloorStreams.of(random), config);
  }

  private Enemy(
      String name,
      EnemyStats stats,
      double healthMultiplier,
      double damageMultiplier,
      boolean boss,
      FloorStreams streams,
      GameProperties.Enemies config) {
    super(name, streams.cosmetic());
    this.stats = stats;
    this.healthMultiplier = healthMultiplier;
    this.damageMultiplier = damageMultiplier;
    this.boss = boss;
    layOutBody(rollParts(stats, boss, streams.generation(), config), config.getWeights());
  }

  /**
   * A Risen: a Dead Soldier that carries the name of the Unit who fell, laid out like any other
   * Dead Soldier.
   */
  public static Enemy risen(String name, FloorStreams streams, GameProperties.Enemies config) {
    return new Enemy(name, EnemyStats.DEAD_SOLDIERS, 1.0, 1.0, false, streams, config);
  }

  /** As above, from the one generator. */
  public static Enemy risen(String name, RandomGenerator random, GameProperties.Enemies config) {
    return risen(name, FloorStreams.of(random), config);
  }

  /** An ordinary garrison enemy at base strength. */
  public Enemy(EnemyStats stats, FloorStreams streams, GameProperties.Enemies config) {
    this(stats, 1.0, 1.0, false, streams, config);
  }

  /** An ordinary garrison enemy at base strength, from the one generator. */
  public Enemy(EnemyStats stats, RandomGenerator random, GameProperties.Enemies config) {
    this(stats, FloorStreams.of(random), config);
  }

  private Enemy(
      Integer id,
      String name,
      EnemyStats stats,
      double healthMultiplier,
      double damageMultiplier,
      boolean boss) {
    super(id, name);
    this.stats = stats;
    this.healthMultiplier = healthMultiplier;
    this.damageMultiplier = damageMultiplier;
    this.boss = boss;
  }

  /**
   * Rebuilds a garrison enemy from its persisted facts; the body parts are reapplied as they were
   * and the health totals derive from them.
   */
  public static Enemy restore(
      Integer id,
      String name,
      EnemyStats stats,
      double healthMultiplier,
      double damageMultiplier,
      boolean boss,
      List<Part> savedParts,
      boolean salted) {
    var enemy = new Enemy(id, name, stats, healthMultiplier, damageMultiplier, boss);
    enemy.parts.addAll(savedParts);
    enemy.salted = salted;
    return enemy;
  }

  /** The enemy's body parts in the order torso, head, arms, legs; lost parts stay listed. */
  public List<Part> getParts() {
    return Collections.unmodifiableList(parts);
  }

  /**
   * Which parts the enemy is born with: the torso, the head when there are two or more, and random
   * limbs for the remaining slots. A guardian gets all six without drawing anything.
   */
  private static Set<BodyPart> rollParts(
      EnemyStats stats, boolean boss, RandomGenerator random, GameProperties.Enemies config) {
    var all = BodyPart.values().length;
    var count = all;
    if (!boss) {
      var range = config.partsOf(stats);
      var min = Math.clamp(range.getMin(), 1, all);
      var max = Math.clamp(range.getMax(), min, all);
      count = min == max ? min : random.nextInt(min, max + 1);
    }
    var kinds = EnumSet.of(BodyPart.TORSO);
    if (count >= 2) {
      kinds.add(BodyPart.HEAD);
    }
    var limbSlots = count - kinds.size();
    var limbs = new ArrayList<>(List.of(LIMBS));
    if (limbSlots < limbs.size()) {
      Collections.shuffle(limbs, random);
    }
    kinds.addAll(limbs.subList(0, limbSlots));
    return kinds;
  }

  /** Divides the total health over the parts it has, by weight renormalised over just those. */
  private void layOutBody(Set<BodyPart> kinds, GameProperties.Body.Weights weights) {
    var total = stats.getHealth() * healthMultiplier;
    parts.clear();
    if (kinds.size() == 1) {
      parts.add(new Part(BodyPart.TORSO, total));
      return;
    }
    var weightSum = kinds.stream().mapToDouble(kind -> weightOf(kind, weights)).sum();
    for (var kind : BodyPart.values()) {
      if (kinds.contains(kind)) {
        parts.add(new Part(kind, total * weightOf(kind, weights) / weightSum));
      } else {
        var missing = new Part(kind, 0);
        missing.setLost(true);
        parts.add(missing);
      }
    }
  }

  private static double weightOf(BodyPart kind, GameProperties.Body.Weights weights) {
    return switch (kind) {
      case TORSO -> weights.getTorso();
      case HEAD -> weights.getHead();
      case LEFT_ARM, RIGHT_ARM -> weights.getArm();
      case LEFT_LEG, RIGHT_LEG -> weights.getLeg();
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

  /** An enemy dies when its torso or head reaches zero; a single body when it does. */
  @Override
  public boolean isDead() {
    return parts.stream()
        .anyMatch(part -> part.getKind().isVital() && part.getCurrentHealth() <= Unit.EPSILON);
  }

  /** Marks a part that has run out: an arm or leg is lost, a vital part is simply at zero. */
  private void settle(Part part) {
    if (part.getCurrentHealth() <= Unit.EPSILON) {
      part.setCurrentHealth(0);
      if (!part.getKind().isVital()) {
        part.setLost(true);
      }
    }
  }

  @Override
  public boolean hasPart(BodyPart kind) {
    return parts.stream().anyMatch(part -> part.getKind() == kind && !part.isLost());
  }

  /**
   * Takes {@code amount} off one part; a limb that runs out is lost, a vital part kills. An enemy
   * carries nothing, so no part drops an item.
   */
  @Override
  public List<Item> damagePart(BodyPart kind, double amount) {
    parts.stream()
        .filter(part -> part.getKind() == kind && !part.isLost())
        .forEach(
            part -> {
              part.setCurrentHealth(Math.max(0, part.getCurrentHealth() - amount));
              settle(part);
            });
    return List.of();
  }

  /**
   * Restores up to {@code amount} health, always to the present part with the lowest share of its
   * maximum, never past a part's maximum.
   *
   * @return the health actually restored
   */
  @Override
  public double heal(double amount) {
    var remaining = amount;
    while (remaining > Unit.EPSILON) {
      var lowest =
          presentParts()
              .filter(part -> part.missing() > Unit.EPSILON)
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

  private long lostCount(Predicate<BodyPart> kind) {
    return parts.stream().filter(part -> part.isLost() && kind.test(part.getKind())).count();
  }

  /** Lost legs make the enemy take more damage; a part it was born without counts as lost. */
  @Override
  public double lostLegsDamageTakenFactor() {
    return Math.pow(Unit.LOST_LEG_DAMAGE_TAKEN_FACTOR, lostCount(BodyPart::isLeg));
  }

  @Override
  public double scaledByWeakness(double attack, AttackType attackType) {
    var multiplier =
        attackType == null ? 1.0 : this.getStats().getWeaknesses().getOrDefault(attackType, 1.0);
    return attack * multiplier;
  }

  public double healthMultiplier() {
    return healthMultiplier;
  }

  public double damageMultiplier() {
    return damageMultiplier;
  }

  private static final String[] GOLEM_MATERIALS = {
    "Granite", "Basalt", "Obsidian", "Marble", "Slate", "Iron", "Bronze", "Onyx"
  };

  private static String nameFor(EnemyStats stats, RandomGenerator cosmetic) {
    var faker = Names.faker(cosmetic);
    return switch (stats) {
      case SLIME -> capitalize(faker.color().name()) + " Slime";
      case TOWER_BEAST -> "Tower " + capitalize(faker.animal().name());
      case CHIMERA -> capitalize(faker.animal().name()) + " Chimera";
      case STONE_GOLEM -> GOLEM_MATERIALS[cosmetic.nextInt(GOLEM_MATERIALS.length)] + " Golem";
      default -> faker.greekPhilosopher().name();
    };
  }

  private static String capitalize(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  /**
   * The damage of one strike: the type's damage times the multiplier, halved for each lost arm
   * unless the enemy casts.
   */
  public double getEffectiveDamage() {
    var damage = stats.getDamage() * damageMultiplier;
    return stats.getAttackType() == AttackType.MAGIC
        ? damage
        : damage * Math.pow(Unit.LOST_ARM_DAMAGE_FACTOR, lostCount(BodyPart::isArm));
  }

  /** Enemies have no accuracy of their own to add to yet. */
  @JsonIgnore
  public double getAccuracy() {
    return 0.0;
  }

  /** Enemies have no evasion yet. */
  @Override
  @JsonIgnore
  public double getEvasion() {
    return 0.0;
  }

  /** Unaimed damage: spread over every present part in proportion to its size. */
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
        "%s%s[%s][%.1f/%.1f]",
        boss ? "BOSS " : "",
        this.getName(),
        this.stats.name(),
        this.getCurrentHealth(),
        this.getMaxHealth());
  }
}

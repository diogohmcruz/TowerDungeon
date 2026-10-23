package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemStat;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.AttackType;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.TowerFloor;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class PartyCombatTest {
  private static final double COST = 1.0;

  private final GameProperties config = new GameProperties();
  private final GameState state = new GameState(config);
  private final RandomGenerator random = new SplittableRandom(7);

  {
    config.getRecruit().setStrengthSpread(0);
    // These tests pin exact numbers, so no aimed strike misses unless a test turns misses back on.
    noMisses(config.getAiming());
  }

  private static void noMisses(GameProperties.Aiming aiming) {
    aiming.setMinMiss(0);
    aiming.setTorsoMiss(0);
    aiming.setHeadMiss(0);
    aiming.setArmMiss(0);
    aiming.setLegMiss(0);
  }

  private Enemy enemy(EnemyStats stats) {
    return new Enemy(stats, new SplittableRandom(3), config.getEnemies());
  }

  private final PartyCombat combat = new PartyCombat(new SplittableRandom(7));

  private Unit recruited(UnitStats stats) {
    // These tests pin exact numbers from type stats, so recruits here are unrolled (factor 1.0).
    return state.recruit(stats, random);
  }

  private static Unit wounded(Unit unit, double missing) {
    unit.setCurrentHealth(unit.getStats().getHealth() - missing);
    return unit;
  }

  /** Units in a fixed order, so a tick's sequence does not depend on hash ordering. */
  private static Map<UnitStats, List<Unit>> party(Unit... units) {
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    for (var unit : units) {
      party.computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }
    return party;
  }

  private static TowerFloor floorOf(Enemy... enemies) {
    var floor = TowerFloor.builder().id(1).difficulty(1).build();
    floor.getEnemies().addAll(List.of(enemies));
    return floor;
  }

  private void tick(Map<UnitStats, List<Unit>> party, TowerFloor floor) {
    combat.unitsAttack(party, floor.getEnemies(), floor, state);
  }

  /**
   * A Slime that melee cannot hurt, so tests can tell a Healer's strike from a Warrior's. The real
   * Slime is only resistant (0.5), so it is not a damage sink for these tests.
   */
  private class MeleeImmuneSlime extends Enemy {
    MeleeImmuneSlime() {
      super(EnemyStats.SLIME, new SplittableRandom(3), config.getEnemies());
    }

    @Override
    public double scaledByWeakness(double attack, AttackType attackType) {
      return attackType == AttackType.MELEE ? 0 : super.scaledByWeakness(attack, attackType);
    }
  }

  @Test
  void theSlimeResistsMeleeWithoutBeingImmune() {
    assertThat(EnemyStats.SLIME.getWeaknesses().get(AttackType.MELEE)).isEqualTo(0.5);
  }

  @Test
  void aWarriorHurtsARealSlime() {
    var warrior = recruited(UnitStats.WARRIOR);
    var slime = enemy(EnemyStats.SLIME);

    tick(party(warrior), floorOf(slime));

    assertThat(slime.getCurrentHealth()).isEqualTo(8.0 - 0.5);
  }

  @Test
  void aHealerWithAWoundedAllyHealsAndDoesNotStrikeInTheSameTick() {
    var healer = recruited(UnitStats.HEALER);
    var warrior = wounded(recruited(UnitStats.WARRIOR), 10);
    var enemy = new MeleeImmuneSlime();
    var enemyHealth = enemy.getCurrentHealth();
    var manaBefore = healer.getCurrentMana();

    tick(party(healer, warrior), floorOf(enemy));

    assertThat(healer.getCurrentMana()).isEqualTo(manaBefore - COST);
    assertThat(warrior.getCurrentHealth()).isGreaterThan(warrior.getStats().getHealth() - 10);
    assertThat(enemy.getCurrentHealth()).isEqualTo(enemyHealth);
  }

  @Test
  void aHealerWithAWoundedAllyButNoManaDoesNothing() {
    var healer = recruited(UnitStats.HEALER);
    healer.spendMana(healer.getCurrentMana());
    var warrior = wounded(recruited(UnitStats.WARRIOR), 10);
    var enemy = new MeleeImmuneSlime();
    var enemyHealth = enemy.getCurrentHealth();

    tick(party(healer, warrior), floorOf(enemy));

    assertThat(warrior.getCurrentHealth()).isEqualTo(warrior.getStats().getHealth() - 10);
    assertThat(enemy.getCurrentHealth()).isEqualTo(enemyHealth);
    assertThat(healer.getCurrentMana()).isZero();
  }

  @Test
  void withNobodyWoundedAHealerStrikesAndLeavesItsAlliesAlone() {
    var healer = recruited(UnitStats.HEALER);
    var warrior = recruited(UnitStats.WARRIOR);
    var enemy = new MeleeImmuneSlime();
    var healthBefore = enemy.getCurrentHealth();

    tick(party(healer, warrior), floorOf(enemy));

    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana() - COST);
    assertThat(enemy.getCurrentHealth()).isLessThan(healthBefore);
    assertThat(warrior.getCurrentHealth()).isEqualTo(warrior.getStats().getHealth());
  }

  @Test
  void aHealerAlonePartyIsNotInNeedOfItsOwnHealing() {
    var healer = wounded(recruited(UnitStats.HEALER), 10);
    var enemy = enemy(EnemyStats.SLIME);

    tick(party(healer), floorOf(enemy));

    assertThat(healer.getCurrentHealth()).isEqualTo(healer.getStats().getHealth() - 10);
  }

  @Test
  void withNoWoundedAllyAHealerOnlyStrikesEnemiesAHealHitHarms() {
    var healer = recruited(UnitStats.HEALER);
    var warrior = recruited(UnitStats.WARRIOR);
    var chymus = enemy(EnemyStats.CHYMUS_INSECT);
    var golem = enemy(EnemyStats.STONE_GOLEM);
    var slime = new MeleeImmuneSlime();
    var slimeHealth = slime.getCurrentHealth();

    tick(party(healer, warrior), floorOf(chymus, golem, slime));

    // Only the Slime qualifies for the Healer, so every Healer strike lands on it.
    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana() - COST);
    assertThat(slime.getCurrentHealth()).isLessThan(slimeHealth);
  }

  @Test
  void aFloorOfOnlyHealedOrImmuneEnemiesCostsTheHealerNoMana() {
    var healer = recruited(UnitStats.HEALER);
    var chymus = enemy(EnemyStats.CHYMUS_INSECT);
    var golem = enemy(EnemyStats.STONE_GOLEM);
    var chymusHealth = chymus.getCurrentHealth();
    var golemHealth = golem.getCurrentHealth();

    tick(party(healer), floorOf(chymus, golem));

    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana());
    assertThat(chymus.getCurrentHealth()).isEqualTo(chymusHealth);
    assertThat(golem.getCurrentHealth()).isEqualTo(golemHealth);
  }

  @Test
  void twoHealersJudgeNeedFromTheStartOfTheTickAndBothHealTheSameAlly() {
    var first = recruited(UnitStats.HEALER);
    var second = recruited(UnitStats.HEALER);
    var warrior = wounded(recruited(UnitStats.WARRIOR), 10);
    var healPerCast =
        first.getStats().getDamage() * -UnitStats.WARRIOR.getWeaknesses().get(AttackType.HEAL);

    tick(party(first, second, warrior), floorOf(enemy(EnemyStats.SLIME)));

    assertThat(first.getCurrentMana()).isEqualTo(first.getMaxMana() - COST);
    assertThat(second.getCurrentMana()).isEqualTo(second.getMaxMana() - COST);
    assertThat(warrior.getCurrentHealth())
        .isEqualTo(warrior.getStats().getHealth() - 10 + 2 * healPerCast);
  }

  @Test
  void aMageStillAttacksOnceAndOtherUnitsAreUnaffected() {
    var mage = recruited(UnitStats.MAGE);
    var warrior = recruited(UnitStats.WARRIOR);
    var enemy = enemy(EnemyStats.SLIME);
    var healthBefore = enemy.getCurrentHealth();

    tick(party(mage, warrior), floorOf(enemy));

    assertThat(mage.getCurrentMana()).isEqualTo(mage.getMaxMana() - COST);
    assertThat(enemy.getCurrentHealth()).isLessThan(healthBefore);
  }

  /**
   * With no Healers and no kills, the tick draws one random target per unit in party order, exactly
   * as it did before Healers were limited to one cast, then aims each strike at a body part; these
   * values were captured from that draw order with seed 7 and guard against it shifting.
   */
  @Test
  void aSeededTickForAPartyWithoutHealersIsPinned() {
    var warrior = recruited(UnitStats.WARRIOR);
    var archer = recruited(UnitStats.ARCHER);
    var mage = recruited(UnitStats.MAGE);
    var beast = enemy(EnemyStats.TOWER_BEAST);
    var soldiers = enemy(EnemyStats.DEAD_SOLDIERS);

    tick(party(warrior, archer, mage), floorOf(beast, soldiers));

    assertThat(List.of(beast.getCurrentHealth(), soldiers.getCurrentHealth()))
        .isEqualTo(List.of(37.34375, 13.125));
    assertThat(mage.getCurrentMana()).isEqualTo(mage.getMaxMana() - COST);
  }

  @Test
  void aSlainEnemyLeavesTheFloorAndCountsAsAKill() {
    var warrior = recruited(UnitStats.WARRIOR);
    var enemy = enemy(EnemyStats.SLIME);
    enemy.setCurrentHealth(0.1);
    var floor = floorOf(enemy);

    tick(party(warrior), floor);

    assertThat(floor.getEnemies()).isEmpty();
    assertThat(state.getEnemiesDefeated()).isEqualTo(1);
  }

  @Test
  void enemiesStrikeAndAFallenUnitIsRemoved() {
    var warrior = recruited(UnitStats.WARRIOR);
    warrior.setCurrentHealth(0.1);
    state.getUnitsOnTower().put(UnitStats.WARRIOR, new ArrayList<>(List.of(warrior)));
    var enemies = new ArrayList<>(List.of(enemy(EnemyStats.SLIME)));

    // Strikes are aimed and can miss or land on a limb, so it takes a few until a vital part falls.
    for (var strike = 0; strike < 500 && !warrior.isDead(); strike++) {
      combat.enemiesAttack(state, enemies, party(warrior));
    }

    assertThat(warrior.isDead()).isTrue();
    assertThat(state.getUnitsOnTower().getOrDefault(UnitStats.WARRIOR, List.of()))
        .doesNotContain(warrior);
  }

  @Test
  void aHealerHealsTheLowestRatioPartOfTheWoundedAlly() {
    var healer = recruited(UnitStats.HEALER);
    var warrior = recruited(UnitStats.WARRIOR);
    warrior.damagePart(BodyPart.TORSO, 1.0);
    warrior.damagePart(BodyPart.HEAD, 1.0);
    var enemy = new MeleeImmuneSlime();

    tick(party(healer, warrior), floorOf(enemy));

    // The head sits at half its maximum, the torso at nine tenths: the heal goes to the head.
    var head = warrior.getParts().stream().filter(p -> p.getKind() == BodyPart.HEAD).findFirst();
    assertThat(head.orElseThrow().getCurrentHealth()).isEqualTo(2.0);
  }

  @Test
  void aUnitWithOnlyALostLimbIsNotWoundedAndAttractsNoHealer() {
    var healer = recruited(UnitStats.HEALER);
    var warrior = recruited(UnitStats.WARRIOR);
    warrior.damagePart(BodyPart.LEFT_ARM, 100);
    var enemy = new MeleeImmuneSlime();
    var healthBefore = enemy.getCurrentHealth();

    tick(party(healer, warrior), floorOf(enemy));

    assertThat(warrior.isWounded()).isFalse();
    assertThat(enemy.getCurrentHealth()).isLessThan(healthBefore);
  }

  @Test
  void aUnitIsRemovedInTheSameStrikeThatKillsIt() {
    var warrior = recruited(UnitStats.WARRIOR);
    warrior.setCurrentHealth(0.1);
    state.getUnitsOnTower().put(UnitStats.WARRIOR, new ArrayList<>(List.of(warrior)));
    var enemies = new ArrayList<>(List.of(enemy(EnemyStats.SLIME)));

    for (var strike = 0; strike < 500 && !warrior.isDead(); strike++) {
      combat.enemiesAttack(state, enemies, party(warrior));
      assertThat(
              state.getUnitsOnTower().getOrDefault(UnitStats.WARRIOR, List.of()).contains(warrior))
          .isEqualTo(!warrior.isDead());
    }

    assertThat(warrior.isDead()).isTrue();
  }

  @Test
  void castersTargetTheEnemiesThatWereAliveWhenTheTickBegan() {
    var alwaysFirst =
        new RandomGenerator() {
          @Override
          public long nextLong() {
            return 0;
          }
        };
    var combat = new PartyCombat(alwaysFirst);
    var warrior = recruited(UnitStats.WARRIOR);
    var mage = recruited(UnitStats.MAGE);
    var dying = enemy(EnemyStats.DEAD_SOLDIERS);
    dying.setCurrentHealth(0.1);
    var standing = enemy(EnemyStats.DEAD_SOLDIERS);
    var standingHealth = standing.getCurrentHealth();
    var floor = floorOf(dying, standing);

    // The Warrior acts first and kills the first enemy; the Mage still aims at the tick-start
    // snapshot, whose first enemy is the one that just fell, and wastes its cast on it.
    combat.unitsAttack(party(warrior, mage), floor.getEnemies(), floor, state);

    assertThat(floor.getEnemies()).containsExactly(standing);
    assertThat(standing.getCurrentHealth()).isEqualTo(standingHealth);
    assertThat(mage.getCurrentMana()).isEqualTo(mage.getMaxMana() - COST);
    assertThat(state.getEnemiesDefeated()).isEqualTo(1);
  }

  @Test
  void aSlimeStrikesOnePartAtATimeAndSomeStrikesMiss() {
    var defaults = new GameProperties.Aiming();
    config.getAiming().setMinMiss(defaults.getMinMiss());
    config.getAiming().setTorsoMiss(defaults.getTorsoMiss());
    config.getAiming().setHeadMiss(defaults.getHeadMiss());
    config.getAiming().setArmMiss(defaults.getArmMiss());
    config.getAiming().setLegMiss(defaults.getLegMiss());
    var enemies = new ArrayList<>(List.of(enemy(EnemyStats.SLIME)));
    var misses = 0;
    var landed = 0;
    for (var strike = 0; strike < 300; strike++) {
      var tank = recruited(UnitStats.TANK);
      combat.enemiesAttack(state, enemies, party(tank));
      var woundedParts = tank.getParts().stream().filter(part -> part.missing() > 0).count();
      assertThat(woundedParts).isLessThanOrEqualTo(1);
      if (woundedParts == 0) {
        misses++;
      } else {
        landed++;
      }
    }

    assertThat(misses).isPositive();
    assertThat(landed).isGreaterThan(misses);
  }

  @Test
  void aUnitsStrikeLandsOnOnePartOfTheEnemyAtATime() {
    var golem = enemy(EnemyStats.STONE_GOLEM);
    var porter = recruited(UnitStats.PORTER);

    tick(party(porter), floorOf(golem));

    var hurt = golem.getParts().stream().filter(part -> part.missing() > 0).toList();
    assertThat(hurt).hasSize(1);
  }

  @Test
  void aStrikeThatEmptiesAVitalPartRemovesTheEnemyFromTheFloor() {
    var golem = enemy(EnemyStats.STONE_GOLEM);
    golem.damagePart(BodyPart.TORSO, golem.getParts().getFirst().getMaxHealth() - 0.01);
    golem.damagePart(BodyPart.HEAD, golem.getParts().get(1).getMaxHealth() - 0.01);
    var floor = floorOf(golem);
    var mage = recruited(UnitStats.MAGE);

    for (var round = 0; round < 50 && !floor.getEnemies().isEmpty(); round++) {
      mage.chargeToFull(5);
      tick(party(mage), floor);
    }

    assertThat(golem.isDead()).isTrue();
    assertThat(floor.getEnemies()).isEmpty();
    assertThat(state.getEnemiesDefeated()).isEqualTo(1);
  }

  @Test
  void environmentalDamageIsNotAimed() {
    var tank = recruited(UnitStats.TANK);

    tank.receiveAttack(5.0, null);

    assertThat(tank.getParts()).allMatch(part -> part.missing() > 0);
  }

  @Test
  void aUnitThatDiesInAStrikeDropsEverythingItWore() {
    var rating = new ItemRating(config.getItems().getWeights());
    var warrior = recruited(UnitStats.WARRIOR);
    var charm =
        new Item(
            "charm",
            "charm",
            ItemTier.LEFTOVER,
            ItemCategory.TRINKET,
            Map.of(ItemStat.SANITY, 5.0),
            null);
    warrior.equip(charm, rating);
    // Every part at a sliver, so whatever the strike hits kills or maims.
    warrior.setCurrentHealth(0.001);
    state.getUnitsOnTower().put(UnitStats.WARRIOR, new ArrayList<>(List.of(warrior)));
    var enemies = new ArrayList<>(List.of(enemy(EnemyStats.SLIME)));

    var drops = new ArrayList<Item>();
    for (var strike = 0; strike < 50 && !warrior.isDead(); strike++) {
      drops.addAll(combat.enemiesAttack(state, enemies, party(warrior)));
    }

    assertThat(warrior.isDead()).isTrue();
    assertThat(drops).containsExactly(charm);
  }
}

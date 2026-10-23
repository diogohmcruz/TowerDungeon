package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.Item;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemEffect;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

class ThornTest {
  private final GameProperties config = noMisses();
  private final GameState state = new GameState(config);
  private final PartyCombat combat = new PartyCombat(new SplittableRandom(7));

  private static GameProperties noMisses() {
    var config = new GameProperties();
    config.getAiming().setMinMiss(0);
    config.getAiming().setMaxMiss(0);
    return config;
  }

  private Enemy enemy(EnemyStats type) {
    return new Enemy(type, new SplittableRandom(1), config.getEnemies());
  }

  private static Unit thornWearer() {
    var unit = new Unit(UnitStats.TANK);
    unit.setHealthFactor(100);
    unit.equip(
        new Item("thorn", "Thorn", ItemTier.TALISMAN, ItemCategory.TRINKET, Map.of(), "thorn"),
        new ItemRating(new GameProperties().getItems().getWeights()));
    return unit;
  }

  private Map<UnitStats, List<Unit>> party(Unit unit) {
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    party.put(unit.getStats(), new ArrayList<>(List.of(unit)));
    state.setUnitsOnTower(party);
    return party;
  }

  @Test
  void aMeleeAttackerTakesOneDamageBackWhenItHurtsTheWearer() {
    var unit = thornWearer();
    var beast = enemy(EnemyStats.TOWER_BEAST);
    var healthBefore = beast.getCurrentHealth();

    combat.enemiesAttack(state, new ArrayList<>(List.of(beast)), party(unit));

    assertThat(beast.getCurrentHealth()).isEqualTo(healthBefore - ItemEffectValues.thorn());
  }

  @Test
  void aRangedOrMagicAttackerTakesNothingBack() {
    var unit = thornWearer();
    var chimera = enemy(EnemyStats.CHIMERA);
    var healthBefore = chimera.getCurrentHealth();

    combat.enemiesAttack(state, new ArrayList<>(List.of(chimera)), party(unit));

    assertThat(chimera.getCurrentHealth()).isEqualTo(healthBefore);
  }

  @Test
  void anAttackerThatMissesTakesNothingBack() {
    config.getAiming().setMinMiss(90);
    config.getAiming().setMaxMiss(90);
    var unit = thornWearer();
    var beast = enemy(EnemyStats.TOWER_BEAST);
    var healthBefore = beast.getCurrentHealth();
    // Every draw is zero, below any miss chance, so every blow misses.
    var missing = new PartyCombat(() -> 0L);
    var unitHealth = unit.getCurrentHealth();

    missing.enemiesAttack(state, new ArrayList<>(List.of(beast)), party(unit));

    assertThat(unit.getCurrentHealth()).isEqualTo(unitHealth);
    assertThat(beast.getCurrentHealth()).isEqualTo(healthBefore);
  }

  @Test
  void anAttackerThatDiesOnTheThornsLeavesTheGarrisonAndCountsAsAKill() {
    var unit = thornWearer();
    var slime = enemy(EnemyStats.SLIME);
    slime.setCurrentHealth(0.01);
    var garrison = new ArrayList<>(List.of(slime));
    var kills = state.getEnemiesDefeated();

    combat.enemiesAttack(state, garrison, party(unit));

    assertThat(slime.isDead()).isTrue();
    assertThat(garrison).isEmpty();
    assertThat(state.getEnemiesDefeated()).isEqualTo(kills + 1);
  }

  @Test
  void aWearerWithoutThornReturnsNothing() {
    var unit = new Unit(UnitStats.TANK);
    unit.setHealthFactor(100);
    var beast = enemy(EnemyStats.TOWER_BEAST);
    var healthBefore = beast.getCurrentHealth();

    combat.enemiesAttack(state, new ArrayList<>(List.of(beast)), party(unit));

    assertThat(beast.getCurrentHealth()).isEqualTo(healthBefore);
  }

  @Test
  void attritionOnTheTowerIsHalvedBySaltSatchelOnly() {
    var wearer = new Unit(UnitStats.WARRIOR);
    wearer.equip(
        new Item(
            "yuvas-salt-satchel",
            "Yuva's Salt Satchel",
            ItemTier.ARTIFACT,
            ItemCategory.TRINKET,
            Map.of(),
            ItemEffect.YUVAS_SALT_SATCHEL.id()),
        new ItemRating(config.getItems().getWeights()));
    var bare = new Unit(UnitStats.WARRIOR);
    var party = new LinkedHashMap<UnitStats, List<Unit>>();
    party.put(UnitStats.WARRIOR, new ArrayList<>(List.of(wearer, bare)));
    state.setUnitsOnTower(party);

    state.passingTime(10_000L);

    var lostWearer = wearer.getMaxHealth() - wearer.getCurrentHealth();
    var lostBare = bare.getMaxHealth() - bare.getCurrentHealth();
    assertThat(lostBare).isGreaterThan(0);
    assertThat(lostWearer).isEqualTo(lostBare / 2, org.assertj.core.api.Assertions.within(1e-9));
  }

  /** The configured Thorn damage, so the tests follow config rather than a literal. */
  private static final class ItemEffectValues {
    static double thorn() {
      return new GameProperties().getItems().getEffects().getThornDamage();
    }
  }
}

package io.github.diogohmcruz.towerdungeon.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
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
  private final PartyCombat combat = new PartyCombat(new SplittableRandom(7));

  private Unit raised(UnitStats stats) {
    return state.recruit(stats);
  }

  private static Unit wounded(Unit unit, double missing) {
    unit.setCurrentHealth(unit.getStats().getHealth() - missing);
    return unit;
  }

  private static Map<UnitStats, List<Unit>> party(Unit... units) {
    var party = new HashMap<UnitStats, List<Unit>>();
    for (var unit : units) {
      party.computeIfAbsent(unit.getStats(), _ -> new ArrayList<>()).add(unit);
    }
    return party;
  }

  private TowerFloor floorOf(Enemy... enemies) {
    var floor = TowerFloor.builder().id(1).difficulty(1).build();
    floor.getEnemies().addAll(List.of(enemies));
    return floor;
  }

  private void tick(Map<UnitStats, List<Unit>> party, TowerFloor floor) {
    combat.unitsAttack(party, floor.getEnemies(), floor, state);
  }

  @Test
  void aHealerWithAWoundedAllyHealsAndDoesNotStrikeInTheSameTick() {
    var healer = raised(UnitStats.HEALER);
    var warrior = wounded(raised(UnitStats.WARRIOR), 10);
    var enemy = new Enemy(EnemyStats.SLIME);
    var enemyHealth = enemy.getCurrentHealth();
    var manaBefore = healer.getCurrentMana();

    tick(party(healer, warrior), floorOf(enemy));

    assertThat(healer.getCurrentMana()).isEqualTo(manaBefore - COST);
    assertThat(warrior.getCurrentHealth()).isGreaterThan(warrior.getStats().getHealth() - 10);
    assertThat(enemy.getCurrentHealth()).isEqualTo(enemyHealth);
  }

  @Test
  void aHealerWithAWoundedAllyButNoManaDoesNothing() {
    var healer = raised(UnitStats.HEALER);
    healer.spendMana(healer.getCurrentMana());
    var warrior = wounded(raised(UnitStats.WARRIOR), 10);
    var enemy = new Enemy(EnemyStats.DEAD_SOLDIERS);

    var floor = floorOf(enemy);
    tick(party(healer, warrior), floor);

    // A Heal hit would one-shot Dead Soldiers (x10), so the Healer did not strike.
    assertThat(floor.getEnemies()).contains(enemy);
    assertThat(warrior.getCurrentHealth()).isEqualTo(warrior.getStats().getHealth() - 10);
    assertThat(healer.getCurrentMana()).isZero();
  }

  @Test
  void aHealerNeverSpendsACastOnAnAllyAtFullHealth() {
    var healer = raised(UnitStats.HEALER);
    var warrior = raised(UnitStats.WARRIOR);
    var enemy = new Enemy(EnemyStats.SLIME);
    var healthBefore = enemy.getCurrentHealth();

    tick(party(healer, warrior), floorOf(enemy));

    // Nobody to heal, so the Healer strikes the Slime (a Heal hit harms it).
    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana() - COST);
    assertThat(enemy.getCurrentHealth()).isLessThan(healthBefore);
  }

  @Test
  void aHealerAlonePartyIsNotInNeedOfItsOwnHealing() {
    var healer = wounded(raised(UnitStats.HEALER), 10);
    var enemy = new Enemy(EnemyStats.SLIME);

    tick(party(healer), floorOf(enemy));

    assertThat(healer.getCurrentHealth()).isEqualTo(healer.getStats().getHealth() - 10);
  }

  @Test
  void withNoWoundedAllyAHealerOnlyStrikesEnemiesAHealHitHarms() {
    var healer = raised(UnitStats.HEALER);
    var warrior = raised(UnitStats.WARRIOR);
    var chymus = new Enemy(EnemyStats.CHYMUS_INSECT);
    var golem = new Enemy(EnemyStats.STONE_GOLEM);
    var slime = new Enemy(EnemyStats.SLIME);
    var slimeHealth = slime.getCurrentHealth();

    tick(party(healer, warrior), floorOf(chymus, golem, slime));

    // Only the Slime qualifies for the Healer, so every Healer strike lands on it.
    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana() - COST);
    assertThat(slime.getCurrentHealth()).isLessThan(slimeHealth);
  }

  @Test
  void aFloorOfOnlyHealedOrImmuneEnemiesCostsTheHealerNoMana() {
    var healer = raised(UnitStats.HEALER);
    var chymus = new Enemy(EnemyStats.CHYMUS_INSECT);
    var golem = new Enemy(EnemyStats.STONE_GOLEM);
    var chymusHealth = chymus.getCurrentHealth();
    var golemHealth = golem.getCurrentHealth();

    tick(party(healer), floorOf(chymus, golem));

    assertThat(healer.getCurrentMana()).isEqualTo(healer.getMaxMana());
    assertThat(chymus.getCurrentHealth()).isEqualTo(chymusHealth);
    assertThat(golem.getCurrentHealth()).isEqualTo(golemHealth);
  }

  @Test
  void twoHealersJudgeNeedFromTheStartOfTheTickAndBothMayHealTheSameAlly() {
    var first = raised(UnitStats.HEALER);
    var second = raised(UnitStats.HEALER);
    var warrior = wounded(raised(UnitStats.WARRIOR), 0.5);

    tick(party(first, second, warrior), floorOf(new Enemy(EnemyStats.SLIME)));

    assertThat(first.getCurrentMana()).isEqualTo(first.getMaxMana() - COST);
    assertThat(second.getCurrentMana()).isEqualTo(second.getMaxMana() - COST);
  }

  @Test
  void aMageStillAttacksOnceAndOtherUnitsAreUnaffected() {
    var mage = raised(UnitStats.MAGE);
    var warrior = raised(UnitStats.WARRIOR);
    var enemy = new Enemy(EnemyStats.SLIME);
    var healthBefore = enemy.getCurrentHealth();

    tick(party(mage, warrior), floorOf(enemy));

    assertThat(mage.getCurrentMana()).isEqualTo(mage.getMaxMana() - COST);
    assertThat(enemy.getCurrentHealth()).isLessThan(healthBefore);
  }

  @Test
  void aSlainEnemyLeavesTheFloorAndCountsAsAKill() {
    var warrior = raised(UnitStats.WARRIOR);
    var enemy = new Enemy(EnemyStats.DEAD_SOLDIERS);
    enemy.setCurrentHealth(0.1);
    var floor = floorOf(enemy);

    tick(party(warrior), floor);

    assertThat(floor.getEnemies()).isEmpty();
    assertThat(state.getEnemiesDefeated()).isEqualTo(1);
  }

  @Test
  void enemiesStrikeAndAFallenUnitIsRemoved() {
    var warrior = raised(UnitStats.WARRIOR);
    warrior.setCurrentHealth(0.1);
    state.getUnitsOnTower().put(UnitStats.WARRIOR, new ArrayList<>(List.of(warrior)));
    var enemies = new ArrayList<>(List.of(new Enemy(EnemyStats.SLIME)));

    combat.enemiesAttack(state, enemies, party(warrior));

    assertThat(warrior.isDead()).isTrue();
    assertThat(state.getUnitsOnTower().getOrDefault(UnitStats.WARRIOR, List.of()))
        .doesNotContain(warrior);
  }
}

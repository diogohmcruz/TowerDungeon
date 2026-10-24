package io.github.diogohmcruz.towerdungeon.domain.prestige;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType;
import io.github.diogohmcruz.towerdungeon.domain.models.shortcut.ShortcutType;
import io.github.diogohmcruz.towerdungeon.domain.random.RandomSource;

/** Course IV and the Epitaphs: every number is config, and a reloaded Campaign keeps them. */
class CourseIvEffectsTest {
  private static PlayerMeta meta(
      List<InheritanceType> held,
      List<String> epitaphs,
      Map<Integer, List<Integer>> slain,
      int breach) {
    var roll = held.stream().map(type -> new RollEntry(type.name(), 1)).toList();
    return new PlayerMeta(
        2, 0, roll, 0, LifetimeLedger.EMPTY, List.of(), slain, epitaphs, breach, "secret");
  }

  private static Tower tower(GameProperties config) {
    var plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            100,
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
    return new Tower(
        100, config.getBoss().copy(), config.getEnemies().copy(), new RandomSource(5), plan);
  }

  // ---- Hard-Won Habits

  @Test
  void hardWonHabitsGrantsAllFourMilestonesAtTheStart() {
    var config = new GameProperties().copy();
    var state = new GameState(config, new RandomSource(1));
    assertThat(state.getMilestones()).noneMatch(offer -> offer.achieved());

    RollEffects.of(meta(List.of(InheritanceType.HARD_WON_HABITS), List.of(), Map.of(), 0), config)
        .start(state, Map.of());

    assertThat(state.getMilestones())
        .hasSize(MilestoneType.values().length)
        .allMatch(offer -> offer.achieved());
    assertThat(state.isUnitUnlocked(UnitStats.CAPTAIN)).isTrue();
    assertThat(state.isUnitUnlocked(UnitStats.MAGE)).isTrue();
    // They are saved with the Campaign, so a reload keeps them without the Roll.
    var restored = GameState.restore(state.toSnapshot(), config, new RandomSource(1), null);
    assertThat(restored.getMilestones()).allMatch(offer -> offer.achieved());
  }

  // ---- Offering Stone

  @Test
  void theOfferingPaysOneLegacyPerThresholdUpToTheCap() {
    var config = new GameProperties().copy();
    var stone = config.getPrestige().getRoll().getOfferingStone();
    stone.setRelicsPerLegacy(100);
    stone.setMaxLegacy(3);
    var effects =
        RollEffects.of(
            meta(List.of(InheritanceType.OFFERING_STONE), List.of(), Map.of(), 0), config);

    assertThat(effects.offering(99)).isZero();
    assertThat(effects.offering(100)).isEqualTo(1);
    assertThat(effects.offering(250)).isEqualTo(2);
    assertThat(effects.offering(100_000)).isEqualTo(3);
  }

  @Test
  void withoutAStoneNothingIsOffered() {
    var config = new GameProperties().copy();

    assertThat(RollEffects.of(List.of(), config).offering(10_000)).isZero();
  }

  // ---- Epitaphs

  @Test
  void anEpitaphIsGrantedWhenTheLedgerCrossesItsThresholdAndNeverLost() {
    var rules = new GameProperties().getPrestige().getEpitaphs();
    var low = new LifetimeLedger(0, 0, 0, 0, 0, 99, 14, 14, 9, 0, 0, 0);
    var high = new LifetimeLedger(0, 0, 0, 0, 0, 100, 15, 15, 10, 0, 0, 0);

    assertThat(Epitaph.granted(List.of(), low, rules)).isEmpty();
    assertThat(Epitaph.granted(List.of(), high, rules))
        .containsExactly("FIRST_FOOTING", "NAMES_ON_THE_WALL", "TEN_SEALS_BROKEN");
    // A ratchet: held stays held even when the Ledger no longer shows it, and none repeats.
    assertThat(Epitaph.granted(List.of("NAMES_ON_THE_WALL"), low, rules))
        .containsExactly("NAMES_ON_THE_WALL");
    assertThat(Epitaph.granted(List.of("FIRST_FOOTING"), high, rules))
        .containsExactly("FIRST_FOOTING", "NAMES_ON_THE_WALL", "TEN_SEALS_BROKEN");
  }

  @Test
  void theThresholdsAreConfig() {
    var rules = new GameProperties().getPrestige().getEpitaphs();
    rules.getFirstFooting().setLifetimeHighestFloor(3);
    var ledger = new LifetimeLedger(0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0);

    assertThat(Epitaph.granted(List.of(), ledger, rules)).containsExactly("FIRST_FOOTING");
  }

  @Test
  void endingACampaignGrantsTheEpitaphsTheNewLedgerEarns() {
    var rules = new GameProperties().getPrestige().getEpitaphs();
    var report =
        new SuccessionReport(
            GameOutcome.EXODUS,
            1,
            new Toll(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0),
            2,
            false,
            3,
            12,
            4);
    var tally = new Tally(0, 0, 0, 0, 4, 12, 3);

    var ended =
        PlayerMeta.first("s").ended(report, tally, 0, List.of(), 50, List.of(10, 20, 30), rules);

    assertThat(ended.epitaphs()).containsExactly("FIRST_FOOTING");
    assertThat(ended.has(Epitaph.FIRST_FOOTING)).isTrue();
    assertThat(ended.slainGuardians()).containsEntry(1, List.of(10, 20, 30));
  }

  @Test
  void firstFootingOpensTheVanguardCampFromTheStart() {
    var config = new GameProperties().copy();
    var state = new GameState(config, new RandomSource(1));
    assertThat(state.getShortcuts())
        .noneMatch(
            offer ->
                offer.floor() == config.getShortcuts().floorOf(ShortcutType.VANGUARD_CAMP)
                    && offer.unlocked());

    RollEffects.of(meta(List.of(), List.of("FIRST_FOOTING"), Map.of(), 0), config)
        .start(state, Map.of());

    assertThat(state.getShortcuts())
        .anyMatch(
            offer ->
                offer.floor() == config.getShortcuts().floorOf(ShortcutType.VANGUARD_CAMP)
                    && offer.unlocked());
  }

  @Test
  void namesOnTheWallRaisesEveryUnitsHealthTotalBeforeItIsDividedIntoParts() {
    var config = new GameProperties().copy();
    config.getRecruit().setStrengthSpread(0);
    config.getPrestige().getRoll().getHeir().setHealthMultiplier(1.0);
    var state = new GameState(config, new RandomSource(1));
    state.addUnits(UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR)));
    var base = state.getUnits().get(UnitStats.WARRIOR).getFirst().getMaxHealth();

    RollEffects.of(
            meta(List.of(InheritanceType.THE_HEIR), List.of("NAMES_ON_THE_WALL"), Map.of(), 0),
            config)
        .start(state, Map.of("HEIR", "HEIR_WARRIOR"));

    var warriors = state.getUnits().get(UnitStats.WARRIOR);
    assertThat(warriors).hasSize(2);
    // The Founder and the Heir are both raised by 5%, once, and the parts add up to the total.
    assertThat(warriors.get(0).getMaxHealth())
        .isCloseTo(base * 1.05, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(warriors.get(1).getMaxHealth())
        .isCloseTo(base * 1.05, org.assertj.core.data.Offset.offset(1e-9));
    // A later recruit takes the bonus too.
    var recruit = state.recruit(UnitStats.WARRIOR);
    assertThat(recruit.getMaxHealth())
        .isCloseTo(UnitStats.WARRIOR.getHealth() * 1.05, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void theHealthBonusIsReappliedToRecruitsOfALoadedCampaignButNotToUnitsAlreadySaved() {
    var config = new GameProperties().copy();
    config.getRecruit().setStrengthSpread(0);
    var state = new GameState(config, new RandomSource(1));
    state.addUnits(UnitStats.WARRIOR, List.of(state.recruit(UnitStats.WARRIOR)));
    var effects =
        RollEffects.of(meta(List.of(), List.of("NAMES_ON_THE_WALL"), Map.of(), 0), config);
    effects.start(state, Map.of());
    var founder = state.getUnits().get(UnitStats.WARRIOR).getFirst().getMaxHealth();

    var restored = GameState.restore(state.toSnapshot(), config, new RandomSource(1), null);
    effects.carry(restored);

    assertThat(restored.getUnits().get(UnitStats.WARRIOR).getFirst().getMaxHealth())
        .isEqualTo(founder);
    assertThat(restored.recruit(UnitStats.ARCHER).getMaxHealth())
        .isCloseTo(UnitStats.ARCHER.getHealth() * 1.05, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void tenSealsBrokenMultipliesEasingByTheConfiguredFactor() {
    var plain = new GameProperties().copy();
    var sealed = new GameProperties().copy();
    sealed.getPrestige().getEpitaphs().getTenSealsBroken().setEasingMultiplier(2.0);
    RollEffects.of(meta(List.of(), List.of("TEN_SEALS_BROKEN"), Map.of(), 0), sealed).shape(sealed);
    assertThat(sealed.getEnemies().getEaseMultiplier()).isEqualTo(2.0);

    var plainTower = tower(plain);
    var sealedTower = tower(sealed);
    plainTower.markBossCleared(20);
    sealedTower.markBossCleared(20);

    assertThat(sealedTower.easedBaseBudget(15)).isLessThan(plainTower.easedBaseBudget(15));
    var unease = tower(plain).easedBaseBudget(15);
    assertThat(plainTower.easedBaseBudget(15))
        .isCloseTo(
            unease * (1 - plain.getEnemies().getEaseBudgetPerBoss()),
            org.assertj.core.data.Offset.offset(1e-9));
    assertThat(sealedTower.easedBaseBudget(15))
        .isCloseTo(
            unease * (1 - 2.0 * plain.getEnemies().getEaseBudgetPerBoss()),
            org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void theDefaultEasingFactorIsOneAndTheEpitaphRaisesItToOnePointTwoFive() {
    var config = new GameProperties().copy();
    assertThat(config.getEnemies().getEaseMultiplier()).isEqualTo(1.0);

    RollEffects.of(meta(List.of(), List.of("TEN_SEALS_BROKEN"), Map.of(), 0), config).shape(config);

    assertThat(config.getEnemies().getEaseMultiplier()).isEqualTo(1.25);
  }

  // ---- The Open Breach

  @Test
  void theBreachFloorHoldsOnlyItsGarrisonAndIsNotAGuardianOrASlainOne() {
    var config = new GameProperties().copy();
    var held = List.of(InheritanceType.THE_OPEN_BREACH);
    RollEffects.of(meta(held, List.of(), Map.of(1, List.of(10, 20)), 20), config).shape(config);
    assertThat(config.getBoss().getOpenBreach()).isEqualTo(20);
    var tower = tower(config);

    tower.startNewRunAt(20);
    assertThat(tower.getCurrentTowerFloor().isBoss()).isFalse();
    assertThat(tower.getCurrentTowerFloor().getEnemies()).noneMatch(enemy -> enemy.isBoss());
    tower.startNewRunAt(10);
    assertThat(tower.getCurrentTowerFloor().isBoss()).isTrue();
    // It is not slain, so it never counts for the Toll's Guardians.
    assertThat(tower.getSlainGuardians()).isEmpty();
  }

  @Test
  void theBreachCountsAsSlainForEasingButOnlyBelowIt() {
    var open = new GameProperties().copy();
    open.getBoss().setOpenBreach(20);
    var closed = new GameProperties().copy();
    var easeBudget = closed.getEnemies().getEaseBudgetPerBoss();

    assertThat(tower(open).easedBaseBudget(15))
        .isCloseTo(
            tower(closed).easedBaseBudget(15) * (1 - easeBudget),
            org.assertj.core.data.Offset.offset(1e-9));
    assertThat(tower(open).easedBaseBudget(25)).isEqualTo(tower(closed).easedBaseBudget(25));
  }

  @Test
  void aBreachAlreadyAmongTheSlainIsNotEasedTwice() {
    var open = new GameProperties().copy();
    open.getBoss().setOpenBreach(20);
    var once = tower(new GameProperties().copy());
    var twice = tower(open);
    once.markBossCleared(20);
    twice.markBossCleared(20);

    assertThat(twice.easedBaseBudget(15)).isEqualTo(once.easedBaseBudget(15));
  }

  @Test
  void theBreachOnlyCountsWhileTheRollHoldsIt() {
    var config = new GameProperties().copy();

    RollEffects.of(meta(List.of(), List.of(), Map.of(1, List.of(20)), 20), config).shape(config);

    assertThat(config.getBoss().getOpenBreach()).isZero();
  }

  @Test
  void theBreachIsChosenFromGuardiansSlainInAnEarlierGenerationAndNeverTheSummit() {
    var held = List.of(InheritanceType.THE_OPEN_BREACH);
    var meta = meta(held, List.of(), Map.of(1, List.of(10, 20), 2, List.of(20, 30, 100)), 0);

    assertThat(OpenBreach.candidates(meta, 100)).containsExactly(10, 20, 30);
    assertThat(OpenBreach.resolve(meta, "BREACH_20", 100)).isEqualTo(20);
    assertThat(OpenBreach.resolve(meta, "BREACH_40", 100)).isZero();
    assertThat(OpenBreach.resolve(meta, "BREACH_100", 100)).isZero();
    assertThat(OpenBreach.resolve(meta, OpenBreach.NONE, 100)).isZero();
    assertThat(OpenBreach.resolve(meta, null, 100)).isZero();
    assertThat(OpenBreach.resolve(meta, "BREACH_x", 100)).isZero();
    assertThat(
            OpenBreach.resolve(
                meta(List.of(), List.of(), meta.slainGuardians(), 0), "BREACH_20", 100))
        .isZero();
  }
}

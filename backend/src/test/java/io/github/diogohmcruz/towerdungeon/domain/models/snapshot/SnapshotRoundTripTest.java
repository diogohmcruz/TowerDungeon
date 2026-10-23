package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemCategory;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemRating;
import io.github.diogohmcruz.towerdungeon.domain.items.ItemTier;
import io.github.diogohmcruz.towerdungeon.domain.items.LeftoverGenerator;
import io.github.diogohmcruz.towerdungeon.domain.models.BodyPart;
import io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.Tower;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.architecture.ArchitecturePlan;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import tools.jackson.databind.json.JsonMapper;

class SnapshotRoundTripTest {
  private GameProperties config;
  private RandomGenerator random;
  private ArchitecturePlan plan;

  @BeforeEach
  void setUp() {
    config = new GameProperties();
    random = new SplittableRandom(11);
    plan =
        ArchitecturePlan.generate(
            config.getArchitecture().getSeed(),
            config.getTower().getMaxFloor(),
            config.getBoss().getInterval(),
            config.getEnemies().getSurgeInterval());
  }

  private GameState midCampaign() {
    var state = new GameState(config);
    state.setCredit(250d);
    for (var type :
        List.of(UnitStats.WARRIOR, UnitStats.WARRIOR, UnitStats.ARCHER, UnitStats.MAGE)) {
      state.addUnits(type, List.of(state.recruit(type, random)));
    }
    var wounded = state.getUnits().get(UnitStats.WARRIOR).getFirst();
    wounded.receiveAttack(4d, null);
    var rating = new ItemRating(config.getItems().getWeights());
    wounded.equip(new LeftoverGenerator(random, config).generate(12), rating);
    wounded.equip(new LeftoverGenerator(random, config).generate(12), rating);
    var mage = state.getUnits().get(UnitStats.MAGE).getFirst();
    mage.spendMana(2d);
    state.getResources().put(ResourceType.MATERIALS, 400d);
    state.getResources().put(ResourceType.RELICS, 5d);
    assertThat(state.applyUpgrade(UpgradeType.UNLOCK_HEALER)).isTrue();
    assertThat(state.applyUpgrade(UpgradeType.SUPPLY_LINES)).isTrue();
    for (var i = 0; i < 4; i++) {
      state.completeExpedition();
    }
    for (var i = 0; i < 12; i++) {
      state.recordEnemyKill();
    }
    state.getTally().addFoodGrown(1234.5);
    state.getTally().addRelicsBanked(7);
    state.getTally().addCreditsBanked(55);
    state.recordFloorReached(12);
    state.buildRunSummary(RunSummary.EXTRACTED);
    state.getVillage().restore(37.5, 6);
    return state;
  }

  private GameState midExpedition() {
    var state = midCampaign();
    state.addUnits(UnitStats.PORTER, List.of(state.recruit(UnitStats.PORTER, random)));
    var tower =
        new Tower(
            config.getTower().getMaxFloor(),
            config.getBoss().copy(),
            config.getEnemies().copy(),
            random,
            plan);
    state.setTower(tower);
    state.enlistRandom(UnitStats.WARRIOR);
    state.enlistRandom(UnitStats.ARCHER);
    state.enlistRandom(UnitStats.PORTER);
    tower.startNewRun();
    state.startRun();
    tower.markBossCleared(10);
    for (var i = 0; i < 3; i++) {
      tower.moveToNextFloor();
    }
    state.dropGear(2, List.of(new LeftoverGenerator(random, config).generate(2)));
    var garrison = tower.getCurrentTowerFloor().getEnemies();
    garrison.getFirst().receiveAttack(3d, null);
    garrison.getFirst().damagePart(BodyPart.TORSO, 0.25);
    state.gatherLoot(ResourceType.MATERIALS, 12d);
    state.gatherCredits(33d);
    state.addSurplus(ItemTier.LEFTOVER);
    state.addSurplus(ItemTier.LEFTOVER);
    state.drainSupplies(9d);
    state.setRunStartFloor(0);
    state.recordEnemyKill();
    state.sendReinforcement(Map.of(UnitStats.MAGE, 1));
    state.advanceReinforcements();
    return state;
  }

  private GameState viaJson(GameState state) throws Exception {
    var mapper = JsonMapper.builder().build();
    var json = mapper.writeValueAsString(state.toSnapshot());
    var back = mapper.readValue(json, GameSnapshot.class);
    return GameState.restore(back, config, random, plan);
  }

  @Test
  void theTallyAndTheVillageTierSurviveASave() throws Exception {
    var original = midCampaign();
    original.getTally().addMaterialsBanked(1000);
    original.getTally().addFoodGrown(1000);
    original.recordFloorReached(25);
    original.getTally().addRelicsBanked(60);
    original.completeExpedition();
    original.completeExpedition();

    var restored = viaJson(original);

    assertThat(restored.getTally().toSnapshot()).isEqualTo(original.getTally().toSnapshot());
    assertThat(restored.getTally().getFoodGrown()).isEqualTo(2234.5);
    assertThat(restored.getTally().getPeakVillagers()).isEqualTo(20);
    assertThat(restored.getVillageTier()).isEqualTo(original.getVillageTier());
    assertThat(restored.getVillageTierLevel()).isEqualTo(4);
  }

  @Test
  void anOlderSaveWithoutATallyLoadsWithEmptyCountersAtTierOne() throws Exception {
    var snapshot = midCampaign().toSnapshot();
    var c = snapshot.campaign();
    var older =
        new GameSnapshot(
            new CampaignSnapshot(
                c.credit(),
                c.reserves(),
                c.vault(),
                c.upgrades(),
                c.milestones(),
                c.shortcutsOpened(),
                c.tower(),
                c.highestFloor(),
                c.expeditionsCompleted(),
                c.enemiesDefeated(),
                c.outcome(),
                c.lastExpeditionSummary(),
                c.villageFood(),
                c.villagers(),
                c.trades(),
                c.artifactsTaken(),
                null,
                0),
            null);

    var restored = GameState.restore(older, config, random, plan);

    assertThat(restored.getTally().getFoodGrown()).isZero();
    assertThat(restored.getTally().getPeakVillagers()).isEqualTo(6);
    assertThat(restored.getVillageTierLevel()).isEqualTo(1);
  }

  @Test
  void midCampaignRoundTripsToAnEqualState() throws Exception {
    var original = midCampaign();

    var restored = viaJson(original);

    assertThat(restored.toSnapshot()).isEqualTo(original.toSnapshot());
    assertThat(restored.getUnits().values().stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(
            original.getUnits().values().stream().flatMap(List::stream).toList());
    assertThat(restored)
        .usingRecursiveComparison()
        // maxSupplies is derived (the original's is stale between upgrades) and the run counters
        // are in-flight facts that only exist inside an Expedition.
        .ignoringFields(
            "units",
            "unitsOnTower",
            "config",
            "foodDistributionStrategy",
            "maxSupplies",
            "ledger",
            "runEnemiesDefeated")
        .isEqualTo(original);
    assertThat(restored.getVillage().getFood()).isEqualTo(37.5);
    assertThat(restored.getVillage().getVillagersCount()).isEqualTo(6);
  }

  @Test
  void wornItemsSurviveTheRoundTrip() throws Exception {
    var original = midCampaign();
    var worn = original.getUnits().get(UnitStats.WARRIOR).getFirst().getGear().getSlots();
    assertThat(worn).isNotEmpty();

    var restored = viaJson(original);

    var restoredWorn = restored.getUnits().get(UnitStats.WARRIOR).getFirst().getGear().getSlots();
    assertThat(restoredWorn).isEqualTo(worn);
  }

  @Test
  void derivedValuesAreRecomputedNotStored() throws Exception {
    var original = midCampaign();

    var restored = viaJson(original);

    assertThat(restored.getUnlockedUnits()).isEqualTo(original.getUnlockedUnits());
    assertThat(restored.isUnitUnlocked(UnitStats.HEALER)).isTrue();
    assertThat(restored.getDamageMultiplier()).isEqualTo(original.getDamageMultiplier());
    assertThat(restored.getSupplyCapacityBonus()).isEqualTo(original.getSupplyCapacityBonus());
    assertThat(restored.getMaxSupplies())
        .isEqualTo(config.getSupply().getBaseMax() + restored.getSupplyCapacityBonus());
    assertThat(restored.getAvailableUpgrades()).isEqualTo(original.getAvailableUpgrades());
    assertThat(restored.getMilestones()).isEqualTo(original.getMilestones());
    assertThat(restored.getShortcuts()).isEqualTo(original.getShortcuts());
  }

  @Test
  void aShortcutOnceOpenedStaysOpen() throws Exception {
    var original = midCampaign();
    assertThat(original.isStartFloorUnlocked(5)).isTrue();
    var snapshot = original.toSnapshot();
    var stripped =
        new GameSnapshot(
            new CampaignSnapshot(
                snapshot.campaign().credit(),
                snapshot.campaign().reserves(),
                snapshot.campaign().vault(),
                snapshot.campaign().upgrades(),
                snapshot.campaign().milestones(),
                snapshot.campaign().shortcutsOpened(),
                snapshot.campaign().tower(),
                snapshot.campaign().highestFloor(),
                0,
                0,
                snapshot.campaign().outcome(),
                snapshot.campaign().lastExpeditionSummary(),
                snapshot.campaign().villageFood(),
                snapshot.campaign().villagers(),
                snapshot.campaign().trades(),
                snapshot.campaign().artifactsTaken(),
                snapshot.campaign().tally(),
                snapshot.campaign().villageTier()),
            null);

    var restored = GameState.restore(stripped, config, random, plan);

    assertThat(restored.isStartFloorUnlocked(5)).isTrue();
  }

  @Test
  void roundTripInTheMiddleOfAnExpeditionKeepsTheFloorParty_SuppliesAndWaves() throws Exception {
    var original = midExpedition();
    assertThat(original.getReinforcements()).isNotEmpty();
    var garrison = original.getTower().getCurrentTowerFloor().getEnemies();

    var restored = viaJson(original);

    assertThat(restored.toSnapshot()).isEqualTo(original.toSnapshot());
    assertThat(restored.isExpeditionActive()).isTrue();
    assertThat(restored.getTower().getCurrentFloor()).isEqualTo(3);
    assertThat(restored.getTower().getCurrentTowerFloor().getEnemies()).isEqualTo(garrison);
    assertThat(restored.getTower().getSlainGuardians()).containsExactly(10);
    assertThat(restored.getUnitsOnTower().values().stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(
            original.getUnitsOnTower().values().stream().flatMap(List::stream).toList());
    assertThat(restored.getReinforcements()).isEqualTo(original.getReinforcements());
    assertThat(restored.getSupplies()).isEqualTo(original.getSupplies());
    assertThat(restored.getMaxSupplies()).isEqualTo(original.getMaxSupplies());
    assertThat(restored.getCarriedLoot()).isEqualTo(original.getCarriedLoot());
    assertThat(restored.getCarriedCredits()).isEqualTo(33d);
    assertThat(restored.getSurplus()).containsEntry(ItemTier.LEFTOVER, 2);
    assertThat(restored.getRunEnemiesDefeated()).isEqualTo(original.getRunEnemiesDefeated());
  }

  @Test
  void aWipeForfeitsTheSurplusAndExtractionResetsIt() {
    var state = midExpedition();
    assertThat(state.getSurplus()).containsEntry(ItemTier.LEFTOVER, 2);
    state.forfeitLoot();
    assertThat(state.getSurplus()).isEmpty();

    state.addSurplus(ItemTier.LEFTOVER);
    state.bankLoot();
    assertThat(state.getSurplus()).isEmpty();
  }

  @Test
  void unitStrengthsAreReappliedNotRerolled() throws Exception {
    var original = midCampaign();
    var before = original.getUnits().get(UnitStats.ARCHER).getFirst();

    var after = viaJson(original).getUnits().get(UnitStats.ARCHER).getFirst();

    assertThat(after.getId()).isEqualTo(before.getId());
    assertThat(after.getName()).isEqualTo(before.getName());
    assertThat(after.getHealthFactor()).isEqualTo(before.getHealthFactor());
    assertThat(after.getDamageFactor()).isEqualTo(before.getDamageFactor());
    assertThat(after.getMaxHealth()).isEqualTo(before.getMaxHealth());
  }

  @Test
  void anAffinitySurvivesARoundTripAndNoAffinityStaysNone() throws Exception {
    var original = midCampaign();
    var archer = original.getUnits().get(UnitStats.ARCHER).getFirst();
    var mage = original.getUnits().get(UnitStats.MAGE).getFirst();
    archer.setAffinity(ItemCategory.BOOTS);
    mage.setAffinity(null);

    var restored = viaJson(original);

    assertThat(restored.getUnits().get(UnitStats.ARCHER).getFirst().getAffinity())
        .isEqualTo(ItemCategory.BOOTS);
    assertThat(restored.getUnits().get(UnitStats.MAGE).getFirst().getAffinity()).isNull();
  }

  @Test
  void aWoundedUnitKeepsItsHealthAndAMageItsMana() throws Exception {
    var original = midCampaign();
    var warrior = original.getUnits().get(UnitStats.WARRIOR).getFirst();
    var mage = original.getUnits().get(UnitStats.MAGE).getFirst();
    warrior.damagePart(io.github.diogohmcruz.towerdungeon.domain.models.BodyPart.LEFT_ARM, 999d);
    assertThat(warrior.getCurrentHealth()).isLessThan(warrior.getMaxHealth());

    var restored = viaJson(original);

    Unit warriorBack = restored.getUnits().get(UnitStats.WARRIOR).getFirst();
    Unit mageBack = restored.getUnits().get(UnitStats.MAGE).getFirst();
    assertThat(warriorBack.getCurrentHealth()).isEqualTo(warrior.getCurrentHealth());
    assertThat(warriorBack.getParts()).isEqualTo(warrior.getParts());
    assertThat(warriorBack.getDamage()).isEqualTo(warrior.getDamage());
    assertThat(mageBack.getCurrentMana()).isEqualTo(mage.getCurrentMana());
    assertThat(mageBack.getMaxMana()).isEqualTo(mage.getMaxMana());
  }

  @Test
  void sanityAndPhobiasSurviveARoundTripWithTheirKnownFlags() throws Exception {
    var original = midCampaign();
    var archer = original.getUnits().get(UnitStats.ARCHER).getFirst();
    archer.addPhobia(EnemyStats.CHIMERA);
    archer.addPhobia(EnemyStats.DRAGON);
    archer.getPhobias().getFirst().reveal();
    archer.changeSanity(-37.5);

    var back = viaJson(original).getUnits().get(UnitStats.ARCHER).getFirst();

    assertThat(back.getSanity()).isEqualTo(archer.getSanity());
    assertThat(back.getMaxSanity()).isEqualTo(archer.getMaxSanity());
    assertThat(back.getPhobias()).isEqualTo(archer.getPhobias());
    assertThat(back.getPhobias().get(0).isKnown()).isTrue();
    assertThat(back.getPhobias().get(1).isKnown()).isFalse();
    assertThat(back.damageFactorAgainst(EnemyStats.DRAGON))
        .isEqualTo(archer.damageFactorAgainst(EnemyStats.DRAGON));
  }

  @Test
  void aPristineCampaignIsPristineUntilSomethingIsDone() {
    var state = new GameState(config);
    assertThat(state.isPristine()).isTrue();

    state.setCredit(state.getCredit() - 1);

    assertThat(state.isPristine()).isFalse();
  }

  @Test
  void recruitingOrBuyingUpgradesEndsPristine() {
    var recruited = new GameState(config);
    recruited.spendCredit(UnitStats.WARRIOR.getCost());
    recruited.addUnits(UnitStats.WARRIOR, List.of(recruited.recruit(UnitStats.WARRIOR, random)));
    assertThat(recruited.isPristine()).isFalse();

    var upgraded = new GameState(config);
    upgraded.getResources().put(ResourceType.MATERIALS, 100d);
    upgraded.applyUpgrade(UpgradeType.SUPPLY_LINES);
    assertThat(upgraded.isPristine()).isFalse();
  }

  @Test
  void anExpeditionRecordIsOnlyPresentWhileOneIsActive() {
    assertThat(midCampaign().toSnapshot().expedition()).isNull();
    assertThat(midExpedition().toSnapshot().expedition()).isNotNull();
  }

  @Test
  void theTowerOfARestoredCampaignGetsItsOwnConfigCopies() {
    var restored = GameState.restore(midExpedition().toSnapshot(), config, random, plan);

    assertThat(restored.getTower().getBossConfig()).isNotSameAs(config.getBoss());
    assertThat(restored.getTower().getBossConfig()).isEqualTo(config.getBoss());
    assertThat(restored.getTower().getEnemiesConfig()).isNotSameAs(config.getEnemies());
    assertThat(restored.getTower().getEnemiesConfig()).isEqualTo(config.getEnemies());
  }
}

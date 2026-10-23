package io.github.diogohmcruz.towerdungeon.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;

/** Tomes bought with Knowledge, and Posts bought with Materials. */
class VillageTomesTest {
  private static Village village(double knowledge) {
    var config = new GameProperties.Village();
    config.setStartingFood(100);
    config.setStartingVillagers(20);
    var village = new Village(config, new GameProperties.Trades());
    village.restoreTrades(0, 0, Map.of(), knowledge, 0, false, 0, 0);
    return village;
  }

  @Test
  void aTomeCostsSixtyKnowledgeAndGrowsByHalfEachLevel() {
    var village = village(0);

    assertThat(village.tomeCost(Tome.TILLERS_RECORD)).isEqualTo(60);
    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 1), Map.of());
    assertThat(village.tomeCost(Tome.TILLERS_RECORD)).isEqualTo(90);
    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 2), Map.of());
    assertThat(village.tomeCost(Tome.TILLERS_RECORD)).isEqualTo(135);
    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 3), Map.of());
    assertThat(village.tomeCost(Tome.TILLERS_RECORD)).isEqualTo(202);
  }

  @Test
  void buyingATomeSpendsKnowledgeAndRaisesItsLevel() {
    var village = village(70);

    assertThat(village.buyTome(Tome.SEAM_CHARTS, 0)).isEmpty();

    assertThat(village.getKnowledge()).isEqualTo(10);
    assertThat(village.tomeLevel(Tome.SEAM_CHARTS)).isEqualTo(1);
    assertThat(village.tomeLevel(Tome.TILLERS_RECORD)).isZero();
  }

  @Test
  void aTomeYouCannotAffordIsRefusedWithTheShortfall() {
    var village = village(14);

    var refused = village.buyTome(Tome.TILLERS_RECORD, 0);

    assertThat(refused).contains("Tome 1 costs 60. Knowledge 14.");
    assertThat(village.tomeLevel(Tome.TILLERS_RECORD)).isZero();
    assertThat(village.getKnowledge()).isEqualTo(14);
  }

  @Test
  void aLevelTheStockroomCannotHoldIsGatedWithTheReason() {
    var village = village(500);
    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 1), Map.of());

    assertThat(village.tomeGate(Tome.TILLERS_RECORD, 5))
        .isEqualTo("Tome 2 costs 90. Stockroom holds 80. Needs Highest Floor 8.");
    assertThat(village.buyTome(Tome.TILLERS_RECORD, 5))
        .contains("Tome 2 costs 90. Stockroom holds 80. Needs Highest Floor 8.");
    assertThat(village.tomeLevel(Tome.TILLERS_RECORD)).isEqualTo(1);
    assertThat(village.getKnowledge()).isEqualTo(500);
  }

  @Test
  void theGateLiftsAtTheFloorNamedAndTheTomeCanBeBought() {
    var village = village(500);
    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 1), Map.of());

    assertThat(village.tomeGate(Tome.TILLERS_RECORD, 8)).isNull();
    assertThat(village.buyTome(Tome.TILLERS_RECORD, 8)).isEmpty();
    assertThat(village.tomeLevel(Tome.TILLERS_RECORD)).isEqualTo(2);
    assertThat(village.tomeGate(Tome.TILLERS_RECORD, 8))
        .isEqualTo("Tome 3 costs 135. Stockroom holds 92. Needs Highest Floor 19.");
  }

  @Test
  void tillersRecordRaisesFieldsOutputByAQuarterPerLevel() {
    var village = village(0);
    assertThat(village.getProductionRate()).isEqualTo(10.0);

    village.restoreUpgrades(Map.of(Tome.TILLERS_RECORD, 2), Map.of());

    assertThat(village.getProductionRate()).isCloseTo(15.0, within(1e-9));
    assertThat(village.preview(Trade.BREAKER, 1, 0, 0).fields()).isCloseTo(14.25, within(1e-9));
  }

  @Test
  void seamChartsRaisesBreakerOutputByAQuarterPerLevel() {
    var village = village(0);
    village.assign(Trade.BREAKER, 1);

    village.restoreUpgrades(Map.of(Tome.SEAM_CHARTS, 1), Map.of());

    assertThat(village.materialsPerTick(0)).isCloseTo(0.25 * 1.25, within(1e-9));
    assertThat(village.knowledgePerTick(0)).isZero();
  }

  @Test
  void postsStartAtTenAndGainFivePerLevelForTheQuarryAndTheStudyOnly() {
    var village = village(0);
    assertThat(village.posts(Trade.BREAKER)).isEqualTo(10);

    village.restoreUpgrades(Map.of(), Map.of(Trade.BREAKER, 2));

    assertThat(village.posts(Trade.BREAKER)).isEqualTo(20);
    assertThat(village.posts(Trade.SCRIBE)).isEqualTo(10);
    assertThat(village.posts(Trade.TILLER)).isNull();
  }

  @Test
  void aPostUpgradeCostsTwentyFiveMaterialsAndGrowsByThreeFifthsEachLevel() {
    var village = village(0);

    assertThat(village.postCost(Trade.BREAKER)).isEqualTo(25);
    village.restoreUpgrades(Map.of(), Map.of(Trade.BREAKER, 1));
    assertThat(village.postCost(Trade.BREAKER)).isEqualTo(40);
    village.restoreUpgrades(Map.of(), Map.of(Trade.BREAKER, 2));
    assertThat(village.postCost(Trade.BREAKER)).isEqualTo(64);
    village.restoreUpgrades(Map.of(), Map.of(Trade.BREAKER, 3));
    assertThat(village.postCost(Trade.BREAKER)).isEqualTo(102);
  }

  @Test
  void theFieldsHaveNoPostsToBuy() {
    var village = village(0);

    assertThat(village.postUpgradeBlocked(Trade.TILLER, 1000))
        .contains("The Fields have no Posts to raise.");
  }

  @Test
  void aPostUpgradeNeedsTheMaterialsInTheVault() {
    var village = village(0);

    assertThat(village.postUpgradeBlocked(Trade.SCRIBE, 24.9))
        .contains("Posts cost 25 Materials. The Vault holds 24.");
    assertThat(village.postUpgradeBlocked(Trade.SCRIBE, 25)).isEmpty();
  }

  @Test
  void aWorkplaceFillsItsRaisedPostsAndNoMore() {
    var village = village(0);
    village.restore(100, 40);
    village.restoreUpgrades(Map.of(), Map.of(Trade.BREAKER, 1));
    for (var i = 0; i < 15; i++) {
      assertThat(village.assign(Trade.BREAKER, 1)).as("hand %d", i).isEmpty();
    }

    assertThat(village.assign(Trade.BREAKER, 1)).contains("Quarry: 15 of 15 Posts.");
  }
}

package io.github.diogohmcruz.towerdungeon.agent.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The per-Floor table is computed from the rows alone, and keeps two games apart. */
class BalanceReportTest {
  private static Map<String, Object> floor(
      String hash,
      int floor,
      boolean cleared,
      int party,
      int end,
      double ticks,
      double hp,
      double dealt) {
    var row = new LinkedHashMap<String, Object>();
    row.put("row", "floor");
    row.put("engine", "td-1");
    row.put("config_hash", hash);
    row.put("policy", "greedy");
    row.put("floor", floor);
    row.put("guardian", floor % 10 == 0);
    row.put("surge", false);
    row.put("cleared", cleared);
    row.put("end", cleared ? "CLEARED" : "WIPED");
    row.put("party_start", Map.of("WARRIOR", party));
    row.put("party_size_end", end);
    row.put("ticks_on_floor", ticks);
    row.put("garrison_health_start", hp);
    row.put("damage_dealt", dealt);
    row.put("supplies_drained", 10d);
    row.put("damage_taken_combat", 4d);
    row.put("damage_taken_attrition", 2d);
    row.put("damage_taken_starvation", 0d);
    row.put("units_lost", List.of());
    return row;
  }

  @Test
  void theSizingRuleIsComputedFromTheRowsNotQuoted() {
    // 4 Units for 10 ticks each: 40 Unit-ticks, 200 damage: d = 5 per Unit per tick.
    // HP 125: sqrt(125 / (1.25 x 5)) = sqrt(20) = 4.472.
    var builder = new BalanceReport.Builder(2);
    builder.add(floor("h1", 3, true, 4, 4, 10, 125, 200));
    builder.add(floor("h1", 3, true, 4, 4, 10, 125, 200));

    var tables = builder.build();

    assertThat(tables).hasSize(1);
    var line = tables.getFirst().floors().getFirst();
    assertThat(line.floor()).isEqualTo(3);
    assertThat(line.samples()).isEqualTo(2);
    assertThat(line.thin()).isFalse();
    assertThat(line.damagePerUnitTick()).isCloseTo(5.0, within(1e-9));
    assertThat(line.meanGarrisonHealth()).isCloseTo(125.0, within(1e-9));
    assertThat(line.unitsNeeded()).isCloseTo(Math.sqrt(20), within(1e-9));
    assertThat(line.clearRate()).isEqualTo(1.0);
    assertThat(line.wipeRate()).isZero();
    assertThat(line.meanSuppliesDrained()).isEqualTo(10.0);
  }

  @Test
  void aFloorWithFewerRowsThanTheThresholdIsMarkedThin() {
    var builder = new BalanceReport.Builder(30);
    builder.add(floor("h1", 5, true, 4, 4, 10, 100, 100));

    assertThat(builder.build().getFirst().floors().getFirst().thin()).isTrue();
    assertThat(BalanceReport.markdown(builder.build(), 30, 0)).contains("thin");
  }

  @Test
  void aFloorWhereNothingWasDealtHasNoUnitsNeededAndSaysSo() {
    var builder = new BalanceReport.Builder(1);
    builder.add(floor("h1", 2, false, 3, 0, 5, 80, 0));

    var line = builder.build().getFirst().floors().getFirst();

    assertThat(line.unitsNeeded()).isNaN();
    assertThat(line.clearRate()).isZero();
    assertThat(line.wipeRate()).isEqualTo(1.0);
    assertThat(BalanceReport.markdown(builder.build(), 1, 0)).contains("n/a");
  }

  @Test
  void rowsFromTwoBalancesAreTwoTablesAndNeverAveraged() {
    var builder = new BalanceReport.Builder(1);
    builder.add(floor("before", 4, true, 4, 4, 10, 100, 100));
    builder.add(floor("after", 4, true, 4, 4, 10, 300, 100));

    var tables = builder.build();
    var markdown = BalanceReport.markdown(tables, 1, 0);

    assertThat(tables).hasSize(2);
    assertThat(tables)
        .extracting(BalanceReport.Table::configHash)
        .containsExactly("after", "before");
    assertThat(tables.get(0).floors().getFirst().meanGarrisonHealth()).isEqualTo(300.0);
    assertThat(tables.get(1).floors().getFirst().meanGarrisonHealth()).isEqualTo(100.0);
    assertThat(markdown).contains("NOT COMPARABLE", "config_hash after", "config_hash before");
  }

  @Test
  void campaignAndExpeditionRowsFeedThePolicyTable() {
    var builder = new BalanceReport.Builder(1);
    for (var highest : new int[] {2, 4, 6, 8}) {
      var campaign = new LinkedHashMap<String, Object>();
      campaign.put("row", "campaign");
      campaign.put("engine", "td-1");
      campaign.put("config_hash", "h1");
      campaign.put("policy", "greedy");
      campaign.put("highest_floor", highest);
      campaign.put("stop", "MAX_EXPEDITIONS");
      campaign.put("expedition_ticks", 100);
      campaign.put("village_ticks", 50);
      campaign.put("village_food_grown", 500);
      builder.add(campaign);
    }
    for (var outcome : new String[] {"WIPED", "EXTRACTED", "EXTRACTED", "EXTRACTED"}) {
      var expedition = new LinkedHashMap<String, Object>();
      expedition.put("row", "expedition");
      expedition.put("engine", "td-1");
      expedition.put("config_hash", "h1");
      expedition.put("policy", "greedy");
      expedition.put("outcome", outcome);
      expedition.put("total_ticks", 20);
      builder.add(expedition);
    }

    var policy = builder.build().getFirst().policies().getFirst();

    assertThat(policy.campaigns()).isEqualTo(4);
    assertThat(policy.meanHighestFloor()).isEqualTo(5.0);
    assertThat(policy.maxHighestFloor()).isEqualTo(8);
    assertThat(policy.wipeRate()).isEqualTo(0.25);
    assertThat(policy.meanExpeditionTicks()).isEqualTo(20.0);
    assertThat(policy.villageTicksPerExpeditionTick()).isCloseTo(0.5, within(1e-9));
    assertThat(policy.villageFoodPerVillageTick()).isCloseTo(10.0, within(1e-9));
    assertThat(policy.stops()).containsEntry("MAX_EXPEDITIONS", 4);
  }

  @Test
  void theTableCanBeCutAtAFloor() {
    var builder = new BalanceReport.Builder(1);
    builder.add(floor("h1", 1, true, 4, 4, 10, 10, 10));
    builder.add(floor("h1", 9, true, 4, 4, 10, 10, 10));

    var markdown = BalanceReport.markdown(builder.build(), 1, 5);

    assertThat(markdown).contains("| 1 |").doesNotContain("| 9 |");
  }
}

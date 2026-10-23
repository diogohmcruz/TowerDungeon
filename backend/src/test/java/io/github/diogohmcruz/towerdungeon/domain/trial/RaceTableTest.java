package io.github.diogohmcruz.towerdungeon.domain.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class RaceTableTest {
  private static final Instant AT = Instant.parse("2026-10-24T10:00:00Z");

  private static TrialResult finished(String player, String alias, long seconds, int fallen) {
    return new TrialResult(
        "r",
        player,
        1,
        alias,
        ContenderLabel.HUMAN,
        ContenderStatus.FINISHED,
        seconds,
        seconds,
        10,
        10,
        new TrialResult.Cost(5, 1, fallen, 0),
        Map.of(),
        AT);
  }

  private static TrialResult stalled(String player, String alias, int floor, long arrivedAt) {
    return new TrialResult(
        "r",
        player,
        1,
        alias,
        ContenderLabel.HUMAN,
        ContenderStatus.TIMED_OUT,
        5400,
        5400,
        10,
        floor,
        TrialResult.Cost.NONE,
        Map.of(floor, arrivedAt),
        AT);
  }

  private static List<String> aliases(List<RaceTable.Row> rows) {
    return rows.stream().map(RaceTable.Row::alias).toList();
  }

  @Test
  void finishersComeFirstByTimeThenTheRestByFloorThenEarlierArrival() {
    var table =
        RaceTable.of(
            "r",
            List.of(
                stalled("p1", "Slow", 4, 900),
                finished("p2", "Second", 2500, 1),
                stalled("p3", "Higher", 7, 3000),
                finished("p4", "First", 2400, 1),
                stalled("p5", "Early", 4, 500)));

    assertThat(aliases(table.hands()))
        .containsExactly("First", "Second", "Higher", "Early", "Slow");
    assertThat(table.hands().stream().map(RaceTable.Row::place)).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  void equalTimesAreTiedAndCostNeverBreaksTheTie() {
    var table =
        RaceTable.of(
            "r",
            List.of(
                finished("p1", "Lean", 2400, 12),
                finished("p2", "Costly", 2400, 112),
                finished("p3", "Later", 2401, 0)));

    var rows = table.hands();
    assertThat(rows.get(0).place()).isEqualTo(1);
    assertThat(rows.get(1).place()).isEqualTo(1);
    assertThat(rows.get(0).tied()).isTrue();
    assertThat(rows.get(1).tied()).isTrue();
    assertThat(rows.get(2).place()).isEqualTo(3);
    assertThat(rows.get(2).tied()).isFalse();
  }

  @Test
  void shuffledInputGivesTheSameTableEveryTime() {
    var results = new ArrayList<TrialResult>();
    for (var i = 0; i < 30; i++) {
      results.add(finished("p" + i, "c" + (i % 7), 2000 + (i % 5) * 10, i));
    }
    results.add(stalled("q1", "x", 3, 100));
    results.add(stalled("q2", "y", 3, 100));
    var expected = RaceTable.of("r", results);

    var random = new Random(1);
    for (var round = 0; round < 25; round++) {
      Collections.shuffle(results, random);
      assertThat(RaceTable.of("r", results)).isEqualTo(expected);
    }
  }

  @Test
  void aContendersBestAttemptCounts() {
    var worse = finished("p1", "Ada", 3000, 1);
    var better =
        new TrialResult(
            "r",
            "p1",
            2,
            "Ada",
            ContenderLabel.HUMAN,
            ContenderStatus.FINISHED,
            2000,
            2000,
            10,
            10,
            TrialResult.Cost.NONE,
            Map.of(),
            AT);

    var table = RaceTable.of("r", List.of(worse, better));

    assertThat(table.hands()).hasSize(1);
    assertThat(table.hands().getFirst().seconds()).isEqualTo(2000);
    assertThat(table.hands().getFirst().attempt()).isEqualTo(2);
  }

  @Test
  void agentsAreOrderedInATableOfTheirOwn() {
    var agent =
        new TrialResult(
            "r",
            "bot",
            1,
            "Bot",
            ContenderLabel.AGENT,
            ContenderStatus.FINISHED,
            100,
            100,
            10,
            10,
            TrialResult.Cost.NONE,
            Map.of(),
            AT);

    var table = RaceTable.of("r", List.of(finished("p1", "Ada", 2000, 1), agent));

    assertThat(aliases(table.hands())).containsExactly("Ada");
    assertThat(aliases(table.agents())).containsExactly("Bot");
    assertThat(table.hands().getFirst().place()).isEqualTo(1);
    assertThat(table.contendersWith("p1")).isEqualTo(1);
  }

  @Test
  void aRowCarriesTheTwoColumnsAndNothingThatSumsThem() {
    var row = RaceTable.of("r", List.of(finished("p1", "Ada", 2000, 9))).hands().getFirst();

    assertThat(row.cost().unitsLost()).isEqualTo(9);
    assertThat(RaceTable.Row.class.getRecordComponents())
        .extracting(c -> c.getName().toLowerCase())
        .noneMatch(name -> name.contains("score") || name.contains("total"));
  }

  @Test
  void theViewerSeesTheirOwnRowMarkedAndNoIds() throws Exception {
    var table =
        RaceTable.of("r", List.of(finished("p1", "Ada", 2000, 1), finished("p2", "Bo", 2100, 1)))
            .viewedBy("p2");

    assertThat(table.hands().stream().filter(RaceTable.Row::you).map(RaceTable.Row::alias))
        .containsExactly("Bo");
    var json = JsonMapper.builder().build().writeValueAsString(table);
    assertThat(json).doesNotContain("\"p1\"").doesNotContain("\"p2\"").doesNotContain("playerId");
  }
}

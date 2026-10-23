package io.github.diogohmcruz.towerdungeon.domain.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class ResultsStoreTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  private static TrialResult result(String player, int attempt, long seconds) {
    return new TrialResult(
        "r",
        player,
        attempt,
        "Ada",
        ContenderLabel.HUMAN,
        ContenderStatus.FINISHED,
        seconds,
        seconds,
        10,
        10,
        new TrialResult.Cost(4, 1, 7, 150.5),
        Map.of(1, 30L, 2, 90L),
        Instant.parse("2026-10-24T10:00:00Z"));
  }

  @Test
  void resultsAndTheAttemptCounterSurviveARestartKeyedByRaceAndPlayer(@TempDir Path dir) {
    var ledger = new AttemptLedger(dir, mapper);
    ledger.start("r", "p");
    ledger.close(result("p", 1, 2000));
    ledger.start("r", "q");
    ledger.start("other", "p");
    ledger.drain();

    var reopened = new AttemptLedger(dir, mapper);

    assertThat(reopened.standing("r", "p").started()).isEqualTo(1);
    assertThat(reopened.standing("r", "p").currentClosed()).isTrue();
    assertThat(reopened.standing("r", "q").currentClosed()).isFalse();
    assertThat(reopened.standing("other", "p").started()).isEqualTo(1);
    assertThat(reopened.results("r")).containsExactly(result("p", 1, 2000));
    assertThat(reopened.results("other")).isEmpty();
  }

  @Test
  void aResultIsKeptOnceEvenIfReportedAgain(@TempDir Path dir) {
    var ledger = new AttemptLedger(dir, mapper);
    ledger.start("r", "p");

    assertThat(ledger.close(result("p", 1, 2000))).isTrue();
    assertThat(ledger.close(result("p", 1, 1))).isFalse();
    ledger.drain();

    assertThat(new AttemptLedger(dir, mapper).results("r")).hasSize(1);
  }

  @Test
  void theFileRotatesAndAllOfItIsReadBack(@TempDir Path dir) throws Exception {
    var ledger = new AttemptLedger(dir, mapper, 300);
    for (var i = 0; i < 12; i++) {
      var player = "p" + i;
      ledger.start("r", player);
      ledger.close(result(player, 1, 1000 + i));
      ledger.drain();
    }

    try (var files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()).toList())
          .contains("r.jsonl", "r.1.jsonl", "r.2.jsonl");
    }
    var reopened = new AttemptLedger(dir, mapper, 300);
    assertThat(reopened.results("r")).hasSize(12);
    assertThat(reopened.standing("r", "p11").currentClosed()).isTrue();
    assertThat(reopened.table("r").hands()).hasSize(12);
  }

  @Test
  void aTornLastLineIsSkipped(@TempDir Path dir) throws Exception {
    var ledger = new AttemptLedger(dir, mapper);
    ledger.start("r", "p");
    ledger.close(result("p", 1, 2000));
    ledger.drain();
    Files.writeString(
        dir.resolve("r.jsonl"), "{\"event\":\"CLOSE\",\"playerId\":\"z", StandardOpenOption.APPEND);

    var reopened = new AttemptLedger(dir, mapper);

    assertThat(reopened.results("r")).hasSize(1);
    assertThat(reopened.standing("r", "p").started()).isEqualTo(1);
  }
}

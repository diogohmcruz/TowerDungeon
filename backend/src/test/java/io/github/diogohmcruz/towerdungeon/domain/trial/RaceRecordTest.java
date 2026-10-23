package io.github.diogohmcruz.towerdungeon.domain.trial;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RaceRecordTest {
  private static final Instant AT = Instant.parse("2026-10-24T23:59:59Z");

  private static TrialResult finished(String player, long seconds, int fallen) {
    return new TrialResult(
        "r",
        player,
        1,
        player,
        ContenderLabel.HUMAN,
        ContenderStatus.FINISHED,
        seconds,
        seconds,
        20,
        20,
        new TrialResult.Cost(5, 1, fallen, 0),
        Map.of(),
        AT);
  }

  @Test
  void theLineIsPlain() {
    var table =
        RaceTable.of(
            "r",
            List.of(finished("a", 2000, 3), finished("b", 2100, 4), finished("me", 2468, 112)));

    var record =
        RaceRecord.of("r", 20, table.rowOf("me").orElseThrow(), table.contendersWith("me"), AT);

    assertThat(record.line())
        .isEqualTo("Race of 2026-10-24. Floor 20. 3rd of 3. 41:08. 112 fallen.");
    assertThat(record.finishFloor()).isEqualTo(20);
  }

  @Test
  void aTiedPlaceSaysSo() {
    var table = RaceTable.of("r", List.of(finished("a", 2000, 3), finished("b", 2000, 9)));

    var record =
        RaceRecord.of("r", 20, table.rowOf("b").orElseThrow(), table.contendersWith("b"), AT);

    assertThat(record.line()).contains("1st of 2 (tied).");
  }

  @Test
  void ordinalsFollowEnglish() {
    assertThat(List.of(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111))
        .map(RaceRecord::ordinal)
        .containsExactly(
            "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th");
  }
}

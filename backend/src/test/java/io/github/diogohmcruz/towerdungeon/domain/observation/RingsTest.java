package io.github.diogohmcruz.towerdungeon.domain.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;

class RingsTest {
  @Test
  void theEventLogKeepsTheNewestFiveHundredAndIdsKeepRising() {
    var log = new EventLog();

    for (var i = 0; i < 700; i++) {
      log.append(i, "TICK", Map.of("n", i));
    }

    assertThat(log.size()).isEqualTo(EventLog.DEFAULT_CAPACITY);
    assertThat(log.lastId()).isEqualTo(700);
    assertThat(log.since(0)).hasSize(500).first().extracting(EventLog.Event::id).isEqualTo(201L);
    assertThat(log.since(698)).extracting(EventLog.Event::id).containsExactly(699L, 700L);
    assertThat(log.since(700)).isEmpty();
  }

  @Test
  void anEventsDataIsACopy() {
    var data = new java.util.HashMap<String, Object>(Map.of("a", 1));
    var log = new EventLog(3);
    log.append(1, "X", data);

    data.put("a", 2);

    assertThat(log.since(0).getFirst().data()).containsEntry("a", 1);
  }

  @Test
  void aZeroCapacityIsRefused() {
    assertThatThrownBy(() -> new EventLog(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ExpeditionSummaries(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theSummaryListKeepsTheLastFiftyOldestFirst() {
    var summaries = new ExpeditionSummaries();

    for (var i = 0; i < 60; i++) {
      summaries.add(new RunSummary("EXTRACTED", 0, i, i, 0, 0d, 0d, 0d, 0, 1, java.util.List.of()));
    }

    assertThat(summaries.size()).isEqualTo(ExpeditionSummaries.DEFAULT_CAPACITY);
    assertThat(summaries.all().getFirst().highestFloor()).isEqualTo(10);
    assertThat(summaries.all().getLast().highestFloor()).isEqualTo(59);
  }
}

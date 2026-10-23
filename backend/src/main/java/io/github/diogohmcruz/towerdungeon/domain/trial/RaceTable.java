package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A race's finished table. Each Contender is one row, their best attempt; hands and agents are
 * ordered in separate tables. A row carries the two columns side by side, Order of Arrival (the
 * place, shared when the times or the Floors and arrivals are equal) and Cost, and nothing that
 * adds them. Cost never breaks a tie. The rows are put in order by value alone, so the order slots
 * were visited or results were read in cannot change the table.
 */
public record RaceTable(String raceId, List<Row> hands, List<Row> agents) {

  /** One second of a Floor's first arrival. */
  public record Arrival(int floor, long seconds) {}

  /**
   * @param place the place in the Order of Arrival; tied rows share it
   * @param tied whether another row shares the place
   * @param finished whether the Finish Floor's Guardian was slain
   * @param seconds the time to Finish when finished, else the time the attempt ran
   * @param arrivalSeconds when the highest Floor was first reached
   * @param timeline the second of each Floor's first arrival, lowest Floor first
   * @param you whether the viewer is this row
   */
  public record Row(
      int place,
      boolean tied,
      String alias,
      boolean finished,
      ContenderStatus status,
      long seconds,
      int highestFloor,
      long arrivalSeconds,
      int attempt,
      TrialResult.Cost cost,
      List<Arrival> timeline,
      boolean you,
      @JsonIgnore String playerId) {}

  /** Builds the table of the closed attempts of a race, however they are ordered. */
  public static RaceTable of(String raceId, Collection<TrialResult> attempts) {
    return new RaceTable(
        raceId, rows(attempts, ContenderLabel.HUMAN), rows(attempts, ContenderLabel.AGENT));
  }

  private static List<Row> rows(Collection<TrialResult> attempts, ContenderLabel label) {
    var best = new TreeMap<String, TrialResult>();
    for (var result : attempts) {
      best.merge(
          result.playerId(),
          result,
          (a, b) ->
              TrialResult.ARRIVAL.thenComparingInt(TrialResult::attempt).compare(a, b) <= 0
                  ? a
                  : b);
    }
    var chosen =
        best.values().stream()
            .filter(result -> result.label() == label)
            .sorted(
                TrialResult.ARRIVAL
                    .thenComparing(r -> r.alias().toLowerCase(Locale.ROOT))
                    .thenComparing(TrialResult::alias)
                    .thenComparing(TrialResult::playerId))
            .toList();
    var rows = new ArrayList<Row>();
    for (var i = 0; i < chosen.size(); i++) {
      var result = chosen.get(i);
      var place = i + 1;
      var tied = false;
      if (i > 0 && TrialResult.ARRIVAL.compare(chosen.get(i - 1), result) == 0) {
        place = rows.get(i - 1).place();
        tied = true;
        rows.set(i - 1, withTied(rows.get(i - 1)));
      }
      rows.add(rowOf(result, place, tied));
    }
    return List.copyOf(rows);
  }

  private static Row withTied(Row row) {
    return new Row(
        row.place(),
        true,
        row.alias(),
        row.finished(),
        row.status(),
        row.seconds(),
        row.highestFloor(),
        row.arrivalSeconds(),
        row.attempt(),
        row.cost(),
        row.timeline(),
        row.you(),
        row.playerId());
  }

  private static Row rowOf(TrialResult result, int place, boolean tied) {
    var timeline =
        new TreeMap<>(result.arrivals())
            .entrySet().stream()
                .map(entry -> new Arrival(entry.getKey(), entry.getValue()))
                .toList();
    return new Row(
        place,
        tied,
        result.alias(),
        result.finished(),
        result.status(),
        result.seconds(),
        result.highestFloor(),
        result.arrivalAtHighest(),
        result.attempt(),
        result.cost(),
        timeline,
        false,
        result.playerId());
  }

  /** The row of a Contender, in whichever table they are. */
  public Optional<Row> rowOf(String playerId) {
    return Stream.concat(hands.stream(), agents.stream())
        .filter(row -> row.playerId().equals(playerId))
        .findFirst();
  }

  /** How many Contenders stand in the same table as this one. */
  public int contendersWith(String playerId) {
    if (hands.stream().anyMatch(row -> row.playerId().equals(playerId))) {
      return hands.size();
    }
    return agents.stream().anyMatch(row -> row.playerId().equals(playerId)) ? agents.size() : 0;
  }

  /** The same table with the viewer's own row marked. */
  public RaceTable viewedBy(String playerId) {
    return new RaceTable(raceId, mark(hands, playerId), mark(agents, playerId));
  }

  private static List<Row> mark(List<Row> rows, String playerId) {
    return rows.stream()
        .map(
            row ->
                new Row(
                    row.place(),
                    row.tied(),
                    row.alias(),
                    row.finished(),
                    row.status(),
                    row.seconds(),
                    row.highestFloor(),
                    row.arrivalSeconds(),
                    row.attempt(),
                    row.cost(),
                    row.timeline(),
                    row.playerId().equals(playerId),
                    row.playerId()))
        .toList();
  }
}

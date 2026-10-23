package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.diogohmcruz.towerdungeon.agent.tools.Args;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;

/**
 * The conditions {@code td_wait} may wait for, judged against the Campaign as it stood when the
 * wait began. A condition is checked under the state's lock after each tick.
 */
public final class WaitConditions {
  /** The condition types, in the order they are checked. */
  public static final List<String> TYPES =
      List.of(
          "floor_changed",
          "unit_lost",
          "expedition_ended",
          "supplies_below",
          "party_health_fraction_below",
          "ticks");

  /** One condition; {@code value} is null for the types that take none. */
  public record Condition(String type, double value) {}

  private final List<Condition> conditions;
  private final int floor;
  private final boolean expeditionActive;
  private final int expeditionsCompleted;
  private final int units;
  private final long seq;

  private WaitConditions(List<Condition> conditions, GameState state, long seq) {
    this.conditions = conditions;
    this.floor = floorOf(state);
    this.expeditionActive = state.isExpeditionActive();
    this.expeditionsCompleted = state.getExpeditionsCompleted();
    this.units = unitCount(state);
    this.seq = seq;
  }

  /** Reads and checks the caller's conditions, then fixes the baseline from {@code state}. */
  public static WaitConditions begin(List<Map<String, Object>> raw, GameState state, long seq) {
    var parsed = new ArrayList<Condition>();
    for (var item : raw) {
      var type = item.get("type");
      if (!(type instanceof String name) || !TYPES.contains(name)) {
        throw new ToolFailure("INVALID_ARGUMENT", "A wait condition has an unknown type.");
      }
      var args = new Args(item);
      var value =
          switch (name) {
            case "supplies_below" -> args.number("value", 0, 1_000_000);
            case "party_health_fraction_below" -> args.number("value", 0, 1);
            case "ticks" -> args.integer("value", 1, 3600, 1);
            default -> 0d;
          };
      parsed.add(new Condition(name, value));
    }
    return new WaitConditions(List.copyOf(parsed), state, seq);
  }

  /** The most ticks a {@code ticks} condition asks for, or {@code fallback} when it has none. */
  public int tickLimit(int fallback) {
    return conditions.stream()
        .filter(c -> c.type().equals("ticks"))
        .mapToInt(c -> (int) c.value())
        .min()
        .orElse(fallback);
  }

  /**
   * The first condition that holds now, or empty. {@code ticksRun} counts ticks since the start.
   */
  public Optional<String> met(GameState state, long currentSeq, int ticksRun) {
    for (var condition : conditions) {
      var holds =
          switch (condition.type()) {
            case "floor_changed" -> floorOf(state) != floor;
            case "unit_lost" -> unitCount(state) < units;
            case "expedition_ended" ->
                (expeditionActive && !state.isExpeditionActive())
                    || state.getExpeditionsCompleted() > expeditionsCompleted;
            case "supplies_below" ->
                state.isExpeditionActive() && state.getSupplies() < condition.value();
            case "party_health_fraction_below" ->
                state.isExpeditionActive() && partyHealth(state) < condition.value();
            case "ticks" -> Math.max(ticksRun, currentSeq - seq) >= condition.value();
            default -> false;
          };
      if (holds) {
        return Optional.of(condition.type());
      }
    }
    return Optional.empty();
  }

  private static int floorOf(GameState state) {
    return state.getTower() == null ? 0 : state.getTower().getCurrentFloor();
  }

  private static int unitCount(GameState state) {
    return state.getUnits().values().stream().mapToInt(List::size).sum()
        + state.getUnitsOnTower().values().stream().mapToInt(List::size).sum();
  }

  private static double partyHealth(GameState state) {
    var party = state.getUnitsOnTower().values().stream().flatMap(List::stream).toList();
    var max = party.stream().mapToDouble(BaseUnit::getMaxHealth).sum();
    var current = party.stream().mapToDouble(BaseUnit::getCurrentHealth).sum();
    return max <= 0 ? 0 : current / max;
  }
}

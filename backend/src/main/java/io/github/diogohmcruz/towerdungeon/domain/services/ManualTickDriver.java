package io.github.diogohmcruz.towerdungeon.domain.services;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;

/**
 * Advances one Sandbox Campaign on request, a simulated second at a time. The canonical second
 * (engine {@code td-1}) is one {@code attritionTick(1000)} followed by one {@code lifecycleTick}:
 * equal, for everything combat can observe, to a hundred 10 ms attrition calls and then the
 * lifecycle, because combat, drain and Starvation run only inside the lifecycle tick, and Attrition
 * and Starvation damage are proportional to part health. The state's lock is taken per second, so a
 * long advance never blocks a reader for its whole length.
 *
 * <p>A Sandbox is seeded, so a run is deterministic in its draws as well as its tick order: the
 * same seed and the same commands and ticks make the same run. {@link #DETERMINISM_NOTE} says so.
 */
public final class ManualTickDriver implements TickDriver {
  /** Said in every Sandbox response. */
  public static final String DETERMINISM_NOTE =
      "Draws are seeded: the same seed, commands and ticks always make the same run.";

  /** Why an advance stopped. */
  public enum Stop {
    /** Every requested tick ran. */
    COMPLETED,
    /** The caller's condition became true after a tick. */
    CONDITION,
    /** More ticks were asked for than one wait may run; the cap was applied. */
    TICK_CAP,
    /** The Campaign reached its simulated-time ceiling. */
    SIMULATED_TIME_CAP,
    /** The call used up its wall-time budget. */
    WALL_TIME_CAP,
    /** The Campaign has ended and waits for its next Generation: no tick runs. */
    CAMPAIGN_ENDED,
    /** There is no such Sandbox Campaign. */
    NOT_FOUND
  }

  /** What one advance did. {@code simulatedSeconds} is the Campaign's total so far. */
  public record Advance(
      Stop stop, int ticksRun, long simulatedSeconds, long simulatedLimit, String note) {}

  private static final long SECOND_MS = 1000;

  private final GameService service;
  private final LongSupplier nanoClock;

  ManualTickDriver(GameService service, LongSupplier nanoClock) {
    this.service = service;
    this.nanoClock = nanoClock;
  }

  @Override
  public ClockMode mode() {
    return ClockMode.MANUAL;
  }

  /**
   * Runs up to {@code ticks} simulated seconds on a Sandbox Campaign, stopping early when {@code
   * until} holds after a tick (it may be null), when the wait cap, the Campaign's simulated-time
   * ceiling or the wall-time budget is reached. At least one tick runs if any is wanted and time
   * remains.
   */
  Advance advance(
      String sandboxId,
      int ticks,
      int maxTicksPerWait,
      Duration wallBudget,
      Predicate<GameState> until) {
    var slot = service.slotOf(sandboxId);
    if (slot == null || !drives(slot)) {
      return new Advance(Stop.NOT_FOUND, 0, 0, 0, DETERMINISM_NOTE);
    }
    var wanted = Math.max(0, Math.min(ticks, maxTicksPerWait));
    var started = nanoClock.getAsLong();
    var ran = 0;
    var stop = ticks > maxTicksPerWait ? Stop.TICK_CAP : Stop.COMPLETED;
    while (ran < wanted) {
      var step = step(slot, until);
      if (step == Stop.SIMULATED_TIME_CAP || step == Stop.CAMPAIGN_ENDED) {
        stop = step;
        break;
      }
      ran++;
      if (step == Stop.CONDITION) {
        stop = step;
        break;
      }
      if (ran < wanted && nanoClock.getAsLong() - started >= wallBudget.toNanos()) {
        stop = Stop.WALL_TIME_CAP;
        break;
      }
    }
    return new Advance(stop, ran, slot.simulatedSeconds(), slot.simulatedLimit(), DETERMINISM_NOTE);
  }

  /** One simulated second under the lock: Attrition for the second, then the lifecycle tick. */
  private Stop step(PlayerSlot slot, Predicate<GameState> until) {
    var state = slot.state();
    try {
      return state.callLocked(
          () -> {
            if (state.getPhase() == CampaignPhase.OFFER) {
              return Stop.CAMPAIGN_ENDED;
            }
            if (slot.simulatedSeconds() >= slot.simulatedLimit()) {
              return Stop.SIMULATED_TIME_CAP;
            }
            service.attritionTick(state, SECOND_MS);
            service.lifecycleTick(slot, state);
            service.settleEnded(slot, state);
            slot.addSimulatedSeconds(1);
            return until != null && until.test(state) ? Stop.CONDITION : Stop.COMPLETED;
          });
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to tick the Sandbox " + slot.playerId(), e);
    }
  }
}

package io.github.diogohmcruz.towerdungeon.domain.trial;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.diogohmcruz.towerdungeon.domain.models.snapshot.TrialSnapshot;
import lombok.Getter;

/**
 * The Trial a Campaign is: which race and attempt, where its Finish Floor lies and how long it has
 * run. {@code ticksSinceStart} counts lifecycle ticks of a connected, unheld Campaign, so connected
 * time orders the table and nobody races a calendar. Touch it only under the Campaign's lock.
 *
 * <p>This is what a frame shows of a Trial; it carries nothing of the Terms' seed.
 */
@Getter
public final class TrialRun {
  private final String raceId;
  private final int attempt;
  private final int finishFloor;
  private final long limitTicks;
  private long ticksSinceStart;
  private ContenderStatus status = ContenderStatus.RACING;

  public TrialRun(String raceId, int attempt, int finishFloor, long limitTicks) {
    this.raceId = raceId;
    this.attempt = attempt;
    this.finishFloor = finishFloor;
    this.limitTicks = limitTicks;
  }

  /** A Trial as saved. */
  public static TrialRun restore(TrialSnapshot saved) {
    var run =
        new TrialRun(saved.raceId(), saved.attempt(), saved.finishFloor(), saved.limitTicks());
    run.ticksSinceStart = saved.ticksSinceStart();
    run.status = saved.status();
    return run;
  }

  public TrialSnapshot toSnapshot() {
    return new TrialSnapshot(raceId, attempt, finishFloor, limitTicks, ticksSinceStart, status);
  }

  @JsonIgnore
  public boolean isClosed() {
    return status.isClosed();
  }

  /** One lifecycle second. Does nothing once the Trial is over. */
  public void tick() {
    if (!isClosed()) {
      ticksSinceStart++;
    }
  }

  /** Whether the clock has run out of its limit. */
  @JsonIgnore
  public boolean isOutOfTime() {
    return !isClosed() && ticksSinceStart >= limitTicks;
  }

  /** Freezes the result: the Finish Floor's Guardian is slain. */
  public void finish() {
    if (!isClosed()) {
      status = ContenderStatus.FINISHED;
    }
  }

  /** The Campaign ended in Defeat: nothing is left to climb with. */
  public void defeat() {
    if (!isClosed()) {
      status = ContenderStatus.DEFEATED;
    }
  }

  public void timeOut() {
    if (!isClosed()) {
      status = ContenderStatus.TIMED_OUT;
    }
  }
}

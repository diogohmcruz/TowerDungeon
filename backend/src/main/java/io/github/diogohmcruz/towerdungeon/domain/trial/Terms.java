package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.time.Duration;
import java.time.Instant;

/**
 * What pins a race. Every Contender of a race climbs the Tower this seed makes, on the balance this
 * hash names, to the same Finish Floor. The seed stays on the server: it is left out of {@link
 * #toString()} and no frame, file or log carries it.
 *
 * @param raceId the race; also the name of its attempts record, so it is kept to a safe alphabet
 * @param kind how it is run
 * @param planVersion the version of the Tower plan, so an old result stays valid
 * @param configHash the balance the race was set on; a server whose balance differs refuses entry
 * @param seed the Tower seed, server only
 * @param finishFloor the Guardian Floor that ends a Trial, apart from the Tower's top Floor
 * @param opensAt when entry opens; null is open already
 * @param closesAt when entry closes; null never closes
 * @param limit the clock: connected time a Trial may take
 * @param maxAttempts Trials one Campaign Key may start in this race
 * @param maxConcurrent connected Trials of this race at once
 */
public record Terms(
    String raceId,
    TermsKind kind,
    int planVersion,
    String configHash,
    long seed,
    int finishFloor,
    Instant opensAt,
    Instant closesAt,
    Duration limit,
    int maxAttempts,
    int maxConcurrent) {

  /** Whether entry is open at {@code now}. */
  public boolean isOpenAt(Instant now) {
    return (opensAt == null || !now.isBefore(opensAt))
        && (closesAt == null || now.isBefore(closesAt));
  }

  @Override
  public String toString() {
    return "Terms[raceId=%s, kind=%s, finishFloor=%d]".formatted(raceId, kind, finishFloor);
  }
}

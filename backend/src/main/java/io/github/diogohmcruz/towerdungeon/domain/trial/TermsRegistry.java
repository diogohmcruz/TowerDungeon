package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import io.github.diogohmcruz.towerdungeon.domain.random.SeedSource;
import lombok.extern.slf4j.Slf4j;

/**
 * The races this server offers, by id. Built once from configuration: a race with no seed of its
 * own takes one derived from the server secret, and one with no balance hash is pinned to the
 * balance now running. A race whose Terms cannot be honoured (an unsafe id, a Finish Floor off the
 * Tower, a clock past the cap) stops the server starting rather than offering a broken race.
 */
@Slf4j
@Component
public class TermsRegistry {
  private static final Pattern RACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

  private final Map<String, Terms> races = new LinkedHashMap<>();

  @Autowired
  public TermsRegistry(GameProperties config, SeedSource seeds) {
    var balance = ConfigHash.of(config);
    var maxFloor = config.getTower().getMaxFloor();
    var maxLimit = config.getTrial().getMaxLimit();
    for (var race : config.getTrial().getRaces()) {
      var id = race.getRaceId();
      if (id == null || !RACE_ID.matcher(id).matches()) {
        throw new IllegalStateException("A race id must be 1 to 40 letters, digits, - or _.");
      }
      if (race.getFinishFloor() < 1 || race.getFinishFloor() > maxFloor) {
        throw new IllegalStateException(
            "Race %s: the Finish Floor must lie on the Tower (1 to %d).".formatted(id, maxFloor));
      }
      var limit = race.getLimit();
      if (limit.isZero() || limit.isNegative() || limit.compareTo(maxLimit) > 0) {
        throw new IllegalStateException(
            "Race %s: the limit must be above zero and at most %s.".formatted(id, maxLimit));
      }
      if (race.getMaxAttempts() < 1 || race.getMaxConcurrent() < 1) {
        throw new IllegalStateException(
            "Race %s: attempts and concurrent Trials must be at least 1.".formatted(id));
      }
      if (races.containsKey(id)) {
        throw new IllegalStateException("Race %s is configured twice.".formatted(id));
      }
      var seed = race.getSeed() != null ? race.getSeed() : seeds.termsSeedOf(id);
      if (race.getSeed() == null && !seeds.isStable()) {
        log.warn(
            "Race {} takes its seed from a random secret: its Tower will change at the next start."
                + " Set game.seed-secret.",
            id);
      }
      var hash =
          race.getConfigHash() == null || race.getConfigHash().isBlank()
              ? balance
              : race.getConfigHash();
      races.put(
          id,
          new Terms(
              id,
              TermsKind.valueOf(race.getKind().trim().toUpperCase()),
              race.getPlanVersion(),
              hash,
              seed,
              race.getFinishFloor(),
              race.getOpensAt(),
              race.getClosesAt(),
              limit,
              race.getMaxAttempts(),
              race.getMaxConcurrent()));
    }
  }

  /** A registry of ready-made Terms: for tests. */
  public TermsRegistry(Collection<Terms> terms) {
    terms.forEach(t -> races.put(t.raceId(), t));
  }

  public Optional<Terms> find(String raceId) {
    return Optional.ofNullable(races.get(raceId));
  }

  public List<Terms> all() {
    return List.copyOf(races.values());
  }

  /** A clock of {@code limit} in lifecycle ticks of {@code tick}. */
  public static long ticksIn(Duration limit, long lifecycleMs) {
    return Math.max(1, limit.toMillis() / Math.max(1, lifecycleMs));
  }
}

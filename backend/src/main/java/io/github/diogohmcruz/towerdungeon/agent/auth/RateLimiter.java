package io.github.diogohmcruz.towerdungeon.agent.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Token buckets, one per (class, key). Reads, writes and a per-source flood limit have their own
 * rates (design 6.2); a caller over its limit is told how long to wait. The clock is injected so a
 * test never sleeps.
 */
public final class RateLimiter {
  /** A class of call and its rate. */
  public enum Kind {
    /** Reads: 10 a second, a burst of 20. */
    READ(10, 20),
    /** Writes: 2 a second, a burst of 6. */
    WRITE(2, 6),
    /** One source address, before any token is looked at: 60 a second. */
    SOURCE(60, 60);

    private final double perSecond;
    private final double burst;

    Kind(double perSecond, double burst) {
      this.perSecond = perSecond;
      this.burst = burst;
    }
  }

  /** The answer: allowed, or how many milliseconds until a call would be. */
  public record Verdict(boolean allowed, long retryAfterMs) {}

  private static final int SWEEP_ABOVE = 10_000;

  private final LongSupplier nanos;
  private final Map<String, Bucket> buckets = new HashMap<>();

  public RateLimiter() {
    this(System::nanoTime);
  }

  public RateLimiter(LongSupplier nanos) {
    this.nanos = nanos;
  }

  /** Takes one token from the bucket of {@code key} in the class {@code kind}, if there is one. */
  public synchronized Verdict tryAcquire(Kind kind, String key) {
    var now = nanos.getAsLong();
    if (buckets.size() > SWEEP_ABOVE) {
      buckets.values().removeIf(b -> b.refilled(now) >= b.kind.burst);
    }
    var bucket = buckets.computeIfAbsent(kind.name() + ":" + key, _ -> new Bucket(kind, now));
    var have = bucket.refilled(now);
    bucket.tokens = have;
    bucket.last = now;
    if (have >= 1) {
      bucket.tokens = have - 1;
      return new Verdict(true, 0);
    }
    var missing = 1 - have;
    return new Verdict(false, (long) Math.ceil(missing / kind.perSecond * 1000));
  }

  private static final class Bucket {
    private final Kind kind;
    private double tokens;
    private long last;

    private Bucket(Kind kind, long now) {
      this.kind = kind;
      this.tokens = kind.burst;
      this.last = now;
    }

    private double refilled(long now) {
      var elapsedSeconds = Math.max(0, now - last) / 1_000_000_000d;
      return Math.min(kind.burst, tokens + elapsedSeconds * kind.perSecond);
    }
  }
}

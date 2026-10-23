package io.github.diogohmcruz.towerdungeon.agent.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.agent.mcp.AgentHttpProperties;
import tools.jackson.databind.json.JsonMapper;

/** The small parts of access: token hashing, the rate limiter and the audit log. */
class AuthPartsTest {

  // ---------------------------------------------------------------- token hashing

  @Test
  void aTokenMatchesOnlyItsOwnHash() {
    var hash = TokenHasher.sha256Hex("correct horse");

    assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    assertThat(TokenHasher.matches("correct horse", hash)).isTrue();
    assertThat(TokenHasher.matches("correct horse", hash.toUpperCase())).isTrue();
    assertThat(TokenHasher.matches("correct hors", hash)).isFalse();
    assertThat(TokenHasher.matches("correct horse", "")).isFalse();
    assertThat(TokenHasher.matches("correct horse", "not hex")).isFalse();
    assertThat(TokenHasher.matches(null, hash)).isFalse();
  }

  @Test
  void accessIsGrantedOnlyForTheConfiguredHashAndNoHashMeansNoAccess() {
    var properties = new AgentHttpProperties();
    assertThat(new AgentAccess(properties).authenticate("anything")).isEmpty();

    properties.setDevTokenHash(TokenHasher.sha256Hex("t0ken"));
    var access = new AgentAccess(properties);

    assertThat(access.authenticate("t0ken")).contains(Principal.dev());
    assertThat(access.authenticate("t0ken ")).isEmpty();
    assertThat(access.authenticate("")).isEmpty();
    assertThat(access.authenticate(null)).isEmpty();
  }

  // ---------------------------------------------------------------- rate limiter

  @Test
  void aBucketAllowsItsBurstThenRefillsWithTime() {
    var nanos = new AtomicLong();
    var limiter = new RateLimiter(nanos::get);

    for (var i = 0; i < 6; i++) {
      assertThat(limiter.tryAcquire(RateLimiter.Kind.WRITE, "g").allowed())
          .as("write " + i)
          .isTrue();
    }
    var denied = limiter.tryAcquire(RateLimiter.Kind.WRITE, "g");
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterMs()).isEqualTo(500);

    nanos.addAndGet(500_000_000L);
    assertThat(limiter.tryAcquire(RateLimiter.Kind.WRITE, "g").allowed()).isTrue();
    assertThat(limiter.tryAcquire(RateLimiter.Kind.WRITE, "g").allowed()).isFalse();
  }

  @Test
  void keysAndClassesDoNotShareABucket() {
    var limiter = new RateLimiter(() -> 0L);
    for (var i = 0; i < 6; i++) {
      limiter.tryAcquire(RateLimiter.Kind.WRITE, "a");
    }

    assertThat(limiter.tryAcquire(RateLimiter.Kind.WRITE, "a").allowed()).isFalse();
    assertThat(limiter.tryAcquire(RateLimiter.Kind.WRITE, "b").allowed()).isTrue();
    assertThat(limiter.tryAcquire(RateLimiter.Kind.READ, "a").allowed()).isTrue();
  }

  @Test
  void aBucketNeverHoldsMoreThanItsBurst() {
    var nanos = new AtomicLong();
    var limiter = new RateLimiter(nanos::get);
    limiter.tryAcquire(RateLimiter.Kind.READ, "g");
    nanos.addAndGet(3_600_000_000_000L);

    var allowed = 0;
    while (limiter.tryAcquire(RateLimiter.Kind.READ, "g").allowed()) {
      allowed++;
    }
    assertThat(allowed).isEqualTo(20);
  }

  // ---------------------------------------------------------------- audit log

  @Test
  void theAuditLogWritesOneJsonLinePerCallWithFixedFieldsOnly() throws IOException {
    var file = Files.createTempDirectory("td-audit").resolve("sub").resolve("audit.jsonl");
    var clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    var audit = new AuditLog(file, clock);

    audit.record(new AuditLog.Entry("dev", "td_get_state", "td:observe", "ok", "OK", 7L, 3));
    audit.record(
        new AuditLog.Entry("dev", "td_wait", "td:observe", "rejected", "RATE_LIMITED", null, 0));

    var lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    var first = new JsonMapper().readTree(lines.getFirst());
    assertThat(first.get("ts").asString()).isEqualTo("2026-10-23T12:00:00Z");
    assertThat(first.get("tool").asString()).isEqualTo("td_get_state");
    assertThat(first.get("seq").asLong()).isEqualTo(7);
    assertThat(first.propertyNames())
        .containsExactlyInAnyOrder(
            "ts", "grant_id", "tool", "scope", "status", "code", "seq", "latency_ms");
  }

  @Test
  void theAuditLogRotatesOnceAtItsLimitAndNeverFailsACall() throws IOException {
    var dir = Files.createTempDirectory("td-audit-rotate");
    var file = dir.resolve("audit.jsonl");
    var audit = new AuditLog(file, Clock.systemUTC(), 200);

    for (var i = 0; i < 10; i++) {
      audit.record(
          new AuditLog.Entry("dev", "td_get_state", "td:observe", "ok", "OK", (long) i, 1));
    }

    assertThat(Files.exists(dir.resolve("audit.jsonl.1"))).isTrue();
    assertThat(Files.size(file)).isLessThan(400);
    // A path that cannot be written is swallowed.
    new AuditLog(dir.resolve("audit.jsonl.1").resolve("nope").resolve("x"), Clock.systemUTC())
        .record(new AuditLog.Entry("dev", "t", "s", "ok", "OK", null, 0));
    AuditLog.none().record(new AuditLog.Entry("dev", "t", "s", "ok", "OK", null, 0));
  }
}

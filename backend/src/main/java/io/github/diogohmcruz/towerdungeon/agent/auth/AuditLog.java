package io.github.diogohmcruz.towerdungeon.agent.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * One JSON line per call to the agent surface: when, which grant, which tool, how it ended and how
 * long it took (design 6.3). It never holds a token, a Campaign Key, an {@code Authorization}
 * header or an argument value, only fixed names and numbers. The file rotates once at a size limit,
 * keeping one older file. A write that fails is logged and dropped; the audit never fails a call.
 */
@Slf4j
public final class AuditLog {
  /** One audited call. {@code seq} is the state sequence after it, or null when none applies. */
  public record Entry(
      String grantId,
      String tool,
      String scope,
      String status,
      String code,
      Long seq,
      long latencyMs,
      String requestId) {
    public Entry(
        String grantId,
        String tool,
        String scope,
        String status,
        String code,
        Long seq,
        long latencyMs) {
      this(grantId, tool, scope, status, code, seq, latencyMs, null);
    }
  }

  private static final long DEFAULT_ROTATE_BYTES = 5L * 1024 * 1024;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final Path file;
  private final Clock clock;
  private final long rotateBytes;

  /** An audit that writes nowhere. */
  public static AuditLog none() {
    return new AuditLog(null, Clock.systemUTC(), DEFAULT_ROTATE_BYTES);
  }

  public AuditLog(Path file, Clock clock) {
    this(file, clock, DEFAULT_ROTATE_BYTES);
  }

  public AuditLog(Path file, Clock clock, long rotateBytes) {
    this.file = file;
    this.clock = clock;
    this.rotateBytes = rotateBytes;
  }

  public synchronized void record(Entry entry) {
    if (file == null) {
      return;
    }
    var line = new LinkedHashMap<String, Object>();
    line.put("ts", clock.instant().toString());
    line.put("grant_id", entry.grantId());
    line.put("tool", entry.tool());
    line.put("scope", entry.scope());
    line.put("status", entry.status());
    line.put("code", entry.code());
    line.put("seq", entry.seq());
    line.put("latency_ms", entry.latencyMs());
    if (entry.requestId() != null) {
      line.put("request_id", entry.requestId());
    }
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      rotateIfLarge();
      Files.writeString(
          file,
          MAPPER.writeValueAsString(line) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException e) {
      log.warn("Could not write the audit log: {}", e.getClass().getSimpleName());
    }
  }

  private void rotateIfLarge() throws IOException {
    if (Files.exists(file) && Files.size(file) >= rotateBytes) {
      Files.move(
          file,
          file.resolveSibling(file.getFileName() + ".1"),
          StandardCopyOption.REPLACE_EXISTING);
    }
  }
}

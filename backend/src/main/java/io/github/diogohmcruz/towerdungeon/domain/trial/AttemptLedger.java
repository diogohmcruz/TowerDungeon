package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * How many attempts each Campaign Key has used in each race, kept by the server and never in a
 * Trial file. A Trial file cannot be exported or imported, so an older file cannot buy another
 * attempt: the count lives here, append-only, one JSON line per event in {@code
 * <data-dir>/trials/<raceId>.jsonl}. It is the seed of the results store, not a leaderboard.
 *
 * <p>The count is held in memory and read from the file once per race. A start is written before it
 * is granted; a close is written behind the game thread, so no tick waits on a disk.
 */
@Slf4j
@Component
public class AttemptLedger {
  /**
   * @param started attempts begun so far
   * @param currentClosed whether the latest of them is over
   */
  public record Standing(int started, boolean currentClosed) {
    static final Standing NONE = new Standing(0, true);
  }

  private final Path dir;
  private final ObjectMapper mapper;
  private final Map<String, Map<String, Standing>> loaded = new ConcurrentHashMap<>();
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          runnable -> {
            var thread = new Thread(runnable, "attempt-ledger");
            thread.setDaemon(true);
            return thread;
          });

  @Autowired
  public AttemptLedger(GameProperties config, ObjectMapper mapper) {
    this(Path.of(config.getPersistence().getDataDir()).resolve("trials"), mapper);
  }

  /** A ledger over {@code dir}, or one that keeps nothing on disk when {@code dir} is null. */
  public AttemptLedger(Path dir, ObjectMapper mapper) {
    this.dir = dir;
    this.mapper = mapper;
  }

  public Standing standing(String raceId, String playerId) {
    return race(raceId).getOrDefault(playerId, Standing.NONE);
  }

  /**
   * Begins the next attempt and returns its number. The caller has checked the limit; this is
   * written to disk before it returns.
   */
  public synchronized int start(String raceId, String playerId) {
    var races = race(raceId);
    var number = races.getOrDefault(playerId, Standing.NONE).started() + 1;
    append(raceId, Map.of("event", "START", "playerId", playerId, "attempt", number, "at", now()));
    races.put(playerId, new Standing(number, false));
    return number;
  }

  /** Notes that an attempt is over. Written behind the caller. */
  public synchronized void close(
      String raceId, String playerId, int attempt, ContenderStatus status, long ticks) {
    var races = race(raceId);
    var standing = races.getOrDefault(playerId, Standing.NONE);
    if (standing.started() != attempt || standing.currentClosed()) {
      return;
    }
    races.put(playerId, new Standing(attempt, true));
    var line =
        Map.<String, Object>of(
            "event",
            "CLOSE",
            "playerId",
            playerId,
            "attempt",
            attempt,
            "status",
            status.name(),
            "ticks",
            ticks,
            "at",
            now());
    writer.execute(
        () -> {
          try {
            append(raceId, line);
          } catch (RuntimeException e) {
            log.error("Could not record the end of an attempt in race {}", raceId, e);
          }
        });
  }

  private static String now() {
    return Instant.now().toString();
  }

  private Map<String, Standing> race(String raceId) {
    return loaded.computeIfAbsent(raceId, this::read);
  }

  private Map<String, Standing> read(String raceId) {
    var standings = new ConcurrentHashMap<String, Standing>();
    var file = file(raceId);
    if (file == null || !Files.exists(file)) {
      return standings;
    }
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (var line : lines) {
        if (line.isBlank()) {
          continue;
        }
        var node = mapper.readTree(line);
        var player = node.path("playerId").asString("");
        var attempt = node.path("attempt").asInt(0);
        if ("START".equals(node.path("event").asString())) {
          standings.put(player, new Standing(attempt, false));
        } else if ("CLOSE".equals(node.path("event").asString())) {
          standings.computeIfPresent(player, (_, s) -> new Standing(s.started(), true));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the attempts of race " + raceId, e);
    }
    return standings;
  }

  private void append(String raceId, Map<String, Object> event) {
    var file = file(raceId);
    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(dir);
      Files.writeString(
          file,
          mapper.writeValueAsString(event) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not record an attempt in race " + raceId, e);
    }
  }

  private Path file(String raceId) {
    return dir == null ? null : dir.resolve(raceId + ".jsonl");
  }
}

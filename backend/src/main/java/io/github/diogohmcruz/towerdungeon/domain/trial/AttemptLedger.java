package io.github.diogohmcruz.towerdungeon.domain.trial;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The results store: how many attempts each Campaign Key has used in each race, and what each
 * closed attempt came to, kept by the server and never in a Trial file. A Trial file cannot be
 * exported or imported, so an older file cannot buy another attempt: the count lives here,
 * append-only, one JSON line per event in {@code <data-dir>/trials/<raceId>.jsonl}. It is not a
 * leaderboard; the table is read from it by {@link RaceTable}.
 *
 * <p>Each line is written with one write and forced to disk, so a line is whole or absent (a torn
 * last line is skipped on reading). When the open file passes the rotation size it is renamed to
 * {@code <raceId>.<n>.jsonl} and a new one begun; all of them are read together, oldest first.
 *
 * <p>The count and the results are held in memory and read from the files once per race. A start is
 * written before it is granted; a close is written behind the game thread, so no tick waits on a
 * disk.
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

  private static final long DEFAULT_ROTATE_BYTES = 1_048_576L;

  private final Path dir;
  private final ObjectMapper mapper;
  private final long rotateBytes;
  private final Map<String, Race> loaded = new ConcurrentHashMap<>();
  private final Object fileLock = new Object();
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          runnable -> {
            var thread = new Thread(runnable, "attempt-ledger");
            thread.setDaemon(true);
            return thread;
          });

  /** One race as the store holds it: the attempts used, and the closed results. */
  private static final class Race {
    final Map<String, Standing> standings = new ConcurrentHashMap<>();
    final List<TrialResult> results = new CopyOnWriteArrayList<>();
  }

  @Autowired
  public AttemptLedger(GameProperties config, ObjectMapper mapper) {
    this(
        Path.of(config.getPersistence().getDataDir()).resolve("trials"),
        mapper,
        config.getTrial().getResultsRotateBytes());
  }

  /** A ledger over {@code dir}, or one that keeps nothing on disk when {@code dir} is null. */
  public AttemptLedger(Path dir, ObjectMapper mapper) {
    this(dir, mapper, DEFAULT_ROTATE_BYTES);
  }

  public AttemptLedger(Path dir, ObjectMapper mapper, long rotateBytes) {
    this.dir = dir;
    this.mapper = mapper;
    this.rotateBytes = Math.max(1, rotateBytes);
  }

  public Standing standing(String raceId, String playerId) {
    return race(raceId).standings.getOrDefault(playerId, Standing.NONE);
  }

  /** Every closed attempt of a race, in the order they closed. */
  public List<TrialResult> results(String raceId) {
    return List.copyOf(race(raceId).results);
  }

  /** The finished table of a race, read from the closed attempts. */
  public RaceTable table(String raceId) {
    return RaceTable.of(raceId, race(raceId).results);
  }

  /**
   * Begins the next attempt and returns its number. The caller has checked the limit; this is
   * written to disk before it returns.
   */
  public synchronized int start(String raceId, String playerId) {
    var race = race(raceId);
    var number = race.standings.getOrDefault(playerId, Standing.NONE).started() + 1;
    var line = new java.util.LinkedHashMap<String, Object>();
    line.put("event", "START");
    line.put("playerId", playerId);
    line.put("attempt", number);
    line.put("at", now());
    append(raceId, line);
    race.standings.put(playerId, new Standing(number, false));
    return number;
  }

  /**
   * Notes that an attempt is over and what it came to. Written behind the caller.
   *
   * @return whether this closed the attempt; false when it was closed already (a Trial that reloads
   *     closed reports again) or is not the Key's current one, so a result is kept once
   */
  public synchronized boolean close(TrialResult result) {
    var race = race(result.raceId());
    var standing = race.standings.getOrDefault(result.playerId(), Standing.NONE);
    if (standing.started() != result.attempt() || standing.currentClosed()) {
      return false;
    }
    race.standings.put(result.playerId(), new Standing(result.attempt(), true));
    race.results.add(result);
    ObjectNode line = mapper.valueToTree(result);
    line.put("event", "CLOSE");
    line.put("at", now());
    writer.execute(
        () -> {
          try {
            append(result.raceId(), line);
          } catch (RuntimeException e) {
            log.error("Could not record the end of an attempt in race {}", result.raceId(), e);
          }
        });
    return true;
  }

  /** Waits until every close written so far is on disk. For tests and shutdown. */
  public void drain() {
    try {
      writer.submit(() -> {}).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String now() {
    return Instant.now().toString();
  }

  private Race race(String raceId) {
    return loaded.computeIfAbsent(raceId, this::read);
  }

  private Race read(String raceId) {
    var race = new Race();
    for (var file : files(raceId)) {
      List<String> lines;
      try {
        lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException("Could not read the attempts of race " + raceId, e);
      }
      for (var line : lines) {
        if (!line.isBlank()) {
          apply(race, line, raceId);
        }
      }
    }
    return race;
  }

  private void apply(Race race, String line, String raceId) {
    JsonNode node;
    try {
      node = mapper.readTree(line);
    } catch (RuntimeException e) {
      log.warn("Skipping an unreadable line of the results of race {}", raceId);
      return;
    }
    var player = node.path("playerId").asString("");
    var attempt = node.path("attempt").asInt(0);
    var event = node.path("event").asString("");
    if ("START".equals(event)) {
      race.standings.put(player, new Standing(attempt, false));
    } else if ("CLOSE".equals(event)) {
      var standing = race.standings.get(player);
      if (standing != null && standing.started() == attempt) {
        race.standings.put(player, new Standing(attempt, true));
      }
      if (node.has("highestFloor")) {
        try {
          race.results.add(mapper.treeToValue(node, TrialResult.class));
        } catch (RuntimeException e) {
          log.warn("Skipping an unreadable result in race {}", raceId);
        }
      }
    }
  }

  /** The files of a race, oldest first: the rotated ones by number, then the open one. */
  private List<Path> files(String raceId) {
    if (dir == null || !Files.isDirectory(dir)) {
      return List.of();
    }
    var rotated = Pattern.compile(Pattern.quote(raceId) + "\\.(\\d+)\\.jsonl");
    var found = new ArrayList<Path>();
    try (Stream<Path> listing = Files.list(dir)) {
      listing
          .filter(path -> rotated.matcher(path.getFileName().toString()).matches())
          .sorted(
              java.util.Comparator.comparingInt(
                  path -> {
                    var m = rotated.matcher(path.getFileName().toString());
                    m.matches();
                    return Integer.parseInt(m.group(1));
                  }))
          .forEach(found::add);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list the attempts of race " + raceId, e);
    }
    var open = file(raceId);
    if (Files.exists(open)) {
      found.add(open);
    }
    return found;
  }

  private void append(String raceId, Object event) {
    if (dir == null) {
      return;
    }
    var bytes = (mapper.writeValueAsString(event) + "\n").getBytes(StandardCharsets.UTF_8);
    synchronized (fileLock) {
      try {
        Files.createDirectories(dir);
        var open = file(raceId);
        if (Files.exists(open) && Files.size(open) >= rotateBytes) {
          Files.move(open, dir.resolve(raceId + "." + nextRotation(raceId) + ".jsonl"));
        }
        try (var channel =
            FileChannel.open(
                open,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
          var buffer = ByteBuffer.wrap(bytes);
          while (buffer.hasRemaining()) {
            channel.write(buffer);
          }
          channel.force(true);
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Could not record an attempt in race " + raceId, e);
      }
    }
  }

  private int nextRotation(String raceId) throws IOException {
    var rotated = Pattern.compile(Pattern.quote(raceId) + "\\.(\\d+)\\.jsonl");
    var highest = 0;
    try (Stream<Path> listing = Files.list(dir)) {
      for (var path : (Iterable<Path>) listing::iterator) {
        var m = rotated.matcher(path.getFileName().toString());
        if (m.matches()) {
          highest = Math.max(highest, Integer.parseInt(m.group(1)));
        }
      }
    }
    return highest + 1;
  }

  private Path file(String raceId) {
    return dir.resolve(raceId + ".jsonl");
  }
}

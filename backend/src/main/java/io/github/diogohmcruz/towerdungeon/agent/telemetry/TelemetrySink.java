package io.github.diogohmcruz.towerdungeon.agent.telemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where balance telemetry is kept: JSON Lines, one file per batch, {@code {dir}/{batch_id}.jsonl}.
 * The server makes every batch id; nothing a caller sends ever reaches a path. A row carries {@code
 * engine} and {@code config_hash} or it is refused, so numbers from before and after a balance
 * change can never be mistaken for comparable ones, and every text in a row is a short token, so a
 * row is data and never an instruction.
 *
 * <p>One sink, two readers: the {@code td_get_telemetry} tool and the balance report both read
 * through {@link #read}.
 */
@AgentEnabled
@Component
public class TelemetrySink implements DisposableBean {
  /** The shape of a batch id. The sink makes them, so a caller's text never matches by chance. */
  public static final Pattern BATCH_ID = Pattern.compile("^[a-z0-9-]{8,40}$");

  /** Every text in a row: a short token, no spaces, nothing a reader could take for a command. */
  public static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_.:+-]{0,64}$");

  /** The kinds of row, in the {@code row} column. */
  public static final List<String> KINDS = List.of("floor", "expedition", "campaign");

  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  /** One page of rows read back. */
  public record Page(List<Map<String, Object>> rows, int offset, boolean more) {}

  /** What a batch file holds, for a listing. */
  public record BatchInfo(String id, long bytes) {}

  private final Path dir;
  private final int maxRowsPerRead;
  private final Map<String, Batch> open = new ConcurrentHashMap<>();

  @Autowired
  public TelemetrySink(GameProperties config) {
    this(
        Path.of(config.getAgent().getTelemetry().getDir()),
        config.getAgent().getTelemetry().getMaxRowsPerRead());
  }

  public TelemetrySink(Path dir, int maxRowsPerRead) {
    this.dir = dir;
    this.maxRowsPerRead = maxRowsPerRead;
  }

  /** On shutdown the rows still buffered are written. */
  @Override
  public void destroy() {
    open.values().forEach(Batch::flush);
  }

  public int maxRowsPerRead() {
    return maxRowsPerRead;
  }

  /** Begins a new batch under a fresh server-made id. */
  public Batch open() {
    try {
      Files.createDirectories(dir);
      var id = "tb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
      var batch = new Batch(id, dir.resolve(id + ".jsonl"));
      open.put(id, batch);
      return batch;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot open a telemetry batch in " + dir, e);
    }
  }

  /**
   * A batch being written. Rows are buffered and appended to the file when the buffer is large, on
   * {@link #flush} and on {@link #close}; no file stays open between writes, so a batch nobody
   * closes holds nothing but its buffer. Safe to read while it is open.
   */
  public final class Batch implements AutoCloseable {
    private static final int FLUSH_AT = 64 * 1024;

    private final String id;
    private final Path file;
    private final StringBuilder buffer = new StringBuilder();

    private Batch(String id, Path file) {
      this.id = id;
      this.file = file;
    }

    public String id() {
      return id;
    }

    /**
     * Appends a row. A row without {@code row}, {@code engine} or {@code config_hash} is refused.
     */
    public synchronized void append(Map<String, Object> row) {
      check(row);
      buffer.append(MAPPER.writeValueAsString(row)).append('\n');
      if (buffer.length() >= FLUSH_AT) {
        flush();
      }
    }

    public synchronized void flush() {
      if (buffer.isEmpty()) {
        return;
      }
      try {
        Files.writeString(
            file,
            buffer,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        buffer.setLength(0);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public synchronized void close() {
      try {
        flush();
      } finally {
        open.remove(id);
      }
    }
  }

  /** Reads rows of one kind (null for every kind) from a batch, {@code limit} at most. */
  public Page read(String batchId, String kind, int offset, int limit) {
    if (batchId == null || !BATCH_ID.matcher(batchId).matches()) {
      throw new IllegalArgumentException("not a batch id");
    }
    var live = open.get(batchId);
    if (live != null) {
      live.flush();
    }
    var file = dir.resolve(batchId + ".jsonl");
    if (!Files.isRegularFile(file)) {
      return new Page(List.of(), offset, false);
    }
    var cap = Math.max(1, Math.min(limit, maxRowsPerRead));
    var rows = new ArrayList<Map<String, Object>>();
    var seen = 0;
    var more = false;
    try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
      var iterator = lines.iterator();
      while (iterator.hasNext()) {
        var line = iterator.next();
        if (line.isBlank()) {
          continue;
        }
        var row = MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {});
        if (kind != null && !kind.equals(row.get("row"))) {
          continue;
        }
        if (seen++ < offset) {
          continue;
        }
        if (rows.size() == cap) {
          more = true;
          break;
        }
        rows.add(row);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new Page(List.copyOf(rows), offset, more);
  }

  /** Streams every row of a batch (of one kind, or all when null) once, in the order written. */
  public void forEach(String batchId, String kind, Consumer<Map<String, Object>> sink) {
    if (batchId == null || !BATCH_ID.matcher(batchId).matches()) {
      throw new IllegalArgumentException("not a batch id");
    }
    var live = open.get(batchId);
    if (live != null) {
      live.flush();
    }
    var file = dir.resolve(batchId + ".jsonl");
    if (!Files.isRegularFile(file)) {
      return;
    }
    try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
      lines
          .filter(line -> !line.isBlank())
          .map(line -> MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {}))
          .filter(row -> kind == null || kind.equals(row.get("row")))
          .forEach(sink);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Every row of a batch, for tests and small batches; a report streams with {@link #forEach}. */
  public List<Map<String, Object>> readAll(String batchId, String kind) {
    var all = new ArrayList<Map<String, Object>>();
    forEach(batchId, kind, all::add);
    return all;
  }

  /** The batches on disk, by id. */
  public List<BatchInfo> list() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (var files = Files.list(dir)) {
      return files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".jsonl"))
          .map(name -> name.substring(0, name.length() - ".jsonl".length()))
          .filter(id -> BATCH_ID.matcher(id).matches())
          .sorted()
          .map(id -> new BatchInfo(id, sizeOf(dir.resolve(id + ".jsonl"))))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long sizeOf(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      return 0;
    }
  }

  private static void check(Map<String, Object> row) {
    for (var required : List.of("row", "engine", "config_hash")) {
      if (!(row.get(required) instanceof String text) || text.isEmpty()) {
        throw new IllegalArgumentException("A telemetry row needs " + required);
      }
    }
    if (!KINDS.contains(row.get("row"))) {
      throw new IllegalArgumentException("Unknown kind of row");
    }
    walk(row);
  }

  private static void walk(Object value) {
    switch (value) {
      case Map<?, ?> map ->
          map.forEach(
              (key, inner) -> {
                token(String.valueOf(key));
                walk(inner);
              });
      case Iterable<?> list -> list.forEach(TelemetrySink::walk);
      case String text -> token(text);
      case null, default -> {}
    }
  }

  private static void token(String text) {
    if (!TOKEN.matcher(text).matches()) {
      throw new IllegalArgumentException("A telemetry row holds text that is not a token");
    }
  }
}

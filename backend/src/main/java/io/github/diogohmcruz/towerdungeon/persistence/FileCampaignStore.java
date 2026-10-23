package io.github.diogohmcruz.towerdungeon.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.diogohmcruz.towerdungeon.config.GameProperties;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A {@link CampaignStore} that keeps each Campaign in a checksummed file under {@code
 * <data-dir>/players/<first two hex chars>/<playerId>/}: {@code campaign.tdc}, {@code
 * backup/1..5.tdc} and {@code corrupt/}. File I/O is blocking: saves run on a dedicated
 * single-thread writer so they never reorder, loads on {@code boundedElastic}.
 */
public class FileCampaignStore implements CampaignStore, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(FileCampaignStore.class);

  static final int BACKUPS = 5;
  static final int MAX_CORRUPT = 3;

  private static final Pattern PLAYER_ID = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern PRE_MIGRATION = Pattern.compile("pre-migration-v([0-9]{1,9})\\.tdc");
  private static final String HEAD_FILE = "campaign.tdc";
  static final String ENDED_LAST = "ended-last.tdc";

  private final Path playersDir;
  private final Runnable beforeCommit;
  private final Scheduler writer = Schedulers.newSingle("campaign-writer");
  private final Object[] locks = new Object[64];

  public FileCampaignStore(GameProperties.Persistence config) {
    this(Path.of(config.getDataDir()), () -> {});
  }

  /** {@code beforeCommit} runs after the temp file is durable and before the head is touched. */
  FileCampaignStore(Path dataDir, Runnable beforeCommit) {
    this.playersDir = dataDir.resolve("players");
    this.beforeCommit = beforeCommit;
    Arrays.setAll(locks, i -> new Object());
  }

  @Override
  public Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      byte[] payload) {
    return Mono.defer(
            () -> {
              requireValidId(playerId);
              if (gameVersion == null
                  || !CampaignFile.GAME_VERSION.matcher(gameVersion).matches()) {
                throw new IllegalArgumentException("Invalid game version: " + gameVersion);
              }
              byte[] bytes = payload.clone();
              return Mono.fromCallable(
                  () -> saveBlocking(playerId, expectedSeq, rotation, schema, gameVersion, bytes));
            })
        .subscribeOn(writer);
  }

  /** Runs {@code payload} on the writer thread, so serialization never happens on the caller. */
  @Override
  public Mono<Long> save(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      Supplier<byte[]> payload) {
    return Mono.defer(
            () -> {
              requireValidId(playerId);
              if (gameVersion == null
                  || !CampaignFile.GAME_VERSION.matcher(gameVersion).matches()) {
                throw new IllegalArgumentException("Invalid game version: " + gameVersion);
              }
              return Mono.fromCallable(
                  () ->
                      saveBlocking(
                          playerId, expectedSeq, rotation, schema, gameVersion, payload.get()));
            })
        .subscribeOn(writer);
  }

  @Override
  public Mono<Void> savePreMigration(
      String playerId, int schema, String gameVersion, byte[] payload) {
    return Mono.defer(
            () -> {
              requireValidId(playerId);
              if (gameVersion == null
                  || !CampaignFile.GAME_VERSION.matcher(gameVersion).matches()) {
                throw new IllegalArgumentException("Invalid game version: " + gameVersion);
              }
              byte[] bytes = payload.clone();
              return Mono.<Void>fromCallable(
                  () -> {
                    savePreMigrationBlocking(playerId, schema, gameVersion, bytes);
                    return null;
                  });
            })
        .subscribeOn(writer);
  }

  @Override
  public Mono<Void> saveEnded(String playerId, int schema, String gameVersion, byte[] payload) {
    return Mono.defer(
            () -> {
              requireValidId(playerId);
              if (gameVersion == null
                  || !CampaignFile.GAME_VERSION.matcher(gameVersion).matches()) {
                throw new IllegalArgumentException("Invalid game version: " + gameVersion);
              }
              byte[] bytes = payload.clone();
              return Mono.<Void>fromCallable(
                  () -> {
                    saveEndedBlocking(playerId, schema, gameVersion, bytes);
                    return null;
                  });
            })
        .subscribeOn(writer);
  }

  @Override
  public Mono<LoadResult> load(String playerId) {
    return Mono.defer(
            () -> {
              requireValidId(playerId);
              return Mono.fromCallable(() -> loadBlocking(playerId));
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public void close() {
    writer.dispose();
  }

  private static void requireValidId(String playerId) {
    if (playerId == null || !PLAYER_ID.matcher(playerId).matches()) {
      throw new IllegalArgumentException("Invalid player id");
    }
  }

  private Object lockFor(String playerId) {
    return locks[(playerId.hashCode() & 0x7fffffff) % locks.length];
  }

  private Path playerDir(String playerId) {
    return playersDir.resolve(playerId.substring(0, 2)).resolve(playerId);
  }

  private static Path backupFile(Path dir, int n) {
    return dir.resolve("backup").resolve(n + ".tdc");
  }

  private long saveBlocking(
      String playerId,
      long expectedSeq,
      Rotation rotation,
      int schema,
      String gameVersion,
      byte[] payload)
      throws IOException {
    Path dir = playerDir(playerId);
    synchronized (lockFor(playerId)) {
      long current = currentSeq(dir);
      if (current != expectedSeq) {
        throw new CampaignConflictException(expectedSeq, current);
      }
      long seq = expectedSeq + 1;
      Files.createDirectories(dir);
      Path head = dir.resolve(HEAD_FILE);
      Path temp = Files.createTempFile(dir, "campaign-", ".tmp");
      try {
        writeDurably(temp, CampaignFile.encode(schema, seq, Instant.now(), gameVersion, payload));
        beforeCommit.run();
        if (rotation == Rotation.ROTATE && Files.exists(head)) {
          rotate(dir, head);
        }
        Files.move(temp, head, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
      syncDirectory(dir);
      return seq;
    }
  }

  private void savePreMigrationBlocking(
      String playerId, int schema, String gameVersion, byte[] payload) throws IOException {
    Path dir = playerDir(playerId);
    synchronized (lockFor(playerId)) {
      Path target = preMigrationFile(dir, schema);
      if (Files.exists(target)) {
        return;
      }
      Files.createDirectories(dir);
      Path temp = Files.createTempFile(dir, "pre-migration-", ".tmp");
      try {
        writeDurably(temp, CampaignFile.encode(schema, 0, Instant.now(), gameVersion, payload));
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
      syncDirectory(dir);
    }
  }

  private void saveEndedBlocking(String playerId, int schema, String gameVersion, byte[] payload)
      throws IOException {
    Path dir = playerDir(playerId);
    synchronized (lockFor(playerId)) {
      Files.createDirectories(dir);
      Path temp = Files.createTempFile(dir, "ended-", ".tmp");
      try {
        writeDurably(temp, CampaignFile.encode(schema, 0, Instant.now(), gameVersion, payload));
        Files.move(
            temp,
            dir.resolve(ENDED_LAST),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temp);
      }
      syncDirectory(dir);
    }
  }

  private static Path preMigrationFile(Path dir, int schema) {
    return dir.resolve("pre-migration-v" + schema + ".tdc");
  }

  /** The schemas that have a pre-migration copy, newest first. */
  private static List<Integer> preMigrationSchemas(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .map(f -> PRE_MIGRATION.matcher(f.getFileName().toString()))
          .filter(Matcher::matches)
          .map(m -> Integer.parseInt(m.group(1)))
          .sorted(Comparator.reverseOrder())
          .toList();
    }
  }

  /**
   * The sequence number of the head. A head that is missing or unreadable (and is then set aside)
   * counts as the newest readable backup, so a Campaign recovered from a backup can be saved.
   */
  private long currentSeq(Path dir) throws IOException {
    Record record = firstReadable(dir, new ArrayList<>());
    return record == null ? INITIAL_SEQ : record.seq();
  }

  private void rotate(Path dir, Path head) throws IOException {
    Files.createDirectories(dir.resolve("backup"));
    for (int n = BACKUPS - 1; n >= 1; n--) {
      Path from = backupFile(dir, n);
      if (Files.exists(from)) {
        Files.move(from, backupFile(dir, n + 1), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    Files.copy(head, backupFile(dir, 1), StandardCopyOption.REPLACE_EXISTING);
  }

  private LoadResult loadBlocking(String playerId) throws IOException {
    Path dir = playerDir(playerId);
    synchronized (lockFor(playerId)) {
      List<Source> quarantined = new ArrayList<>();
      Record record = firstReadable(dir, quarantined);
      if (record == null) {
        return null;
      }
      return new LoadResult(
          record.payload(),
          record.schema(),
          record.gameVersion(),
          record.seq(),
          record.savedAt(),
          record.source(),
          List.copyOf(quarantined));
    }
  }

  private static Record decode(byte[] content, Source source) {
    CampaignFile file = CampaignFile.decode(content);
    return new Record(
        file.payload(), file.schema(), file.gameVersion(), file.seq(), file.savedAt(), source);
  }

  private Record firstReadable(Path dir, List<Source> quarantined) throws IOException {
    List<Source> candidates = new ArrayList<>();
    for (int n = 0; n <= BACKUPS; n++) {
      candidates.add(new Source(n));
    }
    for (int schema : preMigrationSchemas(dir)) {
      candidates.add(Source.preMigration(schema));
    }
    for (Source source : candidates) {
      Path file = fileOf(dir, source);
      byte[] content;
      try {
        content = Files.readAllBytes(file);
      } catch (NoSuchFileException e) {
        continue;
      }
      try {
        return decode(content, source);
      } catch (CampaignFile.CorruptFileException e) {
        log.warn("Quarantining unreadable record {}: {}", file, e.getMessage());
        quarantine(dir, file, source);
        quarantined.add(source);
      }
    }
    return null;
  }

  private static Path fileOf(Path dir, Source source) {
    if (source.isPreMigration()) {
      return preMigrationFile(dir, -source.backup());
    }
    return source.isHead() ? dir.resolve(HEAD_FILE) : backupFile(dir, source.backup());
  }

  private void quarantine(Path dir, Path file, Source source) throws IOException {
    Path corrupt = dir.resolve("corrupt");
    Files.createDirectories(corrupt);
    String label =
        source.isPreMigration()
            ? "pre-migration-v" + -source.backup()
            : source.isHead() ? "head" : "backup" + source.backup();
    long now = System.currentTimeMillis();
    Path target;
    int suffix = 0;
    do {
      target = corrupt.resolve("%013d-%s-%d.tdc".formatted(now, label, suffix++));
    } while (Files.exists(target));
    Files.move(file, target);
    try (Stream<Path> files = Files.list(corrupt)) {
      List<Path> all = files.sorted().toList();
      for (Path old : all.subList(0, Math.max(0, all.size() - MAX_CORRUPT))) {
        Files.delete(old);
      }
    }
  }

  private static void writeDurably(Path file, byte[] content) throws IOException {
    try (FileChannel channel =
        FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer buffer = ByteBuffer.wrap(content);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
  }

  private static void syncDirectory(Path dir) {
    try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | UnsupportedOperationException e) {
      log.debug("Directory fsync not supported for {}", dir);
    }
  }

  private record Record(
      byte[] payload, int schema, String gameVersion, long seq, Instant savedAt, Source source) {}
}

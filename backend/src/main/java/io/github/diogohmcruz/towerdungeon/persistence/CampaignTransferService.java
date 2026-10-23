package io.github.diogohmcruz.towerdungeon.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Export and import of a Campaign file ({@code .tdc}). */
@Service
@RequiredArgsConstructor
public class CampaignTransferService {
  /** The largest file that can be imported, in bytes. */
  public static final int MAX_IMPORT_BYTES = 1024 * 1024;

  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);

  private final CampaignLoader loader;
  private final CampaignStore store;
  private final CampaignCodec codec;
  private final LiveCampaigns live;
  private final GameVersion gameVersion;
  private final Clock clock = Clock.systemUTC();

  public record ExportFile(String filename, byte[] content) {}

  /**
   * @param notices anything the player should be told about the file that was imported
   */
  public record ImportResult(int schema, List<String> notices) {}

  /** The file is not a readable Campaign file, or its Campaign cannot be read by this build. */
  public static class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
      super(message);
    }
  }

  /** The file is larger than {@link #MAX_IMPORT_BYTES}. */
  public static class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
      super("A Campaign file can be at most " + MAX_IMPORT_BYTES + " bytes");
    }
  }

  /** An Expedition is active or Held, so the Campaign cannot be swapped. */
  public static class ExpeditionInProgressException extends RuntimeException {
    public ExpeditionInProgressException() {
      super("An Expedition is in progress; finish or recall it before importing");
    }
  }

  /** The player has no Campaign to export yet. */
  public static class NothingToExportException extends RuntimeException {
    public NothingToExportException() {
      super("There is no Campaign to export yet");
    }
  }

  public Mono<ExportFile> export(String playerId) {
    return loader
        .load(playerId)
        .map(
            loaded -> {
              var saved = live.snapshot(playerId).orElse(loaded.snapshot());
              if (saved == null) {
                throw new NothingToExportException();
              }
              var snapshot = saved.withoutRandom();
              var meta = loaded.meta();
              Instant now = clock.instant();
              byte[] payload = codec.encode(snapshot, meta);
              byte[] content =
                  CampaignFile.encode(
                      codec.current(), loaded.seq(), now, gameVersion.current(), payload);
              int generation = meta == null ? 1 : meta.path("generation").asInt(1);
              return new ExportFile(
                  "towerdungeon-g%d-%s.tdc".formatted(generation, STAMP.format(now)), content);
            });
  }

  public Mono<ImportResult> importFile(String playerId, byte[] content) {
    return Mono.fromCallable(() -> read(content))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(
            read ->
                loader
                    .load(playerId)
                    .flatMap(loaded -> swap(playerId, read, loaded.seq()))
                    .map(_ -> new ImportResult(read.file().schema(), read.decoded().notices())));
  }

  private record Read(CampaignFile file, CampaignCodec.Decoded decoded) {}

  private Read read(byte[] content) {
    if (content.length > MAX_IMPORT_BYTES) {
      throw new FileTooLargeException();
    }
    CampaignFile file;
    try {
      file = CampaignFile.decode(content);
    } catch (CampaignFile.CorruptFileException e) {
      throw new InvalidFileException("Not a readable Campaign file: " + e.getMessage());
    }
    if (file.schema() > codec.current()) {
      throw new SchemaNewerException(file.schema());
    }
    if (file.schema() < 1) {
      throw new InvalidFileException("Not a readable Campaign file: bad schema");
    }
    var decoded = codec.decode(file.schema(), file.payload(), CampaignCodec.PreMigrationSink.NONE);
    if (decoded.campaignLost()) {
      throw new InvalidFileException("The Campaign in this file cannot be read by this version");
    }
    return new Read(file, decoded);
  }

  private Mono<Boolean> swap(String playerId, Read read, long storedSeq) {
    return Mono.fromCallable(
            () -> {
              // Whatever seed the file carries is ignored: the Campaign draws its own.
              var snapshot = read.decoded().snapshot().withoutRandom();
              byte[] payload = codec.encode(snapshot, read.decoded().meta());
              boolean swapped =
                  live.replaceIfIdle(
                      playerId,
                      snapshot,
                      read.decoded().meta(),
                      liveSeq ->
                          store
                              .save(
                                  playerId,
                                  liveSeq.orElse(storedSeq),
                                  CampaignStore.Rotation.ROTATE,
                                  codec.current(),
                                  gameVersion.current(),
                                  payload)
                              .block());
              if (!swapped) {
                throw new ExpeditionInProgressException();
              }
              return true;
            })
        .subscribeOn(Schedulers.boundedElastic());
  }
}

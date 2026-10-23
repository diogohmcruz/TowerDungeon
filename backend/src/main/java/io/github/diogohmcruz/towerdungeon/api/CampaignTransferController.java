package io.github.diogohmcruz.towerdungeon.api;

import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.diogohmcruz.towerdungeon.persistence.CampaignConflictException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema.SchemaNewerException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService.ExpeditionInProgressException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService.FileTooLargeException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService.InvalidFileException;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService.NothingToExportException;
import io.github.diogohmcruz.towerdungeon.persistence.PlayerIdResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Export and import of a Campaign file. The caller is identified by the Campaign Key as bearer. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campaign")
@CrossOrigin(
    origins = {"http://localhost:4200"},
    exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
@Tag(name = "Campaign", description = "Campaign file export and import")
public class CampaignTransferController {
  private static final String BEARER = "Bearer ";

  private final CampaignTransferService transfer;
  private final PlayerIdResolver playerIds;

  public record ImportResponse(int schema, List<String> notices) {}

  @Operation(
      summary = "Export the Campaign file",
      description = "Returns the Campaign as a .tdc file; the Campaign Key is not inside it.")
  @GetMapping("/export")
  public Mono<ResponseEntity<byte[]>> export(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    return playerId(authorization)
        .flatMap(transfer::export)
        .map(
            file ->
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                            .filename(file.filename())
                            .build()
                            .toString())
                    .contentLength(file.content().length)
                    .body(file.content()))
        .onErrorMap(NothingToExportException.class, e -> status(HttpStatus.NOT_FOUND, e));
  }

  @Operation(
      summary = "Import a Campaign file",
      description =
          "Replaces the Campaign with the file's. At most 1 MiB. 422 when the file is from a newer"
              + " build, 409 while an Expedition is active or Held. The previous Campaign becomes"
              + " backup 1.")
  @PutMapping("/import")
  public Mono<ImportResponse> importFile(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false) Long contentLength,
      @RequestBody Flux<DataBuffer> body) {
    if (contentLength != null && contentLength > CampaignTransferService.MAX_IMPORT_BYTES) {
      return Mono.error(status(HttpStatus.CONTENT_TOO_LARGE, new FileTooLargeException()));
    }
    return playerId(authorization)
        .flatMap(id -> readBody(body).flatMap(content -> transfer.importFile(id, content)))
        .map(result -> new ImportResponse(result.schema(), result.notices()))
        .onErrorMap(FileTooLargeException.class, e -> status(HttpStatus.CONTENT_TOO_LARGE, e))
        .onErrorMap(InvalidFileException.class, e -> status(HttpStatus.BAD_REQUEST, e))
        .onErrorMap(SchemaNewerException.class, e -> status(HttpStatus.UNPROCESSABLE_CONTENT, e))
        .onErrorMap(ExpeditionInProgressException.class, e -> status(HttpStatus.CONFLICT, e))
        .onErrorMap(CampaignConflictException.class, e -> status(HttpStatus.CONFLICT, e));
  }

  /** Reads the body up to the limit; a longer body is refused without being buffered whole. */
  private static Mono<byte[]> readBody(Flux<DataBuffer> body) {
    return DataBufferUtils.join(body, CampaignTransferService.MAX_IMPORT_BYTES)
        .map(
            buffer -> {
              try {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return bytes;
              } finally {
                DataBufferUtils.release(buffer);
              }
            })
        .onErrorMap(DataBufferLimitException.class, e -> new FileTooLargeException())
        .defaultIfEmpty(new byte[0]);
  }

  private Mono<String> playerId(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER)) {
      return Mono.error(
          new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A Campaign Key is required"));
    }
    return playerIds
        .resolve(authorization.substring(BEARER.length()).strip())
        .switchIfEmpty(
            Mono.error(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Campaign Key")));
  }

  private static ResponseStatusException status(HttpStatus status, Throwable cause) {
    return new ResponseStatusException(status, cause.getMessage(), cause);
  }
}

package io.github.diogohmcruz.towerdungeon.api;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignFile;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignKey;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignTransferService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Runs the real application, so the 1 MiB limit is checked under the actual codec setup. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CampaignTransferEndpointTest {
  private static final Path DATA_DIR = createDataDir();
  private static final Instant SAVED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final int MAX = CampaignTransferService.MAX_IMPORT_BYTES;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Value("${local.server.port}")
  int port;

  @Autowired GameService gameService;

  private static Path createDataDir() {
    try {
      return Files.createTempDirectory("tower-dungeon-transfer-test");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
  }

  /**
   * A new client per request: a refused upload makes the server close the connection, and a reused
   * one would fail for reasons that have nothing to do with what is being tested.
   */
  private static HttpClient newClient() {
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  private static String keyNamed(String name) {
    return (name + "AAAAAAAAAAAAAAAAAAAAAA").substring(0, 22);
  }

  private static String playerId(String key) {
    return CampaignKey.playerId(key).orElseThrow();
  }

  private HttpResponse<byte[]> export(String authorization) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/campaign/export"));
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return newClient().send(request.GET().build(), BodyHandlers.ofByteArray());
  }

  private HttpResponse<byte[]> importFile(String authorization, byte[] body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/campaign/import"));
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return newClient()
        .send(request.PUT(BodyPublishers.ofByteArray(body)).build(), BodyHandlers.ofByteArray());
  }

  private HttpResponse<byte[]> importChunked(String authorization, byte[] body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/campaign/import"))
            .header("Authorization", authorization);
    // No Content-Length: the limit has to be enforced while reading the body.
    var publisher = BodyPublishers.fromPublisher(BodyPublishers.ofByteArray(body));
    return newClient().send(request.PUT(publisher).build(), BodyHandlers.ofByteArray());
  }

  /**
   * The server may answer 413 and close the connection while the client is still sending, which the
   * client sees as an I/O error instead of a response; both mean the body was refused.
   */
  private static boolean refusedAsTooLarge(ThrowingSupplier<HttpResponse<byte[]>> send) {
    try {
      return send.get().statusCode() == 413;
    } catch (IOException e) {
      return true;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  private HttpResponse<byte[]> exportOf(String key) throws Exception {
    gameService.attach(playerId(key), "ws-" + key).block();
    return export("Bearer " + key);
  }

  private ObjectNode payloadOf(byte[] file) {
    return (ObjectNode) mapper.readTree(CampaignFile.decode(file).payload());
  }

  private byte[] withCredit(byte[] file, double credit) {
    var payload = payloadOf(file);
    ((ObjectNode) payload.get("campaign")).put("credit", credit);
    return CampaignFile.encode(1, 1, SAVED_AT, "0.1.0", mapper.writeValueAsBytes(payload));
  }

  private double creditOf(byte[] file) {
    return payloadOf(file).get("campaign").get("credit").asDouble();
  }

  private double liveCredit(String key) {
    return gameService.snapshotOf(playerId(key)).orElseThrow().campaign().credit();
  }

  private Path playerDir(String key) {
    String id = playerId(key);
    return DATA_DIR.resolve("players").resolve(id.substring(0, 2)).resolve(id);
  }

  @Test
  void exportAndImportRequireABearerKey() throws Exception {
    assertThat(export(null).statusCode()).isEqualTo(401);
    assertThat(export("Bearer too-short").statusCode()).isEqualTo(401);
    assertThat(export("Basic abc").statusCode()).isEqualTo(401);
    assertThat(importFile(null, new byte[] {1}).statusCode()).isEqualTo(401);
    assertThat(importFile("Bearer nope", new byte[] {1}).statusCode()).isEqualTo(401);
  }

  @Test
  void aPlayerWithNothingYetHasNothingToExport() throws Exception {
    assertThat(export("Bearer " + keyNamed("nothing")).statusCode()).isEqualTo(404);
  }

  @Test
  void exportReturnsAVerifiableFileWithoutTheKeyInside() throws Exception {
    String key = keyNamed("exporter");

    var response = exportOf(key);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
        .matches("attachment; filename=\"?towerdungeon-g1-\\d{8}-\\d{4}\\.tdc\"?");
    var file = CampaignFile.decode(response.body());
    assertThat(file.schema()).isEqualTo(1);
    assertThat(new String(response.body(), ISO_8859_1)).doesNotContain(key);
    assertThat(payloadOf(response.body()).get("campaign").get("reserves")).isNotEmpty();
  }

  @Test
  void importReplacesTheCampaignAndRotatesTheHeadIntoBackupOne() throws Exception {
    String key = keyNamed("importer");
    byte[] base = exportOf(key).body();

    var first = importFile("Bearer " + key, withCredit(base, 99));
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(creditOf(export("Bearer " + key).body())).isEqualTo(99);
    assertThat(playerDir(key).resolve("backup").resolve("1.tdc")).doesNotExist();

    var second = importFile("Bearer " + key, withCredit(base, 123));
    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(creditOf(export("Bearer " + key).body())).isEqualTo(123);

    byte[] backupOne = Files.readAllBytes(playerDir(key).resolve("backup").resolve("1.tdc"));
    byte[] head = Files.readAllBytes(playerDir(key).resolve("campaign.tdc"));
    assertThat(creditOf(backupOne)).isEqualTo(99);
    assertThat(creditOf(head)).isEqualTo(123);
  }

  @Test
  void aFileFromANewerSchemaIsRefusedWith422AndChangesNothing() throws Exception {
    String key = keyNamed("newer");
    byte[] base = exportOf(key).body();
    byte[] newer =
        CampaignFile.encode(2, 1, SAVED_AT, "9.9.9", CampaignFile.decode(base).payload());

    var response = importFile("Bearer " + key, newer);

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(playerDir(key).resolve("campaign.tdc")).doesNotExist();
  }

  @Test
  void aCorruptFileIsRefusedWith400() throws Exception {
    String key = keyNamed("corrupt");
    byte[] file = exportOf(key).body();
    file[file.length - 2] ^= 1;

    assertThat(importFile("Bearer " + key, file).statusCode()).isEqualTo(400);
    assertThat(importFile("Bearer " + key, new byte[0]).statusCode()).isEqualTo(400);
    assertThat(importFile("Bearer " + key, "TDC1 nonsense\n{}".getBytes(ISO_8859_1)).statusCode())
        .isEqualTo(400);
    assertThat(playerDir(key).resolve("campaign.tdc")).doesNotExist();
  }

  /** A valid file of exactly {@code total} bytes, padded through a field of {@code meta}. */
  private byte[] fileOfSize(byte[] base, int total) {
    var payload = payloadOf(base);
    var meta = payload.putObject("meta");
    int pad = total - base.length;
    for (int attempt = 0; attempt < 10; attempt++) {
      meta.put("pad", "x".repeat(Math.max(pad, 0)));
      byte[] file = CampaignFile.encode(1, 1, SAVED_AT, "0.1.0", mapper.writeValueAsBytes(payload));
      if (file.length == total) {
        return file;
      }
      pad += total - file.length;
    }
    throw new IllegalStateException("could not size the file to " + total);
  }

  @Test
  void aBodyOfExactlyOneMebibyteImportsAndOneByteMoreIsRejected() throws Exception {
    String key = keyNamed("sizes");
    byte[] base = exportOf(key).body();

    byte[] atLimit = fileOfSize(base, MAX);
    assertThat(atLimit).hasSize(MAX);
    assertThat(importFile("Bearer " + key, atLimit).statusCode()).isEqualTo(200);

    double creditBefore = liveCredit(key);
    byte[] richer = fileOfSize(withCredit(base, creditBefore + 1), MAX + 1);
    assertThat(richer).hasSize(MAX + 1);
    assertThat(refusedAsTooLarge(() -> importFile("Bearer " + key, richer))).isTrue();
    assertThat(importChunked("Bearer " + key, atLimit).statusCode()).isEqualTo(200);
    assertThat(refusedAsTooLarge(() -> importChunked("Bearer " + key, richer))).isTrue();
    assertThat(liveCredit(key)).isEqualTo(creditBefore);
  }
}

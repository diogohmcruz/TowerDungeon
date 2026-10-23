package io.github.diogohmcruz.towerdungeon.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignKey;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignSchema;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignStore.Rotation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Drives the real socket endpoint over a real connection. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketHandlerTest {
  private static final java.nio.file.Path DATA_DIR;

  static {
    try {
      DATA_DIR = Files.createTempDirectory("td-ws-test");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
  }

  @LocalServerPort int port;
  @Autowired GameService gameService;
  @Autowired CampaignStore store;
  @Autowired ObjectMapper mapper;

  private final HttpClient http = HttpClient.newHttpClient();

  /** A client connection that records what the server sends and how it closed. */
  private static final class Client implements WebSocket.Listener {
    final List<String> frames = new CopyOnWriteArrayList<>();
    final AtomicInteger closeCode = new AtomicInteger(-1);
    volatile Throwable error;
    WebSocket socket;
    private final StringBuilder partial = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        frames.add(partial.toString());
        partial.setLength(0);
      }
      ws.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
      closeCode.set(statusCode);
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable err) {
      error = err;
    }

    boolean closed() {
      return closeCode.get() != -1 || error != null;
    }
  }

  private static String newKey() {
    var bytes = UUID.randomUUID().toString().replace("-", "").substring(0, 22);
    return bytes;
  }

  private Client connect(String key) throws Exception {
    var client = new Client();
    client.socket =
        http.newWebSocketBuilder()
            .subprotocols("td.v1", key)
            .buildAsync(URI.create("ws://localhost:" + port + "/api/ws"), client)
            .get(5, TimeUnit.SECONDS);
    return client;
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Timed out waiting for the condition");
      }
      Thread.sleep(20);
    }
  }

  private static String playerId(String key) {
    return CampaignKey.playerId(key).orElseThrow();
  }

  @Test
  void aFrameKeepsTheStateAtTheTopLevelAndAddsTheSessionFacts() throws Exception {
    var client = connect(newKey());
    await(() -> !client.frames.isEmpty());

    JsonNode frame = mapper.readTree(client.frames.getFirst());
    assertThat(frame.has("credit")).isTrue();
    assertThat(frame.has("units")).isTrue();
    assertThat(frame.get("session").get("resumed").asBoolean()).isFalse();
    assertThat(frame.get("session").get("schema").asInt()).isEqualTo(CampaignSchema.CURRENT);
    assertThat(frame.get("session").get("notices").isArray()).isTrue();
    assertThat(frame.get("session").get("held").asBoolean()).isFalse();
    client.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
  }

  @Test
  void aMalformedKeyIsClosedWith4400() throws Exception {
    var client = connect("short");

    await(client::closed);
    assertThat(client.closeCode.get()).isEqualTo(4400);
    assertThat(client.frames).isEmpty();
  }

  @Test
  void aSecondConnectionWithTheSameKeyReplacesTheFirstWith4001() throws Exception {
    var key = newKey();
    var first = connect(key);
    await(() -> !first.frames.isEmpty());

    var second = connect(key);
    await(first::closed);

    assertThat(first.closeCode.get()).isEqualTo(4001);
    await(() -> !second.frames.isEmpty());
    assertThat(second.closed()).isFalse();
    assertThat(gameService.isConnected(playerId(key))).isTrue();
    second.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
  }

  @Test
  void aReturningKeyResumesItsSavedCampaign() throws Exception {
    var key = newKey();
    var saved = gameServiceFresh();
    saved.setCredit(777.0);
    store
        .save(
            playerId(key),
            CampaignStore.INITIAL_SEQ,
            Rotation.KEEP,
            CampaignSchema.CURRENT,
            "test",
            mapper.writeValueAsBytes(saved.toSnapshot()))
        .block();

    var client = connect(key);
    await(() -> !client.frames.isEmpty());

    JsonNode frame = mapper.readTree(client.frames.getFirst());
    assertThat(frame.get("credit").asDouble()).isEqualTo(777.0);
    assertThat(frame.get("session").get("resumed").asBoolean()).isTrue();
    client.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
  }

  @Test
  void aRecordFromANewerSchemaIsClosedWith4426() throws Exception {
    var key = newKey();
    store
        .save(
            playerId(key),
            CampaignStore.INITIAL_SEQ,
            Rotation.KEEP,
            CampaignSchema.CURRENT + 1,
            "test",
            "{}".getBytes(UTF_8))
        .block();

    var client = connect(key);

    await(client::closed);
    assertThat(client.closeCode.get()).isEqualTo(4426);
  }

  @Test
  void anUnreadableRecordIsClosedWith4500() throws Exception {
    var key = newKey();
    store
        .save(
            playerId(key),
            CampaignStore.INITIAL_SEQ,
            Rotation.KEEP,
            CampaignSchema.CURRENT,
            "test",
            "not json".getBytes(UTF_8))
        .block();

    var client = connect(key);

    await(client::closed);
    assertThat(client.closeCode.get()).isEqualTo(4500);
    assertThat(gameService.hasSlot(playerId(key))).isFalse();
  }

  @Test
  void aCleanCloseDetachesTheSession() throws Exception {
    var key = newKey();
    var client = connect(key);
    await(() -> gameService.isConnected(playerId(key)));

    client.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);

    await(() -> !gameService.isConnected(playerId(key)));
    // The slot lingers without ticking; it is not resurrected and not connected.
    assertThat(gameService.hasSlot(playerId(key))).isTrue();
  }

  @Test
  void anAbruptDisconnectDetachesTheSession() throws Exception {
    var key = newKey();
    var client = connect(key);
    await(() -> gameService.isConnected(playerId(key)));

    client.socket.abort();

    await(() -> !gameService.isConnected(playerId(key)));
  }

  @Test
  void aBadActionDoesNotDropTheCampaignAndAnswersWithANotice() throws Exception {
    var key = newKey();
    var client = connect(key);
    await(() -> !client.frames.isEmpty());

    client.socket.sendText("this is not json", true).get(5, TimeUnit.SECONDS);
    client
        .socket
        .sendText(
            "{\"gameAction\":\"INVADE\",\"payload\":{\"units\":{\"WARRIOR\":2,\"ARCHER\":9},"
                + "\"startFloor\":0}}",
            true)
        .get(5, TimeUnit.SECONDS);

    await(() -> client.frames.stream().anyMatch(f -> f.contains("INVALID_INVASION")));
    await(() -> client.frames.stream().anyMatch(f -> f.contains("ACTION_FAILED")));
    assertThat(client.closed()).isFalse();
    assertThat(gameService.isConnected(playerId(key))).isTrue();
    assertThat(gameService.playerState(playerId(key)).hasUnitsOnTower()).isFalse();
    client.socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
  }

  private io.github.diogohmcruz.towerdungeon.domain.models.GameState gameServiceFresh() {
    return new io.github.diogohmcruz.towerdungeon.domain.models.GameState(
        new io.github.diogohmcruz.towerdungeon.config.GameProperties());
  }
}

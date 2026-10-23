package io.github.diogohmcruz.towerdungeon.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import io.github.diogohmcruz.TowerDungeonApplication;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code agent-stdio} profile as a program: JSON-RPC in on stdin, JSON-RPC out on stdout, and
 * nothing else on stdout, whatever the game or the library logs. The same tools answer there as on
 * HTTP, through the stateful binding.
 */
class AgentStdioMcpTest {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final PipedOutputStream toServer = new PipedOutputStream();
  private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

  private void send(String json) throws Exception {
    toServer.write((json + "\n").getBytes(StandardCharsets.UTF_8));
    toServer.flush();
  }

  private List<String> lines() {
    return captured.toString(StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).toList();
  }

  /** The reply to a request id, waiting at most ten seconds for it. */
  private JsonNode reply(int id) throws Exception {
    var deadline = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < deadline) {
      for (var line : lines()) {
        if (!line.startsWith("{")) {
          continue;
        }
        var node = MAPPER.readTree(line);
        if (node.has("id")
            && node.get("id").asInt(-1) == id
            && (node.has("result") || node.has("error"))) {
          return node;
        }
      }
      Thread.sleep(25);
    }
    throw new AssertionError("no reply to request " + id + " in: " + lines());
  }

  @Test
  void theToolsAnswerOverStdioAndStdoutCarriesOnlyProtocol() throws Exception {
    var dataDir = Files.createTempDirectory("td-stdio-mcp");
    var realIn = System.in;
    var realOut = System.out;
    InputStream in = new PipedInputStream(toServer, 1 << 16);
    System.setIn(in);
    System.setOut(new PrintStream(captured, true));
    try (var context =
        new SpringApplicationBuilder(TowerDungeonApplication.class)
            .profiles("agent-stdio")
            .run("--game.persistence.data-dir=" + dataDir)) {
      System.setOut(realOut);
      System.setIn(realIn);

      send(
          "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":"
              + "\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}");
      var init = reply(1);
      assertThat(init.at("/result/serverInfo/name").asString()).isEqualTo("tower-dungeon");
      send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

      send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
      var names = new ArrayList<String>();
      reply(2).at("/result/tools").forEach(t -> names.add(t.get("name").asString()));
      assertThat(names)
          .containsExactlyElementsOf(
              context.getBean(ToolCatalogue.class).tools().stream().map(t -> t.name()).toList());

      send(
          "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":"
              + "\"td_sandbox_new\",\"arguments\":{}}}");
      var opened = reply(3).at("/result");
      assertThat(opened.get("isError").asBoolean()).isFalse();
      var handle = opened.at("/structuredContent/campaign").asString();
      assertThat(handle).startsWith("sandbox-");

      send(
          "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":"
              + "\"td_wait\",\"arguments\":{\"conditions\":[{\"type\":\"ticks\",\"value\":3}]}}}");
      var waited = reply(4).at("/result");
      assertThat(waited.at("/structuredContent/met").asString()).isEqualTo("ticks");

      send("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}");
      assertThat(reply(5).at("/result/resources").size()).isGreaterThan(5);
      send(
          "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"resources/read\",\"params\":{\"uri\":\"td://state\"}}");
      assertThat(reply(6).at("/result/contents/0/text").asString()).contains("\"seq\":3");
    } finally {
      System.setOut(realOut);
      System.setIn(realIn);
      toServer.close();
    }

    // Devtools (a test-classpath dependency, absent from the packaged jar) logs one line to stdout
    // before Spring Boot's logging exists. Everything else on stdout must be JSON-RPC.
    var stdout = lines().stream().filter(line -> !line.contains("devtools")).toList();
    assertThat(stdout).isNotEmpty();
    for (var line : stdout) {
      var node = MAPPER.readTree(line);
      assertThat(node.get("jsonrpc").asString()).as(line).isEqualTo("2.0");
    }
  }
}

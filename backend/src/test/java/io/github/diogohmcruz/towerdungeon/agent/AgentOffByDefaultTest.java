package io.github.diogohmcruz.towerdungeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpStatelessAsyncServer;

/** Without the agent profile the game exposes no MCP endpoint, tool or server at all. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentOffByDefaultTest {
  private static final java.nio.file.Path DATA_DIR;

  static {
    try {
      DATA_DIR = Files.createTempDirectory("td-agent-off");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
  }

  @LocalServerPort int port;
  @Autowired ApplicationContext context;

  @Test
  void thereIsNoAgentSurfaceAndNoMcpServer() {
    assertThat(context.getBeansOfType(ToolCatalogue.class)).isEmpty();
    assertThat(context.getBeansOfType(McpStatelessAsyncServer.class)).isEmpty();
    assertThat(context.getBeansOfType(McpAsyncServer.class)).isEmpty();
  }

  @Test
  void theMcpPathIsNotFound() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

    var status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

    assertThat(status.statusCode()).isEqualTo(404);
  }
}

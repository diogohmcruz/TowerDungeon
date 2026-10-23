package io.github.diogohmcruz.towerdungeon.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import io.github.diogohmcruz.TowerDungeonApplication;
import io.github.diogohmcruz.towerdungeon.api.GameWebSocketHandler;
import io.github.diogohmcruz.towerdungeon.api.HandshakeGuardFilter;
import io.github.diogohmcruz.towerdungeon.api.UnitStatsController;
import io.github.diogohmcruz.towerdungeon.api.WebSocketConfig;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;

/**
 * The {@code agent-stdio} profile runs the game with no web server, Sandboxes switched on, and
 * nothing on stdout, which belongs to the MCP protocol.
 */
class AgentStdioProfileTest {

  @Test
  void theProfileBootsWithoutAWebServerAndKeepsStdoutClean() throws Exception {
    var dataDir = Files.createTempDirectory("td-stdio-test");
    var realOut = System.out;
    var captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true));
    try (var context =
        new SpringApplicationBuilder(TowerDungeonApplication.class)
            .profiles("agent-stdio")
            .run("--game.persistence.data-dir=" + dataDir)) {
      System.setOut(realOut);

      assertThat(context)
          .isNotInstanceOf(org.springframework.web.context.WebApplicationContext.class);
      assertThat(context.getBeansOfType(GameWebSocketHandler.class)).isEmpty();
      assertThat(context.getBeansOfType(WebSocketConfig.class)).isEmpty();
      assertThat(context.getBeansOfType(HandshakeGuardFilter.class)).isEmpty();
      assertThat(context.getBeansOfType(UnitStatsController.class)).isEmpty();
      assertThat(context.getBean(GameService.class)).isNotNull();
      assertThat(context.getBean(SandboxRegistry.class).open("grant", null, null).sandboxId())
          .isNotNull();
    } finally {
      System.setOut(realOut);
    }

    // Devtools (a test-classpath dependency, absent from the packaged jar) logs one line to stdout
    // before Spring Boot's logging exists. Nothing else may reach stdout.
    assertThat(captured.toString().lines().filter(line -> !line.contains("devtools"))).isEmpty();
  }

  @Test
  void sandboxesAreOffUnlessTheProfileOrTheSettingTurnsThemOn() {
    assertThat(new GameProperties().getAgent().getSandbox().isEnabled()).isFalse();
  }
}

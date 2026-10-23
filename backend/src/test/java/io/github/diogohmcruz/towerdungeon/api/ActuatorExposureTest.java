package io.github.diogohmcruz.towerdungeon.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Only health, info and metrics are reachable over HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorExposureTest {
  private static final java.nio.file.Path DATA_DIR;

  static {
    try {
      DATA_DIR = Files.createTempDirectory("td-actuator-test");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
  }

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

  private int status(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
    return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  @Test
  void healthInfoAndMetricsAreExposed() throws Exception {
    assertThat(status("/actuator/health")).isEqualTo(200);
    assertThat(status("/actuator/metrics")).isEqualTo(200);
    assertThat(status("/actuator/info")).isEqualTo(200);
  }

  @Test
  void everythingElseIsNot() throws Exception {
    for (var endpoint :
        new String[] {
          "env", "beans", "heapdump", "threaddump", "configprops", "mappings", "loggers"
        }) {
      assertThat(status("/actuator/" + endpoint)).as(endpoint).isEqualTo(404);
    }
  }
}

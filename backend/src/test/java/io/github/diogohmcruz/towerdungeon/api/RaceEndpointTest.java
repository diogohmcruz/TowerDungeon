package io.github.diogohmcruz.towerdungeon.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.diogohmcruz.towerdungeon.domain.trial.AttemptLedger;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderLabel;
import io.github.diogohmcruz.towerdungeon.domain.trial.ContenderStatus;
import io.github.diogohmcruz.towerdungeon.domain.trial.TrialResult;
import io.github.diogohmcruz.towerdungeon.persistence.CampaignKey;
import tools.jackson.databind.json.JsonMapper;

/** The finished table over the real application. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RaceEndpointTest {
  private static final Path DATA_DIR = createDataDir();

  @Value("${local.server.port}")
  int port;

  @Autowired AttemptLedger ledger;

  private static Path createDataDir() {
    try {
      return Files.createTempDirectory("tower-dungeon-race-test");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
  }

  private static String keyNamed(String name) {
    return (name + "AAAAAAAAAAAAAAAAAAAAAA").substring(0, 22);
  }

  private HttpResponse<String> get(String authorization) throws Exception {
    var request =
        HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/races/season-1/table"));
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return HttpClient.newHttpClient().send(request.GET().build(), BodyHandlers.ofString());
  }

  @Test
  void theTableIsShownOnlyToAContenderWhoseAttemptIsOver() throws Exception {
    var finisher = CampaignKey.playerId(keyNamed("finisher")).orElseThrow();
    ledger.start("season-1", finisher);
    ledger.close(
        new TrialResult(
            "season-1",
            finisher,
            1,
            "Ada",
            ContenderLabel.HUMAN,
            ContenderStatus.FINISHED,
            2000,
            2000,
            10,
            10,
            new TrialResult.Cost(3, 1, 12, 40),
            Map.of(1, 60L),
            Instant.parse("2026-10-24T10:00:00Z")));

    assertThat(get(null).statusCode()).isEqualTo(401);
    assertThat(get("Bearer " + keyNamed("stranger")).statusCode()).isEqualTo(403);
    var shown = get("Bearer " + keyNamed("finisher"));
    assertThat(shown.statusCode()).isEqualTo(200);

    var row = JsonMapper.builder().build().readTree(shown.body()).path("hands").get(0);
    assertThat(row.path("alias").asString()).isEqualTo("Ada");
    assertThat(row.path("place").asInt()).isEqualTo(1);
    assertThat(row.path("you").asBoolean()).isTrue();
    assertThat(row.path("cost").path("unitsLost").asInt()).isEqualTo(12);
    assertThat(shown.body()).doesNotContain(finisher);
  }
}

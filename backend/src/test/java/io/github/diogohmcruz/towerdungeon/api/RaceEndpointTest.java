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

  private HttpResponse<String> getRaces(String authorization) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/races"));
    if (authorization != null) {
      request.header("Authorization", authorization);
    }
    return HttpClient.newHttpClient().send(request.GET().build(), BodyHandlers.ofString());
  }

  @Test
  void theRacesOnOfferShowTheTermsAndTheKeysAttemptsAndNoSeed() throws Exception {
    assertThat(getRaces(null).statusCode()).isEqualTo(401);

    var fresh = getRaces("Bearer " + keyNamed("newcomer"));
    assertThat(fresh.statusCode()).isEqualTo(200);
    var race = JsonMapper.builder().build().readTree(fresh.body()).get(0);
    assertThat(race.path("raceId").asString()).isEqualTo("season-1");
    assertThat(race.path("finishFloor").asInt()).isEqualTo(10);
    assertThat(race.path("attemptsLeft").asInt()).isEqualTo(race.path("maxAttempts").asInt());
    assertThat(race.path("refusal").isNull()).isTrue();
    assertThat(fresh.body()).doesNotContain("seed").doesNotContain("configHash");

    var spent = CampaignKey.playerId(keyNamed("spender")).orElseThrow();
    for (var attempt = 1; attempt <= 3; attempt++) {
      ledger.start("season-1", spent);
      ledger.close(
          new TrialResult(
              "season-1",
              spent,
              attempt,
              "Ada",
              ContenderLabel.HUMAN,
              ContenderStatus.TIMED_OUT,
              10,
              10,
              10,
              2,
              new TrialResult.Cost(1, 0, 0, 0),
              Map.of(),
              Instant.parse("2026-10-24T10:00:00Z")));
    }
    var used =
        JsonMapper.builder()
            .build()
            .readTree(getRaces("Bearer " + keyNamed("spender")).body())
            .get(0);
    assertThat(used.path("attemptsLeft").asInt()).isZero();
    assertThat(used.path("refusal").asString()).isEqualTo("NO_ATTEMPTS");
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

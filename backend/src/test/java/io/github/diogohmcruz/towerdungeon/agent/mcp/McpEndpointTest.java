package io.github.diogohmcruz.towerdungeon.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.auth.TokenHasher;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolCatalogue;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent surface over HTTP, end to end on the real server: the protocol matrix of the guarding
 * filter, then the observe and sandbox tools and the resources through the MCP library, against a
 * Sandbox Campaign and a human one. A secrets canary asserts that hidden facts reach no result.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("agent")
class McpEndpointTest {
  private static final String TOKEN = "td-test-token-9c1e5a";
  private static final String HUMAN = "0123456789abcdef0123456789abcdef";
  private static final Path DATA_DIR;
  private static final Path AUDIT;

  static {
    try {
      DATA_DIR = Files.createTempDirectory("td-mcp-test");
      AUDIT = DATA_DIR.resolve("audit.jsonl");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("game.persistence.data-dir", DATA_DIR::toString);
    registry.add("game.agent.http.dev-token-hash", () -> TokenHasher.sha256Hex(TOKEN));
    registry.add("game.agent.http.audit-file", AUDIT::toString);
    registry.add("game.agent.http.dev-campaign", () -> HUMAN);
    registry.add("game.agent.sandbox.enabled", () -> "true");
  }

  /** Every call finds a full bucket: the limits themselves are tested on their own. */
  @TestConfiguration
  static class Unlimited {
    @Bean
    @Primary
    RateLimiter unlimitedRateLimiter() {
      var clock = new AtomicLong();
      return new RateLimiter(() -> clock.addAndGet(1_000_000_000L));
    }
  }

  @LocalServerPort int port;
  @Autowired GameService service;
  @Autowired ToolCatalogue catalogue;
  @Autowired SandboxRegistry sandboxes;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private WebTestClient client;
  private int nextId = 1;

  @BeforeEach
  void connect() {
    client =
        WebTestClient.bindToServer(new JdkClientHttpConnector())
            .baseUrl("http://localhost:" + port)
            .build();
    if (!service.hasSlot(HUMAN)) {
      service.attach(HUMAN, "ws-test").block();
    }
  }

  // ---------------------------------------------------------------- helpers

  private WebTestClient.RequestBodySpec post(String token) {
    var spec =
        client
            .post()
            .uri("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
    if (token != null) {
      spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return spec;
  }

  private String raw(String body) {
    return post(TOKEN)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private JsonNode rpc(String method, String params) {
    var body =
        "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"%s\",\"params\":%s}"
            .formatted(nextId++, method, params);
    return mapper.readTree(raw(body));
  }

  /**
   * Calls a tool and returns the whole result object ({@code isError}, {@code structuredContent}).
   */
  private JsonNode call(String tool, String arguments) {
    var response =
        rpc("tools/call", "{\"name\":\"%s\",\"arguments\":%s}".formatted(tool, arguments));
    assertThat(response.has("error")).as(response.toString()).isFalse();
    return response.get("result");
  }

  private JsonNode ok(String tool, String arguments) {
    var result = call(tool, arguments);
    assertThat(result.get("isError").asBoolean()).as(result.toString()).isFalse();
    return result.get("structuredContent");
  }

  private String openSandbox() {
    return ok("td_sandbox_new", "{}").get("campaign").asString();
  }

  @AfterEach
  void closeSandboxes() {
    sandboxes.closeAll("dev");
  }

  // ---------------------------------------------------------------- the protocol matrix

  @Test
  void noTokenIs401WithABearerChallenge() {
    post(null)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .expectBody(String.class)
        .isEqualTo("{\"error\":\"unauthorized\"}");
  }

  @Test
  void aWrongTokenIs401() {
    post("not-the-token").bodyValue("{}").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void aBadOriginIs403EvenWithAGoodToken() {
    post(TOKEN)
        .header(HttpHeaders.ORIGIN, "https://evil.example")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getIs405AndSaysWhichMethodIsAllowed() {
    client
        .get()
        .uri("/mcp")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
        .exchange()
        .expectStatus()
        .isEqualTo(405)
        .expectHeader()
        .valueEquals(HttpHeaders.ALLOW, "POST");
  }

  @Test
  void anOversizedBodyIs413BeforeTheLibrarySeesIt() {
    var big = "{\"pad\":\"" + "x".repeat(70_000) + "\"}";

    post(TOKEN).bodyValue(big).exchange().expectStatus().isEqualTo(413);
  }

  @Test
  void malformedJsonIsAFixed400WithNoTrace() {
    var body =
        post(TOKEN)
            .bodyValue("{this is not json")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertNoTrace(body);
    assertThat(mapper.readTree(body).at("/error/code").asInt()).isEqualTo(-32600);
  }

  @Test
  void anUnknownMethodIsACleanMethodNotFound() {
    var body =
        post(TOKEN)
            .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"server/discover\"}")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertNoTrace(body);
    assertThat(mapper.readTree(body).at("/error/code").asInt()).isEqualTo(-32601);
  }

  @Test
  void anUnknownToolNeverLeaksATrace() {
    var body =
        raw(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"td_nope\",\"arguments\":{}}}");

    assertNoTrace(body);
  }

  @Test
  void theAuditLogRecordsRejectionsWithoutTheToken() throws IOException {
    post("wrong-token-for-audit").bodyValue("{}").exchange().expectStatus().isUnauthorized();

    var audit = Files.readString(AUDIT);
    assertThat(audit)
        .contains("HTTP_401")
        .doesNotContain("wrong-token-for-audit")
        .doesNotContain(TOKEN);
  }

  private static void assertNoTrace(String body) {
    assertThat(body)
        .doesNotContain(
            "stackTrace", "\tat ", "io.modelcontextprotocol", "java.lang.", "Exception");
  }

  // ---------------------------------------------------------------- tools through the library

  @Test
  void toolsListIsTheCatalogueInOrderWithExplicitAnnotations() {
    var tools = rpc("tools/list", "{}").at("/result/tools");

    var names = new ArrayList<String>();
    tools.forEach(tool -> names.add(tool.get("name").asString()));
    assertThat(names)
        .containsExactlyElementsOf(catalogue.tools().stream().map(t -> t.name()).toList());
    tools.forEach(
        tool -> {
          var annotations = tool.get("annotations");
          assertThat(annotations.has("readOnlyHint")).isTrue();
          assertThat(annotations.has("destructiveHint")).isTrue();
          assertThat(annotations.has("idempotentHint")).isTrue();
          assertThat(annotations.has("openWorldHint")).isTrue();
          assertThat(tool.get("description").asString().length()).isLessThanOrEqualTo(400);
        });
  }

  @Test
  void aSandboxIsOpenedReadAdvancedAndClosed() {
    var sandbox = openSandbox();

    var brief = ok("td_get_state", "{}");
    assertThat(brief.get("view").asString()).isEqualTo("brief");
    assertThat(brief.get("clock").asString()).isEqualTo("manual");
    assertThat(brief.get("campaign").get("outcome").asString()).isEqualTo("PLAYING");

    var waited = ok("td_wait", "{\"conditions\":[{\"type\":\"ticks\",\"value\":5}]}");
    assertThat(waited.get("met").asString()).isEqualTo("ticks");
    assertThat(waited.get("ticks").asInt()).isEqualTo(5);
    assertThat(waited.get("state").get("seq").asLong()).isGreaterThanOrEqualTo(5);

    var actions = ok("td_get_actions", "{\"campaign\":\"" + sandbox + "\"}");
    assertThat(actions.get("actions").size()).isGreaterThan(5);

    assertThat(
            ok("td_sandbox_close", "{\"campaign\":\"" + sandbox + "\"}").get("closed").asBoolean())
        .isTrue();
    var after = call("td_get_state", "{\"campaign\":\"" + sandbox + "\"}");
    assertThat(after.get("isError").asBoolean()).isTrue();
    assertThat(after.get("structuredContent").get("code").asString()).isEqualTo("NO_CAMPAIGN");
  }

  @Test
  void aHumanCampaignIsReadableWhenNoSandboxIsOpen() {
    var state = ok("td_get_state", "{}");

    assertThat(state.get("clock").asString()).isEqualTo("realtime");
    assertThat(state.get("village").get("villagers").asInt()).isGreaterThan(0);
  }

  @Test
  void aHumanCampaignIsWatchedNotAdvancedByAWait() {
    var before = ok("td_get_state", "{}").get("seq").asLong();

    var waited =
        ok(
            "td_wait",
            "{\"conditions\":[{\"type\":\"supplies_below\",\"value\":1}],\"timeout_ms\":300}");

    assertThat(waited.get("met").asString()).isEqualTo("timeout");
    assertThat(waited.has("simulated_seconds")).isFalse();
    assertThat(ok("td_get_state", "{}").get("seq").asLong()).isGreaterThanOrEqualTo(before);
  }

  @Test
  void aCampaignThatIsNotYoursReadsLikeOneThatDoesNotExist() {
    var elsewhere =
        call("td_get_state", "{\"campaign\":\"sandbox-00000000-0000-0000-0000-000000000000\"}");
    var human = call("td_get_state", "{\"campaign\":\"" + HUMAN + "\"}");

    assertThat(elsewhere.get("structuredContent").get("code").asString()).isEqualTo("NO_CAMPAIGN");
    assertThat(human.get("structuredContent").get("code").asString()).isEqualTo("NO_CAMPAIGN");
    assertThat(human.get("structuredContent").get("message"))
        .isEqualTo(elsewhere.get("structuredContent").get("message"));
  }

  @Test
  void anExpeditionShowsInTheTowerLedgerAndEvents() {
    var sandbox = openSandbox();
    var started = service.execute(sandbox, new Command.StartExpedition(java.util.Map.of(), 0));
    assertThat(started.changedState()).isTrue();

    var waited =
        ok(
            "td_wait",
            "{\"conditions\":[{\"type\":\"floor_changed\"},{\"type\":\"expedition_ended\"}],"
                + "\"max_ticks\":600}");
    assertThat(waited.get("met").asString()).isIn("floor_changed", "expedition_ended");

    var tower = ok("td_get_tower", "{}");
    assertThat(tower.get("max_floor").asInt()).isEqualTo(100);
    var events = ok("td_get_events", "{}");
    assertThat(events.get("events").size()).isGreaterThan(0);
    var ledger = ok("td_get_ledger", "{}");
    assertThat(ledger.has("lines")).isTrue();
    ok("td_sandbox_close", "{\"campaign\":\"" + sandbox + "\"}");
  }

  @Test
  void rulesAreSectionedAndTheEnemyTableIsForSandboxCallersOnly() {
    var index = ok("td_get_rules", "{}");
    assertThat(index.get("sections").size()).isEqualTo(5);
    assertThat(index.get("manifest_hash").asString()).isEqualTo(catalogue.manifestHash());

    var units = ok("td_get_rules", "{\"section\":\"units\"}");
    assertThat(units.get("units").size()).isEqualTo(UnitStats.values().length);
    assertThat(ok("td_get_rules", "{\"section\":\"enemies\"}").get("enemies").size())
        .isGreaterThan(5);
  }

  @Test
  void badArgumentsAreToolErrorsNotProtocolErrors() {
    var unknown = call("td_get_state", "{\"nope\":1}");
    var badView = call("td_get_state", "{\"view\":\"everything\"}");
    var badWait = call("td_wait", "{\"conditions\":[{\"type\":\"bogus\"}]}");

    // The library checks the schema first; the catalogue's own check backs it up. Either way the
    // caller gets a tool error with no trace, never a protocol error.
    for (var result : List.of(unknown, badView, badWait)) {
      assertThat(result.get("isError").asBoolean()).isTrue();
      assertNoTrace(result.toString());
    }
  }

  // ---------------------------------------------------------------- resources

  @Test
  void resourcesListAndReadAndTheGlossaryIsFiltered() {
    var list = rpc("resources/list", "{}").at("/result/resources");
    var uris = new ArrayList<String>();
    list.forEach(r -> uris.add(r.get("uri").asString()));
    assertThat(uris).contains("td://glossary", "td://state", "td://rules/units", "td://tower");

    var glossary =
        rpc("resources/read", "{\"uri\":\"td://glossary\"}")
            .at("/result/contents/0/text")
            .asString();
    assertThat(glossary)
        .contains("**Campaign**")
        .doesNotContain("_Avoid_")
        .doesNotContain("Planned");

    var rules = rpc("resources/read", "{\"uri\":\"td://rules/units\"}");
    assertThat(rules.at("/result/contents/0/mimeType").asString()).isEqualTo("application/json");

    var templates = rpc("resources/templates/list", "{}").at("/result/resourceTemplates");
    assertThat(templates.size()).isEqualTo(2);
  }

  @Test
  void aMissingResourceIsAFixedError() {
    var response = rpc("resources/read", "{\"uri\":\"td://nope\"}");

    assertThat(response.has("error")).isTrue();
    assertNoTrace(response.toString());
  }

  // ---------------------------------------------------------------- seeds

  private JsonNode openSandboxWith(String arguments) {
    return ok("td_sandbox_new", arguments);
  }

  @Test
  void aSandboxSeedIsPublicAndRepeatsARunThroughTheTools() {
    var first = openSandboxWith("{\"seed\":4242}");
    var second = openSandboxWith("{\"seed\":4242}");
    assertThat(first.get("seed").asLong()).isEqualTo(4242);
    assertThat(openSandboxWith("{}").get("seed").isNumber()).isTrue();

    var views = new ArrayList<String>();
    for (var sandbox :
        List.of(first.get("campaign").asString(), second.get("campaign").asString())) {
      service.execute(sandbox, new Command.StartExpedition(java.util.Map.of(), 0));
      var args =
          "{\"campaign\":\"%s\",\"conditions\":[{\"type\":\"ticks\",\"value\":8}]}"
              .formatted(sandbox);
      ok("td_wait", args);
      views.add(
          ok("td_get_state", "{\"campaign\":\"" + sandbox + "\",\"view\":\"full\"}").toString());
    }

    assertThat(views.get(1)).isEqualTo(views.get(0));
  }

  @Test
  void aHumanCampaignsSeedAndTowerPlanAreInNoResultAndNoSandboxHasToBeOpenToReadIt() {
    var human = service.playerState(HUMAN);
    var seed = human.getRandom().snapshot().seed();
    human.getConfig().getArchitecture().setSeed(SEED_CANARY);

    var seen = new ArrayList<String>();
    for (var view : List.of("brief", "full")) {
      seen.add(ok("td_get_state", "{\"view\":\"" + view + "\"}").toString());
    }
    for (var tool :
        List.of(
            "td_get_actions", "td_get_tower", "td_get_ledger", "td_get_events", "td_get_rules")) {
      seen.add(ok(tool, "{}").toString());
    }
    for (var uri :
        List.of("td://state", "td://actions", "td://tower", "td://ledger", "td://events")) {
      seen.add(rpc("resources/read", "{\"uri\":\"" + uri + "\"}").toString());
    }

    var everything = String.join("\n", seen);
    assertThat(everything)
        .doesNotContain(String.valueOf(seed), String.valueOf(SEED_CANARY), "\"seed\"");
  }

  // ---------------------------------------------------------------- the secrets canary

  private static final long SEED_CANARY = 6174839251L;
  private static final double HEALTH_FACTOR_CANARY = 1.2345678;
  private static final double DAMAGE_FACTOR_CANARY = 0.8765432;

  @Test
  void secretsSeededIntoACampaignAppearInNoToolResultAndNoResource() {
    var sandbox = openSandbox();
    var state = service.playerState(sandbox);
    var names = new ArrayList<String>();
    state.getConfig().getArchitecture().setSeed(SEED_CANARY);
    state
        .getUnits()
        .values()
        .forEach(
            units ->
                units.forEach(
                    unit -> {
                      unit.setHealthFactor(HEALTH_FACTOR_CANARY);
                      unit.setDamageFactor(DAMAGE_FACTOR_CANARY);
                      names.add(unit.getName());
                      for (var enemy :
                          io.github.diogohmcruz.towerdungeon.domain.models.EnemyStats.values()) {
                        unit.addPhobia(enemy);
                      }
                    }));
    service.execute(sandbox, new Command.StartExpedition(java.util.Map.of(), 0));
    ok("td_wait", "{\"conditions\":[{\"type\":\"ticks\",\"value\":30}]}");

    var seen = new ArrayList<String>();
    for (var view : List.of("brief", "full")) {
      seen.add(ok("td_get_state", "{\"view\":\"" + view + "\"}").toString());
    }
    seen.add(ok("td_get_actions", "{}").toString());
    seen.add(ok("td_get_tower", "{}").toString());
    seen.add(ok("td_get_events", "{}").toString());
    seen.add(ok("td_get_rules", "{}").toString());
    for (var section : List.of("units", "upgrades", "shortcuts", "milestones", "tick")) {
      seen.add(ok("td_get_rules", "{\"section\":\"" + section + "\"}").toString());
    }
    for (var uri :
        List.of(
            "td://state",
            "td://actions",
            "td://tower",
            "td://events",
            "td://glossary",
            "td://rules/units",
            "td://rules/upgrades",
            "td://rules/shortcuts",
            "td://rules/milestones",
            "td://rules/tick",
            "td://rules/enemies",
            "td://tower/floors/1")) {
      var read = rpc("resources/read", "{\"uri\":\"" + uri + "\"}");
      if (read.has("result")) {
        seen.add(read.at("/result/contents/0/text").asString());
      }
    }
    // The tool list names a "seed" argument (a Sandbox's seed is public), so it is checked apart.
    assertThat(rpc("tools/list", "{}").toString())
        .doesNotContain(String.valueOf(SEED_CANARY), "towerSecret", "healthFactor", "blueprint");

    // The Ledger names the fallen, and only the Ledger may.
    var ledger = ok("td_get_ledger", "{}").toString();
    assertThat(ledger).doesNotContain(String.valueOf(SEED_CANARY), "healthFactor", "towerSecret");
    assertThat(rpc("resources/read", "{\"uri\":\"td://ledger\"}").toString())
        .doesNotContain(String.valueOf(SEED_CANARY), "healthFactor", "towerSecret");

    var everything = String.join("\n", seen);
    assertThat(everything).isNotBlank();
    assertThat(everything)
        .doesNotContain(
            String.valueOf(SEED_CANARY),
            String.valueOf(HEALTH_FACTOR_CANARY),
            String.valueOf(DAMAGE_FACTOR_CANARY),
            "towerSecret",
            "healthFactor",
            "damageFactor",
            "\"seed\"",
            "blueprint");
    names.forEach(name -> assertThat(everything).as("a unit name").doesNotContain(name));
    var phobiaCounts = ok("td_get_state", "{\"view\":\"full\"}").at("/detail/units/known_phobias");
    assertThat(phobiaCounts.size()).as("no phobia is known yet").isEqualTo(0);
    assertThat(everything).as("a human Campaign key").doesNotContain(HUMAN);
    ok("td_sandbox_close", "{\"campaign\":\"" + sandbox + "\"}");
  }
}

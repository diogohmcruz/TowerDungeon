package io.github.diogohmcruz.towerdungeon.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.agent.act.Actions;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.resources.Glossary;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tool catalogue as data: names, descriptions, annotations, the golden manifest, and the one
 * checked path every call takes (scope, arguments, rate limit, audit, error shield).
 *
 * <p>The golden manifest fails on any unreviewed tool or description change. After a deliberate
 * change, review the diff and regenerate both golden files with {@code mvn test
 * -Dtest=ToolCatalogueTest -Dtd.golden.update=true}.
 */
class ToolCatalogueTest {
  private static final Path GOLDEN_DIR = Path.of("src/test/resources/agent");
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Observations observations;
  private Actions actions;
  private AtomicLong nanos;
  private Path auditFile;
  private ToolCatalogue catalogue;
  private final Principal reader = new Principal("reader", EnumSet.of(Scope.OBSERVE));
  private final Principal dev = Principal.dev();

  @BeforeEach
  void setUp() throws IOException {
    observations = mock(Observations.class);
    actions = mock(Actions.class);
    nanos = new AtomicLong(1_000_000_000_000L);
    auditFile = Files.createTempDirectory("td-tools-test").resolve("audit.jsonl");
    catalogue =
        new ToolCatalogue(
            observations,
            actions,
            mock(SandboxRegistry.class),
            new RateLimiter(nanos::get),
            new AuditLog(auditFile, Clock.systemUTC()));
  }

  private CallToolResult call(Principal who, String tool, Map<String, Object> args) {
    return catalogue.call(who, tool, args).block();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(CallToolResult result) {
    return (Map<String, Object>) result.structuredContent();
  }

  // ---------------------------------------------------------------- the catalogue as data

  @Test
  void namesAreFlatSnakeCaseWithTheTdPrefixAndInDeterministicOrder() {
    var names = catalogue.tools().stream().map(ToolDef::name).toList();

    assertThat(names).allMatch(n -> n.matches("td_[a-z0-9_]+"));
    assertThat(names).isSorted().doesNotHaveDuplicates();
    assertThat(names)
        .containsExactly(
            "td_buy_upgrade",
            "td_buy_villagers",
            "td_extract",
            "td_get_actions",
            "td_get_events",
            "td_get_ledger",
            "td_get_rules",
            "td_get_state",
            "td_get_tower",
            "td_recruit",
            "td_sandbox_close",
            "td_sandbox_new",
            "td_sell",
            "td_send_reinforcement_wave",
            "td_start_expedition",
            "td_wait");
  }

  @Test
  void descriptionsAreAtMost400CharactersAndPlain() {
    catalogue
        .tools()
        .forEach(
            tool -> {
              assertThat(tool.description().length()).as(tool.name()).isBetween(20, 400);
              assertThat(tool.description()).as(tool.name()).doesNotContain("\n", "!");
            });
  }

  @Test
  void noNameDescriptionOrSchemaTextUsesATermTheGlossaryAvoids() {
    var glossary = Glossary.load();
    for (var tool : catalogue.tools()) {
      // A name is the snake_case of its words, and "buy" is the verb the design gives two of them.
      var text =
          tool.name().replace('_', ' ').replace(" buy ", " ")
              + " "
              + tool.description()
              + " "
              + MAPPER.writeValueAsString(tool.inputSchema());
      // The Unit Type identifiers are the game's own names for them, Mage among them.
      for (var type : io.github.diogohmcruz.towerdungeon.domain.models.UnitStats.values()) {
        text = text.replace("\"" + type.name() + "\"", "\"UNIT_TYPE\"");
      }
      assertThat(glossary.avoidedIn(text)).as(tool.name()).isEmpty();
    }
  }

  @Test
  void everyToolStatesItsAnnotationsAndTheReadToolsReadOnly() {
    var manifest = MAPPER.readTree(catalogue.manifestJson());
    manifest
        .get("tools")
        .forEach(
            tool -> {
              var annotations = tool.get("annotations");
              assertThat(annotations.has("readOnlyHint")).isTrue();
              assertThat(annotations.has("destructiveHint")).isTrue();
              assertThat(annotations.has("idempotentHint")).isTrue();
              assertThat(annotations.get("openWorldHint").asBoolean()).isFalse();
            });
    catalogue
        .tools()
        .forEach(
            tool -> {
              if (tool.name().startsWith("td_get_")) {
                assertThat(tool.readOnly()).as(tool.name()).isTrue();
                assertThat(tool.destructive()).as(tool.name()).isFalse();
              }
            });
    var close =
        catalogue.tools().stream()
            .filter(t -> t.name().equals("td_sandbox_close"))
            .findFirst()
            .orElseThrow();
    assertThat(close.destructive()).isTrue();
    // td_wait advances a Sandbox's time, so it does not claim to be read-only.
    var wait =
        catalogue.tools().stream()
            .filter(t -> t.name().equals("td_wait"))
            .findFirst()
            .orElseThrow();
    assertThat(wait.readOnly()).isFalse();
  }

  // ---------------------------------------------------------------- the golden manifest

  @Test
  void theManifestMatchesTheGoldenFileAndItsHash() throws IOException {
    var manifest = catalogue.manifestJson();
    var hash = catalogue.manifestHash() + "\n";
    var manifestFile = GOLDEN_DIR.resolve("tools-manifest.json");
    var hashFile = GOLDEN_DIR.resolve("tools-manifest.sha256");
    if (Boolean.getBoolean("td.golden.update")) {
      Files.createDirectories(GOLDEN_DIR);
      Files.writeString(manifestFile, manifest);
      Files.writeString(hashFile, hash);
    }

    assertThat(manifest)
        .as(
            "The tool manifest changed. Review the change, then regenerate the golden files with"
                + " -Dtd.golden.update=true")
        .isEqualTo(Files.readString(manifestFile));
    assertThat(hash).as("manifestHash").isEqualTo(Files.readString(hashFile));
  }

  @Test
  void theManifestIsDeterministic() {
    assertThat(catalogue.manifestJson()).isEqualTo(catalogue.manifestJson());
    assertThat(catalogue.manifestHash()).matches("[0-9a-f]{64}");
  }

  // ---------------------------------------------------------------- one checked path for every
  // call

  @Test
  void aCallWithoutTheScopeIsForbiddenBeforeTheHandlerRuns() {
    var result = call(reader, "td_sandbox_new", Map.of());

    assertThat(result.isError()).isTrue();
    assertThat(data(result)).containsEntry("code", "FORBIDDEN");
  }

  @Test
  void anUnknownToolAnUnknownArgumentAndAMissingOneAreRefused() {
    assertThat(data(call(dev, "td_nope", Map.of()))).containsEntry("code", "UNKNOWN_TOOL");
    assertThat(data(call(dev, "td_get_state", Map.of("nope", 1))))
        .containsEntry("code", "INVALID_ARGUMENT");
    assertThat(data(call(dev, "td_wait", Map.of()))).containsEntry("code", "INVALID_ARGUMENT");
    assertThat(data(call(null, "td_get_state", Map.of()))).containsEntry("code", "UNAUTHENTICATED");
  }

  @Test
  void aHandlerThatThrowsShowsTheCallerOnlyAFixedError() {
    when(observations.state(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("boom: internal detail at /secret/path"));

    var result = call(dev, "td_get_state", Map.of());

    assertThat(result.isError()).isTrue();
    assertThat(data(result)).containsEntry("code", "INTERNAL_ERROR");
    assertThat(MAPPER.writeValueAsString(result)).doesNotContain("boom", "secret", "IllegalState");
  }

  @Test
  void aToolRefusalKeepsItsFixedCodeAndMessage() {
    when(observations.actions(any(), any()))
        .thenThrow(new ToolFailure("NO_CAMPAIGN", "There is no such Campaign for this grant."));

    var result = call(dev, "td_get_actions", Map.of());

    assertThat(result.isError()).isTrue();
    assertThat(data(result))
        .containsEntry("code", "NO_CAMPAIGN")
        .containsEntry("status", "rejected");
  }

  @Test
  void aResultIsBothStructuredAndATextBlock() {
    when(observations.tower(any(), any())).thenReturn(Map.of("highest_floor", 3));

    var result = call(dev, "td_get_tower", Map.of());

    assertThat(result.isError()).isFalse();
    assertThat(data(result)).containsEntry("highest_floor", 3);
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().getFirst().toString()).contains("highest_floor");
  }

  @Test
  void anOversizedResultIsRefusedNotSent() {
    var huge = new HashMap<String, Object>();
    huge.put("blob", "x".repeat(ToolCatalogue.MAX_RESULT_CHARS + 10));
    when(observations.tower(any(), any())).thenReturn(huge);

    var result = call(dev, "td_get_tower", Map.of());

    assertThat(result.isError()).isTrue();
    assertThat(data(result)).containsEntry("code", "RESULT_TOO_LARGE");
  }

  @Test
  void readsAreRateLimitedPerGrantAndToldHowLongToWait() {
    when(observations.tower(any(), any())).thenReturn(Map.of("ok", true));

    for (var i = 0; i < 20; i++) {
      assertThat(call(dev, "td_get_tower", Map.of()).isError()).as("call " + i).isFalse();
    }
    var limited = call(dev, "td_get_tower", Map.of());

    assertThat(limited.isError()).isTrue();
    assertThat(data(limited)).containsEntry("code", "RATE_LIMITED");
    assertThat(((Number) data(limited).get("retry_after_ms")).longValue()).isBetween(1L, 200L);
    // Another grant has its own bucket, and time refills this one.
    assertThat(call(Principal.stdio(), "td_get_tower", Map.of()).isError()).isFalse();
    nanos.addAndGet(1_000_000_000L);
    assertThat(call(dev, "td_get_tower", Map.of()).isError()).isFalse();
  }

  @Test
  void everyCallIsAuditedWithoutArgumentsOrTokens() throws IOException {
    when(observations.tower(any(), any())).thenReturn(Map.of("seq", 12));

    call(dev, "td_get_tower", Map.of());
    call(reader, "td_sandbox_new", Map.of("max_simulated_seconds", 77_777));

    var lines = Files.readAllLines(auditFile);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0))
        .contains(
            "\"grant_id\":\"dev\"", "\"tool\":\"td_get_tower\"", "\"status\":\"ok\"", "\"seq\":12");
    assertThat(lines.get(1)).contains("\"status\":\"rejected\"", "FORBIDDEN");
    assertThat(String.join("", lines)).doesNotContain("77777");
  }

  @Test
  void aSandboxRefusalIsAnErrorResultWithTheRegistrysCode() {
    var sandboxes = mock(SandboxRegistry.class);
    when(sandboxes.open(any(), any(), any(), any()))
        .thenReturn(
            new SandboxRegistry.Opened(
                SandboxRegistry.Status.GRANT_CAP,
                null,
                0,
                "This grant already holds 8 live Sandboxes.",
                null));
    var local =
        new ToolCatalogue(
            observations, actions, sandboxes, new RateLimiter(nanos::get), AuditLog.none());

    var result = local.call(dev, "td_sandbox_new", Map.of()).block();

    assertThat(result.isError()).isTrue();
    assertThat(data(result)).containsEntry("code", "GRANT_CAP");
  }

  // ---------------------------------------------------------------- the action tools

  private static final Map<String, Object> RECRUIT =
      Map.of("request_id", "req-00000001", "unit_type", "WARRIOR", "quantity", 1);

  @Test
  void everyActionToolIsAWriteWithExplicitAnnotationsAndTheRightScope() {
    var expected =
        Map.of(
            "td_recruit", false,
            "td_buy_villagers", false,
            "td_buy_upgrade", false,
            "td_send_reinforcement_wave", false,
            "td_sell", true,
            "td_start_expedition", true,
            "td_extract", true);
    expected.forEach(
        (name, destructive) -> {
          var tool =
              catalogue.tools().stream()
                  .filter(t -> t.name().equals(name))
                  .findFirst()
                  .orElseThrow();
          assertThat(tool.readOnly()).as(name).isFalse();
          assertThat(tool.destructive()).as(name).isEqualTo(destructive);
          assertThat(tool.idempotent()).as(name).isTrue();
          assertThat(tool.write()).as(name).isTrue();
          assertThat(tool.required()).as(name).contains("request_id");
          assertThat(tool.scope()).as(name).isIn(Scope.ACT_VILLAGE, Scope.ACT_EXPEDITION);
        });
    assertThat(expected)
        .hasSameSizeAs(
            catalogue.tools().stream()
                .filter(t -> t.write() && t.scope() != Scope.SANDBOX)
                .toList());
  }

  @Test
  void aGrantWithoutTheActScopeCannotActAndOneWithItCan() {
    when(actions.recruit(any(), any())).thenReturn(Map.of("status", "applied", "code", "OK"));

    var forbidden = call(reader, "td_recruit", RECRUIT);
    var allowed = call(dev, "td_recruit", RECRUIT);

    assertThat(data(forbidden)).containsEntry("code", "FORBIDDEN");
    assertThat(allowed.isError()).isFalse();
    var villageOnly = new Principal("village", EnumSet.of(Scope.ACT_VILLAGE));
    assertThat(data(call(villageOnly, "td_extract", Map.of("request_id", "req-00000002"))))
        .containsEntry("code", "FORBIDDEN");
  }

  @Test
  void anActionWithoutARequestIdOrWithABadOneIsRefusedBeforeItRuns() {
    assertThat(data(call(dev, "td_recruit", Map.of("unit_type", "WARRIOR", "quantity", 1))))
        .containsEntry("code", "INVALID_ARGUMENT");
    when(actions.recruit(any(), any())).thenThrow(Args.invalid("request_id"));

    assertThat(data(call(dev, "td_recruit", RECRUIT))).containsEntry("code", "INVALID_ARGUMENT");
  }

  @Test
  void aRefusedActionIsAnErrorResultAndAnAppliedOrPartialOneIsNot() {
    when(actions.recruit(any(), any()))
        .thenReturn(Map.of("status", "rejected", "code", "INSUFFICIENT_CREDITS", "state_seq", 4))
        .thenReturn(Map.of("status", "partial", "code", "INSUFFICIENT_CREDITS", "state_seq", 5))
        .thenReturn(Map.of("status", "applied", "code", "OK", "state_seq", 6));

    assertThat(call(dev, "td_recruit", RECRUIT).isError()).isTrue();
    assertThat(call(dev, "td_recruit", RECRUIT).isError()).isFalse();
    assertThat(call(dev, "td_recruit", RECRUIT).isError()).isFalse();
  }

  @Test
  void actionsAreWritesAndAreRateLimitedMoreTightlyThanReads() {
    when(actions.recruit(any(), any())).thenReturn(Map.of("status", "applied", "code", "OK"));

    for (var i = 0; i < 6; i++) {
      assertThat(call(dev, "td_recruit", RECRUIT).isError()).as("call " + i).isFalse();
    }
    var limited = call(dev, "td_recruit", RECRUIT);

    assertThat(limited.isError()).isTrue();
    assertThat(data(limited)).containsEntry("code", "RATE_LIMITED");
  }

  @Test
  void theAuditNamesTheRequestIdAndTheStateSeqOfAnAction() throws IOException {
    when(actions.recruit(any(), any()))
        .thenReturn(Map.of("status", "applied", "code", "OK", "state_seq", 41));

    call(dev, "td_recruit", RECRUIT);
    call(
        dev,
        "td_recruit",
        Map.of("request_id", "bad id; drop", "unit_type", "WARRIOR", "quantity", 1));

    var lines = Files.readAllLines(auditFile);
    assertThat(lines.get(0)).contains("\"request_id\":\"req-00000001\"", "\"seq\":41");
    assertThat(lines.get(1)).doesNotContain("drop");
  }

  @Test
  void toolListsAreStableAcrossInstances() {
    var other =
        new ToolCatalogue(
            observations, actions, mock(SandboxRegistry.class), new RateLimiter(), AuditLog.none());

    assertThat(other.manifestJson()).isEqualTo(catalogue.manifestJson());
    assertThat(List.copyOf(other.tools())).hasSameSizeAs(catalogue.tools());
  }
}

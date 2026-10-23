package io.github.diogohmcruz.towerdungeon.agent.tools;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.act.Actions;
import io.github.diogohmcruz.towerdungeon.agent.auth.AuditLog;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.RateLimiter;
import io.github.diogohmcruz.towerdungeon.agent.auth.Scope;
import io.github.diogohmcruz.towerdungeon.agent.auth.TokenHasher;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.observe.RuleViews;
import io.github.diogohmcruz.towerdungeon.agent.observe.WaitConditions;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry;
import io.github.diogohmcruz.towerdungeon.domain.services.SandboxRegistry.Status;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent's tools, defined once as data, and the one place a call to any of them is checked,
 * limited, audited and shielded. The MCP binding asks this class for the list and for every call;
 * nothing in a tool depends on the library that carries it.
 *
 * <p>A call is refused before its handler runs when the grant lacks the scope, an argument is
 * unknown or missing, or the rate limit is spent. A handler that fails never reaches the caller as
 * more than a fixed code and sentence: an unexpected exception becomes {@code INTERNAL_ERROR}.
 */
@AgentEnabled
@Component
@Slf4j
public class ToolCatalogue {
  /** The largest result, in characters (about 6,000 tokens): bigger is a bug in a view. */
  public static final int MAX_RESULT_CHARS = 24_000;

  private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
  private static final Pattern SANDBOX_HANDLE = Pattern.compile("^sandbox-[0-9a-f-]{36}$");
  private static final List<String> VIEWS = List.of("brief", "full");

  private static final JsonMapper COMPACT = JsonMapper.builder().build();
  private static final JsonMapper CANONICAL =
      JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  private final Observations observations;
  private final Actions actions;
  private final SandboxRegistry sandboxes;
  private final RateLimiter limiter;
  private final AuditLog audit;
  private final List<ToolDef> tools;
  private final Map<String, ToolDef> byName = new LinkedHashMap<>();

  public ToolCatalogue(
      Observations observations,
      Actions actions,
      SandboxRegistry sandboxes,
      RateLimiter limiter,
      AuditLog audit) {
    this.observations = observations;
    this.actions = actions;
    this.sandboxes = sandboxes;
    this.limiter = limiter;
    this.audit = audit;
    this.tools = definitions().stream().sorted(Comparator.comparing(ToolDef::name)).toList();
    tools.forEach(tool -> byName.put(tool.name(), tool));
  }

  /** The tools, in a deterministic order (by name). */
  public List<ToolDef> tools() {
    return tools;
  }

  // ---------------------------------------------------------------- the manifest

  /**
   * The canonical manifest: what a {@code tools/list} would carry, in one deterministic form.
   * Reviewed by hand whenever it changes (a golden file pins it).
   */
  public String manifestJson() {
    var list = new java.util.ArrayList<Map<String, Object>>();
    for (var tool : tools) {
      var entry = new LinkedHashMap<String, Object>();
      entry.put("name", tool.name());
      entry.put("description", tool.description());
      entry.put("inputSchema", tool.inputSchema());
      var annotations = new TreeMap<String, Object>();
      annotations.put("readOnlyHint", tool.readOnly());
      annotations.put("destructiveHint", tool.destructive());
      annotations.put("idempotentHint", tool.idempotent());
      annotations.put("openWorldHint", false);
      entry.put("annotations", annotations);
      list.add(entry);
    }
    return CANONICAL.writeValueAsString(Map.of("tools", list)) + "\n";
  }

  /** SHA-256 of {@link #manifestJson()}: it changes when any tool, schema or description does. */
  public String manifestHash() {
    return TokenHasher.sha256Hex(manifestJson());
  }

  // ---------------------------------------------------------------- one call

  /** The outcome of one call, for the binding and the audit. */
  public Mono<McpSchema.CallToolResult> call(
      Principal principal, String name, Map<String, Object> arguments) {
    var started = System.nanoTime();
    var tool = name == null ? null : byName.get(name);
    var toolName = tool == null ? "unknown" : tool.name();
    var scope = tool == null ? "-" : tool.scope().label();
    var grant = principal == null ? "-" : principal.grantId();
    return Mono.defer(() -> run(principal, tool, arguments))
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::ok)
        .onErrorResume(e -> Mono.just(failed(toolName, e)))
        .doOnNext(
            result ->
                audit.record(
                    new AuditLog.Entry(
                        grant,
                        toolName,
                        scope,
                        Boolean.TRUE.equals(result.isError()) ? "rejected" : "ok",
                        codeOf(result),
                        seqOf(result),
                        (System.nanoTime() - started) / 1_000_000,
                        requestIdOf(arguments))));
  }

  private Mono<Map<String, Object>> run(
      Principal principal, ToolDef tool, Map<String, Object> arguments) {
    if (principal == null) {
      return Mono.error(new ToolFailure("UNAUTHENTICATED", "No caller was identified."));
    }
    if (tool == null) {
      return Mono.error(new ToolFailure("UNKNOWN_TOOL", "There is no such tool."));
    }
    if (!principal.has(tool.scope())) {
      return Mono.error(new ToolFailure("FORBIDDEN", "This grant may not use that tool."));
    }
    var given = arguments == null ? Map.<String, Object>of() : arguments;
    var known = Set.copyOf(tool.propertyNames());
    if (!known.containsAll(given.keySet())) {
      return Mono.error(
          new ToolFailure("INVALID_ARGUMENT", "An argument is not known to this tool."));
    }
    for (var required : tool.required()) {
      if (given.get(required) == null) {
        return Mono.error(new ToolFailure("INVALID_ARGUMENT", "A required argument is missing."));
      }
    }
    var verdict =
        limiter.tryAcquire(
            tool.write() ? RateLimiter.Kind.WRITE : RateLimiter.Kind.READ, principal.grantId());
    if (!verdict.allowed()) {
      return Mono.error(
          new ToolFailure(
              "RATE_LIMITED",
              "Too many calls. Wait before the next one.",
              Map.of("retry_after_ms", verdict.retryAfterMs())));
    }
    return tool.handler().handle(new ToolDef.Call(principal, new Args(given)));
  }

  /**
   * A result. An action's answer that was refused ({@code status: rejected}) is flagged as an error
   * so a client sees it as one; one that took effect in part is not, so the part is read.
   */
  private McpSchema.CallToolResult ok(Map<String, Object> data) {
    var json = COMPACT.writeValueAsString(data);
    if (json.length() > MAX_RESULT_CHARS) {
      log.error("A tool result of {} characters was refused", json.length());
      return failed(
          "result", new ToolFailure("RESULT_TOO_LARGE", "The result is too large to return."));
    }
    return McpSchema.CallToolResult.builder()
        .structuredContent(data)
        .addTextContent(json)
        .isError("rejected".equals(data.get("status")))
        .build();
  }

  private McpSchema.CallToolResult failed(String toolName, Throwable error) {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("status", "rejected");
    if (error instanceof ToolFailure failure) {
      envelope.put("code", failure.code());
      envelope.put("message", failure.getMessage());
      envelope.putAll(failure.extra());
    } else {
      log.error("Tool {} failed unexpectedly", toolName, error);
      envelope.put("code", "INTERNAL_ERROR");
      envelope.put("message", "The tool failed. Nothing was changed by this call.");
    }
    return McpSchema.CallToolResult.builder()
        .structuredContent(envelope)
        .addTextContent(COMPACT.writeValueAsString(envelope))
        .isError(true)
        .build();
  }

  @SuppressWarnings("unchecked")
  private static String codeOf(McpSchema.CallToolResult result) {
    return result.structuredContent() instanceof Map<?, ?> map
            && map.get("code") instanceof String c
        ? c
        : "OK";
  }

  private static Long seqOf(McpSchema.CallToolResult result) {
    if (!(result.structuredContent() instanceof Map<?, ?> map)) {
      return null;
    }
    var seq = map.get("seq") instanceof Number n ? n : map.get("state_seq");
    return seq instanceof Number n ? n.longValue() : null;
  }

  /** The request id of an action, when it is well formed: only that shape is ever audited. */
  private static String requestIdOf(Map<String, Object> arguments) {
    return arguments != null
            && arguments.get("request_id") instanceof String id
            && Actions.REQUEST_ID.matcher(id).matches()
        ? id
        : null;
  }

  // ---------------------------------------------------------------- the definitions

  private List<ToolDef> definitions() {
    return List.of(
        read(
            "td_get_state",
            "Read the Campaign's current state: Vault, Pantry, Villagers, Reserves, and the Party,"
                + " Supplies, Loot and Garrison while an Expedition is out. 'brief' is a few"
                + " hundred tokens; 'full' adds the Roster, Upgrades, Milestones, Shortcuts and"
                + " Trades. Units are counted by type, never listed one by one. Pass since_seq to"
                + " skip an unchanged state.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "view", enumOf(VIEWS, "How much detail. Default brief."),
                    "since_seq",
                        Map.of(
                            "type", "integer",
                            "minimum", 0,
                            "description",
                                "Skip the state if it has not moved past this sequence number.")),
                List.of()),
            call ->
                Mono.just(
                    observations.state(
                        call.principal(),
                        handle(call),
                        call.args().oneOf("view", VIEWS, "brief"),
                        call.args().has("since_seq")
                            ? call.args().integer("since_seq", 0, Long.MAX_VALUE, 0)
                            : null))),
        read(
            "td_get_actions",
            "List what the Campaign would accept now: which Unit Types can be recruited and how"
                + " many, Villager and sale options, affordable Upgrades, and whether an Expedition"
                + " can set out, a Reinforcement Wave can follow or the Party can Extract. Each"
                + " refusal carries a reason code. Reads only.",
            schema(Map.of("campaign", campaign()), List.of()),
            call -> Mono.just(observations.actions(call.principal(), handle(call)))),
        read(
            "td_get_tower",
            "Read the Tower as far as the Party has climbed: highest Floor, Guardian and Surge"
                + " Floors met, Guardians slain, Shortcuts, Milestones, and the Garrison of the"
                + " current Floor during an Expedition. Floors not yet reached are not shown.",
            schema(Map.of("campaign", campaign()), List.of()),
            call -> Mono.just(observations.tower(call.principal(), handle(call)))),
        read(
            "td_get_rules",
            "Read the fixed rules in sections: units, upgrades, shortcuts, milestones, tick. Call"
                + " with no section for the list of sections. A Sandbox caller may also ask for"
                + " 'enemies'. These are the values the player sees; nothing in them is Campaign"
                + " state.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "section",
                        enumOf(
                            List.of(
                                "units",
                                "upgrades",
                                "shortcuts",
                                "milestones",
                                "tick",
                                RuleViews.ENEMIES),
                            "The section to read. Omit for the list.")),
                List.of()),
            call -> {
              var section =
                  call.args()
                      .oneOf(
                          "section",
                          List.of(
                              "units",
                              "upgrades",
                              "shortcuts",
                              "milestones",
                              "tick",
                              RuleViews.ENEMIES),
                          null);
              var rules = observations.rules(call.principal(), handle(call), section);
              if (section == null) {
                rules = new LinkedHashMap<>(rules);
                rules.put("manifest_hash", manifestHash());
              }
              return Mono.just(rules);
            }),
        read(
            "td_get_ledger",
            "Read the Village's Ledger: its latest plain lines, and the latest Expedition Summaries"
                + " with the fallen named. Losses are written as plainly as gains.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "lines", bounded("How many Ledger lines, newest last. Default 20.", 1, 50),
                    "expeditions", bounded("How many Expedition Summaries. Default 5.", 1, 20)),
                List.of()),
            call ->
                Mono.just(
                    observations.ledger(
                        call.principal(),
                        handle(call),
                        (int) call.args().integer("lines", 1, 50, 20),
                        (int) call.args().integer("expeditions", 1, 20, 5)))),
        read(
            "td_get_events",
            "Read the event feed of the Campaign after an event id: recruits, sales, Floors"
                + " reached, Enemies slain, Expeditions ended. Oldest first. Use last_id as the"
                + " next 'since'.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "since", bounded("Only events after this id. Default 0.", 0, Long.MAX_VALUE),
                    "limit", bounded("The most events in one answer. Default 100.", 1, 200)),
                List.of()),
            call ->
                Mono.just(
                    observations.events(
                        call.principal(),
                        handle(call),
                        call.args().integer("since", 0, Long.MAX_VALUE, 0),
                        (int) call.args().integer("limit", 1, 200, 100)))),
        new ToolDef(
            "td_wait",
            "Wait for something to change. In a Sandbox this advances time, one simulated second"
                + " per tick, until a condition holds or max_ticks pass. On a live Campaign it only"
                + " watches, up to timeout_ms. Conditions: floor_changed, unit_lost,"
                + " expedition_ended, supplies_below, party_health_fraction_below, ticks. One wait"
                + " at a time.",
            schema(
                Map.of(
                    "campaign", campaign(),
                    "conditions",
                        Map.of(
                            "type",
                            "array",
                            "minItems",
                            1,
                            "maxItems",
                            6,
                            "items",
                            Map.of(
                                "type",
                                "object",
                                "additionalProperties",
                                false,
                                "required",
                                List.of("type"),
                                "properties",
                                Map.of(
                                    "type", Map.of("enum", WaitConditions.TYPES),
                                    "value", Map.of("type", "number")))),
                    "timeout_ms", bounded("Live Campaign only. Default 15000.", 100, 20_000),
                    "max_ticks", bounded("Sandbox only. Default 60.", 1, 3600)),
                List.of("conditions")),
            false,
            false,
            false,
            Scope.OBSERVE,
            false,
            call -> observations.await(call.principal(), call.args())),
        new ToolDef(
            "td_sandbox_new",
            "Open a Sandbox Campaign: a headless Campaign on a manual clock that advances only"
                + " through td_wait, with its own copy of the rules. Pass a seed to repeat an"
                + " earlier one; the seed in use is returned. The number open per grant and in all"
                + " is capped, and so is its simulated time. Returns its handle for the 'campaign'"
                + " argument.",
            schema(
                Map.of(
                    "seed",
                    Map.of(
                        "type", "integer",
                        "description", "The Sandbox's seed. Omit for a fresh one."),
                    "max_simulated_seconds",
                    bounded(
                        "A lower ceiling on its simulated time. Default the server's.", 1, 21_600)),
                List.of()),
            false,
            false,
            false,
            Scope.SANDBOX,
            true,
            call -> Mono.just(openSandbox(call))),
        new ToolDef(
            "td_sandbox_close",
            "Close a Sandbox Campaign you opened and discard it. This cannot be undone.",
            schema(
                Map.of(
                    "campaign",
                    Map.of(
                        "type", "string",
                        "pattern", SANDBOX_HANDLE.pattern(),
                        "description", "The Sandbox handle returned by td_sandbox_new.")),
                List.of("campaign")),
            false,
            true,
            true,
            Scope.SANDBOX,
            true,
            call -> Mono.just(closeSandbox(call))),
        act(
            "td_recruit",
            "Recruit Units into the Reserves, paid from the Vault and from Loot the Party carries"
                + " (carried Credits go first). The Credits must exceed the cost of each one, so"
                + " fewer may be recruited than asked; that is reported as partial. The answer"
                + " names each new Unit with its rolled health and damage factors and its knack.",
            Scope.ACT_VILLAGE,
            false,
            schema(
                withCommon(
                    true,
                    Map.of(
                        "unit_type",
                        Map.of(
                            "type", "string",
                            "enum", unitNames(),
                            "description", "The Unit Type to recruit; it must be unlocked."),
                        "quantity",
                        bounded("How many to recruit.", 1, 100))),
                List.of("request_id", "unit_type", "quantity")),
            call -> Mono.just(actions.recruit(call.principal(), call.args()))),
        act(
            "td_buy_villagers",
            "Pay for Villagers from the Vault's Credits, at a price that rises with the number the"
                + " Village already has. Takes in up to count, and fewer when the Vault runs out;"
                + " that is reported as partial.",
            Scope.ACT_VILLAGE,
            false,
            schema(
                withCommon(
                    true, Map.of("count", bounded("How many to pay for. Default 1.", 1, 1000))),
                List.of("request_id")),
            call -> Mono.just(actions.buyVillagers(call.principal(), call.args()))),
        act(
            "td_sell",
            "Sell Food, Materials or Relics from the Village for Credits. Food is sold in whole"
                + " units above the keep-back and is refused with WOULD_STARVE when the Pantry"
                + " would no longer feed the Reserves, unless force is true. Materials and Relics"
                + " take a number or 'all'. Selling cannot be undone.",
            Scope.ACT_VILLAGE,
            true,
            schema(
                withCommon(
                    true,
                    Map.of(
                        "good", enumOf(List.of("FOOD", "MATERIALS", "RELICS"), "What to sell."),
                        "amount",
                            Map.of(
                                "description",
                                "How much to sell: a positive number, or 'all' for Materials and"
                                    + " Relics only. Food takes a whole number.",
                                "oneOf",
                                List.of(
                                    Map.of("type", "number", "exclusiveMinimum", 0),
                                    Map.of("const", "all"))),
                        "force",
                            Map.of(
                                "type", "boolean",
                                "description",
                                    "Food only: sell even if the Pantry would fall short."))),
                List.of("request_id", "good", "amount")),
            call -> Mono.just(actions.sell(call.principal(), call.args()))),
        act(
            "td_buy_upgrade",
            "Pay for the next step of an Upgrade with the Materials and Relics it costs. Ask"
                + " td_get_actions for the Upgrades on offer. Refused with UPGRADE_MAXED when"
                + " there is no step left and UPGRADE_UNAFFORDABLE when the Vault cannot pay.",
            Scope.ACT_VILLAGE,
            false,
            schema(
                withCommon(
                    true,
                    Map.of(
                        "upgrade_id",
                        Map.of(
                            "type", "string",
                            "pattern", "^[A-Z0-9_]{1,40}$",
                            "description", "The id of an Upgrade, as td_get_actions lists it."))),
                List.of("request_id", "upgrade_id")),
            call -> Mono.just(actions.buyUpgrade(call.principal(), call.args()))),
        act(
            "td_start_expedition",
            "Send a Party up the Tower from the Reserves. Name who goes: a count for each Unit"
                + " Type, or 'all_reserves'. A start_floor above 0 needs an open Shortcut. The"
                + " Party draws Supplies from the Pantry. Refused when an Expedition is out or the"
                + " Campaign has ended.",
            Scope.ACT_EXPEDITION,
            true,
            schema(
                withCommon(
                    true,
                    Map.of(
                        "party", partySchema("Who goes."),
                        "start_floor",
                            bounded(
                                "0 starts at the foot of the Tower. Otherwise the Floor of an open"
                                    + " Shortcut.",
                                0,
                                100))),
                List.of("request_id", "party")),
            call -> Mono.just(actions.startExpedition(call.principal(), call.args()))),
        act(
            "td_send_reinforcement_wave",
            "Send a Reinforcement Wave from the Reserves after the Party on the Tower. Name who"
                + " goes: a count for each Unit Type, or 'all_reserves'. When fewer wait in the"
                + " Reserves than asked, the Reinforcement Wave goes with those and the answer is"
                + " partial. It climbs Floor by Floor and eats on the way.",
            Scope.ACT_EXPEDITION,
            false,
            schema(
                withCommon(
                    false, Map.of("party", partySchema("Who goes in the Reinforcement Wave."))),
                List.of("request_id", "party")),
            call -> Mono.just(actions.sendReinforcementWave(call.principal(), call.args()))),
        act(
            "td_extract",
            "End the Expedition now: the Party comes back to the Village with what it carries. Loot"
                + " is banked, Supplies go back to the Pantry and the answer holds the Expedition"
                + " Summary. It is never confirmed: it is the way out.",
            Scope.ACT_EXPEDITION,
            true,
            schema(withCommon(false, Map.of()), List.of("request_id")),
            call -> Mono.just(actions.extract(call.principal(), call.args()))));
  }

  private Map<String, Object> openSandbox(ToolDef.Call call) {
    var seconds =
        call.args().has("max_simulated_seconds")
            ? call.args().integer("max_simulated_seconds", 1, 21_600, 21_600)
            : null;
    var seed =
        call.args().has("seed")
            ? call.args().integer("seed", Long.MIN_VALUE, Long.MAX_VALUE, 0)
            : null;
    var opened = sandboxes.open(call.principal().grantId(), null, seconds, seed);
    if (opened.status() != Status.OPENED) {
      throw new ToolFailure(opened.status().name(), opened.note());
    }
    var out = new LinkedHashMap<String, Object>();
    out.put("campaign", opened.sandboxId());
    out.put("clock", "manual");
    // A Sandbox's seed is public: it is how a run is repeated. A human Campaign's never is.
    out.put("seed", opened.seed());
    out.put("max_simulated_seconds", opened.maxSimulatedSeconds());
    out.put("note", opened.note());
    return out;
  }

  private Map<String, Object> closeSandbox(ToolDef.Call call) {
    var id = call.args().string("campaign", SANDBOX_HANDLE, null);
    if (!sandboxes.close(call.principal().grantId(), id)) {
      throw new ToolFailure("NO_CAMPAIGN", "There is no such Sandbox for this grant.");
    }
    return Map.of("closed", true);
  }

  // ---------------------------------------------------------------- small builders

  /** An action tool: it writes, so it counts against the write limit and is idempotent by id. */
  private static ToolDef act(
      String name,
      String description,
      Scope scope,
      boolean destructive,
      Map<String, Object> schema,
      ToolDef.Handler handler) {
    return new ToolDef(name, description, schema, false, destructive, true, scope, true, handler);
  }

  private static List<String> unitNames() {
    return java.util.Arrays.stream(UnitStats.values()).map(Enum::name).toList();
  }

  /** The arguments every action takes, and the guard that village tools and setting out add. */
  private static Map<String, Object> withCommon(boolean guarded, Map<String, Object> own) {
    var all = new LinkedHashMap<String, Object>(own);
    all.put(
        "request_id",
        Map.of(
            "type", "string",
            "pattern", Actions.REQUEST_ID.pattern(),
            "description",
                "Your own id for this call. Repeating it returns the first answer and acts once."));
    all.put("campaign", campaign());
    if (guarded) {
      all.put(
          "expected_seq",
          Map.of(
              "type", "integer",
              "minimum", 0,
              "description", "Refuse with STALE_SEQ if the Campaign's state_seq is not this."));
    }
    return all;
  }

  private static Map<String, Object> partySchema(String description) {
    return Map.of(
        "description",
        description + " A count for each Unit Type, or 'all_reserves'.",
        "oneOf",
        List.of(
            Map.of("const", "all_reserves"),
            Map.of(
                "type",
                "object",
                "minProperties",
                1,
                "propertyNames",
                Map.of("enum", unitNames()),
                "additionalProperties",
                Map.of("type", "integer", "minimum", 1, "maximum", 500))));
  }

  private static ToolDef read(
      String name, String description, Map<String, Object> schema, ToolDef.Handler handler) {
    return new ToolDef(name, description, schema, true, false, true, Scope.OBSERVE, false, handler);
  }

  private static String handle(ToolDef.Call call) {
    return call.args().has("campaign") ? call.args().string("campaign", HANDLE, null) : null;
  }

  private static Map<String, Object> campaign() {
    return Map.of(
        "type", "string",
        "pattern", HANDLE.pattern(),
        "description", "A Sandbox handle. Omit to use your only open Sandbox.");
  }

  private static Map<String, Object> enumOf(List<String> values, String description) {
    return Map.of("type", "string", "enum", values, "description", description);
  }

  private static Map<String, Object> bounded(String description, long min, long max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max, "description", description);
  }

  private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("properties", properties);
    if (!required.isEmpty()) {
      schema.put("required", required);
    }
    return schema;
  }
}

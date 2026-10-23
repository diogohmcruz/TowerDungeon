package io.github.diogohmcruz.towerdungeon.agent.act;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import io.github.diogohmcruz.towerdungeon.agent.AgentEnabled;
import io.github.diogohmcruz.towerdungeon.agent.auth.Principal;
import io.github.diogohmcruz.towerdungeon.agent.auth.TokenHasher;
import io.github.diogohmcruz.towerdungeon.agent.observe.Observations;
import io.github.diogohmcruz.towerdungeon.agent.tools.Args;
import io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure;
import io.github.diogohmcruz.towerdungeon.domain.commands.Command;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Outcome;
import io.github.diogohmcruz.towerdungeon.domain.commands.CommandResult.Reason;
import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The seven action tools: what a call means, how it reaches the game, and what it answers. Every
 * call is turned into one domain {@link Command}, run through {@link GameService#execute}, and
 * answered with the same envelope: a status, a stable code, one plain sentence, what changed and
 * what the Village holds after (design 4.2). A rule failure is an answer, never an exception.
 *
 * <p>Every call carries a {@code request_id}. A repeat of a finished call returns the first answer
 * with {@code replayed: true} and does nothing; a repeat of one still running is refused. Calls act
 * on Sandbox Campaigns only until grants on a human's Campaign exist.
 */
@AgentEnabled
@Component
public class Actions {
  /** The shape of a {@code request_id}: it is echoed in results and the audit, so it is closed. */
  public static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

  private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
  private static final Pattern UPGRADE_ID = Pattern.compile("^[A-Z0-9_]{1,40}$");
  private static final List<String> GOODS = List.of("FOOD", "MATERIALS", "RELICS");
  private static final int MAX_QUANTITY = 100;
  private static final int MAX_PARTY_COUNT = 500;
  private static final int MAX_FLOOR = 100;

  private static final JsonMapper CANONICAL =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  private static final Map<String, String> HINTS =
      Map.ofEntries(
          Map.entry("INSUFFICIENT_CREDITS", "Read td_get_actions for what the Vault can pay."),
          Map.entry("NOT_UNLOCKED", "Read td_get_actions: it says what unlocks the type."),
          Map.entry("EXPEDITION_ACTIVE", "Extract first, or wait for the Expedition to end."),
          Map.entry("NO_EXPEDITION", "Set out first with td_start_expedition."),
          Map.entry("START_FLOOR_LOCKED", "td_get_actions lists the Floors with an open Shortcut."),
          Map.entry("NO_UNITS", "Nobody waits in the Reserves."),
          Map.entry("INSUFFICIENT_UNITS", "Read td_get_state for how many wait in the Reserves."),
          Map.entry("WOULD_STARVE", "Sell less, or pass force: true to sell anyway."),
          Map.entry("STALE_SEQ", "The Campaign moved. Read td_get_state and decide again."),
          Map.entry("PARTY_HELD", "The Party is held. It must be resumed first."),
          Map.entry("PHASE_OFFER", "The Campaign has ended and waits for what comes next."),
          Map.entry("DUPLICATE_IN_FLIGHT", "The first call is still running. Wait, then retry."));

  /** What a tool wants done, and what it adds to the answer when it is done. */
  private record Plan(Command command, boolean seqGuarded, Details details) {}

  @FunctionalInterface
  private interface Details {
    Map<String, Object> of(GameState state, Snap before, CommandResult result);
  }

  /** The numbers an answer is made of, read under the Campaign's lock. */
  private record Snap(
      long seq,
      double credits,
      double carriedCredits,
      double food,
      double materials,
      double relics,
      int villagers,
      Map<String, Integer> reserves,
      int reservesTotal,
      int party,
      int waves,
      boolean expedition,
      CampaignPhase phase) {
    static Snap of(GameState state, long seq) {
      var reserves = new TreeMap<String, Integer>();
      state
          .getUnits()
          .forEach(
              (type, list) -> {
                if (!list.isEmpty()) {
                  reserves.put(type.name(), list.size());
                }
              });
      return new Snap(
          seq,
          state.getCredit(),
          state.getCarriedCredits(),
          state.getVillage().getFood(),
          state.getResources().getOrDefault(ResourceType.MATERIALS, 0d),
          state.getResources().getOrDefault(ResourceType.RELICS, 0d),
          state.getVillage().getVillagersCount(),
          reserves,
          reserves.values().stream().mapToInt(Integer::intValue).sum(),
          state.getTowerPartySize(),
          state.getReinforcements().size(),
          state.isExpeditionActive(),
          state.getPhase());
    }
  }

  private final GameService service;
  private final Observations observations;
  private final IdempotencyStore store;

  public Actions(GameService service, Observations observations, IdempotencyStore store) {
    this.service = service;
    this.observations = observations;
    this.store = store;
  }

  // ---------------------------------------------------------------- the seven tools

  public Map<String, Object> recruit(Principal who, Args args) {
    var type = unitType(args.oneOf("unit_type", unitNames(), null));
    var quantity = (int) args.integer("quantity", 1, MAX_QUANTITY, 0);
    if (quantity == 0) {
      throw Args.invalid("quantity");
    }
    return run(
        who,
        "td_recruit",
        args,
        new Plan(
            new Command.Recruit(type, quantity),
            true,
            (state, before, result) -> newRecruits(state, before, result, type)));
  }

  public Map<String, Object> buyVillagers(Principal who, Args args) {
    var count = (int) args.integer("count", 1, 1000, 1);
    return run(
        who,
        "td_buy_villagers",
        args,
        new Plan(new Command.BuyVillagers(count), true, (state, before, result) -> Map.of()));
  }

  public Map<String, Object> sell(Principal who, Args args) {
    var good = args.oneOf("good", GOODS, null);
    if (good == null) {
      throw Args.invalid("good");
    }
    var amount = args.raw("amount");
    var force = args.bool("force", false);
    if (force && !"FOOD".equals(good)) {
      throw Args.invalid("force");
    }
    Command command;
    switch (good) {
      case "FOOD" -> {
        var whole = args.integer("amount", 1, 1_000_000, 0);
        if (whole == 0) {
          throw Args.invalid("amount");
        }
        command = new Command.SellFood((int) whole, force);
      }
      case "MATERIALS" -> command = new Command.SellMaterials(saleAmount(args, amount));
      default -> command = new Command.SellRelics(saleAmount(args, amount));
    }
    return run(who, "td_sell", args, new Plan(command, true, (state, before, result) -> Map.of()));
  }

  public Map<String, Object> buyUpgrade(Principal who, Args args) {
    var id = args.string("upgrade_id", UPGRADE_ID, null);
    return run(
        who,
        "td_buy_upgrade",
        args,
        new Plan(new Command.BuyUpgrade(id), true, (state, before, result) -> Map.of()));
  }

  public Map<String, Object> startExpedition(Principal who, Args args) {
    var party = party(args.raw("party"));
    var floor = (int) args.integer("start_floor", 0, MAX_FLOOR, 0);
    return run(
        who,
        "td_start_expedition",
        args,
        new Plan(
            new Command.StartExpedition(party, floor),
            true,
            (state, before, result) -> {
              var out = new LinkedHashMap<String, Object>();
              if (result.changedState()) {
                out.put("party_size", state.getTowerPartySize());
                out.put("start_floor", floor);
              }
              return out;
            }));
  }

  public Map<String, Object> sendReinforcementWave(Principal who, Args args) {
    var party = party(args.raw("party"));
    return run(
        who,
        "td_send_reinforcement_wave",
        args,
        new Plan(
            new Command.SendReinforcements(party),
            false,
            (state, before, result) -> {
              var out = new LinkedHashMap<String, Object>();
              if (result.changedState()) {
                out.put("waves_on_the_stair", state.getReinforcements().size());
              }
              return out;
            }));
  }

  public Map<String, Object> extract(Principal who, Args args) {
    return run(
        who,
        "td_extract",
        args,
        new Plan(
            new Command.Extract(),
            false,
            (state, before, result) -> {
              var out = new LinkedHashMap<String, Object>();
              if (result.changedState() && state.getLastRunSummary() != null) {
                out.put("summary", Observations.summary(state.getLastRunSummary()));
              }
              return out;
            }));
  }

  // ---------------------------------------------------------------- the one path

  private Map<String, Object> run(Principal who, String tool, Args args, Plan plan) {
    var requestId = args.string("request_id", REQUEST_ID, null);
    var handle = args.has("campaign") ? args.string("campaign", HANDLE, null) : null;
    var expectedSeq =
        plan.seqGuarded() && args.has("expected_seq")
            ? args.integer("expected_seq", 0, Long.MAX_VALUE, 0)
            : null;
    var target = observations.resolve(who, handle);
    if (!target.sandbox()) {
      throw new ToolFailure(
          "FORBIDDEN", "A human's Campaign can be acted on only through a grant on it.");
    }
    var digest = digestOf(tool, args);
    switch (store.begin(who.grantId(), requestId, digest)) {
      case IdempotencyStore.Begin.Replay replay -> {
        var again = new LinkedHashMap<String, Object>(replay.result());
        again.put("replayed", true);
        return again;
      }
      case IdempotencyStore.Begin.InFlight _ -> {
        return refusal("DUPLICATE_IN_FLIGHT", "The first call with this request_id is running.");
      }
      case IdempotencyStore.Begin.Mismatch _ -> {
        throw new ToolFailure("INVALID_ARGUMENT", "That request_id was used for a different call.");
      }
      case IdempotencyStore.Begin.Fresh _ -> {}
    }
    try {
      var answer = act(target.id(), plan, expectedSeq);
      store.complete(who.grantId(), requestId, digest, answer);
      return answer;
    } catch (RuntimeException e) {
      store.abandon(who.grantId(), requestId);
      throw e;
    }
  }

  private Map<String, Object> act(String campaign, Plan plan, Long expectedSeq) {
    var before =
        service
            .view(campaign, (state, meta) -> Snap.of(state, meta.seq()))
            .orElseThrow(() -> new ToolFailure("NO_CAMPAIGN", "The Campaign is no longer live."));
    if (expectedSeq != null && expectedSeq != before.seq()) {
      var stale = refusal("STALE_SEQ", "The Campaign has moved since that sequence number.");
      stale.put("state_seq", before.seq());
      return stale;
    }
    var result = service.execute(campaign, plan.command());
    var seen = new Object[2];
    service.view(
        campaign,
        (state, meta) -> {
          seen[0] = Snap.of(state, meta.seq());
          seen[1] = plan.details().of(state, before, result);
          return Boolean.TRUE;
        });
    if (seen[0] == null) {
      return envelope(result, before, before, Map.of());
    }
    @SuppressWarnings("unchecked")
    var details = (Map<String, Object>) seen[1];
    return envelope(result, before, (Snap) seen[0], details);
  }

  // ---------------------------------------------------------------- the answer

  private static Map<String, Object> envelope(
      CommandResult result, Snap before, Snap after, Map<String, Object> details) {
    var out = new LinkedHashMap<String, Object>();
    out.put("status", status(result.outcome()));
    var code = codeOf(result, after);
    out.put("code", code);
    out.put("message", result.message());
    out.put("replayed", false);
    out.put("state_seq", Math.max(result.stateSeq(), after.seq()));
    var delta = delta(before, after);
    if (!delta.isEmpty()) {
      out.put("delta", delta);
    }
    out.put("after", after(after));
    out.putAll(details);
    var hint = HINTS.get(code);
    if (hint != null && result.outcome() != Outcome.APPLIED) {
      out.put("hint", hint);
    }
    return out;
  }

  private static LinkedHashMap<String, Object> refusal(String code, String message) {
    var out = new LinkedHashMap<String, Object>();
    out.put("status", "rejected");
    out.put("code", code);
    out.put("message", message);
    out.put("replayed", false);
    var hint = HINTS.get(code);
    if (hint != null) {
      out.put("hint", hint);
    }
    return out;
  }

  private static String status(Outcome outcome) {
    return outcome.name().toLowerCase(java.util.Locale.ROOT);
  }

  /** The stable code of a result. An ended Campaign in its offer phase says so. */
  static String codeOf(CommandResult result, Snap after) {
    if (result.reason() == Reason.CAMPAIGN_ENDED && after.phase() == CampaignPhase.OFFER) {
      return "PHASE_OFFER";
    }
    return result.reason().name();
  }

  private static Map<String, Object> delta(Snap before, Snap after) {
    var out = new LinkedHashMap<String, Object>();
    put(out, "credits", after.credits() - before.credits());
    put(out, "carried_credits", after.carriedCredits() - before.carriedCredits());
    put(out, "food", after.food() - before.food());
    put(out, "materials", after.materials() - before.materials());
    put(out, "relics", after.relics() - before.relics());
    put(out, "villagers", after.villagers() - before.villagers());
    var reserves = new TreeMap<String, Integer>();
    var types = new java.util.TreeSet<>(before.reserves().keySet());
    types.addAll(after.reserves().keySet());
    for (var type : types) {
      var change = after.reserves().getOrDefault(type, 0) - before.reserves().getOrDefault(type, 0);
      if (change != 0) {
        reserves.put(type, change);
      }
    }
    if (!reserves.isEmpty()) {
      out.put("reserves", reserves);
    }
    put(out, "party", after.party() - before.party());
    put(out, "waves", after.waves() - before.waves());
    return out;
  }

  private static void put(Map<String, Object> out, String key, double change) {
    var rounded = Math.round(change * 100d) / 100d;
    if (rounded != 0d) {
      out.put(key, rounded == Math.rint(rounded) ? (Object) (long) rounded : (Object) rounded);
    }
  }

  private static Map<String, Object> after(Snap after) {
    var out = new LinkedHashMap<String, Object>();
    out.put("credits", Math.round(after.credits() * 100d) / 100d);
    if (after.expedition()) {
      out.put("carried_credits", Math.round(after.carriedCredits() * 100d) / 100d);
    }
    out.put("food", Math.round(after.food() * 100d) / 100d);
    out.put("villagers", after.villagers());
    out.put("reserves", after.reserves());
    out.put("reserves_total", after.reservesTotal());
    out.put("expedition", after.expedition());
    return out;
  }

  /** The recruits just made: their rolled values are unknown until now. */
  private static Map<String, Object> newRecruits(
      GameState state, Snap before, CommandResult result, UnitStats type) {
    var out = new LinkedHashMap<String, Object>();
    if (!result.changedState()) {
      return out;
    }
    var list = state.getUnits().getOrDefault(type, List.of());
    var made = list.size() - before.reserves().getOrDefault(type.name(), 0);
    var recruits = new java.util.ArrayList<Map<String, Object>>();
    for (var unit : list.subList(Math.max(0, list.size() - made), list.size())) {
      var rolled = new LinkedHashMap<String, Object>();
      rolled.put("name", unit.getName());
      rolled.put("type", type.name());
      rolled.put("health_factor", round3(unit.getHealthFactor()));
      rolled.put("damage_factor", round3(unit.getDamageFactor()));
      rolled.put("knack", unit.getAffinity() == null ? "none" : unit.getAffinity().name());
      recruits.add(rolled);
    }
    out.put("recruited", made);
    out.put("units", recruits);
    return out;
  }

  private static double round3(double value) {
    return Math.round(value * 1000d) / 1000d;
  }

  // ---------------------------------------------------------------- reading arguments

  private static List<String> unitNames() {
    return java.util.Arrays.stream(UnitStats.values()).map(Enum::name).toList();
  }

  private static UnitStats unitType(String name) {
    if (name == null) {
      throw Args.invalid("unit_type");
    }
    return UnitStats.valueOf(name);
  }

  /** A sale amount: a positive number, or "all", which only Materials and Relics take. */
  private static Double saleAmount(Args args, Object raw) {
    if ("all".equals(raw)) {
      return null;
    }
    return args.number("amount", Double.MIN_NORMAL, 1_000_000_000d);
  }

  /**
   * Who goes: a count for each type, or the word {@code all_reserves}. Nothing else: a missing,
   * empty or malformed selection is refused, so no call sends more than it said.
   */
  private static Map<UnitStats, Integer> party(Object raw) {
    if ("all_reserves".equals(raw)) {
      return new EnumMap<>(UnitStats.class);
    }
    if (!(raw instanceof Map<?, ?> given) || given.isEmpty() || given.size() > 20) {
      throw Args.invalid("party");
    }
    var out = new EnumMap<UnitStats, Integer>(UnitStats.class);
    for (var entry : given.entrySet()) {
      if (!(entry.getKey() instanceof String key)
          || !unitNames().contains(key)
          || !(entry.getValue() instanceof Number count)
          || count.doubleValue() != Math.rint(count.doubleValue())
          || count.longValue() < 1
          || count.longValue() > MAX_PARTY_COUNT) {
        throw Args.invalid("party");
      }
      out.put(UnitStats.valueOf(key), count.intValue());
    }
    return out;
  }

  /** What makes two calls the same call: the tool and every argument but the id itself. */
  private static String digestOf(String tool, Args args) {
    var all = new TreeMap<String, Object>();
    for (var name : args.names()) {
      if (!"request_id".equals(name)) {
        all.put(name, args.raw(name));
      }
    }
    return TokenHasher.sha256Hex(tool + CANONICAL.writeValueAsString(all));
  }
}

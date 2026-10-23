package io.github.diogohmcruz.towerdungeon.agent.act;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;
import io.github.diogohmcruz.towerdungeon.domain.services.HeldPartyAccess;

/**
 * The seven action tools through the facade, with a real game behind it and no HTTP: every reason
 * code of design 4.3 that the shipped features can produce is produced here by a named input, and
 * partial fills, replay and the answer's contents are pinned.
 */
class ActionsTest {
  private ActionsFixture fx;
  private String sandbox;

  @BeforeEach
  void setUp() {
    fx = new ActionsFixture(7);
    sandbox = fx.open(7);
  }

  private Map<String, Object> call(String tool, Object... pairs) {
    return fx.call(tool, fx.args(sandbox, pairs));
  }

  /** One row of the reason-code table: what to arrange, then what to call. */
  private record Row(
      String code,
      String status,
      BiFunction<ActionsFixture, String, Void> arrange,
      String tool,
      Object[] pairs) {
    @Override
    public String toString() {
      return code + " via " + tool;
    }
  }

  private static Arguments row(
      String code,
      String status,
      BiFunction<ActionsFixture, String, Void> arrange,
      String tool,
      Object... pairs) {
    return Arguments.of(new Row(code, status, arrange, tool, pairs));
  }

  private static BiFunction<ActionsFixture, String, Void> nothing() {
    return (fx, id) -> null;
  }

  private static Map<String, Integer> party(String type, int count) {
    return Map.of(type, count);
  }

  /** Enough Reserves that their Upkeep outgrows the Fields, and a Pantry that cannot carry them. */
  private static Void starving(ActionsFixture fx, String id) {
    var state = fx.state(id);
    state.setCredit(1_000_000d);
    fx.call(
        "td_recruit",
        fx.args(id, "unit_type", "WARRIOR", "quantity", 100, "request_id", "arrange-0001"));
    var village = state.getVillage();
    village.takeFood(1_000_000d);
    village.addFood(30d);
    return null;
  }

  static Stream<Arguments> reasonCodes() {
    return Stream.of(
        row("OK", "applied", nothing(), "td_recruit", "unit_type", "WARRIOR", "quantity", 1),
        row(
            "NOT_UNLOCKED",
            "rejected",
            nothing(),
            "td_recruit",
            "unit_type",
            "MAGE",
            "quantity",
            1),
        row(
            "INSUFFICIENT_CREDITS",
            "rejected",
            (fx, id) -> {
              fx.state(id).setCredit(0d);
              return null;
            },
            "td_recruit",
            "unit_type",
            "WARRIOR",
            "quantity",
            1),
        row(
            "EXPEDITION_ACTIVE",
            "rejected",
            (fx, id) -> {
              fx.call(
                  "td_start_expedition",
                  fx.args(id, "party", "all_reserves", "request_id", "arrange-0001"));
              return null;
            },
            "td_start_expedition",
            "party",
            "all_reserves"),
        row("NO_EXPEDITION", "rejected", nothing(), "td_extract"),
        row(
            "NO_EXPEDITION",
            "rejected",
            nothing(),
            "td_send_reinforcement_wave",
            "party",
            "all_reserves"),
        row(
            "CAMPAIGN_ENDED",
            "rejected",
            (fx, id) -> {
              fx.state(id).setGameOutcome(GameOutcome.DEFEAT);
              return null;
            },
            "td_start_expedition",
            "party",
            "all_reserves"),
        row(
            "START_FLOOR_LOCKED",
            "rejected",
            nothing(),
            "td_start_expedition",
            "party",
            "all_reserves",
            "start_floor",
            5),
        row(
            "NO_UNITS",
            "rejected",
            (fx, id) -> {
              fx.state(id).getUnits().clear();
              return null;
            },
            "td_start_expedition",
            "party",
            "all_reserves"),
        row(
            "INSUFFICIENT_UNITS",
            "rejected",
            nothing(),
            "td_start_expedition",
            "party",
            party("WARRIOR", 50)),
        row("UNKNOWN_ID", "rejected", nothing(), "td_buy_upgrade", "upgrade_id", "NO_SUCH_UPGRADE"),
        row(
            "UPGRADE_MAXED",
            "rejected",
            (fx, id) -> {
              fx.state(id).getPurchasedUpgrades().put(UpgradeType.UNLOCK_HEALER, 1);
              return null;
            },
            "td_buy_upgrade",
            "upgrade_id",
            "UNLOCK_HEALER"),
        row(
            "UPGRADE_UNAFFORDABLE",
            "rejected",
            nothing(),
            "td_buy_upgrade",
            "upgrade_id",
            "UNLOCK_HEALER"),
        row(
            "NOTHING_TO_SELL",
            "rejected",
            nothing(),
            "td_sell",
            "good",
            "MATERIALS",
            "amount",
            "all"),
        row(
            "WOULD_STARVE",
            "rejected",
            ActionsTest::starving,
            "td_sell",
            "good",
            "FOOD",
            "amount",
            5),
        row(
            "STALE_SEQ",
            "rejected",
            nothing(),
            "td_recruit",
            "unit_type",
            "WARRIOR",
            "quantity",
            1,
            "expected_seq",
            9999),
        row(
            "PARTY_HELD",
            "rejected",
            (fx, id) -> {
              fx.call(
                  "td_start_expedition",
                  fx.args(id, "party", "all_reserves", "request_id", "arrange-0001"));
              HeldPartyAccess.hold(fx.service, id);
              return null;
            },
            "td_send_reinforcement_wave",
            "party",
            "all_reserves"),
        row(
            "PHASE_OFFER",
            "rejected",
            (fx, id) -> {
              fx.state(id).setPhase(CampaignPhase.OFFER);
              return null;
            },
            "td_recruit",
            "unit_type",
            "WARRIOR",
            "quantity",
            1),
        row(
            "DUPLICATE_IN_FLIGHT",
            "rejected",
            (fx, id) -> {
              fx.store.begin("dev", "in-flight-1", "whatever");
              return null;
            },
            "td_recruit",
            "request_id",
            "in-flight-1",
            "unit_type",
            "WARRIOR",
            "quantity",
            1),
        row("INVALID_ARGUMENT", "rejected", nothing(), "td_start_expedition", "party", Map.of()),
        row(
            "INVALID_ARGUMENT",
            "rejected",
            nothing(),
            "td_recruit",
            "unit_type",
            "WARRIOR",
            "quantity",
            0),
        row(
            "INVALID_ARGUMENT",
            "rejected",
            nothing(),
            "td_recruit",
            "request_id",
            "short",
            "unit_type",
            "WARRIOR",
            "quantity",
            1));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("reasonCodes")
  void everyReasonCodeIsProducedByANamedInput(Row row) {
    row.arrange().apply(fx, sandbox);

    var answer = call(row.tool(), row.pairs());

    assertThat(answer.get("status")).as("status").isEqualTo(row.status());
    assertThat(answer.get("code")).as("code").isEqualTo(row.code());
  }

  // ---------------------------------------------------------------- the answer

  @Test
  void aRecruitNamesTheNewUnitsAndTheirRolledValuesAfterwards() {
    var answer = call("td_recruit", "unit_type", "WARRIOR", "quantity", 2);

    assertThat(answer).containsEntry("status", "applied").containsEntry("code", "OK");
    assertThat(answer).containsEntry("recruited", 2).containsEntry("replayed", false);
    @SuppressWarnings("unchecked")
    var units = (List<Map<String, Object>>) answer.get("units");
    assertThat(units).hasSize(2);
    assertThat(units.getFirst())
        .containsKeys("name", "type", "health_factor", "damage_factor", "knack");
    assertThat(((Number) units.getFirst().get("health_factor")).doubleValue()).isPositive();
    @SuppressWarnings("unchecked")
    var delta = (Map<String, Object>) answer.get("delta");
    assertThat(delta).containsEntry("credits", -6L);
  }

  @Test
  void aPartialRecruitIsReportedAndTheStrictGreaterThanRuleIsNotChanged() {
    // 10 Credits and an Archer costs 5: the first is paid (10 > 5), the second is not (5 > 5).
    var answer = call("td_recruit", "unit_type", "ARCHER", "quantity", 2);

    assertThat(answer).containsEntry("status", "partial");
    assertThat(answer).containsEntry("code", "INSUFFICIENT_CREDITS");
    assertThat(answer).containsEntry("recruited", 1);
    assertThat(fx.state(sandbox).getCredit()).isEqualTo(5.0);
  }

  @Test
  void aRepeatedRequestIdReplaysTheFirstAnswerAndActsOnce() {
    var founders = fx.state(sandbox).getUnits().get(UnitStats.WARRIOR).size();
    var arguments = fx.args(sandbox, "unit_type", "WARRIOR", "quantity", 1);
    var first = fx.call("td_recruit", arguments);
    var credits = fx.state(sandbox).getCredit();

    var again = fx.call("td_recruit", arguments);

    assertThat(first).containsEntry("replayed", false);
    assertThat(again).containsEntry("replayed", true);
    assertThat(again.get("state_seq")).isEqualTo(first.get("state_seq"));
    assertThat(again.get("units")).isEqualTo(first.get("units"));
    assertThat(fx.state(sandbox).getCredit()).isEqualTo(credits);
    assertThat(fx.state(sandbox).getUnits().get(UnitStats.WARRIOR)).hasSize(founders + 1);
  }

  @Test
  void aRefusalIsReplayedToo() {
    var arguments = fx.args(sandbox, "unit_type", "MAGE", "quantity", 1);

    var first = fx.call("td_recruit", arguments);
    fx.state(sandbox).getUnlockedUnits().add(UnitStats.MAGE);
    var again = fx.call("td_recruit", arguments);

    assertThat(first).containsEntry("code", "NOT_UNLOCKED");
    assertThat(again).containsEntry("code", "NOT_UNLOCKED").containsEntry("replayed", true);
  }

  @Test
  void aRequestIdUsedForADifferentCallIsRefusedNotReplayed() {
    var arguments = fx.args(sandbox, "unit_type", "WARRIOR", "quantity", 1);
    fx.call("td_recruit", arguments);
    var other = new java.util.HashMap<>(arguments);
    other.put("quantity", 2);

    var answer = fx.call("td_recruit", other);

    assertThat(answer).containsEntry("code", "INVALID_ARGUMENT");
  }

  @Test
  void aFailedCallLeavesTheIdFreeToRunAgainAndAFinishedOneDoesNot() {
    assertThat(fx.store.begin("dev", "abc-12345", "d"))
        .isInstanceOf(IdempotencyStore.Begin.Fresh.class);
    assertThat(fx.store.begin("dev", "abc-12345", "d"))
        .isInstanceOf(IdempotencyStore.Begin.InFlight.class);
    fx.store.abandon("dev", "abc-12345");
    assertThat(fx.store.begin("dev", "abc-12345", "d"))
        .isInstanceOf(IdempotencyStore.Begin.Fresh.class);

    fx.store.complete("dev", "abc-12345", "d", Map.of("status", "applied"));
    fx.store.abandon("dev", "abc-12345");

    assertThat(fx.store.begin("dev", "abc-12345", "d"))
        .isInstanceOf(IdempotencyStore.Begin.Replay.class);
    assertThat(fx.store.begin("dev", "abc-12345", "other"))
        .isInstanceOf(IdempotencyStore.Begin.Mismatch.class);
    assertThat(fx.store.begin("someone-else", "abc-12345", "d"))
        .isInstanceOf(IdempotencyStore.Begin.Fresh.class);
  }

  @Test
  void onlyTheLastSixtyFourResultsOfAGrantAreKept() {
    var first = fx.args(sandbox, "count", 1);
    fx.state(sandbox).setCredit(1_000_000d);
    fx.call("td_buy_villagers", first);
    for (var i = 0; i < IdempotencyStore.MAX_PER_GRANT; i++) {
      fx.call("td_buy_villagers", fx.args(sandbox, "count", 1));
    }

    var forgotten = fx.store.begin("dev", (String) first.get("request_id"), "x");

    assertThat(forgotten).isInstanceOf(IdempotencyStore.Begin.Fresh.class);
  }

  @Test
  void aRepeatAfterTenMinutesRunsAgain() {
    var arguments = fx.args(sandbox, "unit_type", "WARRIOR", "quantity", 1);
    fx.call("td_recruit", arguments);

    fx.millis.addAndGet(IdempotencyStore.KEEP.toMillis() + 1);

    assertThat(fx.call("td_recruit", arguments)).containsEntry("replayed", false);
  }

  @Test
  void anExpectedSeqThatMatchesLetsTheCallThrough() {
    var seq = fx.service.stateSeq(sandbox).orElseThrow();

    var answer =
        call("td_recruit", "unit_type", "WARRIOR", "quantity", 1, "expected_seq", seq.intValue());

    assertThat(answer).containsEntry("status", "applied");
  }

  @Test
  void aStaleSeqChangesNothing() {
    var before = fx.state(sandbox).getCredit();

    var answer = call("td_recruit", "unit_type", "WARRIOR", "quantity", 1, "expected_seq", 9999);

    assertThat(answer).containsEntry("code", "STALE_SEQ");
    assertThat(answer).containsKey("state_seq");
    assertThat(fx.state(sandbox).getCredit()).isEqualTo(before);
  }

  // ---------------------------------------------------------------- the village tools

  @Test
  void villagersAreHiredUpToTheCountAndAPartialFillIsReported() {
    fx.state(sandbox).setCredit(100d);
    var some = call("td_buy_villagers", "count", 2);
    assertThat(some).containsEntry("status", "applied");
    assertThat(fx.state(sandbox).getVillage().getVillagersCount()).isGreaterThanOrEqualTo(2);

    var credits = fx.state(sandbox).getCredit();
    fx.state(sandbox).setCredit(1d);
    var starved = call("td_buy_villagers", "count", 50);

    assertThat(credits).isLessThan(100d);
    assertThat(starved.get("code")).isEqualTo("INSUFFICIENT_CREDITS");
    assertThat(starved.get("status")).isIn("rejected", "partial");
  }

  @Test
  void aSaleOfFoodIsCappedAtTheAmountAndCreditsThePrice() {
    var state = fx.state(sandbox);
    state.getVillage().takeFood(1_000_000d);
    state.getVillage().addFood(40d);
    var before = state.getCredit();

    var answer = call("td_sell", "good", "FOOD", "amount", 5);

    assertThat(answer).containsEntry("status", "applied");
    assertThat(state.getCredit())
        .isEqualTo(before + 5 * state.getConfig().getReward().getFoodSellPrice());
    assertThat(state.getVillage().getFood()).isEqualTo(35d);
  }

  @Test
  void foodAskedForBeyondTheKeepBackIsAPartialSale() {
    var state = fx.state(sandbox);
    state.getVillage().takeFood(1_000_000d);
    state.getVillage().addFood(4d);

    var answer = call("td_sell", "good", "FOOD", "amount", 50);

    assertThat(answer).containsEntry("status", "partial");
    assertThat(state.getVillage().getFood()).isLessThanOrEqualTo(1d);
  }

  @Test
  void forceSellsFoodTheGuardWouldRefuse() {
    starving(fx, sandbox);

    var refused = call("td_sell", "good", "FOOD", "amount", 5);
    var forced = call("td_sell", "good", "FOOD", "amount", 5, "force", true);

    assertThat(refused).containsEntry("code", "WOULD_STARVE");
    assertThat(forced).containsEntry("status", "applied");
  }

  @Test
  void allIsForMaterialsAndRelicsOnlyAndForceForFoodOnly() {
    assertThat(call("td_sell", "good", "FOOD", "amount", "all"))
        .containsEntry("code", "INVALID_ARGUMENT");
    assertThat(call("td_sell", "good", "MATERIALS", "amount", 1, "force", true))
        .containsEntry("code", "INVALID_ARGUMENT");
  }

  @Test
  void materialsAreSoldByTheAmountAndTheRestIsKept() {
    var state = fx.state(sandbox);
    state
        .getResources()
        .put(io.github.diogohmcruz.towerdungeon.domain.models.ResourceType.MATERIALS, 10d);

    var some = call("td_sell", "good", "MATERIALS", "amount", 4);
    var more = call("td_sell", "good", "MATERIALS", "amount", 20);

    assertThat(some).containsEntry("status", "applied");
    assertThat(more).containsEntry("status", "partial");
    assertThat(
            state
                .getResources()
                .get(io.github.diogohmcruz.towerdungeon.domain.models.ResourceType.MATERIALS))
        .isZero();
  }

  @Test
  void anUpgradeIsBoughtWithItsCost() {
    fx.state(sandbox)
        .getResources()
        .put(io.github.diogohmcruz.towerdungeon.domain.models.ResourceType.MATERIALS, 1000d);
    fx.state(sandbox)
        .getResources()
        .put(io.github.diogohmcruz.towerdungeon.domain.models.ResourceType.RELICS, 1000d);

    var answer = call("td_buy_upgrade", "upgrade_id", "SUPPLY_LINES");

    assertThat(answer).containsEntry("status", "applied");
  }

  // ---------------------------------------------------------------- the expedition tools

  @Test
  void anExpeditionSetsOutWithTheNamedPartyAndAnExplicitSelectionIsRequired() {
    var answer = call("td_start_expedition", "party", "all_reserves");

    assertThat(answer).containsEntry("status", "applied");
    assertThat(answer.get("party_size")).isNotNull();
    assertThat(fx.state(sandbox).isExpeditionActive()).isTrue();
    assertThat(call("td_extract").get("status")).isEqualTo("applied");
    assertThat(call("td_start_expedition", "party", "everyone").get("code"))
        .isEqualTo("INVALID_ARGUMENT");
    assertThat(fx.call("td_start_expedition", fx.args(sandbox)).get("code"))
        .isEqualTo("INVALID_ARGUMENT");
  }

  @Test
  void aCountMapSendsExactlyThatManyAndRefusesMoreThanWaitAtHome() {
    var founders = fx.state(sandbox).getUnits().get(UnitStats.WARRIOR).size();
    fx.state(sandbox).setCredit(1000d);
    call("td_recruit", "unit_type", "WARRIOR", "quantity", 4);

    var tooMany = call("td_start_expedition", "party", party("WARRIOR", 99));
    assertThat(tooMany).containsEntry("code", "INSUFFICIENT_UNITS");
    assertThat(fx.state(sandbox).isExpeditionActive()).isFalse();
    assertThat(fx.state(sandbox).getUnits().get(UnitStats.WARRIOR)).hasSize(founders + 4);

    var some = call("td_start_expedition", "party", party("WARRIOR", 2));
    assertThat(some).containsEntry("status", "applied");
    assertThat(fx.state(sandbox).getTowerPartySize()).isEqualTo(2);
  }

  @Test
  void aWaveWithFewerAtHomeThanAskedGoesPartialAndSaysSo() {
    call("td_start_expedition", "party", party("WARRIOR", 1));
    var athome = fx.state(sandbox).getUnits().get(UnitStats.WARRIOR).size();

    var answer = call("td_send_reinforcement_wave", "party", party("WARRIOR", athome + 5));

    assertThat(answer)
        .containsEntry("status", "partial")
        .containsEntry("code", "INSUFFICIENT_UNITS");
    assertThat(fx.state(sandbox).getReinforcements()).hasSize(1);
  }

  @Test
  void extractingReturnsTheExpeditionSummary() {
    call("td_start_expedition", "party", "all_reserves");

    var answer = call("td_extract");

    assertThat(answer).containsEntry("status", "applied");
    @SuppressWarnings("unchecked")
    var summary = (Map<String, Object>) answer.get("summary");
    assertThat(summary)
        .containsEntry("outcome", "EXTRACTED")
        .containsKeys("highest_floor", "survivors");
  }

  @Test
  void aHumansCampaignCannotBeActedOnThroughASandboxGrant() {
    var failure =
        org.assertj.core.api.Assertions.catchThrowable(
            () ->
                fx.actions.recruit(
                    ActionsFixture.WHO,
                    new io.github.diogohmcruz.towerdungeon.agent.tools.Args(
                        Map.of(
                            "request_id", "abcd-1234",
                            "campaign", "sandbox-00000000-0000-0000-0000-000000000000",
                            "unit_type", "WARRIOR",
                            "quantity", 1))));

    assertThat(failure)
        .isInstanceOf(io.github.diogohmcruz.towerdungeon.agent.tools.ToolFailure.class);
  }
}

package io.github.diogohmcruz.towerdungeon.agent.act;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.models.upgrade.UpgradeType;

/**
 * Random tool sequences against seeded Sandboxes (design 9, the invariants fuzz). After every
 * command, whatever it answered: the Vault's Credits are never below 0, Supplies never pass the
 * Carrying Capacity, Units are conserved (Reserves plus Party plus Reinforcement Waves plus the
 * dead equal those recruited), and the Loot a Party carried is lost on every Wipe. A failure names
 * the seed and the step, so the sequence can be run again.
 */
class ActionsInvariantsFuzzTest {
  private static final int SEEDS = 40;
  private static final int STEPS = 160;

  private int wipes;
  private int extractions;

  @Test
  void invariantsHoldOverManySeededRandomToolSequences() {
    for (var seed = 1; seed <= SEEDS; seed++) {
      runOne(seed);
    }
    // The wipe invariant is not vacuous: the sequences do end Expeditions both ways.
    assertThat(wipes).as("wipes seen").isPositive();
    assertThat(extractions).as("extractions seen").isPositive();
  }

  @Test
  void theSameSeedAndTheSameSequenceMakeTheSameAnswers() {
    assertThat(transcript(5)).isEqualTo(transcript(5));
  }

  private List<String> transcript(long seed) {
    var out = new ArrayList<String>();
    run(seed, out);
    return out;
  }

  private void runOne(long seed) {
    run(seed, null);
  }

  private void run(long seed, List<String> transcript) {
    var fx = new ActionsFixture(seed);
    var sandbox = fx.open(seed);
    var state = fx.state(sandbox);
    var dice = new SplittableRandom(seed * 7919);
    var recruited = state.getUnits().values().stream().mapToInt(List::size).sum();
    var diedBefore = 0;
    var steps = new ArrayList<String>();

    for (var step = 0; step < STEPS; step++) {
      var before = view(state);
      var tool = "wait";
      Map<String, Object> answer = null;
      switch (dice.nextInt(12)) {
        case 0, 1, 2 -> {
          tool = "td_recruit";
          answer =
              fx.call(
                  tool,
                  fx.args(
                      sandbox,
                      "unit_type",
                      UnitStats.values()[dice.nextInt(UnitStats.values().length)].name(),
                      "quantity",
                      1 + dice.nextInt(6)));
        }
        case 3 -> {
          tool = "td_buy_villagers";
          answer = fx.call(tool, fx.args(sandbox, "count", 1 + dice.nextInt(4)));
        }
        case 4 -> {
          tool = "td_sell";
          var good = List.of("FOOD", "MATERIALS", "RELICS").get(dice.nextInt(3));
          var args = fx.args(sandbox, "good", good);
          if ("FOOD".equals(good)) {
            args.put("amount", 1 + dice.nextInt(30));
            args.put("force", dice.nextBoolean());
          } else {
            args.put("amount", dice.nextBoolean() ? "all" : (Object) (1 + dice.nextInt(20)));
          }
          answer = fx.call(tool, args);
        }
        case 5 -> {
          tool = "td_buy_upgrade";
          var all = UpgradeType.values();
          answer =
              fx.call(tool, fx.args(sandbox, "upgrade_id", all[dice.nextInt(all.length)].name()));
        }
        case 6, 7 -> {
          tool = "td_start_expedition";
          if (before.activeRun) {
            answer = fx.call(tool, fx.args(sandbox, "party", "all_reserves"));
          } else {
            // A run that sets out resets the last run's counters: close those books first.
            var lastRunLost = state.getRunUnitsLost();
            answer = fx.call(tool, fx.args(sandbox, "party", someParty(dice)));
            if (!"rejected".equals(answer.get("status"))) {
              diedBefore += lastRunLost;
            }
          }
        }
        case 8 -> {
          tool = "td_send_reinforcement_wave";
          answer = fx.call(tool, fx.args(sandbox, "party", someParty(dice)));
        }
        case 9 -> {
          tool = "td_extract";
          answer = fx.call(tool, fx.args(sandbox));
        }
        default -> {
          var ticks = 1 + dice.nextInt(40);
          fx.registry.advance("dev", sandbox, ticks, null);
          answer = Map.of("status", "advanced", "code", "OK", "state_seq", 0);
        }
      }
      steps.add(tool);
      if (transcript != null) {
        transcript.add(tool + " " + answer.get("status") + " " + answer.get("code"));
      }
      var where = "seed " + seed + " step " + step + " after " + tool + " " + answer;

      assertThat(answer.get("status")).as(where).isIn("applied", "partial", "rejected", "advanced");
      assertThat(answer.get("code")).as(where).isNotNull();
      if (answer.get("recruited") instanceof Number made) {
        recruited += made.intValue();
      }
      var now = view(state);

      assertThat(state.getCredit()).as("Vault Credits " + where).isGreaterThanOrEqualTo(0d);
      if (state.isExpeditionActive()) {
        assertThat(state.getSupplies())
            .as("Supplies against Carrying Capacity " + where)
            .isLessThanOrEqualTo(state.getMaxSupplies() + 1e-6);
      }
      var alive = now.reserves + now.party + now.waves;
      var dead = state.getVillage().getReservesLost() + diedBefore + state.getRunUnitsLost();
      assertThat(alive + dead).as("Units conserved " + where).isEqualTo(recruited);

      if (before.activeRun && !now.activeRun) {
        var summary = state.getLastRunSummary();
        assertThat(state.getCarriedCredits()).as("nothing carried " + where).isZero();
        if (summary != null && RunSummary.EXTRACTED.equals(summary.outcome())) {
          extractions++;
        }
        if (summary != null && RunSummary.WIPED.equals(summary.outcome())) {
          wipes++;
          assertThat(state.getCredit())
              .as("carried Credits lost on a Wipe " + where)
              .isEqualTo(before.credits);
          assertThat(state.getResources().get(ResourceType.MATERIALS))
              .as("carried Materials lost on a Wipe " + where)
              .isEqualTo(before.materials);
          assertThat(state.getResources().get(ResourceType.RELICS))
              .as("carried Relics lost on a Wipe " + where)
              .isEqualTo(before.relics);
        }
      }
      if (state.getPhase()
          == io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase.OFFER) {
        break;
      }
    }
    assertThat(steps).isNotEmpty();
  }

  /** Either everyone, or a count for a few types, sometimes more than are at home. */
  private static Object someParty(SplittableRandom dice) {
    if (dice.nextInt(3) == 0) {
      return "all_reserves";
    }
    var party = new LinkedHashMap<String, Object>();
    for (var i = 0; i < 1 + dice.nextInt(3); i++) {
      party.put(
          UnitStats.values()[dice.nextInt(UnitStats.values().length)].name(), 1 + dice.nextInt(5));
    }
    return party;
  }

  private record View(
      double credits,
      double materials,
      double relics,
      int reserves,
      int party,
      int waves,
      boolean activeRun) {}

  private static View view(GameState state) {
    var waveUnits = state.getReinforcements().stream().mapToInt(w -> w.size()).sum();
    return new View(
        state.getCredit(),
        state.getResources().getOrDefault(ResourceType.MATERIALS, 0d),
        state.getResources().getOrDefault(ResourceType.RELICS, 0d),
        state.getUnits().values().stream().mapToInt(List::size).sum(),
        state.getTowerPartySize(),
        waveUnits,
        state.isExpeditionActive());
  }

  @SuppressWarnings("unused")
  private static Map<String, Object> unused() {
    return new HashMap<>();
  }
}

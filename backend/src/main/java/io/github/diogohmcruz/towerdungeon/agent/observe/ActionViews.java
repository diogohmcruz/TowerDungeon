package io.github.diogohmcruz.towerdungeon.agent.observe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;
import io.github.diogohmcruz.towerdungeon.domain.services.GameService.Meta;

/**
 * What the game would accept right now, with a reason beside each near miss. It runs the same
 * affordability rules the commands run (recruiting needs Credits strictly above the cost and spends
 * Loot first; Villagers are paid from the Vault alone) and is not a simulation. It reads; nothing
 * here changes the Campaign.
 */
public final class ActionViews {
  private ActionViews() {}

  public static Map<String, Object> of(GameState state, Meta meta) {
    var view = new LinkedHashMap<String, Object>();
    view.put("seq", meta.seq());
    view.put("spendable_credits", round(state.getSpendableCredit()));
    var actions = new ArrayList<Map<String, Object>>();
    recruits(state, actions);
    villagers(state, actions);
    sales(state, actions);
    upgrades(state, actions, view);
    expedition(state, meta, actions);
    view.put("actions", actions);
    view.put(
        "note", "This lists what the game would accept now. The td_ action tools carry it out.");
    return view;
  }

  private static void recruits(GameState state, List<Map<String, Object>> actions) {
    var spendable = state.getSpendableCredit();
    for (var type : UnitStats.values()) {
      var action = action("RECRUIT");
      action.put("unit_type", type.name());
      action.put("cost_each", type.getCost());
      if (!state.isUnitUnlocked(type)) {
        refuse(action, "NOT_UNLOCKED");
        action.put("needs", unlockHint(state, type));
      } else {
        var max = 0;
        for (var left = spendable; left > type.getCost(); left -= type.getCost()) {
          max++;
        }
        if (max == 0) {
          refuse(action, "INSUFFICIENT_CREDITS");
        } else {
          action.put("max_quantity", max);
        }
      }
      actions.add(action);
    }
  }

  private static String unlockHint(GameState state, UnitStats type) {
    for (var milestone : state.getMilestones()) {
      if (milestone.companion() && milestone.name() != null && unlocks(milestone.id(), type)) {
        return milestone.name() + ": " + milestone.trigger();
      }
    }
    for (var upgrade : state.getAvailableUpgrades()) {
      if (type.name().equals(upgrade.unlockUnit())) {
        return "Upgrade: " + upgrade.name();
      }
    }
    return "An upgrade or a milestone";
  }

  private static boolean unlocks(String milestoneId, UnitStats type) {
    return io.github.diogohmcruz.towerdungeon.domain.models.milestone.MilestoneType.valueOf(
                milestoneId)
            .getUnlockUnit()
        == type;
  }

  private static void villagers(GameState state, List<Map<String, Object>> actions) {
    var action = action("BUY_VILLAGERS");
    var next = state.getVillage().getVillagersCount();
    if (state.getCredit() > next) {
      action.put("cost_next", next);
    } else {
      refuse(action, "INSUFFICIENT_CREDITS");
    }
    actions.add(action);
  }

  private static void sales(GameState state, List<Map<String, Object>> actions) {
    var food = action("SELL_FOOD");
    if (state.getVillage().getFood() > 1) {
      food.put("sells", Math.floor(state.getVillage().getFood() - 1));
    } else {
      refuse(food, "NOTHING_TO_SELL");
    }
    actions.add(food);
    for (var type : List.of(ResourceType.MATERIALS, ResourceType.RELICS)) {
      var sale = action("SELL_" + type.name());
      var stock = state.getResources().getOrDefault(type, 0d);
      if (stock > 0) {
        sale.put("sells", round(stock));
      } else {
        refuse(sale, "NOTHING_TO_SELL");
      }
      actions.add(sale);
    }
  }

  private static void upgrades(
      GameState state, List<Map<String, Object>> actions, Map<String, Object> view) {
    var unaffordable = 0;
    for (var offer : state.getAvailableUpgrades()) {
      if (offer.maxed()) {
        continue;
      }
      var affordable = true;
      var cost = new TreeMap<String, Double>();
      for (var entry : offer.nextCost().entrySet()) {
        cost.put(entry.getKey().name(), entry.getValue());
        if (state.getResources().getOrDefault(entry.getKey(), 0d) < entry.getValue()) {
          affordable = false;
        }
      }
      if (affordable) {
        var action = action("BUY_UPGRADE");
        action.put("upgrade", offer.id());
        action.put("cost", cost);
        actions.add(action);
      } else {
        unaffordable++;
      }
    }
    view.put("upgrades_unaffordable", unaffordable);
  }

  private static void expedition(GameState state, Meta meta, List<Map<String, Object>> actions) {
    var playing = state.getGameOutcome() == GameOutcome.PLAYING;
    var reserves = state.getUnits().values().stream().mapToInt(List::size).sum();
    var out = state.isExpeditionActive();

    var start = action("START_EXPEDITION");
    if (out) {
      refuse(start, "EXPEDITION_ACTIVE");
    } else if (!playing) {
      refuse(start, "CAMPAIGN_ENDED");
    } else if (reserves == 0) {
      refuse(start, "NO_UNITS");
    } else {
      var floors = new ArrayList<Integer>();
      floors.add(0);
      state.getShortcuts().stream().filter(s -> s.unlocked()).forEach(s -> floors.add(s.floor()));
      start.put("start_floors", floors);
      start.put("reserves", reserves);
    }
    actions.add(start);

    var wave = action("SEND_REINFORCEMENT_WAVE");
    if (!out) {
      refuse(wave, "NO_EXPEDITION");
    } else if (meta.held()) {
      refuse(wave, "PARTY_HELD");
    } else if (!playing) {
      refuse(wave, "CAMPAIGN_ENDED");
    } else if (reserves == 0) {
      refuse(wave, "NO_UNITS");
    } else {
      wave.put("reserves", reserves);
    }
    actions.add(wave);

    var extract = action("EXTRACT");
    if (!out) {
      refuse(extract, "NO_EXPEDITION");
    }
    actions.add(extract);

    if (meta.held()) {
      actions.add(action("RESUME"));
    }
  }

  private static Map<String, Object> action(String name) {
    var action = new LinkedHashMap<String, Object>();
    action.put("action", name);
    return action;
  }

  private static void refuse(Map<String, Object> action, String reason) {
    action.put("ok", false);
    action.put("reason", reason);
  }

  private static double round(double value) {
    return Math.round(value * 10d) / 10d;
  }
}

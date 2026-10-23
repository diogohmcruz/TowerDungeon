package io.github.diogohmcruz.towerdungeon.agent.telemetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.RunSummary;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.observation.ExpeditionMeter;
import io.github.diogohmcruz.towerdungeon.domain.services.ManualTickDriver;
import io.github.diogohmcruz.towerdungeon.domain.services.TickObserver;
import io.github.diogohmcruz.towerdungeon.domain.trial.ConfigHash;

/**
 * Turns the ticks of one Sandbox Campaign into telemetry rows (design 7): one row per (Expedition,
 * Floor), one per Expedition and one per Campaign. It only reads the game, so a watched run is the
 * same run as an unwatched one.
 *
 * <p>Every tick is tagged {@code village} (no Expedition out) or {@code expedition}, and only the
 * second kind reaches a Floor row, so idle farming at home never contaminates the Floor numbers.
 * What the village alone did is reported apart, in the Campaign row.
 *
 * <p>A Floor row opens when the Party arrives on it and closes when it leaves (cleared) or the
 * Expedition ends. Its damage, mana and loss columns are what the {@link ExpeditionMeter} counted
 * while the Party stood there.
 *
 * <p>Not thread-safe: the manual driver calls it under the Campaign's lock; {@link #finish} must be
 * called from under that lock too (or once ticking has stopped).
 */
public final class RowRecorder implements TickObserver {
  private static final int ROUND = 1000;

  /** What the game looked like at one moment of an Expedition. */
  private record Sample(
      int floor,
      double partyHealth,
      int partySize,
      double supplies,
      double sanity,
      int waves,
      double credits,
      double materials,
      double relics) {}

  /** A Floor row being built. */
  private static final class FloorAcc {
    int floor;
    boolean guardian;
    boolean surge;
    int ticks;
    Map<String, Integer> partyStart;
    double healthStart;
    double suppliesStart;
    int garrisonCount;
    double garrisonHealth;
    Map<String, Integer> garrisonTypes;
    ExpeditionMeter.Reading meterStart;
    double credits;
    double materials;
    double relics;
    int wavesMerged;
    double healed;
  }

  /** An Expedition row being built. */
  private static final class ExpeditionAcc {
    int index;
    int ticks;
    int villageTicksBefore;
    int startFloor;
    Map<String, Integer> partyStart;
    double suppliesStart;
    double capacity;
    double pantryAtDeparture;
  }

  private final Consumer<Map<String, Object>> rows;
  private final String policy;
  private final int campaignIndex;

  private String engineHash;
  private long seed;
  private boolean started;

  private ExpeditionAcc expedition;
  private FloorAcc floor;
  private Sample last;
  private Sample beforeTick;
  private ExpeditionMeter.Reading meterBeforeTick;
  private boolean tickIsExpedition;
  private RunSummary lastSummary;
  private int expeditions;
  private int villageTicks;
  private int expeditionTicks;
  private int villageTicksSinceExpedition;
  private double villageFood;
  private double foodAtTickStart;
  private long simulated;
  private boolean finished;
  private final Map<String, Integer> milestones = new TreeMap<>();
  private final Map<String, Integer> shortcuts = new TreeMap<>();

  public RowRecorder(Consumer<Map<String, Object>> rows, String policy, int campaignIndex) {
    if (!TelemetrySink.TOKEN.matcher(policy).matches()) {
      throw new IllegalArgumentException("The policy name must be a token");
    }
    this.rows = rows;
    this.policy = policy;
    this.campaignIndex = campaignIndex;
  }

  // ---------------------------------------------------------------- the observer

  @Override
  public void afterCommand(GameState state) {
    begin(state);
    syncExpedition(state);
  }

  @Override
  public void beforeTick(GameState state, long second) {
    begin(state);
    syncExpedition(state);
    tickIsExpedition = state.isExpeditionActive();
    foodAtTickStart = state.getTally().getFoodGrown();
    if (tickIsExpedition) {
      beforeTick = sample(state);
      meterBeforeTick = state.getMeter().snapshot();
      if (floor == null || floor.floor != beforeTick.floor()) {
        openFloor(state, beforeTick);
      }
    }
  }

  @Override
  public void afterTick(GameState state, long second) {
    simulated = second;
    if (!tickIsExpedition) {
      villageTicks++;
      villageTicksSinceExpedition++;
      villageFood += Math.max(0d, state.getTally().getFoodGrown() - foodAtTickStart);
      return;
    }
    expeditionTicks++;
    expedition.ticks++;
    floor.ticks++;
    var after = state.isExpeditionActive() ? sample(state) : ended(beforeTick);
    floor.credits += Math.max(0d, after.credits() - beforeTick.credits());
    floor.materials += Math.max(0d, after.materials() - beforeTick.materials());
    floor.relics += Math.max(0d, after.relics() - beforeTick.relics());
    floor.wavesMerged += Math.max(0, beforeTick.waves() - after.waves());
    // Health the Party got back this tick (a Healer, or sanity gained past its maximum). Only ticks
    // in which nobody fell are counted: a Unit that falls takes its remaining health with it.
    var meterAfter = state.getMeter().snapshot();
    if (meterAfter.unitsLost() == meterBeforeTick.unitsLost()) {
      var taken =
          (meterAfter.takenCombat() - meterBeforeTick.takenCombat())
              + (meterAfter.takenAttrition() - meterBeforeTick.takenAttrition())
              + (meterAfter.takenStarvation() - meterBeforeTick.takenStarvation());
      floor.healed += Math.max(0d, after.partyHealth() - (beforeTick.partyHealth() - taken));
    }
    last = after;
    if (!state.isExpeditionActive()) {
      closeExpedition(state);
    } else if (after.floor() != floor.floor) {
      closeFloor(state, floor.floor < after.floor(), "CLEARED");
      openFloor(state, after);
    }
  }

  /**
   * Closes what is still open and writes the Campaign row. Call once, when the run is over; {@code
   * stop} is a token saying why the driver stopped (it is not the game's outcome).
   */
  public void finish(GameState state, String stop) {
    if (finished) {
      return;
    }
    begin(state);
    syncExpedition(state);
    if (expedition != null) {
      closeExpedition(state);
    }
    finished = true;
    var row = base("campaign");
    row.put("expeditions", expeditions);
    row.put("outcome", state.getGameOutcome().name());
    row.put("stop", stop);
    row.put("highest_floor", state.getHighestFloor());
    row.put("simulated_seconds", simulated);
    row.put("village_ticks", villageTicks);
    row.put("expedition_ticks", expeditionTicks);
    row.put("village_food_grown", round(villageFood));
    row.put("village_food_per_tick", villageTicks == 0 ? 0d : round(villageFood / villageTicks));
    row.put("villagers_end", state.getVillage().getVillagersCount());
    row.put("expeditions_to_milestone", milestones);
    row.put("expeditions_to_shortcut", shortcuts);
    rows.accept(row);
  }

  // ---------------------------------------------------------------- the Campaign's start

  private void begin(GameState state) {
    if (started) {
      return;
    }
    started = true;
    engineHash = ConfigHash.of(state.getConfig());
    seed = state.getRandom().snapshot().seed();
    lastSummary = state.getLastRunSummary();
  }

  /**
   * An Expedition set out, or ended, by a command between ticks. One that ended and another that
   * set out with no tick between are told apart by the new Summary.
   */
  private void syncExpedition(GameState state) {
    if (expedition != null
        && (!state.isExpeditionActive() || state.getLastRunSummary() != lastSummary)) {
      closeExpedition(state);
    }
    if (state.isExpeditionActive() && expedition == null) {
      expedition = new ExpeditionAcc();
      expedition.index = ++expeditions;
      expedition.villageTicksBefore = villageTicksSinceExpedition;
      villageTicksSinceExpedition = 0;
      expedition.startFloor = state.getTower() == null ? 0 : state.getTower().getCurrentFloor();
      expedition.partyStart = party(state);
      expedition.suppliesStart = round(state.getSupplies());
      expedition.capacity = round(state.getMaxSupplies() == null ? 0d : state.getMaxSupplies());
      expedition.pantryAtDeparture = round(state.getVillage().getFood());
      last = sample(state);
    }
  }

  // ---------------------------------------------------------------- Floors

  private void openFloor(GameState state, Sample at) {
    var tower = state.getTower();
    var open = tower.getCurrentTowerFloor();
    var acc = new FloorAcc();
    acc.floor = at.floor();
    acc.guardian = open != null && open.isBoss();
    var surgeEvery = state.getConfig().getEnemies().getSurgeInterval();
    // Floor 0 is the approach to the Tower: no Guardian, no Surge, no Garrison.
    acc.surge = acc.floor >= 1 && !acc.guardian && surgeEvery > 0 && acc.floor % surgeEvery == 0;
    acc.partyStart = party(state);
    acc.healthStart = at.partyHealth();
    acc.suppliesStart = at.supplies();
    var enemies = open == null ? List.<Enemy>of() : open.getEnemies();
    acc.garrisonCount = enemies.size();
    acc.garrisonHealth = enemies.stream().mapToDouble(Enemy::getCurrentHealth).sum();
    var types = new TreeMap<String, Integer>();
    enemies.forEach(enemy -> types.merge(enemy.getStats().name(), 1, Integer::sum));
    acc.garrisonTypes = types;
    acc.meterStart = state.getMeter().snapshot();
    floor = acc;
    last = at;
  }

  /**
   * Writes the Floor row. {@code end} says how the Party left the Floor: CLEARED, or the outcome of
   * the Expedition that ended on it (EXTRACTED, WIPED, VICTORY, UNKNOWN).
   */
  private void closeFloor(GameState state, boolean cleared, String end) {
    if (floor == null) {
      return;
    }
    var now = state.getMeter().snapshot();
    var start = floor.meterStart;
    var row = base("floor");
    row.put("expedition_index", expedition.index);
    row.put("floor", floor.floor);
    row.put("guardian", floor.guardian);
    row.put("surge", floor.surge);
    row.put("cleared", cleared);
    row.put("end", end);
    row.put("ticks_on_floor", floor.ticks);
    row.put("party_start", floor.partyStart);
    row.put("party_health_start", round(floor.healthStart));
    row.put("party_health_end", round(last.partyHealth()));
    row.put("party_size_end", last.partySize());
    row.put("supplies_start", round(floor.suppliesStart));
    row.put("supplies_end", round(last.supplies()));
    row.put("supplies_drained", round(now.suppliesDrained() - start.suppliesDrained()));
    row.put("garrison_count", floor.garrisonCount);
    row.put("garrison_health_start", round(floor.garrisonHealth));
    row.put("garrison_types", floor.garrisonTypes);
    row.put("damage_dealt", round(now.damageDealt() - start.damageDealt()));
    row.put("damage_taken_combat", round(now.takenCombat() - start.takenCombat()));
    row.put("damage_taken_attrition", round(now.takenAttrition() - start.takenAttrition()));
    row.put("damage_taken_starvation", round(now.takenStarvation() - start.takenStarvation()));
    row.put("mana_spent", round(now.manaSpent() - start.manaSpent()));
    var loot = new LinkedHashMap<String, Object>();
    loot.put("credits", round(floor.credits));
    loot.put("materials", round(floor.materials));
    loot.put("relics", round(floor.relics));
    row.put("loot", loot);
    var lost = new ArrayList<Map<String, Object>>();
    for (var loss : state.getMeter().lossesSince(start.unitsLost())) {
      lost.add(Map.of("type", loss.type(), "cause", loss.cause().name()));
    }
    row.put("units_lost", lost);
    row.put("waves_merged", floor.wavesMerged);
    row.put("party_health_recovered", round(floor.healed));
    // Unit-depth columns (new names only): what Parts, items and minds did on this Floor.
    row.put("parts_lost", now.partsLost() - start.partsLost());
    row.put("items_found", now.itemsFound() - start.itemsFound());
    row.put("surplus_items", now.surplusItems() - start.surplusItems());
    row.put("sanity_end", round(last.sanity()));
    rows.accept(row);
    floor = null;
  }

  // ---------------------------------------------------------------- Expeditions

  private void closeExpedition(GameState state) {
    if (expedition == null) {
      return;
    }
    var summary = state.getLastRunSummary();
    var fresh = summary != null && summary != lastSummary;
    lastSummary = summary;
    closeFloor(state, false, fresh ? summary.outcome() : "UNKNOWN");
    var row = base("expedition");
    row.put("expedition_index", expedition.index);
    row.put("outcome", fresh ? summary.outcome() : "UNKNOWN");
    row.put("start_floor", expedition.startFloor);
    row.put("highest_floor", fresh ? summary.highestFloor() : expedition.startFloor);
    row.put("floors_cleared", fresh ? summary.floorsCleared() : 0);
    row.put("enemies_defeated", fresh ? summary.enemiesDefeated() : 0);
    row.put("credits_gained", fresh ? round(summary.creditsGained()) : 0d);
    row.put("materials_gained", fresh ? round(summary.materialsGained()) : 0d);
    row.put("relics_gained", fresh ? round(summary.relicsGained()) : 0d);
    row.put("spores_gained", fresh ? round(summary.sporesGained()) : 0d);
    row.put("units_lost", fresh ? summary.unitsLost() : 0);
    row.put("survivors", fresh ? summary.survivors() : 0);
    row.put("party_start", expedition.partyStart);
    row.put("supplies_start", expedition.suppliesStart);
    row.put("carrying_capacity", expedition.capacity);
    row.put("pantry_at_departure", expedition.pantryAtDeparture);
    row.put("pantry_at_return", round(state.getVillage().getFood()));
    row.put("total_ticks", expedition.ticks);
    row.put("village_ticks_before", expedition.villageTicksBefore);
    rows.accept(row);
    for (var milestone : state.getMilestones()) {
      if (milestone.achieved()) {
        milestones.putIfAbsent(milestone.id(), expedition.index);
      }
    }
    for (var shortcut : state.getShortcuts()) {
      if (shortcut.unlocked()) {
        shortcuts.putIfAbsent(shortcut.id(), expedition.index);
      }
    }
    expedition = null;
    last = null;
  }

  // ---------------------------------------------------------------- reading the game

  private Sample sample(GameState state) {
    var party = state.getUnitsOnTower().values().stream().flatMap(List::stream).toList();
    var health = party.stream().mapToDouble(Unit::getCurrentHealth).sum();
    var sanity = party.stream().mapToDouble(Unit::getSanity).average().orElse(0d);
    var tower = state.getTower();
    return new Sample(
        tower == null ? 0 : tower.getCurrentFloor(),
        health,
        party.size(),
        state.getSupplies(),
        sanity,
        state.getReinforcements().size(),
        state.getCarriedCredits(),
        state.getCarriedLoot().getOrDefault(ResourceType.MATERIALS, 0d),
        state.getCarriedLoot().getOrDefault(ResourceType.RELICS, 0d));
  }

  /**
   * The state after an Expedition ended in a tick: nobody stands on the Tower. Loot is kept as it
   * was, because what the end banks or forfeits is not a gain on this Floor.
   */
  private static Sample ended(Sample before) {
    return new Sample(
        before.floor(),
        0d,
        0,
        0d,
        before.sanity(),
        0,
        before.credits(),
        before.materials(),
        before.relics());
  }

  private static Map<String, Integer> party(GameState state) {
    var out = new TreeMap<String, Integer>();
    state
        .getUnitsOnTower()
        .forEach(
            (type, list) -> {
              if (!list.isEmpty()) {
                out.put(type.name(), list.size());
              }
            });
    return out;
  }

  private Map<String, Object> base(String kind) {
    var row = new LinkedHashMap<String, Object>();
    row.put("row", kind);
    row.put("engine", ManualTickDriver.ENGINE);
    row.put("tick_model", ManualTickDriver.TICK_MODEL);
    row.put("config_hash", engineHash);
    row.put("seed", seed);
    row.put("policy", policy);
    row.put("campaign_index", campaignIndex);
    return row;
  }

  private static double round(double value) {
    return Math.round(value * ROUND) / (double) ROUND;
  }
}

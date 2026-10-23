package io.github.diogohmcruz.towerdungeon.domain.observation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.models.BaseUnit;
import io.github.diogohmcruz.towerdungeon.domain.models.Enemy;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Part;
import io.github.diogohmcruz.towerdungeon.domain.models.Phobia;
import io.github.diogohmcruz.towerdungeon.domain.models.Reinforcement;
import io.github.diogohmcruz.towerdungeon.domain.models.ResourceType;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * A compact, read-only picture of one Campaign for a reader with a small context window. It is
 * built from the live state, never by serialising it.
 *
 * <p>Units are aggregated into one row per type (counts and summaries), so the size does not grow
 * with the number of units, their body parts, their items or their phobias. Nothing hidden is
 * shown: only phobias the player has discovered are counted, no unit names or ids appear, and the
 * rolled strengths that make one recruit better than another are not exposed; only the resulting
 * health totals are, as on the player's own screen.
 *
 * <p>Build it under the state's lock.
 */
public record StateProjection(
    long seq,
    CampaignFacts campaign,
    VillageFacts village,
    Map<String, UnitGroup> reserves,
    ExpeditionFacts expedition) {

  /** How many in-transit waves are listed one by one; the rest are only counted. */
  static final int MAX_LISTED_WAVES = 10;

  public record CampaignFacts(
      String outcome, int highestFloor, int expeditions, int enemiesDefeated) {}

  /** What the Vault holds. Credits carried by a party are not here; they are Loot. */
  public record Vault(double credits, double materials, double relics) {}

  public record VillageFacts(double pantry, int villagers, double netFoodPerTick, Vault vault) {}

  /**
   * One unit type in one place.
   *
   * @param n how many
   * @param health summed current over summed maximum, for example {@code "72/80"}
   * @param meanSanity the mean sanity
   * @param lowestSanity the lowest sanity
   * @param knownPhobias phobias the player has discovered, over all units of the type
   * @param lostParts body parts lost, over all units of the type
   * @param itemsWorn items worn, over all units of the type
   */
  public record UnitGroup(
      int n,
      String health,
      double meanSanity,
      double lowestSanity,
      int knownPhobias,
      int lostParts,
      int itemsWorn) {}

  public record Supplies(double have, double capacity) {}

  public record Loot(double credits, double materials, double relics) {}

  /** The enemies still standing on the floor the party fights on. */
  public record Garrison(int remaining, String health, Map<String, Integer> types) {}

  public record Wave(int n, double floor, double supplies) {}

  public record ExpeditionFacts(
      int floor,
      int startFloor,
      Map<String, UnitGroup> party,
      Supplies supplies,
      Loot loot,
      double spendableCredits,
      Garrison garrison,
      int waveCount,
      List<Wave> waves) {}

  /** The brief view of a state at the given sequence number. */
  public static StateProjection brief(GameState state, long seq) {
    var village = state.getVillage();
    var vault =
        new Vault(
            round(state.getCredit()),
            round(state.getResources().getOrDefault(ResourceType.MATERIALS, 0d)),
            round(state.getResources().getOrDefault(ResourceType.RELICS, 0d)));
    return new StateProjection(
        seq,
        new CampaignFacts(
            state.getGameOutcome().name(),
            state.getHighestFloor(),
            state.getExpeditionsCompleted(),
            state.getEnemiesDefeated()),
        new VillageFacts(
            round(village.getFood()),
            village.getVillagersCount(),
            round(state.getNetFoodPerSecond()),
            vault),
        groups(state.getUnits()),
        state.isExpeditionActive() ? expedition(state) : null);
  }

  private static ExpeditionFacts expedition(GameState state) {
    var tower = state.getTower();
    var waves = state.getReinforcements();
    return new ExpeditionFacts(
        tower == null ? 0 : tower.getCurrentFloor(),
        state.getRunStartFloor(),
        groups(state.getUnitsOnTower()),
        new Supplies(round(state.getSupplies()), round(state.getMaxSupplies())),
        new Loot(
            round(state.getCarriedCredits()),
            round(state.getCarriedLoot().getOrDefault(ResourceType.MATERIALS, 0d)),
            round(state.getCarriedLoot().getOrDefault(ResourceType.RELICS, 0d))),
        round(state.getSpendableCredit()),
        garrison(state),
        waves.size(),
        waves.stream().limit(MAX_LISTED_WAVES).map(StateProjection::wave).toList());
  }

  private static Wave wave(Reinforcement wave) {
    return new Wave(wave.size(), round(wave.getCurrentFloor()), round(wave.getSupplies()));
  }

  private static Garrison garrison(GameState state) {
    var tower = state.getTower();
    var floor = tower == null ? null : tower.getCurrentTowerFloor();
    List<Enemy> enemies = floor == null ? List.of() : floor.getEnemies();
    var types = new TreeMap<String, Integer>();
    enemies.forEach(enemy -> types.merge(enemy.getStats().name(), 1, Integer::sum));
    return new Garrison(enemies.size(), health(enemies), types);
  }

  private static Map<String, UnitGroup> groups(Map<UnitStats, List<Unit>> byType) {
    var groups = new TreeMap<String, UnitGroup>();
    byType.forEach(
        (type, units) -> {
          if (!units.isEmpty()) {
            groups.put(type.name(), group(units));
          }
        });
    return groups;
  }

  private static UnitGroup group(List<Unit> units) {
    var sanity = units.stream().mapToDouble(Unit::getSanity).summaryStatistics();
    var knownPhobias =
        units.stream().flatMap(unit -> unit.getPhobias().stream()).filter(Phobia::isKnown).count();
    var lostParts =
        units.stream().flatMap(unit -> unit.getParts().stream()).filter(Part::isLost).count();
    var itemsWorn = units.stream().mapToInt(unit -> unit.getGear().getSlots().size()).sum();
    return new UnitGroup(
        units.size(),
        health(units),
        round(sanity.getAverage()),
        round(sanity.getMin()),
        (int) knownPhobias,
        (int) lostParts,
        itemsWorn);
  }

  private static String health(Collection<? extends BaseUnit> units) {
    var current = units.stream().mapToDouble(BaseUnit::getCurrentHealth).sum();
    var max = units.stream().mapToDouble(BaseUnit::getMaxHealth).sum();
    return Math.round(current) + "/" + Math.round(max);
  }

  private static double round(Double value) {
    return value == null ? 0d : Math.round(value * 10d) / 10d;
  }

  private static double round(double value) {
    return Math.round(value * 10d) / 10d;
  }
}

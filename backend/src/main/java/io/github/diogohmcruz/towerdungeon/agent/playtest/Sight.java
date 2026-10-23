package io.github.diogohmcruz.towerdungeon.agent.playtest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.diogohmcruz.towerdungeon.domain.models.CampaignPhase;
import io.github.diogohmcruz.towerdungeon.domain.models.GameOutcome;
import io.github.diogohmcruz.towerdungeon.domain.models.GameState;
import io.github.diogohmcruz.towerdungeon.domain.models.Unit;
import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/**
 * What a policy may know: the same things a player sees on the brief state and the legal-action
 * list, as numbers. Nothing of the Tower beyond the Floor the Party stands on, and no seed.
 *
 * @param spendable Credits the Vault and the carried Loot can pay with
 * @param pantry the Food the Village holds
 * @param reserves how many wait at home, by type
 * @param unlocked the Unit Types that can be recruited
 * @param floor the Floor the Party stands on (0 when no Expedition is out)
 * @param suppliesLeft Supplies now over Supplies at departure
 * @param partyHealth Party health now over its maximum
 * @param guardianBelowSlain the Floor just below the Party's holds a Guardian that is slain
 * @param shortcuts the Floors an Expedition may start from besides the foot, deepest last
 * @param upgrades the Upgrades the Materials and Relics can pay for now, by id
 * @param expeditions how many Expeditions the Campaign has set out so far
 * @param homeTicks ticks spent at home since the last Expedition (or the start)
 */
public record Sight(
    double spendable,
    double pantry,
    int villagers,
    Map<UnitStats, Integer> reserves,
    int reservesTotal,
    Set<UnitStats> unlocked,
    boolean expedition,
    int floor,
    int highestFloor,
    double suppliesLeft,
    double partyHealth,
    int partySize,
    boolean guardianBelowSlain,
    List<Integer> shortcuts,
    List<String> upgrades,
    int expeditions,
    int homeTicks,
    boolean ended) {

  /**
   * Reads the state; the runner adds nothing the state does not hold but the departure Supplies.
   */
  static Sight of(GameState state, double suppliesAtDeparture, int expeditions, int homeTicks) {
    var reserves = new EnumMap<UnitStats, Integer>(UnitStats.class);
    state.getUnits().forEach((type, list) -> reserves.put(type, list.size()));
    var tower = state.getTower();
    var party = state.getUnitsOnTower().values().stream().flatMap(List::stream).toList();
    var max = party.stream().mapToDouble(Unit::getMaxHealth).sum();
    var health = party.stream().mapToDouble(Unit::getCurrentHealth).sum();
    var floor = state.isExpeditionActive() && tower != null ? tower.getCurrentFloor() : 0;
    var interval = state.getConfig().getBoss().getInterval();
    var below = floor - 1;
    var slain =
        tower != null
            && below >= 1
            && (below % interval == 0)
            && tower.getSlainGuardians().contains(below);
    var unlocked = new java.util.HashSet<UnitStats>();
    for (var type : UnitStats.values()) {
      if (state.isUnitUnlocked(type)) {
        unlocked.add(type);
      }
    }
    return new Sight(
        state.getSpendableCredit(),
        state.getVillage().getFood(),
        state.getVillage().getVillagersCount(),
        reserves,
        reserves.values().stream().mapToInt(Integer::intValue).sum(),
        unlocked,
        state.isExpeditionActive(),
        floor,
        state.getHighestFloor(),
        suppliesAtDeparture <= 0 ? 0d : state.getSupplies() / suppliesAtDeparture,
        max <= 0 ? 0d : health / max,
        party.size(),
        slain,
        state.getShortcuts().stream()
            .filter(s -> s.unlocked())
            .map(s -> s.floor())
            .sorted()
            .toList(),
        affordable(state),
        expeditions,
        homeTicks,
        state.getPhase() == CampaignPhase.OFFER || state.getGameOutcome() != GameOutcome.PLAYING);
  }

  private static List<String> affordable(GameState state) {
    var ids = new java.util.ArrayList<String>();
    for (var offer : state.getAvailableUpgrades()) {
      if (offer.maxed()) {
        continue;
      }
      var pays =
          offer.nextCost().entrySet().stream()
              .allMatch(e -> state.getResources().getOrDefault(e.getKey(), 0d) >= e.getValue());
      if (pays) {
        ids.add(offer.id());
      }
    }
    return List.copyOf(ids);
  }

  int reserve(UnitStats type) {
    return reserves.getOrDefault(type, 0);
  }
}

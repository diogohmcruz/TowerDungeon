package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.List;
import java.util.Map;

import io.github.diogohmcruz.towerdungeon.domain.models.Tome;
import io.github.diogohmcruz.towerdungeon.domain.models.Trade;

/**
 * The Villager pool's assignments and what they have made. The Tillers are not stored: they are the
 * Villagers not assigned elsewhere. Each settling list holds the ticks left for each hand that is
 * still Settling in that Trade.
 *
 * @param knowledge Knowledge in the Stockroom
 * @param quarryHeap Materials cut and not yet delivered
 * @param emergencyTilling whether Emergency Tilling is running (it ends only above its upper mark)
 * @param villagersLost The Count: Villagers lost to Starvation
 * @param reservesLost The Count: Reserves lost to Starvation
 * @param tomes levels bought of each Tome; null in older saves
 * @param postLevels Post upgrades bought for each Trade; null in older saves
 * @param salters Villagers in the Salt Pans
 * @param settlingSalters ticks left for each Salter still Settling; null in older saves
 * @param salt Salt in the Stockroom
 * @param trust Trust held; earned on Extraction only
 * @param ringLevel levels of the Thought-Binding Ring bought
 */
public record TradesSnapshot(
    int breakers,
    int scribes,
    List<Integer> settlingTillers,
    List<Integer> settlingBreakers,
    List<Integer> settlingScribes,
    double knowledge,
    double quarryHeap,
    boolean emergencyTilling,
    int villagersLost,
    int reservesLost,
    Map<Tome, Integer> tomes,
    Map<Trade, Integer> postLevels,
    int salters,
    List<Integer> settlingSalters,
    double salt,
    double trust,
    int ringLevel) {}

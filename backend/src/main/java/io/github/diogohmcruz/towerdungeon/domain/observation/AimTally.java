package io.github.diogohmcruz.towerdungeon.domain.observation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import io.github.diogohmcruz.towerdungeon.domain.services.AimedStrike.Strike;

/**
 * What the aimed strikes of one side came to during one tick, in fixed names and counts: how many
 * were thrown, with which aim, how many parts they reached, how many landed and how many parts were
 * taken off. An agent reads this to see how the Aim rules play out without a line per blow.
 */
public final class AimTally {
  private int strikes;
  private int hits;
  private int misses;
  private int partsLost;
  private final Map<String, Integer> aims = new TreeMap<>();
  private final Map<String, Integer> partsHit = new TreeMap<>();
  private final Map<String, Integer> partsMissed = new TreeMap<>();

  /** Counts one strike; a blow that was not aimed (a heal, or no damage) is not counted. */
  public void add(Strike strike) {
    if (strike.aim() == null) {
      return;
    }
    strikes++;
    aims.merge(strike.aim().name(), 1, Integer::sum);
    for (var outcome : strike.parts()) {
      if (outcome.hit()) {
        hits++;
        partsHit.merge(outcome.part().name(), 1, Integer::sum);
        if (outcome.lost()) {
          partsLost++;
        }
      } else {
        misses++;
        partsMissed.merge(outcome.part().name(), 1, Integer::sum);
      }
    }
  }

  public boolean isEmpty() {
    return strikes == 0;
  }

  /** The event payload for one side ({@code PARTY} or {@code GARRISON}). */
  public Map<String, Object> toEventData(String side) {
    var data = new LinkedHashMap<String, Object>();
    data.put("side", side);
    data.put("strikes", strikes);
    data.put("hits", hits);
    data.put("misses", misses);
    data.put("partsLost", partsLost);
    data.put("aims", Collections.unmodifiableMap(aims));
    data.put("partsHit", Collections.unmodifiableMap(partsHit));
    data.put("partsMissed", Collections.unmodifiableMap(partsMissed));
    return data;
  }
}

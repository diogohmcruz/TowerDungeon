package io.github.diogohmcruz.towerdungeon.config;

import java.util.Map;
import java.util.TreeMap;

/**
 * A smooth number by Floor, given as anchors in config (Floor to value) and read between them. The
 * scaling pass of Floors 6 to 100 sets the garrison budget, the enemy tier window, a Guardian's
 * health and the Supplies drain this way: a handful of anchors on the Guardian Floors and the
 * pinned Floors, never a table of a hundred rows.
 *
 * <p>Between two anchors the value moves geometrically when both are positive (a curve that climbs
 * by a steady ratio per Floor, as a garrison budget does) and linearly otherwise. Below the first
 * anchor and above the last it holds that anchor's value.
 */
public final class FloorCurve {
  private FloorCurve() {}

  /** The curve's value on {@code floor}; 0 when there are no anchors. */
  public static double at(Map<Integer, Double> anchors, int floor) {
    if (anchors == null || anchors.isEmpty()) {
      return 0d;
    }
    var sorted = anchors instanceof TreeMap<Integer, Double> tree ? tree : new TreeMap<>(anchors);
    var below = sorted.floorEntry(floor);
    var above = sorted.ceilingEntry(floor);
    if (below == null) {
      return above.getValue();
    }
    if (above == null || below.getKey().equals(above.getKey())) {
      return below.getValue();
    }
    var share = (double) (floor - below.getKey()) / (above.getKey() - below.getKey());
    var from = below.getValue();
    var to = above.getValue();
    if (from > 0 && to > 0) {
      return from * Math.pow(to / from, share);
    }
    return from + (to - from) * share;
  }
}

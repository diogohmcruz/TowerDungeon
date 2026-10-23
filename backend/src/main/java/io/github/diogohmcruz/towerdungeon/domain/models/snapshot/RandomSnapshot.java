package io.github.diogohmcruz.towerdungeon.domain.models.snapshot;

import java.util.Map;

/**
 * Where a Campaign's random draws stand, so a restored Campaign carries on instead of replaying.
 * Server-side only: the seed is never shown to a client.
 *
 * @param seed the Campaign seed, from which every stream derives
 * @param combatState the position of the combat and lifecycle stream
 * @param cosmeticState the position of the cosmetic stream
 * @param floorVisits how many times each Floor has been built, which keeps a Floor's next Garrison
 *     different from its last yet still fixed by the seed
 */
public record RandomSnapshot(
    long seed, long combatState, long cosmeticState, Map<Integer, Integer> floorVisits) {

  public RandomSnapshot {
    floorVisits = floorVisits == null ? Map.of() : floorVisits;
  }
}

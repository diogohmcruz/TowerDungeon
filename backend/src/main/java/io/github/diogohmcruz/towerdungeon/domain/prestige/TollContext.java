package io.github.diogohmcruz.towerdungeon.domain.prestige;

/**
 * The inputs of the Toll that are not counters. Version 1 passes {@link #NONE}.
 *
 * @param load total Load of the Mutations that took effect (L)
 * @param offering Legacy added by the Offering Stone
 * @param residue the remainder carried over from the previous Toll
 * @param bestGuardians lifetime best of Guardians slain (G_best)
 * @param baseline the inherited Start Floor actually used (B)
 */
public record TollContext(int load, int offering, double residue, int bestGuardians, int baseline) {
  public static final TollContext NONE = new TollContext(0, 0, 0.0, 0, 0);
}

package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * The Voyage under way, as it is saved: the Relics loaded, the goods they return, the connected
 * ticks still to sail, whether the sea will take the cargo (drawn once, at departure), the Quarter
 * it sailed in and the chance it was drawn against.
 */
public record Voyage(
    int relics, VoyageGoods goods, int ticksLeft, boolean lost, Quarter sailedIn, double risk) {

  /** The same Voyage one connected tick on. */
  public Voyage sailed() {
    return new Voyage(relics, goods, ticksLeft - 1, lost, sailedIn, risk);
  }
}

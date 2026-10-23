package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * The one Panacea a Party carries, shared by all its Units: whoever's head or torso first would
 * reach zero is left at {@code saveShare} of that part's maximum, and the Panacea is spent.
 */
public class Panacea {
  private final double saveShare;
  private boolean armed = true;

  public Panacea(double saveShare) {
    this.saveShare = saveShare;
  }

  /** Share of its maximum the saved part is left at. */
  public double saveShare() {
    return saveShare;
  }

  /** Whether it is still unspent. */
  public boolean isArmed() {
    return armed;
  }

  void spend() {
    armed = false;
  }
}

package io.github.diogohmcruz.towerdungeon.domain.services;

/** Lets a test in another package hold the Party of a Campaign, as a reload with one out does. */
public final class HeldPartyAccess {
  private HeldPartyAccess() {}

  public static void hold(GameService service, String campaign) {
    service.slotOf(campaign).hold();
  }
}

package io.github.diogohmcruz.towerdungeon.domain.models;

/**
 * Where a Campaign stands. The game loops tick only an {@link #ACTIVE} Campaign; an ended one waits
 * in {@link #OFFER} (the Succession) until the next Generation begins.
 */
public enum CampaignPhase {
  ACTIVE,
  OFFER
}

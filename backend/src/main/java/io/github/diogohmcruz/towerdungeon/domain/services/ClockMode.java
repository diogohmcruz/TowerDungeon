package io.github.diogohmcruz.towerdungeon.domain.services;

/**
 * Who advances a Campaign. The game has no clock object: its time is the number of ticks run, so
 * this says which {@link TickDriver} may run them.
 */
public enum ClockMode {
  /** The two scheduled loops: a human's Campaign, as ever. */
  REALTIME,
  /** Only its manual driver: a Sandbox Campaign. */
  MANUAL
}

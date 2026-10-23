package io.github.diogohmcruz.towerdungeon.domain.services;

import io.github.diogohmcruz.towerdungeon.domain.models.GameState;

/**
 * Watches a Sandbox Campaign tick by tick, for the balance telemetry. It is called by the manual
 * driver under the Campaign's lock, so it sees a state at rest and must only read it and never
 * block: it must not change the game, so a watched run is the same run as an unwatched one.
 */
public interface TickObserver {
  /** Before a simulated second runs. {@code second} is the one about to run, counted from 1. */
  void beforeTick(GameState state, long second);

  /**
   * After a command changed the Campaign (a Party set out, a Party was Extracted), between ticks.
   * Without it, an Expedition that sets out and comes home with no tick between would go unseen.
   */
  default void afterCommand(GameState state) {}

  /** After the second ran: Attrition, then the lifecycle, then any ending was settled. */
  void afterTick(GameState state, long second);
}

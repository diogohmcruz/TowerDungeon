package io.github.diogohmcruz.towerdungeon.domain.services;

/**
 * What makes a Campaign's time pass. Both drivers run the same two pieces of the game, {@code
 * GameService.attritionTick} and {@code GameService.lifecycleTick}; they differ in who calls them
 * and when.
 *
 * <ul>
 *   <li>{@link ClockMode#REALTIME}: the scheduled {@code gameLoop} (1000 ms) and {@code
 *       invasionTimer} (10 ms) call them for every attached, unheld Campaign.
 *   <li>{@link ClockMode#MANUAL}: {@link ManualTickDriver} calls them for one Sandbox, one
 *       simulated second at a time, and the scheduled loops never touch it.
 * </ul>
 */
interface TickDriver {

  /** The kind of Campaign this driver advances. */
  ClockMode mode();

  /** Whether this driver is the one that advances the slot. */
  default boolean drives(PlayerSlot slot) {
    return slot.clock() == mode();
  }

  /** The driver of human Campaigns: the scheduled loops call into it. */
  TickDriver REALTIME = () -> ClockMode.REALTIME;
}

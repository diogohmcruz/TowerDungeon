package io.github.diogohmcruz.towerdungeon.agent.playtest;

import io.github.diogohmcruz.towerdungeon.domain.models.UnitStats;

/** What a policy wants done next. Each move is one tool call, or one wait. */
public sealed interface Move {
  /** Recruit {@code quantity} Units of a type ({@code td_recruit}). */
  record Recruit(UnitStats type, int quantity) implements Move {}

  /** Sell whole units of Food ({@code td_sell}). */
  record SellFood(int amount) implements Move {}

  /** Pay for the next step of an Upgrade ({@code td_buy_upgrade}). */
  record BuyUpgrade(String id) implements Move {}

  /**
   * Send the whole Reserves up the Tower ({@code td_start_expedition}), from the foot or from the
   * Floor of an open Shortcut.
   */
  record StartExpedition(int startFloor) implements Move {
    public StartExpedition() {
      this(0);
    }
  }

  /** Bring the Party home ({@code td_extract}). */
  record Extract() implements Move {}

  /** Let {@code ticks} simulated seconds pass ({@code td_wait}). */
  record Wait(int ticks) implements Move {}
}

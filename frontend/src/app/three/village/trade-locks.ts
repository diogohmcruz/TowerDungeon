/**
 * Which Trades open with a Village Tier, and the words for a locked one. Pure;
 * shared by the captions and the Annex buildings.
 */
import type { GameState } from '../../interfaces/game-state';
import type { TradeId } from '../../interfaces/village';

/** Trades whose Annex opens with a Village Tier: the level and its name. */
export const TIER_GATES: Partial<
  Record<TradeId, { level: number; name: string; roman: string }>
> = {
  SALTER: { level: 2, name: 'Sheltered', roman: 'II' },
  SPOREWARDEN: { level: 3, name: 'Fortified', roman: 'III' },
};

/** The words for a locked Trade, from the Village Tier the game is at; null when open. */
export function lockOf(state: GameState, trade: string): string | null {
  const gate = TIER_GATES[trade as TradeId];
  const view = state.tradeViews.find((v) => v.trade === trade);
  if (!gate || !view || state.villageTierLevel >= gate.level) return null;
  return (
    `The ${view.workplace} open at Village Tier ${gate.roman}, ${gate.name}. ` +
    `The Village stands at Tier ${state.villageTierLevel}, ${state.villageTierName}.`
  );
}

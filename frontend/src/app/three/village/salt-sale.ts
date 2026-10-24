/**
 * Selling Salt, one action for both places that hold it (architecture doc 5.6):
 * the Vault Annex below Village Tier II, and the Salt Pans from Tier II on.
 * Pure, no Angular: the Annexes and the Vault both import it.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import type { ActionSpec } from './building-registry';

/** What Selling Salt leaves in the Stockroom: it sells every whole Salt. */
export function saltAfterSale(salt: number): number {
  return salt - Math.floor(salt);
}

export function sellSaltAction(visible?: (state: GameState) => boolean): ActionSpec {
  return {
    id: 'sell-salt',
    label: 'Sell Salt',
    ...(visible ? { visible } : {}),
    availability: (s) =>
      s.village.salt >= 1
        ? { usable: true, reason: null }
        : {
            usable: false,
            reason: 'The Stockroom holds less than one Salt. Nothing is sold.',
          },
    preview: (s) =>
      s.village.salt >= 1
        ? `Sells ${Math.floor(s.village.salt)} Salt and leaves ${saltAfterSale(s.village.salt).toFixed(2)} in the Stockroom.`
        : null,
    run: (commands) => commands.send(GameAction.SELL_SALT),
  };
}

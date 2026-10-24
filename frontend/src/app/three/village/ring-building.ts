/**
 * The Thought-Binding Ring (architecture doc 5.6): it hangs on a post in the Fort's
 * stair, and is bound with Trust. Its popover says what the level gives Waves and
 * what the next one costs; the same definition is a section of the list view.
 * Pure: no Angular, no three.js.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import { defineBuilding } from './building-registry';

export const RING_ID = 'RING';

function two(n: number): string {
  return (Math.round(n * 100) / 100).toString();
}

export const RING = defineBuilding({
  id: RING_ID,
  title: 'The Thought-Binding Ring',
  gauges: [
    {
      id: 'level',
      label: 'Ring level',
      value: (s: GameState) => s.ring.level,
      fill: (s: GameState) => s.ring.level / Math.max(1, s.ring.maxLevel),
      leaves: (s: GameState) =>
        `Level ${s.ring.level} of ${s.ring.maxLevel}: Waves climb +${two(s.ring.speedBonus)} Floor/s and carry +${s.ring.capacityBonus}. ` +
        'The Ring holds while its wearers live.',
    },
    {
      id: 'trust',
      label: 'Trust held',
      value: (s: GameState) => s.trustHeld,
      fill: (s: GameState) =>
        s.ring.maxed ? 1 : s.trustHeld / Math.max(1, s.ring.cost),
      leaves: (s: GameState) =>
        s.ring.maxed ? null : `The next level costs ${s.ring.cost} Trust.`,
    },
  ],
  actions: [
    {
      id: 'bind-ring',
      label: (s: GameState) =>
        s.ring.maxed ? 'The Ring is full' : `Bind the Ring, ${s.ring.cost} Trust`,
      availability: (s: GameState) => {
        if (s.ring.maxed) {
          return { usable: false, reason: 'The Ring is bound to its highest level.' };
        }
        return s.ring.blocked
          ? { usable: false, reason: s.ring.blocked }
          : { usable: true, reason: null };
      },
      preview: (s: GameState) =>
        s.ring.maxed || s.ring.blocked
          ? null
          : `Waves climb +${two(s.ring.speedPerLevel)} Floor/s more and carry +${s.ring.capacityPerLevel} more. Costs ${s.ring.cost} Trust.`,
      run: (commands) => commands.send(GameAction.BUY_RING),
    },
  ],
});

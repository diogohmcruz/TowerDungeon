/**
 * The Annexes as buildings (architecture doc 5.6): the Fields, the Quarry, the
 * Scribe's Study and, once open, the Salt Pans and the Spore Cellar. Each one's
 * popover shows its Trade's Villagers, the two assignment steps and what the
 * Trade makes each tick, and refuses in words. The subject id is the Trade id,
 * so the rows of the list view, the scene volumes and the popover are one thing.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import type { TradeId, TradePreview, TradeView } from '../../interfaces/village';
import {
  defineBuilding,
  type ActionSpec,
  type BuildingDefinition,
  type GaugeSpec,
} from './building-registry';
import { saltAfterSale, sellSaltAction } from './salt-sale';
import { lockOf } from './trade-locks';

/** The least number of Tillers that stay in the Fields (an assignment limit). */
export const TILLER_MINIMUM = 5;

export const ANNEX_TRADES: readonly TradeId[] = [
  'TILLER',
  'BREAKER',
  'SCRIBE',
  'SALTER',
  'SPOREWARDEN',
];

function viewOf(state: GameState, trade: TradeId): TradeView | undefined {
  return state.tradeViews.find((v) => v.trade === trade);
}

export function previewOf(
  state: GameState,
  trade: TradeId,
  delta: 1 | -1,
): TradePreview | undefined {
  return state.tradePreviews.find((p) => p.trade === trade && p.delta === delta);
}

function one(value: number): string {
  return (Math.round(value * 10) / 10).toFixed(1);
}

/** The margin a move would leave, in words: what the Pantry would be left with. */
export function marginText(p: TradePreview): string {
  return p.blocked ?? `After this move: ${marginBody(p)}`;
}

/** The figures of a move that can be made, without the lead. */
export function marginBody(p: TradePreview): string {
  const cover = p.fieldsCoverUpkeep
    ? 'Fields cover Upkeep.'
    : `Fields do not cover Upkeep. Pantry holds ${p.pantryHoldSeconds ?? 0} s.`;
  return `Fields +${one(p.fields)}. Upkeep -${one(p.upkeep)}. ${cover}`;
}

/** What the Trade makes each second (a tick), in its own good. */
function outputOf(state: GameState, trade: TradeId): number {
  switch (trade) {
    case 'TILLER':
      return state.village.productionRate;
    case 'BREAKER':
      return state.quarryRate;
    case 'SALTER':
      return state.saltRate;
    case 'SPOREWARDEN':
      return state.sporeRate;
    default:
      return state.studyRate;
  }
}

interface Product {
  label: string;
  stock(state: GameState): number;
  cap(state: GameState): number;
}

const PRODUCT: Record<TradeId, Product> = {
  TILLER: {
    label: 'Food',
    stock: (s) => s.village.food,
    cap: (s) => Math.max(100, s.village.food),
  },
  BREAKER: {
    label: 'Materials in the Heap',
    stock: (s) => s.village.quarryHeap,
    cap: (s) => s.quarryHeapCap,
  },
  SCRIBE: {
    label: 'Knowledge',
    stock: (s) => s.village.knowledge,
    cap: (s) => s.stockroomCap,
  },
  SALTER: {
    label: 'Salt',
    stock: (s) => s.village.salt,
    cap: (s) => s.stockroomCap,
  },
  SPOREWARDEN: {
    label: 'Dragon Spores',
    stock: (s) => s.village.spores,
    cap: (s) => s.stockroomCap,
  },
};

/** What a Trade's good does once made: where it waits, and what selling it leaves. */
const PRODUCT_LEAVES: Partial<Record<TradeId, (state: GameState) => string | null>> = {
  BREAKER: (s) =>
    `The Heap holds up to ${Math.round(s.quarryHeapCap)}. Carried down on Extraction, ${Math.round(s.heapDeliveryPerFloor)} per Floor cleared; a Wipe delivers none.`,
  SALTER: (s) =>
    `The Stockroom holds ${Math.round(s.stockroomCap)} of each good. Selling Salt sells every whole Salt and leaves ${saltAfterSale(s.village.salt).toFixed(2)}.`,
  SPOREWARDEN: (s) =>
    `The Stockroom holds ${Math.round(s.stockroomCap)} of each good. Spores are not sold: they heal.`,
};

function handsGauge(trade: TradeId, plural: string): GaugeSpec {
  return {
    id: 'hands',
    label: plural,
    value: (s) => viewOf(s, trade)?.hands ?? 0,
    fill: (s) => {
      const v = viewOf(s, trade);
      if (!v) return 0;
      const room = v.posts ?? s.village.villagersCount;
      return v.hands / Math.max(1, room);
    },
    leaves: (s) => {
      const v = viewOf(s, trade);
      if (!v) return null;
      const parts: string[] = [];
      if (trade === 'TILLER') {
        parts.push(`At least ${TILLER_MINIMUM} Tillers stay in the Fields.`);
        if (s.village.emergencyTilling) {
          parts.push('Held at the Fields until the Pantry passes 60.');
        }
      } else {
        parts.push(`${v.hands} of ${v.posts} Posts.`);
        if (v.settling > 0) parts.push(`Settling ${v.settlingSeconds} s.`);
        if (trade === 'BREAKER' || trade === 'SCRIBE') {
          parts.push(
            `Highest Floor ${s.highestFloor}: Quarry and Study yield +${Math.round(s.yieldBonus * 100)}%.`,
          );
        }
      }
      return parts.join(' ');
    },
  };
}

function productGauge(trade: TradeId): GaugeSpec {
  const product = PRODUCT[trade];
  return {
    id: 'output',
    label: product.label,
    value: product.stock,
    fill: (s) => product.stock(s) / Math.max(1, product.cap(s)),
    rate: (s) => outputOf(s, trade),
    leaves: PRODUCT_LEAVES[trade],
  };
}

function assign(trade: TradeId, delta: 1 | -1, plural: string): ActionSpec {
  const more = delta === 1;
  return {
    id: more ? 'assign-more' : 'assign-less',
    label: more ? `Assign one more ${plural}` : `Send one ${plural} to the Fields`,
    availability: (s) => {
      const lock = lockOf(s, trade);
      if (lock) return { usable: false, reason: lock };
      const blocked = previewOf(s, trade, delta)?.blocked ?? null;
      return blocked
        ? { usable: false, reason: blocked }
        : { usable: true, reason: null };
    },
    preview: (s) => {
      const p = previewOf(s, trade, delta);
      return p ? marginText(p) : null;
    },
    run: (commands) => commands.send(GameAction.ASSIGN_TRADE, { trade, delta }),
  };
}

const NAMES: Record<TradeId, { title: string; plural: string }> = {
  TILLER: { title: 'The Fields', plural: 'Tillers' },
  BREAKER: { title: 'The Quarry', plural: 'Breakers' },
  SCRIBE: { title: "The Scribe's Study", plural: 'Scribes' },
  SALTER: { title: 'The Salt Pans', plural: 'Salters' },
  SPOREWARDEN: { title: 'The Spore Cellar', plural: 'Sporewardens' },
};

function annex(trade: TradeId): BuildingDefinition {
  const { title, plural } = NAMES[trade];
  return defineBuilding({
    id: trade,
    title,
    // A footing not yet open is not offered (its hover caption still says what opens it).
    visible: (s) => viewOf(s, trade) !== undefined && lockOf(s, trade) === null,
    gauges: [handsGauge(trade, plural), productGauge(trade)],
    // The Fields have no steps of their own: they are what every other Trade gives hands to.
    actions:
      trade === 'TILLER'
        ? []
        : [
            assign(trade, 1, plural),
            assign(trade, -1, plural),
            // Salt is sold where it is made (the Vault sold it before the Pans opened).
            ...(trade === 'SALTER' ? [sellSaltAction()] : []),
          ],
  });
}

export const ANNEXES: readonly BuildingDefinition[] = ANNEX_TRADES.map(annex);

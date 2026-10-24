/**
 * The registered buildings: the one list later steps of 3.0.0 add to (see
 * `building-registry.ts` for how). Each building's figures, gauges and actions
 * live in its definition; the picture draws the gauge, the popover and the list
 * view show the same definition.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import { defineBuilding, type BuildingDefinition } from './building-registry';
import { GATE } from './gate-building';
import { FORT } from './fort-building';
import { SUCCESSION } from './succession-building';
import { ANNEXES } from './annex-buildings';
import { RING } from './ring-building';
import { PORT } from './voyage-building';
import { STONE_OF_COUNT, WALL_OF_NAMES } from './tier-four-records';
import { REFERENCE_PILLAR } from './reference-pillar-building';
import { sellSaltAction } from './salt-sale';
import { UPGRADE_BUILDINGS } from './upgrade-buildings';
import {
  creditsFill,
  SPLIT_TIER,
  litRelicNotches,
  RELIC_NOTCHES,
  siloFill,
  stockFill,
  vaultCrates,
} from './village-layout';

/** What Selling Food leaves in the Pantry: it sells while more than one Food is held. */
export function foodAfterSale(food: number): number {
  return food > 1 ? food - Math.ceil(food - 1) : food;
}

function credits(n: number): string {
  return `${n} Credit${n === 1 ? '' : 's'}`;
}

export const SILO_ID = 'SILO';

/** What the Vault pays for the next Villager: one Credit more than the Villagers held. */
export function nextVillagerPrice(state: GameState): number {
  return state.village.villagersCount + 1;
}

/** How many Villagers one purchase takes in, and what is left: it buys while the Vault can pay. */
export function villagerPurchase(state: GameState): {
  count: number;
  spent: number;
  left: number;
} {
  let n = state.village.villagersCount;
  let credit = state.credit;
  let count = 0;
  while (n < credit) {
    n++;
    credit -= n;
    count++;
  }
  return { count, spent: state.credit - credit, left: credit };
}

/**
 * The Pantry silo, the demonstration building: Tier I shows one gauge on it,
 * Food, and the one action that sells where the number is.
 */
export const SILO = defineBuilding({
  id: SILO_ID,
  title: 'The Pantry silo',
  gauges: [
    {
      id: 'food',
      label: 'Food',
      value: (s: GameState) => s.village.food,
      fill: (s: GameState) => siloFill(s.village.food),
      rate: (s: GameState) => s.netFoodPerSecond,
      leaves: (s: GameState) =>
        s.village.food > 1
          ? `Selling Food sells every whole Food and leaves ${foodAfterSale(s.village.food).toFixed(2)} in the Pantry.`
          : null,
    },
    {
      id: 'upkeep',
      label: 'Upkeep',
      value: (s: GameState) => s.foodUpkeep,
      fill: (s: GameState) =>
        s.foodUpkeep / Math.max(1, s.village.productionRate),
    },
    {
      id: 'pantry-hold',
      label: 'The Pantry holds',
      value: (s: GameState) => s.pantryHoldSeconds ?? Number.POSITIVE_INFINITY,
      format: (v: number) => (Number.isFinite(v) ? `${Math.round(v)} s` : 'for good'),
      fill: (s: GameState) =>
        s.pantryHoldSeconds === null ? 1 : s.pantryHoldSeconds / 300,
    },
    {
      id: 'villagers',
      label: 'Villagers',
      value: (s: GameState) => s.village.villagersCount,
      fill: (s: GameState) => s.credit / Math.max(1, nextVillagerPrice(s)),
      leaves: (s: GameState) =>
        `The next Villager costs ${credits(nextVillagerPrice(s))}. The Vault holds ${Math.floor(s.credit)}.`,
    },
  ],
  actions: [
    {
      id: 'sell-food',
      label: 'Sell Food',
      availability: (s: GameState) =>
        s.village.food > 1
          ? { usable: true, reason: null }
          : {
              usable: false,
              reason: 'The Pantry holds one Food or less. Nothing is sold.',
            },
      run: (commands) => commands.send(GameAction.SELL_FOOD),
    },
    {
      id: 'buy-villager',
      label: 'Take in Villagers',
      availability: (s: GameState) =>
        s.village.villagersCount < s.credit
          ? { usable: true, reason: null }
          : {
              usable: false,
              reason: `The Vault holds ${credits(Math.floor(s.credit))}. The next Villager costs ${nextVillagerPrice(s)}.`,
            },
      preview: (s: GameState) => {
        const p = villagerPurchase(s);
        return p.count === 0
          ? null
          : `Takes in ${p.count} Villager${p.count === 1 ? '' : 's'} for ${Math.round(p.spent)} Credits, the next at ${nextVillagerPrice(s)}, and leaves ${Math.floor(p.left)} in the Vault. Each arrives as a Tiller.`;
      },
      run: (commands) => commands.send(GameAction.BUY_VILLAGERS),
    },
  ],
});

/** The Village Tier the Salt Pans open at (II), and the Spore Cellar (III). */
export const PANS_TIER = 2;
export const CELLAR_TIER = 3;

const tierBelow = (level: number) => (s: GameState) => s.villageTierLevel < level;

function sellAvailability(held: number, empty: string) {
  return held > 0 ? { usable: true, reason: null } : { usable: false, reason: empty };
}

/**
 * The Vault Annex. At Village Tier I it shows what the Vault holds, Credits,
 * Materials and Relics, and sells Materials and Relics (Credits are what a sale
 * makes, so they have no sale of their own). Salt and Spores are shown here only
 * while held and only below the tier that gives them a building: from Tier II
 * Salt is at the Salt Pans (an Annex, `annex-buildings.ts`), from Tier III
 * Spores at the Spore Cellar. At Tier IV the Vault splits into the Strongbox
 * and the Reliquary: split `gauges` and `actions` there.
 */
export const VAULT = defineBuilding({
  id: 'VAULT',
  title: 'The Vault Annex',
  // At Tier IV it is the Strongbox and the Reliquary, and the Materials are at the Quarry's Heap.
  visible: tierBelow(SPLIT_TIER),
  gauges: [
    {
      id: 'credits',
      label: 'Credits',
      value: (s: GameState) => s.credit,
      fill: (s: GameState) => creditsFill(s.credit),
    },
    {
      id: 'materials',
      label: 'Materials',
      value: (s: GameState) => s.resources.MATERIALS,
      fill: (s: GameState) => vaultCrates(s.resources.MATERIALS, 0) / 12,
      leaves: (s: GameState) =>
        s.resources.MATERIALS > 0
          ? 'Selling Materials sells all of them and leaves none in the Vault.'
          : null,
    },
    {
      id: 'relics',
      label: 'Relics',
      value: (s: GameState) => s.resources.RELICS,
      fill: (s: GameState) => litRelicNotches(s.resources.RELICS) / RELIC_NOTCHES,
      leaves: (s: GameState) =>
        s.resources.RELICS > 0
          ? 'Selling Relics sells all of them and leaves none in the Vault.'
          : null,
    },
    // Salt lived here before the Pans; Spores before the Cellar.
    {
      id: 'salt',
      visible: (s: GameState) => tierBelow(PANS_TIER)(s) && s.village.salt > 0,
      label: 'Salt',
      value: (s: GameState) => s.village.salt,
      fill: (s: GameState) => stockFill(s.village.salt),
      leaves: (s: GameState) =>
        `Selling Salt sells every whole Salt and leaves ${(s.village.salt % 1).toFixed(2)}.`,
    },
    {
      id: 'spores',
      visible: (s: GameState) => tierBelow(CELLAR_TIER)(s) && s.village.spores > 0,
      label: 'Dragon Spores',
      value: (s: GameState) => s.village.spores,
      fill: (s: GameState) => stockFill(s.village.spores),
    },
  ],
  actions: [
    {
      id: 'sell-materials',
      label: 'Sell Materials',
      availability: (s: GameState) =>
        sellAvailability(s.resources.MATERIALS, 'The Vault holds no Materials. Nothing is sold.'),
      run: (commands) => commands.send(GameAction.SELL_MATERIALS),
    },
    {
      id: 'sell-relics',
      label: 'Sell Relics',
      availability: (s: GameState) =>
        sellAvailability(s.resources.RELICS, 'The Vault holds no Relics. Nothing is sold.'),
      run: (commands) => commands.send(GameAction.SELL_RELICS),
    },
    sellSaltAction((s) => tierBelow(PANS_TIER)(s) && s.village.salt > 0),
  ],
});

/** The words for a Tier IV building not yet open; null once the Village stands at Tier IV. */
function splitLock(what: string) {
  return (s: GameState): string | null =>
    s.villageTierLevel >= SPLIT_TIER
      ? null
      : `${what} opens at Village Tier IV, when the Vault splits. ` +
        `The Village stands at Tier ${s.villageTierLevel}, ${s.villageTierName}.`;
}

/**
 * The Strongbox (Village Tier IV): Credits only. A sale makes Credits, so it has
 * no sale of its own: it shows the figure and sells nothing.
 */
export const STRONGBOX = defineBuilding({
  id: 'STRONGBOX',
  title: 'The Strongbox',
  visible: (s: GameState) => s.villageTierLevel >= SPLIT_TIER,
  lock: splitLock('The Strongbox'),
  gauges: [
    {
      id: 'credits',
      label: 'Credits',
      value: (s: GameState) => s.credit,
      fill: (s: GameState) => creditsFill(s.credit),
      leaves: () => 'Credits are what a sale makes. The Strongbox sells nothing.',
    },
  ],
  actions: [],
});

/** The Reliquary (Village Tier IV): Relics only, and the sale of them. */
export const RELIQUARY = defineBuilding({
  id: 'RELIQUARY',
  title: 'The Reliquary',
  visible: (s: GameState) => s.villageTierLevel >= SPLIT_TIER,
  lock: splitLock('The Reliquary'),
  gauges: [
    {
      id: 'relics',
      label: 'Relics',
      value: (s: GameState) => s.resources.RELICS,
      fill: (s: GameState) => litRelicNotches(s.resources.RELICS) / RELIC_NOTCHES,
      leaves: (s: GameState) =>
        s.resources.RELICS > 0
          ? 'Selling Relics sells all of them and leaves none in the Reliquary.'
          : null,
    },
  ],
  actions: [
    {
      id: 'sell-relics',
      label: 'Sell Relics',
      availability: (s: GameState) =>
        sellAvailability(s.resources.RELICS, 'The Reliquary holds no Relics. Nothing is sold.'),
      run: (commands) => commands.send(GameAction.SELL_RELICS),
    },
  ],
});

/**
 * The Quarry's Heap building (Village Tier IV): the Materials the Vault held,
 * and their sale. (The Quarry Annex itself shows the Materials still in the
 * Heap, not yet carried down.)
 */
export const QUARRY_HEAP = defineBuilding({
  id: 'HEAP',
  title: "The Quarry's Heap",
  visible: (s: GameState) => s.villageTierLevel >= SPLIT_TIER,
  lock: splitLock("The Quarry's Heap building"),
  gauges: [
    {
      id: 'materials',
      label: 'Materials',
      value: (s: GameState) => s.resources.MATERIALS,
      fill: (s: GameState) => vaultCrates(s.resources.MATERIALS, 0) / 12,
      leaves: (s: GameState) =>
        s.resources.MATERIALS > 0
          ? "Selling Materials sells all of them and leaves none at the Quarry's Heap."
          : null,
    },
  ],
  actions: [
    {
      id: 'sell-materials',
      label: 'Sell Materials',
      availability: (s: GameState) =>
        sellAvailability(
          s.resources.MATERIALS,
          "The Quarry's Heap holds no Materials. Nothing is sold.",
        ),
      run: (commands) => commands.send(GameAction.SELL_MATERIALS),
    },
  ],
});

/** Every registered building, in the order the list view shows them. */
export const BUILDINGS: readonly BuildingDefinition[] = [
  SILO,
  VAULT,
  STRONGBOX,
  RELIQUARY,
  ...ANNEXES,
  QUARRY_HEAP,
  GATE,
  FORT,
  REFERENCE_PILLAR,
  STONE_OF_COUNT,
  WALL_OF_NAMES,
  SUCCESSION,
  RING,
  PORT,
  ...UPGRADE_BUILDINGS,
];

export function buildingDefinition(
  id: string,
  registry: readonly BuildingDefinition[] = BUILDINGS,
): BuildingDefinition | undefined {
  return registry.find((d) => d.id === id);
}

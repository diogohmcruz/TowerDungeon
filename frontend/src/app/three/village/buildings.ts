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
import { ANNEXES } from './annex-buildings';
import { REFERENCE_PILLAR } from './reference-pillar-building';
import { siloFill } from './village-layout';

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

/** Every registered building, in the order the list view shows them. */
export const BUILDINGS: readonly BuildingDefinition[] = [SILO, ...ANNEXES, GATE, FORT, REFERENCE_PILLAR];

export function buildingDefinition(
  id: string,
  registry: readonly BuildingDefinition[] = BUILDINGS,
): BuildingDefinition | undefined {
  return registry.find((d) => d.id === id);
}

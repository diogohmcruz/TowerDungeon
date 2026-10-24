/**
 * The registered buildings: the one list later steps of 3.0.0 add to (see
 * `building-registry.ts` for how). Each building's figures, gauges and actions
 * live in its definition; the picture draws the gauge, the popover and the list
 * view show the same definition.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import { defineBuilding, type BuildingDefinition } from './building-registry';
import { siloFill } from './village-layout';

/** What Selling Food leaves in the Pantry: it sells while more than one Food is held. */
export function foodAfterSale(food: number): number {
  return food > 1 ? food - Math.ceil(food - 1) : food;
}

export const SILO_ID = 'SILO';

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
  ],
});

/** Every registered building, in the order the list view shows them. */
export const BUILDINGS: readonly BuildingDefinition[] = [SILO];

export function buildingDefinition(
  id: string,
  registry: readonly BuildingDefinition[] = BUILDINGS,
): BuildingDefinition | undefined {
  return registry.find((d) => d.id === id);
}

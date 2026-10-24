/**
 * What the Stock card and its neighbours said that no building states: the
 * Year, the Food's flows, the Stockroom, the count of the lost, the Village Tier,
 * the Tally of the Campaign, the Recovery of the wounded and the Waves climbing.
 * The list view writes them as plain lines (architecture doc 5.6), so no number
 * is left with only the card layer that used to carry it. Pure.
 */
import type { GameState, Reinforcement } from '../../interfaces/game-state';

export interface FactGroup {
  id: string;
  heading: string;
  lines: string[];
}

const QUARTERS: Record<string, string> = {
  SPRING: 'Spring',
  SUMMER: 'Summer',
  AUTUMN: 'Autumn',
  WINTER: 'Winter',
};

function whole(n: number | undefined): string {
  return Math.round(n ?? 0).toLocaleString('en-US');
}

function one(n: number | undefined): string {
  return (n ?? 0).toLocaleString('en-US', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });
}

/** How many Units a Reinforcement Wave holds. */
export function waveSize(wave: Reinforcement): number {
  return Object.values(wave.units ?? {}).reduce(
    (total, members) => total + (members?.length ?? 0),
    0,
  );
}

/** The Year and its Quarter in words; empty in a Trial. */
export function yearLine(s: GameState): string {
  if (!s.quarter || s.year == null) return '';
  const next = s.nextQuarter ? `, ${QUARTERS[s.nextQuarter]} follows` : '';
  return `Year ${s.year}, ${QUARTERS[s.quarter]}${next}`;
}

/** The danger at sea in words, once the Port has a footing. */
export function seaLine(s: GameState): string {
  if (s.seaRisk == null || !s.port || s.port.state === 'HIDDEN') return '';
  return `Danger at sea ${Math.round(s.seaRisk * 100)}%`;
}

export function villageFacts(s: GameState): FactGroup[] {
  const groups: FactGroup[] = [];
  const time = [yearLine(s), seaLine(s)].filter((l) => l !== '');
  if (time.length > 0) groups.push({ id: 'time', heading: 'The year', lines: time });

  groups.push({
    id: 'food',
    heading: 'Food',
    lines: [
      `Fields +${one(s.village.productionRate)}, Upkeep (${s.homeUnitCount} Reserves) -${one(s.foodUpkeep)}, Recovery -${one(s.recoverySpend)} Food/s.`,
      s.pantryHoldSeconds === null
        ? 'The Pantry holds. Fields cover Upkeep.'
        : `The Pantry holds ${s.pantryHoldSeconds} s at current rates.`,
      ...(s.homePartyWounds > 0
        ? [
            `Recovery: ${whole(s.homePartyWounds)} hp to heal, about ${whole(s.foodHealRate)} hp/s from the Pantry.`,
          ]
        : []),
      ...(s.expeditionActive
        ? ['Supplies are drawn from the Pantry. Extraction returns what is left. Wounded Reserves then Recover, paid for from the Pantry.']
        : s.lastFoodReturned > 0
          ? [`Extraction returned ${whole(s.lastFoodReturned)} Food to the Pantry.`]
          : []),
    ],
  });

  groups.push({
    id: 'stock',
    heading: 'The Stockroom and the Village',
    lines: [
      `Stockroom: Knowledge ${one(s.village.knowledge)} of ${whole(s.stockroomCap)}. Credits, Materials, Relics, Salt and Spores are on their buildings.`,
      `Village Tier ${s.villageTierLevel}, ${s.villageTierName}. Open: ${s.villageTierOpens}.`,
      `Lost to Starvation: ${s.theCount.villagers} Villagers, ${s.theCount.reserves} Reserves (this Campaign).`,
      `Tally (this Campaign): Food grown ${whole(s.tally.foodGrown)}, Credits banked ${whole(s.tally.creditsBanked)}, Materials banked ${whole(s.tally.materialsBanked)}, Relics banked ${whole(s.tally.relicsBanked)}, Floors cleared ${whole(s.tally.floorsCleared)}, peak Villagers ${whole(s.tally.peakVillagers)}.`,
      `${whole(s.enemiesDefeated)} enemies slain, ${whole(s.expeditionsCompleted)} Expeditions completed.`,
    ],
  });

  if (s.expeditionActive && s.reinforcements.length > 0) {
    groups.push({
      id: 'waves',
      heading: 'Reinforcement Waves climbing',
      lines: s.reinforcements.map(
        (w) =>
          `Wave of ${waveSize(w)}, Floor ${whole(w.currentFloor)} of ${s.tower?.currentFloor ?? 0}, carrying ${whole(w.supplies)} supplies.`,
      ),
    });
  }
  return groups;
}

/**
 * The Fort (architecture doc 5.6): the Reserves' yard, where Units are hired. Its
 * popover is the hire list the unit cards were, and its hover gives the Reserves
 * count and how many are wounded. The same definition is a section of the list
 * view, so hiring never needs a pointer. Pure: no Angular, no three.js.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import { Unit } from '../../interfaces/unit.enum';
import type { ActionSpec } from './building-registry';
import { defineBuilding } from './building-registry';
import { MAX_RESERVE_FIGURES } from './village-layout';

export const FORT_ID = 'FORT';

/** The milestones that bring a companion, by the Unit Type each one opens. */
const MILESTONE_UNITS: Readonly<Record<string, string>> = {
  ELIQUO_JOINS: Unit.CAPTAIN,
  SARGAN_JOINS: Unit.MAGE,
};

/** Reserves held at the Fort: every Unit at home, as the unit cards counted them. */
export function reserveTotal(state: GameState): number {
  return Object.values(state.units ?? {}).reduce(
    (n, list) => n + (list?.length ?? 0),
    0,
  );
}

/** Reserves that are hurt: below full health, or a body part lost. */
export function woundedTotal(state: GameState): number {
  let n = 0;
  for (const list of Object.values(state.units ?? {})) {
    for (const m of list ?? []) {
      const hurt =
        (m.maxHealth !== undefined && m.currentHealth < m.maxHealth) ||
        (m.parts ?? []).some((p) => p.lost);
      if (hurt) n++;
    }
  }
  return n;
}

export function isRecruitable(state: GameState, type: string): boolean {
  return (state.unlockedUnitTypes ?? []).includes(type);
}

/**
 * What holds a Type shut, in words: an Upgrade that recruits it or a Milestone that
 * brings it. Null when it is open, or when nothing in view names a gate.
 */
export function gateOf(state: GameState, type: string): string | null {
  if (isRecruitable(state, type)) return null;
  const upgrade = (state.availableUpgrades ?? []).find(
    (u) => u.unlockUnit === type,
  );
  if (upgrade) {
    const cost = [
      upgrade.nextCost.MATERIALS ? `${upgrade.nextCost.MATERIALS} Materials` : '',
      upgrade.nextCost.RELICS ? `${upgrade.nextCost.RELICS} Relics` : '',
    ]
      .filter((t) => t)
      .join(' and ');
    return `Locked. The Upgrade "${upgrade.name}" opens it${cost ? `, for ${cost}` : ''}.`;
  }
  const milestone = (state.milestones ?? []).find(
    (m) => MILESTONE_UNITS[m.id] === type && !m.achieved,
  );
  if (milestone) {
    return `Locked. The Milestone "${milestone.name}" brings it: ${milestone.trigger.toLowerCase()}.`;
  }
  return null;
}

function costOf(state: GameState, type: string): number | null {
  const cost = state.unitCatalog?.[type]?.cost;
  return typeof cost === 'number' ? cost : null;
}

function name(type: string): string {
  return type.toLowerCase().replace(/_/g, ' ');
}

function heldOf(state: GameState, type: string): number {
  return state.units?.[type as Unit]?.length ?? 0;
}

function hire(type: string): ActionSpec {
  return {
    id: `hire-${type}`,
    label: (s: GameState) => {
      const cost = costOf(s, type);
      const price = cost === null ? '' : `, ${cost} Credits`;
      return `Recruit ${name(type)}${price} (${heldOf(s, type)} in the Reserves)`;
    },
    visible: (s: GameState) => isRecruitable(s, type) || gateOf(s, type) !== null,
    availability: (s: GameState) => {
      const gate = gateOf(s, type);
      if (gate) return { usable: false, reason: gate };
      const cost = costOf(s, type);
      // The Fort takes a Unit only when the Credits exceed its cost.
      if (cost !== null && !(s.credit > cost)) {
        return {
          usable: false,
          reason: `The Credits must exceed the cost. A ${name(type)} costs ${cost} and ${Math.floor(s.credit)} are held.`,
        };
      }
      return { usable: true, reason: null };
    },
    run: (commands) =>
      commands.send(GameAction.BUY, { unitStats: type, quantity: 1 }),
  };
}

export const FORT = defineBuilding({
  id: FORT_ID,
  title: 'The Fort',
  gauges: [
    {
      id: 'reserves',
      label: 'Reserves',
      value: reserveTotal,
      fill: (s: GameState) => reserveTotal(s) / MAX_RESERVE_FIGURES,
    },
    {
      id: 'wounded',
      label: 'Wounded',
      value: woundedTotal,
      fill: (s: GameState) => {
        const all = reserveTotal(s);
        return all === 0 ? 0 : woundedTotal(s) / all;
      },
    },
  ],
  actions: Object.values(Unit).map(hire),
});

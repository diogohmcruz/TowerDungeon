/**
 * The reference pillar as a registered building (see `building-registry.ts`).
 * At Tiers I to III it shows the Highest Floor, the Milestones and the Toll with
 * Exodus. Each piece is its own export so that at Tier IV the Toll gauge, the
 * Toll lines and Exodus move onto a Stone of Count and the pillar keeps the rest.
 *
 * Only the Highest Floor and the Milestones are on the hover caption; the Toll
 * and Exodus open on a click.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type {
  GameState,
  MilestoneOffer,
  TollForecast,
} from '../../interfaces/game-state';
import {
  defineBuilding,
  type ActionSpec,
  type GaugeSpec,
  type LineSpec,
} from './building-registry';
import { PILLAR_FLOORS, PILLAR_ID, type PillarInput } from './reference-pillar';

/** What the picture carves, from the game's state. */
export function pillarInput(s: GameState): PillarInput {
  const milestones = s.milestones ?? [];
  return {
    highestFloor: s.highestFloor ?? 0,
    milestonesEarned: milestones.filter((m) => m.achieved).length,
    milestonesTotal: milestones.length,
    tollShare: tollShare(s.forecast),
  };
}

/** Share of the way from the last whole point of Legacy to the next, 0..1; 0 with no forecast. */
export function tollShare(forecast: TollForecast | null | undefined): number {
  if (!forecast || forecast.pointsDivisor <= 0) return 0;
  const { toll, pointsDivisor, nextLegacyAt } = forecast;
  const share = (toll.toll - (nextLegacyAt - pointsDivisor)) / pointsDivisor;
  return Math.min(1, Math.max(0, share));
}

const oneDecimal = (n: number) =>
  n.toLocaleString('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export const HIGHEST_FLOOR_GAUGE: GaugeSpec = {
  id: 'highest-floor',
  label: 'Highest Floor',
  value: (s) => s.highestFloor ?? 0,
  fill: (s) => (s.highestFloor ?? 0) / PILLAR_FLOORS,
};

export const MILESTONES_GAUGE: GaugeSpec = {
  id: 'milestones',
  label: 'Milestones',
  value: (s) => (s.milestones ?? []).filter((m) => m.achieved).length,
  figure: (s) => {
    const all = s.milestones ?? [];
    return `${all.filter((m) => m.achieved).length} of ${all.length}`;
  },
  fill: (s) => {
    const all = s.milestones ?? [];
    return all.length ? all.filter((m) => m.achieved).length / all.length : 0;
  },
};

export const TOLL_GAUGE: GaugeSpec = {
  id: 'toll',
  label: 'Toll',
  caption: false,
  value: (s) => s.forecast?.toll.toll ?? 0,
  format: oneDecimal,
  fill: (s) => tollShare(s.forecast),
  leaves: (s) =>
    s.forecast
      ? `If you left now: about ${s.forecast.toll.legacy} Legacy. The next Legacy is at ${oneDecimal(s.forecast.nextLegacyAt)}.`
      : null,
};

/** What a Milestone says it is: when earned, how; when not, what earns it. */
export function milestoneStatus(m: MilestoneOffer): string {
  if (!m.achieved) return m.trigger;
  return m.companion ? 'Joined' : 'Earned';
}

export const MILESTONE_LINES = (s: GameState): LineSpec[] =>
  (s.milestones ?? []).map((m) => ({
    id: `milestone-${m.id}`,
    group: 'Milestones',
    label: m.name,
    value: milestoneStatus(m),
    detail: m.description,
  }));

export const TOLL_LINES = (s: GameState): LineSpec[] => {
  const f = s.forecast;
  if (!f) return [];
  const t = f.toll;
  const counters: [string, number][] = [
    ['Food grown', t.foodMarks],
    ['Materials banked', t.materialsMarks],
    ['Credits banked', t.creditsMarks],
    ['Relics banked', t.relicsMarks],
    ['Expeditions completed', t.expeditionsMarks],
    ['Ascent', t.ascentMarks],
    ['Guardians slain', t.guardianMarks],
  ];
  return [
    ...counters.map(([label, marks]) => ({
      id: `toll-${label.toLowerCase().replace(/ /g, '-')}`,
      group: 'Toll',
      label,
      value: oneDecimal(marks),
    })),
    { id: 'toll-new-ground', group: 'Toll', label: 'New Ground', value: `+${t.newGround}` },
    {
      id: 'toll-residue',
      group: 'Toll',
      label: 'Residue carried on',
      value: oneDecimal(t.residueNext),
    },
  ];
};

/** Why Exodus is closed, in words; null while it is open. */
export function exodusReason(s: GameState): string | null {
  const f = s.forecast;
  if (!f) return 'The Campaign has ended. There is nothing to leave.';
  if (f.canExodus) return null;
  return f.toll.legacy < f.exodusMinimum
    ? `Exodus opens at ${f.exodusMinimum} Legacy.`
    : 'Extract first. Exodus cannot leave a Party on the Tower.';
}

export const EXODUS_ACTION: ActionSpec = {
  id: 'exodus',
  label: 'Exodus',
  availability: (s) => {
    const reason = exodusReason(s);
    return reason === null ? { usable: true, reason: null } : { usable: false, reason };
  },
  confirm: (s) => ({
    prompt: `The village leaves in the night. The Tower keeps whoever stayed. The Campaign ends and pays about ${s.forecast?.toll.legacy ?? 0} Legacy.`,
    confirmLabel: 'Leave',
    cancelLabel: 'Stay',
  }),
  run: (commands) => commands.send(GameAction.EXODUS, null),
};

/** From this Village Tier the Toll and Exodus stand on the Stone of Count, not the pillar. */
export const STONE_TIER = 4;
const tollOnPillar = (s: GameState) => s.villageTierLevel < STONE_TIER;

export const REFERENCE_PILLAR = defineBuilding({
  id: PILLAR_ID,
  title: 'The reference pillar',
  gauges: [HIGHEST_FLOOR_GAUGE, MILESTONES_GAUGE, { ...TOLL_GAUGE, visible: tollOnPillar }],
  lines: (s) => [...MILESTONE_LINES(s), ...(tollOnPillar(s) ? TOLL_LINES(s) : [])],
  actions: [{ ...EXODUS_ACTION, visible: tollOnPillar }],
});

/**
 * The short caption of a Village subject: its name, whether it works, what it
 * makes. A locked footing states its real unlock condition in words. Pure.
 *
 * `CAPTION_BUILDERS` is the extension point: a new kind adds a builder. A
 * Shortcut stays hidden until open, so its builder answers null for a locked one.
 */
import type { GameState } from '../../interfaces/game-state';
import type { TradeId, TradeView } from '../../interfaces/village';
import {
  bellAvailability,
  bellCommand,
  extractAvailability,
} from '../launch/launch-controls';
import { buildingDefinition, BUILDINGS } from './buildings';
import { marginBody, previewOf } from './annex-buildings';
import { lockOf } from './trade-locks';
import { buildingView, gaugeView, isVisible } from './building-registry';
import type { SubjectKind, VillageSubject } from './village-subjects';

export interface VillageCaption {
  name: string;
  /** Whether it works, or why it does not. */
  status: string;
  /** What it makes; empty when it makes nothing. */
  makes: string;
  /** Set when the footing is locked: what opens it. */
  lock: string | null;
  /** A building's rate, "Food +1.20/s"; read like `makes`. */
  rates?: string;
  /** What spending would leave, in one sentence (a building's gauges declare it). */
  leaves?: string;
}

/** What the captions need besides the frame: the Start Floor the panel and the picture share. */
export interface CaptionContext {
  startFloor: number;
}

type CaptionBuilder = (
  subject: VillageSubject,
  state: GameState,
  context: CaptionContext,
) => VillageCaption | null;

export { lockOf };

function viewOf(state: GameState, trade: string): TradeView | undefined {
  return state.tradeViews.find((v) => v.trade === trade);
}

function rate(value: number): string {
  return (Math.round(value * 100) / 100).toFixed(2);
}

function makesOf(state: GameState, trade: string): string {
  switch (trade) {
    case 'TILLER':
      return `Food, +${rate(state.village.productionRate)}/s`;
    case 'BREAKER':
      return `Materials into the Heap, +${rate(state.quarryRate)}/s`;
    case 'SALTER':
      return `Salt, +${rate(state.saltRate)}/s`;
    case 'SPOREWARDEN':
      return `Dragon Spores, +${rate(state.sporeRate)}/s`;
    default:
      return `Knowledge, +${rate(state.studyRate)}/s`;
  }
}

/** The Pantry margin one more hand here would leave (the Fields have no such move). */
function marginLine(state: GameState, trade: TradeId): { leaves?: string } {
  const p = previewOf(state, trade, 1);
  return p && !p.blocked ? { leaves: `With one more here: ${marginBody(p)}` } : {};
}

const annex: CaptionBuilder = (subject, state) => {
  const view = viewOf(state, subject.id);
  if (!view) return null;
  const lock = lockOf(state, subject.id);
  if (lock) {
    return { name: view.workplace, status: 'Closed.', makes: '', lock };
  }
  const posts = view.posts === null ? '' : ` of ${view.posts} Posts`;
  const works =
    view.hands > 0
      ? `Working: ${view.hands} ${view.plural}${posts}.`
      : 'Idle: no hands.';
  // The good the Trade makes, held now: the exact figure and what selling or carrying it leaves.
  const def = buildingDefinition(subject.id);
  const held = ['BREAKER', 'SALTER', 'SPOREWARDEN'].includes(subject.id)
    ? def?.gauges.find((g) => g.id === 'output')
    : undefined;
  const stored = held ? gaugeView(held, state) : null;
  const margin = marginLine(state, subject.id as TradeId).leaves;
  const leaves = [stored?.leaves, margin].filter((t): t is string => !!t).join(' ');
  return {
    name: view.workplace,
    status: stored ? `${works} ${stored.label} held ${stored.figure}.` : works,
    makes: view.hands > 0 ? makesOf(state, subject.id) : '',
    lock: null,
    ...(leaves ? { leaves } : {}),
  };
};

const villagers: CaptionBuilder = (subject, state) => {
  const view = viewOf(state, subject.id);
  if (!view) return null;
  const lock = lockOf(state, subject.id);
  if (lock) {
    return {
      name: view.plural,
      status: 'None. The place is closed.',
      makes: '',
      lock,
    };
  }
  return {
    name: view.plural,
    status:
      view.hands > 0
        ? `${view.hands} at the ${view.workplace}.`
        : `None at the ${view.workplace}.`,
    makes: view.hands > 0 ? makesOf(state, subject.id) : '',
    lock: null,
  };
};

const upgrade: CaptionBuilder = (subject, state) => {
  const view = state.postUpgrades.find((u) => u.trade === subject.id);
  if (!view) return null;
  return {
    name: `Posts of the ${view.workplace}`,
    status: `${view.posts} Posts, ${view.nextPosts} after the next.`,
    makes: '',
    lock: view.blocked,
  };
};

/** Time left in plain words; one lifecycle tick is one second. */
function inWords(ticks: number): string {
  if (ticks < 60) return 'less than a minute';
  const minutes = Math.ceil(ticks / 60);
  return minutes === 1 ? 'about a minute' : `about ${minutes} minutes`;
}

function boatStatus(state: GameState): string {
  const boat = state.boat;
  if (boat?.state === 'DOCKED') return 'Finished. A Boat lies at the pier.';
  if (boat?.state === 'DUE') {
    return `Finished. The Boat is due, in ${inWords(boat.ticksToDock)}.`;
  }
  return 'Finished. The pier stands over the dark water.';
}

/**
 * The Port: the footing says what finishes it, from the frame's Port state; the
 * finished Port says what stands. Hidden, it has no volumes to point at.
 */
const port: CaptionBuilder = (_subject, state) => {
  const p = state.port;
  if (!p || p.state === 'HIDDEN') return null;
  if (p.state === 'BUILT') {
    return {
      name: 'The Port',
      status: boatStatus(state),
      makes: '',
      lock: null,
    };
  }
  return {
    name: 'The Port',
    status: 'A footing and a short pier. Unfinished.',
    makes: '',
    lock:
      `Finishing it costs ${p.materialsCost} Materials and ${p.creditsCost} Credits ` +
      `from the Vault. The footing opened at ${p.footingExpeditions} completed ` +
      `Expeditions; ${p.expeditionsCompleted} are behind the Village.`,
  };
};

/**
 * A Shortcut mark: only an open Shortcut has one. Says what stands there in words,
 * and names the Start Floor, which the mark itself never shows.
 */
const shortcut: CaptionBuilder = (subject, state, context) => {
  const sc = (state.shortcuts ?? []).find((s) => s.id === subject.id);
  if (!sc || !sc.unlocked) return null;
  const chosen = context.startFloor === sc.floor;
  return {
    name: sc.name,
    status: 'A Shortcut stands open. An Expedition can start from here.',
    makes: chosen ? `Start Floor ${sc.floor}. Chosen` : `Start Floor ${sc.floor}`,
    lock: state.expeditionActive ? CHANGE_REFUSED : null,
  };
};

const CHANGE_REFUSED = 'An Expedition is out. The Start Floor cannot change.';

/** The Floor an Expedition begins on: the Base door starts at Floor 1. */
export function startFloorLabel(startFloor: number): number {
  return startFloor > 0 ? startFloor : 1;
}

/** The Base door: the default Start Floor. */
const baseDoor: CaptionBuilder = (_subject, state, context) => ({
  name: 'The Base door',
  status:
    context.startFloor > 0
      ? 'An Expedition can start here, at the foot of the Tower.'
      : 'Chosen. An Expedition starts here, at the foot of the Tower.',
  makes: 'Start Floor 1',
  lock: state.expeditionActive ? CHANGE_REFUSED : null,
});

const whole = (n: number) => Math.floor(n).toLocaleString('en-US');

/**
 * The gate bell. While no Expedition is out it sets the Party out: its caption
 * says who goes, from where, what they carry and what the Pantry holds after.
 * While one is out it sends a Reinforcement Wave. When it cannot be rung, it is
 * dark and the caption says why.
 */
const bell: CaptionBuilder = (_subject, state, context) => {
  const can = bellAvailability(state);
  if (bellCommand(state) === 'wave') {
    return {
      name: 'The bell',
      status: can.usable
        ? 'Ring it to send every Reserve up after the Party.'
        : 'The bell hangs dark.',
      makes: can.usable
        ? `${state.homeUnitCount} Reserves climb from the Base, carrying supplies from the Pantry`
        : '',
      lock: can.reason,
    };
  }
  const plan = state.loadout;
  return {
    name: 'The gate bell',
    status: can.usable
      ? 'Ring it to set the Party out.'
      : 'The bell hangs dark.',
    makes: can.usable
      ? `Party ${plan.partySize}. Start Floor ${startFloorLabel(context.startFloor)}. ` +
        `Supplies carried ${whole(plan.suppliesTaken)}. ` +
        `The Pantry holds ${whole(plan.pantryAfter)} Food after leaving`
      : '',
    lock: can.reason,
  };
};

/** The recall lamp: lighting it calls the Party home. */
const recall: CaptionBuilder = (_subject, state) => {
  const can = extractAvailability(state);
  return {
    name: 'The recall lamp',
    status: can.usable
      ? 'Light it to call the Party home.'
      : 'The lamp stands dark.',
    makes: can.usable
      ? 'The survivors come home, leftover supplies return to the Pantry and carried loot is banked'
      : '',
    lock: can.reason,
  };
};

/**
 * A registered building (`building-registry.ts`): its gauges state the exact
 * figure and its rate, and say what spending would leave. The same text
 * everywhere a figure is read, so the scene, the popover and the list agree.
 */
const building: CaptionBuilder = (subject, state) => {
  const def = buildingDefinition(subject.id, BUILDINGS);
  if (!def || !isVisible(def, state)) return null;
  const view = buildingView(def, state);
  const shown = view.gauges.filter((g) => g.caption);
  const leaves = shown.map((g) => g.leaves).filter((t): t is string => !!t);
  const rates = shown
    .filter((g) => g.rate !== null)
    .map((g) => `${g.label} ${g.rate}`);
  return {
    name: view.title,
    status: shown.map((g) => `${g.label} ${g.figure}`).join(', ') + '.',
    makes: '',
    lock: null,
    ...(rates.length ? { rates: rates.join(', ') } : {}),
    ...(leaves.length ? { leaves: leaves.join(' ') } : {}),
  };
};

export const CAPTION_BUILDERS: Partial<Record<SubjectKind, CaptionBuilder>> = {
  building,
  annex,
  villagers,
  upgrade,
  port,
  shortcut,
  bell,
  recall,
  'base-door': baseDoor,
};

export function captionFor(
  subject: VillageSubject | null,
  state: GameState,
  context: CaptionContext = { startFloor: 0 },
): VillageCaption | null {
  if (!subject) return null;
  return CAPTION_BUILDERS[subject.kind]?.(subject, state, context) ?? null;
}

/** The caption as one plain sentence run, for the scene and for assistive tech. */
export function captionText(c: VillageCaption): string {
  return [
    c.name + '.',
    c.status,
    c.makes ? c.makes + '.' : '',
    c.rates ? c.rates + '.' : '',
    c.leaves ?? '',
    c.lock ?? '',
  ]
    .filter((s) => s)
    .join(' ');
}

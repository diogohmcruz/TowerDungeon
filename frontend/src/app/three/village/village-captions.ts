/**
 * The short caption of a Village subject: its name, whether it works, what it
 * makes. A locked footing states its real unlock condition in words. Pure.
 *
 * `CAPTION_BUILDERS` is the extension point: the Port footing and the Shortcut
 * marks add a kind and a builder. A Shortcut stays hidden until open, so its
 * builder is only reached for an open one.
 */
import type { GameState } from '../../interfaces/game-state';
import type { TradeId, TradeView } from '../../interfaces/village';
import type { SubjectKind, VillageSubject } from './village-subjects';

export interface VillageCaption {
  name: string;
  /** Whether it works, or why it does not. */
  status: string;
  /** What it makes; empty when it makes nothing. */
  makes: string;
  /** Set when the footing is locked: what opens it. */
  lock: string | null;
}

type CaptionBuilder = (
  subject: VillageSubject,
  state: GameState,
) => VillageCaption | null;

/** Trades whose Annex opens with a Village Tier: the level and its name. */
const TIER_GATES: Partial<
  Record<TradeId, { level: number; name: string; roman: string }>
> = {
  SALTER: { level: 2, name: 'Sheltered', roman: 'II' },
  SPOREWARDEN: { level: 3, name: 'Fortified', roman: 'III' },
};

function viewOf(state: GameState, trade: string): TradeView | undefined {
  return state.tradeViews.find((v) => v.trade === trade);
}

/** The words for a locked Trade, from the Village Tier the game is at; null when open. */
export function lockOf(state: GameState, trade: string): string | null {
  const gate = TIER_GATES[trade as TradeId];
  const view = viewOf(state, trade);
  if (!gate || !view || state.villageTierLevel >= gate.level) return null;
  return (
    `The ${view.workplace} open at Village Tier ${gate.roman}, ${gate.name}. ` +
    `The Village stands at Tier ${state.villageTierLevel}, ${state.villageTierName}.`
  );
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
  return {
    name: view.workplace,
    status: works,
    makes: view.hands > 0 ? makesOf(state, subject.id) : '',
    lock: null,
  };
};

const villagers: CaptionBuilder = (subject, state) => {
  const view = viewOf(state, subject.id);
  if (!view) return null;
  const lock = lockOf(state, subject.id);
  if (lock) {
    return { name: view.plural, status: 'None. The place is closed.', makes: '', lock };
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

export const CAPTION_BUILDERS: Partial<Record<SubjectKind, CaptionBuilder>> = {
  annex,
  villagers,
  upgrade,
};

export function captionFor(
  subject: VillageSubject | null,
  state: GameState,
): VillageCaption | null {
  if (!subject) return null;
  return CAPTION_BUILDERS[subject.kind]?.(subject, state) ?? null;
}

/** The caption as one plain sentence run, for the scene and for assistive tech. */
export function captionText(c: VillageCaption): string {
  return [c.name + '.', c.status, c.makes ? c.makes + '.' : '', c.lock ?? '']
    .filter((s) => s)
    .join(' ');
}

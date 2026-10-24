/**
 * The Succession at the Tower's foot (architecture doc 5.6): when the Campaign
 * ends and waits in OFFER, the Roll stone stands at the foot of the Tower and its
 * popover opens by itself. It holds what the Succession screen held, in the same
 * words: the Toll as plain numbers, the Roll tree with its Courses where Legacy is
 * spent (Take, Give back), the Dowry and the Heir, the Chronicle, and Begin
 * Generation with its confirmation. It never congratulates; state is in words.
 *
 * The Toll breakdown is `tollLines`, a function of its own, so the Tier IV Stone
 * of Count can take it from here without touching the rest.
 */
import { signal } from '@angular/core';

import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import type {
  SuccessionCourse,
  SuccessionNode,
  SuccessionOption,
  SuccessionView,
} from '../../interfaces/succession';
import {
  defineBuilding,
  type ActionSpec,
  type LineSpec,
} from './building-registry';

export const SUCCESSION_ID = 'SUCCESSION';

/** The Roll and the choices the server sent for the Succession; null outside OFFER. */
export function successionOf(state: GameState): SuccessionView | null {
  return state.phase === 'OFFER' ? (state.session?.succession ?? null) : null;
}

const OUTCOMES: Record<string, string> = {
  VICTORY: 'The Heirs',
  DEFEAT: 'The Second Company',
  EXODUS: 'The Flight',
};

const CHOICE_LABELS: Record<string, string> = {
  DOWRY_CREDITS: 'Credits',
  DOWRY_MATERIALS: 'Materials',
  DOWRY_RELICS: 'Relics',
  HEIR_WARRIOR: 'Warrior',
  HEIR_ARCHER: 'Archer',
  HEIR_PORTER: 'Porter',
};

const GROUP_NAMES: Record<string, string> = {
  DOWRY: 'The Dowry',
  HEIR: 'The Heir',
};

const GROUP_TITLES: Record<string, string> = {
  DOWRY: 'The Dowry. What the village carries into the next Generation.',
  HEIR: 'The Heir. The unit type the next Generation starts with.',
};

/** What the ending says, in the game's plain words; it never congratulates. */
const ENDING_WORDS: Record<string, string> = {
  VICTORY:
    "The Dragon is down. The Tower did not fall. It only listened. Your survivors' children will climb it again, because someone must.",
  EXODUS: 'The Village leaves in the night. The Tower keeps whoever stayed.',
  DEFEAT:
    'No one is left to ring the bell. In spring a second company finds the graves and the Ledger and reads the names aloud.',
};

/** The ending of the Campaign as a heading and a line; null while it is still played. */
export function endingOf(state: GameState): { heading: string; text: string } | null {
  const outcome = state.gameOutcome;
  return ENDING_WORDS[outcome]
    ? { heading: outcomeName(outcome), text: ENDING_WORDS[outcome] }
    : null;
}

export function outcomeName(outcome: string): string {
  return OUTCOMES[outcome] ?? outcome;
}

export function choiceLabel(id: string): string {
  return CHOICE_LABELS[id] ?? id;
}

/** How long a started Generation is held before a second Begin is allowed again. */
export const BEGIN_HOLD_MS = 3000;

/**
 * The Generation whose start has been sent; a second Begin for it is a no-op.
 * Client-only and keyed by the Generation, so a later Succession starts clean.
 * Reads made inside a computed or an effect track the signal.
 */
export const beginFlow = {
  begunFor: signal<number | null>(null),
};

const USABLE = { usable: true, reason: null } as const;
const refused = (reason: string) => ({ usable: false, reason });

/** A whole figure of marks, one decimal as the Toll is written. */
function marks(value: number): string {
  return value.toLocaleString('en-US', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });
}

const share = (part: number, whole: number) =>
  Math.min(1, Math.max(0, part / Math.max(1, whole)));

/** The Toll as plain numbers: what each thing the Generation did counted for. Empty with no report. */
export function tollLines(state: GameState): LineSpec[] {
  const report = state.pending;
  if (!report) return [];
  const toll = report.toll;
  const line = (id: string, label: string, value: string, detail?: string): LineSpec => ({
    id: `toll-${id}`,
    group: 'Toll',
    label,
    value,
    ...(detail ? { detail } : {}),
  });
  return [
    line(
      'total',
      'Toll',
      marks(toll.toll),
      `Legacy ${report.legacy}${report.pity ? ' (the floor paid after a Defeat)' : ''}.`,
    ),
    line('food', 'Food grown', marks(toll.foodMarks)),
    line('materials', 'Materials banked', marks(toll.materialsMarks)),
    line('credits', 'Credits banked', marks(toll.creditsMarks)),
    line('relics', 'Relics banked', marks(toll.relicsMarks)),
    line('expeditions', 'Expeditions completed', marks(toll.expeditionsMarks)),
    line('ascent', 'Ascent', marks(toll.ascentMarks)),
    line('guardians', 'Guardians slain', marks(toll.guardianMarks)),
    line('new-ground', 'New Ground', `+${toll.newGround}`),
    line('residue', 'Residue carried on', marks(toll.residueNext)),
  ];
}

function courseState(course: SuccessionCourse): string | null {
  return course.open
    ? null
    : `Closed. Spend ${course.gate} Legacy in the Courses below (${course.spentBelow} spent).`;
}

function courseLines(view: SuccessionView): LineSpec[] {
  return view.courses.map((course) => {
    const detail = courseState(course);
    return {
      id: `course-${course.course}`,
      group: 'The Roll',
      label: `Course ${course.course}`,
      value: course.open ? 'Open' : 'Closed',
      ...(detail ? { detail } : {}),
    };
  });
}

/** The node's state in words. */
export function nodeState(node: SuccessionNode): string {
  if (node.held) return 'Held';
  if (!node.available) return 'Sealed';
  return node.whyNot ? 'Not yet' : 'Open';
}

/** Why the node cannot be bought, in words; empty while it can. */
export function nodeWhyNot(node: SuccessionNode, legacy: number): string {
  if (node.held) return '';
  if (!node.available) return 'Sealed. The Tower has not opened this.';
  switch (node.whyNot) {
    case null:
      return '';
    case 'COURSE_CLOSED':
      return 'Its Course is closed.';
    case 'INSUFFICIENT_LEGACY':
      return `It costs ${node.cost} Legacy. You hold ${legacy}.`;
    case 'ALREADY_HELD':
      return 'Already held.';
    default:
      return 'It cannot be bought now.';
  }
}

function canBuy(node: SuccessionNode): boolean {
  return node.available && !node.held && node.whyNot === null;
}

/** One Inheritance as one action: Take while it is not held, Give back while it is. */
function nodeAction(node: SuccessionNode): ActionSpec {
  const kind = node.kind.toLowerCase();
  const find = (state: GameState) =>
    successionOf(state)?.inheritances.find((n) => n.id === node.id) ?? node;
  return {
    id: `roll-${node.id}`,
    label: (state) => {
      const n = find(state);
      return n.held
        ? `Give back ${n.name} (Course ${n.course}, ${kind}, ${nodeState(n)}, paid ${n.paid} Legacy)`
        : `Take ${n.name} (Course ${n.course}, ${kind}, ${nodeState(n)}, cost ${n.cost} Legacy)`;
    },
    availability: (state) => {
      const n = find(state);
      if (n.held) {
        return n.refundable ? USABLE : refused('Others stand on it.');
      }
      return canBuy(n)
        ? USABLE
        : refused(
            nodeWhyNot(n, successionOf(state)?.legacy ?? 0) ||
              'It cannot be bought now.',
          );
    },
    preview: (state) => {
      const n = find(state);
      const legacy = successionOf(state)?.legacy ?? 0;
      return n.held
        ? `Giving it back returns ${n.paid} Legacy and leaves ${legacy + n.paid} to spend.`
        : `Taking it costs ${n.cost} Legacy and leaves ${legacy - n.cost} to spend.`;
    },
    run: (commands, state) => {
      const n = find(state);
      commands.send(n.held ? GameAction.ROLL_REFUND : GameAction.ROLL_BUY, {
        id: n.id,
      });
    },
  };
}

/** Whether the option is the answer now, or the default while none is given. */
function isChosen(options: SuccessionOption[], option: SuccessionOption): boolean {
  return options.some((o) => o.selected) ? option.selected : option.isDefault;
}

function choiceGroups(view: SuccessionView): Map<string, SuccessionOption[]> {
  const groups = new Map<string, SuccessionOption[]>();
  for (const option of view.choices) {
    const list = groups.get(option.group) ?? [];
    list.push(option);
    groups.set(option.group, list);
  }
  return groups;
}

function choiceLines(view: SuccessionView): LineSpec[] {
  return [...choiceGroups(view)].map(([group, options]) => {
    const chosen = options.find((o) => isChosen(options, o));
    return {
      id: `choice-${group}`,
      group: 'The choices',
      label: GROUP_NAMES[group] ?? group,
      value: chosen ? choiceLabel(chosen.id) : 'None',
      detail: GROUP_TITLES[group] ?? group,
    };
  });
}

function choiceActions(view: SuccessionView): ActionSpec[] {
  const out: ActionSpec[] = [];
  for (const [group, options] of choiceGroups(view)) {
    for (const option of options) {
      const answer = (state: GameState) => {
        const now = successionOf(state)?.choices.filter((o) => o.group === group) ?? options;
        const me = now.find((o) => o.id === option.id) ?? option;
        return { now, me };
      };
      out.push({
        id: `choose-${option.id}`,
        label: (state) => {
          const { now, me } = answer(state);
          return (
            `${GROUP_NAMES[group] ?? group}: ${choiceLabel(me.id)}` +
            (isChosen(now, me) ? ' (chosen)' : '') +
            (me.isDefault ? ' (taken if none is chosen)' : '')
          );
        },
        availability: (state) =>
          answer(state).me.selected ? refused('Already chosen.') : USABLE,
        preview: () => GROUP_TITLES[group] ?? group,
        run: (commands, state) =>
          commands.send(GameAction.CHOOSE, { id: answer(state).me.id }),
      });
    }
  }
  return out;
}

function chronicleLines(view: SuccessionView, state: GameState): LineSpec[] {
  const entries = [...(view.chronicle ?? [])].reverse();
  const out: LineSpec[] = [];
  if (entries.length === 0) {
    out.push({ id: 'chronicle', group: 'Chronicle', label: 'Nothing is written yet.', value: '' });
  }
  for (const entry of entries) {
    const fallen =
      entry.fallen.length > 0
        ? `Fallen: ${entry.fallen
            .slice(0, 5)
            .map((f) => `${f.name} (${f.type})`)
            .join(', ')}.`
        : 'No names were kept.';
    out.push({
      id: `chronicle-${entry.generation}`,
      group: 'Chronicle',
      label: `Generation ${entry.generation}`,
      value: `${outcomeName(entry.outcome)}. Toll ${marks(entry.toll)}`,
      detail: `Legacy ${entry.legacy}. Floor ${entry.highestFloor}. ${fallen}`,
    });
  }
  (state.raceRecords ?? []).forEach((record, i) => {
    out.push({
      id: `race-${record.raceId}-${record.attempt}-${i}`,
      group: 'Race record',
      label: record.line,
      value: '',
    });
  });
  return out;
}

function begunNow(view: SuccessionView): boolean {
  return beginFlow.begunFor() === view.generation;
}

/** Begin Generation, asked once and then confirmed (the action row asks). */
const beginAction: ActionSpec = {
  id: 'begin',
  label: (s) => `Begin Generation ${(successionOf(s)?.generation ?? 0) + 1}`,
  availability: (s) => {
    const v = successionOf(s);
    return v && begunNow(v) ? refused('The village is on the road.') : USABLE;
  },
  confirm: (s) => {
    const v = successionOf(s);
    return {
      prompt: `Generation ${(v?.generation ?? 0) + 1} begins and the Roll is fixed. ${v?.legacy ?? 0} Legacy goes unspent and is kept.`,
      confirmLabel: 'Begin',
      cancelLabel: 'Not yet',
    };
  },
  run: (commands, s) => {
    const v = successionOf(s);
    if (!v || begunNow(v)) return;
    const generation = v.generation;
    beginFlow.begunFor.set(generation);
    commands.send(GameAction.BEGIN_GENERATION, null);
    // If the server refused and the Campaign still waits, allow another try.
    setTimeout(() => {
      if (beginFlow.begunFor() === generation) beginFlow.begunFor.set(null);
    }, BEGIN_HOLD_MS);
  },
};

/**
 * The Roll stone. Its gauges are what the hover reads (the Generation and the
 * Legacy left); the rest is the card's: the Toll, the Courses, the choices and the
 * Chronicle as rows of words, every Inheritance, choice and Begin as an action.
 */
export const SUCCESSION = defineBuilding({
  id: SUCCESSION_ID,
  title: 'The Succession',
  wide: true,
  visible: (s: GameState) => successionOf(s) !== null,
  // How the Campaign ended, in words, above the Toll it left.
  sections: [
    {
      id: 'ending',
      heading: (s: GameState) => endingOf(s)?.heading ?? null,
      lines: (s: GameState) => {
        const ending = endingOf(s);
        return ending ? [{ text: ending.text, loss: false }] : [];
      },
      empty: '',
    },
  ],
  gauges: [
    {
      id: 'generation',
      label: 'Generation',
      value: (s) => successionOf(s)?.generation ?? 0,
      format: (v) => `${v} has ended`,
      fill: () => 1,
      leaves: () => 'What it left is counted below. The Tower keeps count.',
    },
    {
      id: 'legacy',
      label: 'Legacy to spend',
      value: (s) => successionOf(s)?.legacy ?? 0,
      fill: (s) => {
        const v = successionOf(s);
        return v ? share(v.legacy, v.legacy + v.spent) : 0;
      },
      leaves: (s) => {
        const v = successionOf(s);
        return v
          ? `Spent on the Roll ${v.spent}. Giving an Inheritance back returns what was paid, while this screen is open.`
          : null;
      },
    },
  ],
  lines: (s: GameState) => {
    const view = successionOf(s);
    if (!view) return [];
    return [
      ...tollLines(s),
      ...courseLines(view),
      ...choiceLines(view),
      ...chronicleLines(view, s),
    ];
  },
  actions: [],
  moreActions: (s: GameState) => {
    const view = successionOf(s);
    if (!view) return [];
    const nodes = [...view.inheritances].sort((a, b) => a.course - b.course);
    return [...nodes.map(nodeAction), ...choiceActions(view), beginAction];
  },
});

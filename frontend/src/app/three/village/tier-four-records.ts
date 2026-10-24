/**
 * The two records that stand apart at Village Tier IV (architecture doc 5.6): a
 * Wall of Names takes the fallen Units, so the Scribe's Study keeps only the
 * Ledger, and a Stone of Count takes the Toll forecast and Exodus, so the
 * reference pillar keeps only the Highest Floor and the Milestones. Below Tier IV
 * neither is drawn or listed, and the Study and the pillar are as they were.
 *
 * Each is a building (`building-registry.ts`): the Wall carries the fallen as a
 * written section, the Stone the Toll gauge, the Toll lines and Exodus (reusing
 * the pillar's own pieces, so the figures and the words are the same ones).
 */
import type { GameState } from '../../interfaces/game-state';
import { barGauge } from './gauge-kit';
import { defineBuilding, type SectionSpec, type WrittenLine } from './building-registry';
import { typeName } from './records-buildings';
import { EXODUS_ACTION, STONE_TIER, TOLL_GAUGE, TOLL_LINES } from './reference-pillar-building';
import type { Volume } from './village-layout';

/** The Village Tier at which the pillar and the Study hand these records on. */
export const RECORDS_TIER = STONE_TIER;

export const WALL_PREFIX = 'names-';
export const WALL_ID = 'WALL_OF_NAMES';
export const STONE_PREFIX = 'count-';
export const STONE_ID = 'STONE_OF_COUNT';

/** Whether this Village has the two records standing: Tier IV and above. */
export const atRecordsTier = (s: GameState): boolean => s.villageTierLevel >= RECORDS_TIER;

/** Ink density per part, as the Village's own tones. */
const TONE = { stone: 0.72, footing: 0.5, dark: 0.12, lit: 1 } as const;

/** Where they stand: the back row, right of the Base door and the Shortcut marks. */
export const STONE = { x: 1.36, z: -0.3, w: 0.36, h: 1.5, d: 0.2 } as const;
export const WALL = { x: 2.7, z: -0.3, w: 0.9, h: 1.8, d: 0.16 } as const;

/**
 * Names the wall has room to carve: three columns of four. They are cut high on
 * the wall, above the Vault and the Study that stand in front of its foot.
 */
export const WALL_SLOTS = 12;

function box(
  out: Volume[],
  id: string,
  x: number,
  z: number,
  bottom: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  alpha = 1,
): void {
  out.push({ id, group: 'village', x, y: bottom + h / 2, z, w, h, d, tone, alpha });
}

/**
 * The Stone of Count: a squat stone with the Toll towards the next Legacy as a
 * bar cut across its face (the pillar's Toll bar, moved here). `tollShare` is 0..1.
 */
export function stoneOfCount(out: Volume[], tollShare: number): void {
  const { x, z, w, h, d } = STONE;
  const id = (part: string) => `${STONE_PREFIX}${part}`;
  box(out, id('plinth'), x, z, 0, w + 0.1, 0.12, d + 0.08, TONE.footing);
  box(out, id('shaft'), x, z, 0.12, w, h - 0.18, d, TONE.stone);
  box(out, id('cap'), x, z, h - 0.06, w + 0.08, 0.06, d + 0.06, TONE.stone);
  barGauge(out, `${STONE_PREFIX}toll`, {
    x,
    bottom: 1.1,
    z: z + d / 2 + 0.02,
    length: 0.24,
    fill: tollShare,
  });
  // The near-miss pad, kept narrow so it stays clear of the Shortcut marks beside it.
  box(out, id('hit'), x, z, 0, w + 0.12, h + 0.1, d + 0.1, TONE.dark, 0);
}

/**
 * The Wall of Names: a low wall with a name carved for each of the fallen, up to
 * twelve, three columns of four; a carved name is bone, an empty place is dark.
 */
export function wallOfNames(out: Volume[], fallen: number): void {
  const { x, z, w, h, d } = WALL;
  const id = (part: string) => `${WALL_PREFIX}${part}`;
  const names = Math.min(WALL_SLOTS, Math.max(0, Math.floor(fallen)));
  box(out, id('plinth'), x, z, 0, w + 0.1, 0.12, d + 0.08, TONE.footing);
  box(out, id('slab'), x, z, 0.12, w, h - 0.18, d, TONE.stone);
  box(out, id('cap'), x, z, h - 0.06, w + 0.08, 0.06, d + 0.06, TONE.stone);
  const face = z + d / 2 + 0.02;
  for (let i = 0; i < WALL_SLOTS; i++) {
    const column = i % 3;
    const row = Math.floor(i / 3);
    box(
      out,
      id(`name-${i}`),
      x + (column - 1) * 0.27,
      face,
      1.2 + row * 0.14,
      0.2,
      0.1,
      0.02,
      i < names ? TONE.lit : TONE.dark,
    );
  }
  box(out, id('hit'), x, z, 0, w + 0.12, h + 0.1, d + 0.1, TONE.dark, 0);
}

/** One line a fallen Unit is written as: where it fell, or that it starved (Floor 0). */
export function fallenLine(unit: { name: string; type: string; floor: number }): WrittenLine {
  const where = unit.floor > 0 ? `fell on Floor ${unit.floor}` : 'starved at home';
  return { text: `${unit.name}, ${typeName(unit.type)}, ${where}.`, loss: true };
}

/** The fallen, most recent first. */
export function fallenLines(state: GameState): WrittenLine[] {
  return [...(state.wallOfNames ?? [])].reverse().map(fallenLine);
}

export const WALL_SECTION: SectionSpec = {
  id: 'fallen',
  heading: () => 'The fallen',
  lines: fallenLines,
  empty: 'No name is carved yet.',
  scroll: true,
};

export const WALL_OF_NAMES = defineBuilding({
  id: WALL_ID,
  title: 'The Wall of Names',
  visible: atRecordsTier,
  gauges: [
    {
      id: 'names',
      label: 'Names',
      value: (s) => (s.wallOfNames ?? []).length,
      fill: (s) => (s.wallOfNames ?? []).length / WALL_SLOTS,
    },
  ],
  actions: [],
  sections: [WALL_SECTION],
});

export const STONE_OF_COUNT = defineBuilding({
  id: STONE_ID,
  title: 'The Stone of Count',
  visible: atRecordsTier,
  gauges: [{ ...TOLL_GAUGE, caption: true }],
  lines: (s) => [{ id: 'count-words', label: 'The Tower keeps count.', value: '' }, ...TOLL_LINES(s)],
  actions: [EXODUS_ACTION],
});

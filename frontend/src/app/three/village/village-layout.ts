/**
 * The Village Elevation (architecture doc section 5.2): a pure layout of
 * rectangular volumes. No three.js here, so the bounds and the Annex rules are
 * unit-testable. Every volume is a box; the renderer draws them as one ink
 * instanced mesh. World units: x across, y up from the ground top, z toward the
 * camera. The Tower stands behind the Village and rises into the dither.
 */
import type { TradeId } from '../../interfaces/village';
import { outlineVolumes } from './village-subjects';

/** Width of the frame at the Village's depth. The camera is fitted to this. */
export const FRAME_WIDTH = 11;
/** The Village never spans more than this share of the frame width. */
export const MAX_VILLAGE_SHARE = 0.6;
/** Village volumes stay under this height (the Tower and its slab are not the Village). */
export const MAX_VILLAGE_HEIGHT = 2;
/** A black gap between the ground and the underside of the Base slab, so it floats. */
export const BASE_GAP = 0.35;
/** Villagers shown at most (one figure stands for two Villagers). */
export const MAX_VILLAGER_FIGURES = 40;
/** Reserves stand in ranks, at most this many. */
export const MAX_RESERVE_FIGURES = 30;
/** Village Tiers I to IV: one lamp each along the stair. */
export const VILLAGE_TIERS = 4;

/** Ink density per volume (0..1), see `TONE` in `ink-material.ts`. */
export const VILLAGE_TONE = {
  ground: 0.3,
  footing: 0.5,
  stone: 0.72,
  timber: 0.55,
  silo: 0.66,
  dark: 0.12,
  lit: 1,
  lampOff: 0.22,
  ash: 1,
  salt: 0.95,
  heap: 0.5,
  tower: 0.32,
} as const;

/** Floors the Tower's drawn height stands for, so a Floor maps to a relative height. */
export const SHORTCUT_FLOOR_SPAN = 100;
/** Where the first Tower segment starts above the ground (the Base slab's top). */
export const TOWER_FOOT = BASE_GAP + 1.25;
/** Height of one Tower segment and the number drawn. */
export const TOWER_SEGMENT = 2.2;
export const TOWER_SEGMENTS = 14;
/** Shortcut marks sit on the Tower's flank at this x and just in front of its face. */
export const SHORTCUT_MARK_X = 1.9;
export const SHORTCUT_MARK_Z = -0.27;
/** Id prefix of a Shortcut mark volume; the rest is the Shortcut id. */
export const SHORTCUT_MARK_ID = 'shortcut-mark-';

export type VolumeGroup = 'village' | 'tower';

/** A box: `x`, `y`, `z` are the centre; `y` is the centre height above the ground top. */
export interface Volume {
  id: string;
  group: VolumeGroup;
  x: number;
  y: number;
  z: number;
  w: number;
  h: number;
  d: number;
  tone: number;
  alpha: number;
}

/** An open Shortcut as the Village sees it: no name, no numeral. */
export interface VillageShortcut {
  id: string;
  floor: number;
}

export interface VillageInput {
  food: number;
  /** Reserves waiting in the Fort. */
  reserves: number;
  /** Hands per Trade, from the Trade views. */
  hands: Record<TradeId, number>;
  /** Posts of the Quarry and the Study (null when uncapped). */
  posts: Partial<Record<TradeId, number | null>>;
  quarryHeap: number;
  quarryHeapCap: number;
  /** Materials and Relics in the Vault. */
  materials: number;
  relics: number;
  villageTierLevel: number;
  /** Levels owned per Upgrade id (the catalogue's `UpgradeType` names); absent means none. */
  upgrades: Record<string, number>;
  saltPansOpen: boolean;
  /** Salt held, for the crust height. */
  salt: number;
  /** Open Shortcuts only; a locked Shortcut is never passed in, so it draws nothing. */
  shortcuts: VillageShortcut[];
  /** Shortcut whose mark is drawn thicker (hook for the shared selection service). */
  highlightedShortcut: string | null;
  /** Seconds-ish counter: villagers step once per tick. */
  tick: number;
  /** Lamp flicker phase in seconds; 0 freezes the lamps. */
  flicker: number;
  /** Fully still scene (`prefers-reduced-motion`). */
  still: boolean;
  /** The id of the thing the pointer or focus is on; its outline is one pixel. */
  hovered?: string | null;
  /** The id of the thing chosen by a click or a row; its outline is two pixels. */
  selected?: string | null;
}

export const EMPTY_VILLAGE: VillageInput = {
  food: 0,
  reserves: 0,
  hands: { TILLER: 0, BREAKER: 0, SCRIBE: 0, SALTER: 0, SPOREWARDEN: 0 },
  posts: {},
  quarryHeap: 0,
  quarryHeapCap: 0,
  materials: 0,
  relics: 0,
  villageTierLevel: 0,
  upgrades: {},
  saltPansOpen: false,
  salt: 0,
  shortcuts: [],
  highlightedShortcut: null,
  tick: 0,
  flicker: 0,
  still: true,
};

/** Where the Annexes stand (x ranges), the single source of the layout. */
export const ANNEX_X = {
  fort: [-3.2, -1.9],
  silo: [-1.75, -1.45],
  fields: [-1.3, -0.1],
  quarry: [0.0, 1.1],
  study: [1.25, 1.85],
  vault: [2.0, 3.2],
  pans: [0.0, 1.1],
} as const;

/** The Annex each Trade works at; the Fields are the Tillers'. */
export const TRADE_ANNEX: Record<TradeId, keyof typeof ANNEX_X> = {
  TILLER: 'fields',
  BREAKER: 'quarry',
  SCRIBE: 'study',
  SALTER: 'pans',
  // The Spore Cellar lies below the Base, so its hands work at the Fort's foot.
  SPOREWARDEN: 'fort',
};

/** The lane each Trade walks: x range and z, in front of its Annex. */
export const LANES: Record<
  TradeId,
  { x0: number; x1: number; z: number }
> = {
  TILLER: { x0: -1.25, x1: -0.15, z: 1.5 },
  BREAKER: { x0: 0.05, x1: 1.05, z: 1.15 },
  SCRIBE: { x0: 1.3, x1: 1.8, z: 1.15 },
  SALTER: { x0: 0.05, x1: 1.05, z: 1.7 },
  SPOREWARDEN: { x0: -3.1, x1: -2.0, z: 1.3 },
};

/** The silo stands behind the stair's foot, so the two never share a plan. */
const SILO_Z = -0.3;
const FIGURE = { w: 0.1, h: 0.35, d: 0.05 };
/** One virtual pixel in world units (an 11-unit frame across about 220 pixels). */
const PIXEL = FRAME_WIDTH / 220;

const mid = (r: readonly [number, number]) => (r[0] + r[1]) / 2;
const span = (r: readonly [number, number]) => r[1] - r[0];

/** Box helper: `bottom` is the underside height, not the centre. */
function box(
  out: Volume[],
  id: string,
  group: VolumeGroup,
  x: number,
  bottom: number,
  z: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  alpha = 1,
): void {
  out.push({ id, group, x, y: bottom + h / 2, z, w, h, d, tone, alpha });
}

/** Fill of the Pantry silo slit, 0..1: `min(1, log10(1 + food) / 3)`. */
export function siloFill(food: number): number {
  return Math.min(1, Math.log10(1 + Math.max(0, food)) / 3);
}

/** Crates on the Vault Annex roof: `min(12, floor(log2(1 + materials + 5 * relics)))`. */
export function vaultCrates(materials: number, relics: number): number {
  return Math.min(
    12,
    Math.floor(Math.log2(1 + Math.max(0, materials) + 5 * Math.max(0, relics))),
  );
}

/** Heap blocks equal Heap fill, 0..12. */
export function heapBlocks(heap: number, cap: number): number {
  return cap > 0 ? Math.min(12, Math.round((12 * heap) / cap)) : 0;
}

/** Lit slits of the Scribe's Study, 0..3. */
export function litSlits(hands: number, posts: number | null | undefined): number {
  if (hands <= 0) return 0;
  if (!posts || posts <= 0) return 1;
  return Math.min(3, Math.max(1, Math.ceil((3 * hands) / posts)));
}

/** Lamps lit along the stair: one per Village Tier reached, at most four. */
export function litLamps(villageTierLevel: number): number {
  return Math.max(0, Math.min(VILLAGE_TIERS, Math.floor(villageTierLevel)));
}

/**
 * Villager figures shown per Trade: one per two Villagers, rounded up, the
 * total capped at `MAX_VILLAGER_FIGURES` by trimming the largest Trade first.
 */
export function figureCounts(
  hands: Record<TradeId, number>,
): Record<TradeId, number> {
  const trades: TradeId[] = [
    'TILLER',
    'BREAKER',
    'SCRIBE',
    'SALTER',
    'SPOREWARDEN',
  ];
  const counts = {} as Record<TradeId, number>;
  for (const t of trades) counts[t] = Math.ceil(Math.max(0, hands[t]) / 2);
  let total = trades.reduce((s, t) => s + counts[t], 0);
  while (total > MAX_VILLAGER_FIGURES) {
    const biggest = trades.reduce((a, b) => (counts[b] > counts[a] ? b : a));
    counts[biggest]--;
    total--;
  }
  return counts;
}

/** A slow, triangle-wave step so figures pace in their lane, one pixel per tick. */
function pace(tick: number, seed: number): number {
  const period = 8;
  const p = (((tick + seed * 3) % period) + period) % period;
  return (p < period / 2 ? p : period - p) - period / 4; // -2..+2
}

function fort(out: Volume[]): void {
  const x = mid(ANNEX_X.fort);
  const w = span(ANNEX_X.fort);
  // Timber-framed keep on a stone plinth with a stepped parapet.
  box(out, 'fort-plinth', 'village', x, 0, 0.05, w, 0.3, 1.0, VILLAGE_TONE.stone);
  box(out, 'fort-keep', 'village', x, 0.3, 0, w - 0.3, 0.55, 0.8, VILLAGE_TONE.timber);
  for (let i = 0; i < 4; i++) {
    const mx = ANNEX_X.fort[0] + 0.27 + (i * (w - 0.54)) / 3;
    box(out, `fort-merlon-${i}`, 'village', mx, 0.85, 0.35, 0.16, 0.14, 0.12, VILLAGE_TONE.stone);
  }
  box(out, 'fort-door', 'village', x + 0.2, 0.3, 0.41, 0.18, 0.3, 0.02, VILLAGE_TONE.dark);
}

function silo(out: Volume[], food: number): void {
  const x = mid(ANNEX_X.silo);
  const w = span(ANNEX_X.silo);
  box(out, 'silo', 'village', x, 0, SILO_Z, w, 1.7, 0.3, VILLAGE_TONE.silo);
  // The slit is a dark channel; the bone fill rises inside it with the food.
  box(out, 'silo-slit', 'village', x, 0.25, SILO_Z + 0.16, 0.06, 1.2, 0.02, VILLAGE_TONE.dark);
  const fill = siloFill(food);
  if (fill > 0) {
    box(out, 'silo-fill', 'village', x, 0.25, SILO_Z + 0.17, 0.06, 1.2 * fill, 0.02, VILLAGE_TONE.lit);
  }
}

function stair(out: Volume[], tier: number, flicker: number): void {
  // One steep flight as one extruded stepped profile (rise 1.6, run 1.9).
  const steps = 8;
  const run = 1.9;
  const rise = 1.6;
  const x0 = -1.9;
  for (let i = 0; i < steps; i++) {
    const h = (rise * (i + 1)) / steps;
    box(out, `stair-${i}`, 'village', x0 + (run / steps) * (i + 0.5), 0, 0.2, run / steps, h, 0.4, VILLAGE_TONE.stone);
    // A tread line: a thin bright strip on each step.
    box(out, `tread-${i}`, 'village', x0 + (run / steps) * (i + 0.5), h, 0.2, run / steps, 0.012, 0.4, 0.9);
  }
  // Village Tier as lamps along the stair, one more lit per level.
  const lit = litLamps(tier);
  for (let i = 0; i < VILLAGE_TIERS; i++) {
    const step = 1 + i * 2;
    const h = (rise * (step + 1)) / steps;
    // Plus or minus 8% on a slow noise of about 0.3 Hz, per lamp.
    const wobble = 1 + 0.08 * Math.sin(flicker * 2 * Math.PI * 0.3 + i * 2.1);
    const on = i < lit;
    box(
      out,
      `lamp-${i}`,
      'village',
      x0 + (run / steps) * (step + 0.5),
      h + 0.1,
      0.42,
      0.07,
      0.12,
      0.07,
      on ? Math.min(1, VILLAGE_TONE.lit * wobble) : VILLAGE_TONE.lampOff,
    );
    box(out, `lamp-post-${i}`, 'village', x0 + (run / steps) * (step + 0.5), h, 0.42, 0.02, 0.1, 0.02, VILLAGE_TONE.timber);
  }
}

function fields(out: Volume[]): void {
  const x = mid(ANNEX_X.fields);
  // Three stepped terraces in edge lines and furrow hatch, no building.
  for (let t = 0; t < 3; t++) {
    const w = span(ANNEX_X.fields) - t * 0.1;
    const z = 1.2 - t * 0.3;
    box(out, `field-${t}`, 'village', x, 0, z, w, 0.08 + t * 0.1, 0.28, VILLAGE_TONE.footing);
    const top = 0.08 + t * 0.1;
    for (let f = 0; f < 3; f++) {
      box(out, `furrow-${t}-${f}`, 'village', x, top, z - 0.09 + f * 0.09, w - 0.1, 0.012, 0.02, 0.95);
    }
  }
}

function quarry(out: Volume[], heap: number, heapCap: number): void {
  const x0 = ANNEX_X.quarry[0];
  // A stepped cut into rock...
  for (let i = 0; i < 3; i++) {
    box(out, `quarry-cut-${i}`, 'village', x0 + 0.7 + i * 0.12, 0, 0.2 - i * 0.15, 0.3, 0.3 + i * 0.3, 0.3, VILLAGE_TONE.stone);
  }
  // ...and a gantry: two piers, a beam and a rope as one line.
  box(out, 'gantry-pier-a', 'village', x0 + 0.1, 0, 0.5, 0.05, 1.0, 0.05, VILLAGE_TONE.timber);
  box(out, 'gantry-pier-b', 'village', x0 + 0.6, 0, 0.5, 0.05, 1.0, 0.05, VILLAGE_TONE.timber);
  box(out, 'gantry-beam', 'village', x0 + 0.35, 1.0, 0.5, 0.6, 0.05, 0.05, VILLAGE_TONE.timber);
  box(out, 'gantry-rope', 'village', x0 + 0.35, 0.55, 0.5, 0.012, 0.45, 0.012, 0.9);
  // The Heap: a pile of small blocks at the foot, in the dull Quarry brown.
  const n = heapBlocks(heap, heapCap);
  for (let i = 0; i < n; i++) {
    const row = i < 6 ? 0 : i < 10 ? 1 : 2;
    const col = row === 0 ? i : row === 1 ? i - 6 + 0.5 : i - 10 + 1;
    box(out, `heap-${i}`, 'village', x0 + 0.1 + col * 0.09, row * 0.07, 0.8, 0.08, 0.07, 0.08, VILLAGE_TONE.heap);
  }
}

function study(out: Volume[], hands: number, posts: number | null | undefined): void {
  const x = mid(ANNEX_X.study);
  const w = span(ANNEX_X.study);
  // A sealed cell on four stilts with vertical slits: the only clad timber building.
  for (let i = 0; i < 4; i++) {
    const sx = x + (i % 2 === 0 ? -1 : 1) * (w / 2 - 0.05);
    const sz = i < 2 ? 0.2 : 0.7;
    box(out, `stilt-${i}`, 'village', sx, 0, sz, 0.05, 0.4, 0.05, VILLAGE_TONE.timber);
  }
  box(out, 'study-cell', 'village', x, 0.4, 0.45, w, 0.45, 0.6, VILLAGE_TONE.timber);
  const lit = litSlits(hands, posts);
  for (let i = 0; i < 3; i++) {
    box(
      out,
      `slit-${i}`,
      'village',
      x - 0.17 + i * 0.17,
      0.5,
      0.76,
      0.04,
      0.25,
      0.02,
      i < lit ? VILLAGE_TONE.lit : VILLAGE_TONE.dark,
    );
  }
}

function vault(out: Volume[], materials: number, relics: number): void {
  const x = mid(ANNEX_X.vault);
  const w = span(ANNEX_X.vault);
  // A squat monolithic block corbelled off the Base: one door slab, no windows.
  box(out, 'vault', 'village', x, 0, 0.35, w, 0.9, 0.8, VILLAGE_TONE.stone);
  box(out, 'vault-corbel', 'village', x - 0.2, 0.9, 0.35, w - 0.4, 0.08, 0.7, VILLAGE_TONE.stone);
  box(out, 'vault-door', 'village', x, 0, 0.76, 0.24, 0.5, 0.02, VILLAGE_TONE.dark);
  // Crates on the roof: log2 of what the Vault holds, stacked in two rows.
  const crates = vaultCrates(materials, relics);
  for (let i = 0; i < crates; i++) {
    const row = i < 6 ? 0 : 1;
    const col = row === 0 ? i : i - 6;
    box(out, `crate-${i}`, 'village', x - 0.4 + col * 0.15, 0.98 + row * 0.1, 0.35, 0.12, 0.1, 0.12, VILLAGE_TONE.timber);
  }
}

function pans(out: Volume[], open: boolean, salt: number): void {
  const [x0, x1] = ANNEX_X.pans;
  if (!open) {
    // A locked Annex is a footing: a hairline rectangle on the ground, no label.
    const w = x1 - x0;
    const z = 1.85;
    const d = 0.45;
    box(out, 'footing-n', 'village', mid(ANNEX_X.pans), 0, z - d / 2, w, 0.012, 0.012, VILLAGE_TONE.footing + 0.3);
    box(out, 'footing-s', 'village', mid(ANNEX_X.pans), 0, z + d / 2, w, 0.012, 0.012, VILLAGE_TONE.footing + 0.3);
    box(out, 'footing-w', 'village', x0, 0, z, 0.012, 0.012, d, VILLAGE_TONE.footing + 0.3);
    box(out, 'footing-e', 'village', x1, 0, z, 0.012, 0.012, d, VILLAGE_TONE.footing + 0.3);
    return;
  }
  // Three raised bunded basins, 0.12 high; a pale crust fills with stock.
  const crust = Math.min(1, Math.log10(1 + Math.max(0, salt)) / 3);
  for (let i = 0; i < 3; i++) {
    const x = x0 + 0.17 + i * 0.38;
    box(out, `pan-${i}`, 'village', x, 0, 2.05, 0.34, 0.12, 0.4, VILLAGE_TONE.stone);
    box(out, `pan-well-${i}`, 'village', x, 0.1, 2.05, 0.26, 0.02, 0.32, VILLAGE_TONE.dark);
    if (crust > 0) {
      box(out, `pan-crust-${i}`, 'village', x, 0.1, 2.05, 0.26, 0.02 * crust + 0.002, 0.32, VILLAGE_TONE.salt);
    }
  }
}

/**
 * Growth: what each purchased Upgrade adds to the Village. One module per
 * level owned, up to `cap`; a module is a few boxes under ids `grow:<Upgrade>:<n>:<part>`.
 * Modules sit inside the existing footprint (x within the Annex span, under
 * the silo's height), so the Village grows denser, never wider or taller.
 */
interface GrowthModule {
  /** Most levels shown; levels beyond this still count in the game, not in the picture. */
  cap: number;
  /** Draws the module for the `n`th level owned (0-based). */
  build(out: Volume[], id: string, n: number): void;
}

/** The module table: one entry per Upgrade in the catalogue (see `INVISIBLE_UPGRADES`). */
export const UPGRADE_MODULES: Record<string, GrowthModule> = {
  // The Field Medic: a lean-to roof over the Fort's yard.
  UNLOCK_HEALER: {
    cap: 1,
    build(out, id) {
      const x = mid(ANNEX_X.fort);
      box(out, `${id}:roof`, 'village', x, 0.55, 0.85, 1.2, 0.04, 0.5, VILLAGE_TONE.timber);
      box(out, `${id}:post-a`, 'village', x - 0.55, 0, 0.7, 0.04, 0.55, 0.04, VILLAGE_TONE.timber);
      box(out, `${id}:post-b`, 'village', x + 0.55, 0, 0.7, 0.04, 0.55, 0.04, VILLAGE_TONE.timber);
    },
  },
  // The Ironclad: a stockade fence across the Fort's front.
  UNLOCK_TANK: {
    cap: 1,
    build(out, id) {
      const [x0, x1] = ANNEX_X.fort;
      for (let i = 0; i < 6; i++) {
        box(out, `${id}:post-${i}`, 'village', x0 + 0.1 + (i * (x1 - x0 - 0.2)) / 5, 0, 1.12, 0.05, 0.3, 0.05, VILLAGE_TONE.timber);
      }
      box(out, `${id}:rail`, 'village', mid(ANNEX_X.fort), 0.2, 1.12, x1 - x0 - 0.1, 0.03, 0.03, VILLAGE_TONE.timber);
    },
  },
  // The Shadow: a watchtower behind the Vault.
  UNLOCK_ROGUE: {
    cap: 1,
    build(out, id) {
      box(out, `${id}:shaft`, 'village', 2.6, 0, -0.6, 0.3, 1.6, 0.3, VILLAGE_TONE.stone);
      box(out, `${id}:cap`, 'village', 2.6, 1.6, -0.6, 0.46, 0.16, 0.46, VILLAGE_TONE.timber);
      box(out, `${id}:slit`, 'village', 2.6, 1.2, -0.43, 0.04, 0.2, 0.02, VILLAGE_TONE.dark);
    },
  },
  // The Necromancer: a lone dark obelisk behind the Quarry.
  UNLOCK_NECROMANCER: {
    cap: 1,
    build(out, id) {
      box(out, `${id}:obelisk`, 'village', 0.3, 0, -0.7, 0.16, 1.3, 0.16, VILLAGE_TONE.dark);
      box(out, `${id}:plinth`, 'village', 0.3, 0, -0.7, 0.3, 0.1, 0.3, VILLAGE_TONE.stone);
    },
  },
  // The Draco-Metamorph: a squat bone-lit brazier behind the Fields.
  UNLOCK_DRACO_METAMORPH: {
    cap: 1,
    build(out, id) {
      box(out, `${id}:bowl`, 'village', -0.7, 0, -0.7, 0.34, 0.5, 0.34, VILLAGE_TONE.stone);
      box(out, `${id}:ember`, 'village', -0.7, 0.5, -0.7, 0.2, 0.06, 0.2, VILLAGE_TONE.lit);
    },
  },
  // Supply Lines: a second silo, then crates stacked beside it.
  SUPPLY_LINES: {
    cap: 4,
    build(out, id, n) {
      if (n === 0) {
        box(out, `${id}:silo`, 'village', -1.0, 0, SILO_Z, 0.3, 1.4, 0.3, VILLAGE_TONE.silo);
        return;
      }
      box(out, `${id}:crate`, 'village', -0.7 + n * 0.17, 0, SILO_Z, 0.14, 0.14 + (n % 2) * 0.06, 0.14, VILLAGE_TONE.timber);
    },
  },
  // Sharpened Arms: a spear stood in the rack before the Vault, one per level.
  SHARPENED_ARMS: {
    cap: 4,
    build(out, id, n) {
      const x = 2.2 + n * 0.22;
      box(out, `${id}:spear`, 'village', x, 0, 1.0, 0.03, 0.8, 0.03, VILLAGE_TONE.ash);
      box(out, `${id}:head`, 'village', x, 0.8, 1.0, 0.07, 0.1, 0.03, VILLAGE_TONE.lit);
    },
  },
};

/** Upgrades in the catalogue that intentionally show nothing. None today. */
export const INVISIBLE_UPGRADES: readonly string[] = [];

/** Levels of an Upgrade the picture shows: owned, whole, capped per Upgrade. */
export function growthLevels(id: string, owned: number): number {
  const m = UPGRADE_MODULES[id];
  if (!m || !(owned > 0)) return 0;
  return Math.min(m.cap, Math.floor(owned));
}

function growth(out: Volume[], upgrades: Record<string, number>): void {
  // Table order, not the order of the state's keys, so the layout is deterministic.
  for (const id of Object.keys(UPGRADE_MODULES)) {
    const n = growthLevels(id, upgrades[id] ?? 0);
    for (let i = 0; i < n; i++) UPGRADE_MODULES[id].build(out, `grow:${id}:${i}`, i);
  }
}

function tower(out: Volume[]): void {
  // The Base slab floats above a black gap and the Tower rises behind the Village
  // into dither: dissolving alpha, not fog, so the top fades into ink.
  box(out, 'base-slab', 'tower', 0, BASE_GAP, -1.7, 4.8, 1.25, 3.0, VILLAGE_TONE.tower + 0.1);
  for (let i = 0; i < TOWER_SEGMENTS; i++) {
    const alpha = Math.max(0.06, 1 - i * 0.075);
    box(out, `tower-${i}`, 'tower', 0, TOWER_FOOT + i * TOWER_SEGMENT, -1.7, 4.4, TOWER_SEGMENT, 2.8, VILLAGE_TONE.tower, alpha);
  }
}

/** Height above the ground of the mark for a Floor (relative to the drawn Tower). */
export function shortcutMarkHeight(floor: number): number {
  const rel = Math.min(1, Math.max(0, floor) / SHORTCUT_FLOOR_SPAN);
  return TOWER_FOOT + rel * TOWER_SEGMENT * TOWER_SEGMENTS;
}

/**
 * One small lit mark on the Tower's flank per open Shortcut, at its Floor's
 * relative height. No numeral. Locked Shortcuts are not in the input, so they
 * leave nothing, not even a hairline. The highlighted one is drawn thicker.
 */
function shortcutMarks(
  out: Volume[],
  shortcuts: VillageShortcut[],
  highlighted: string | null,
): void {
  for (const sc of shortcuts) {
    if (sc.floor <= 0) continue;
    const on = sc.id === highlighted;
    box(
      out,
      `${SHORTCUT_MARK_ID}${sc.id}`,
      'tower',
      SHORTCUT_MARK_X,
      shortcutMarkHeight(sc.floor) - 0.05,
      SHORTCUT_MARK_Z,
      on ? 0.3 : 0.18,
      on ? 0.1 : 0.05,
      0.04,
      VILLAGE_TONE.lit,
    );
  }
}

function ground(out: Volume[]): void {
  box(out, 'ground', 'village', 0, -0.2, 1, FRAME_WIDTH * 2, 0.2, 5, VILLAGE_TONE.ground);
}

/** Reserves standing in ranks in the Fort's yard. */
function reserves(out: Volume[], count: number): void {
  const n = Math.min(MAX_RESERVE_FIGURES, Math.max(0, count));
  for (let i = 0; i < n; i++) {
    const rank = Math.floor(i / 10);
    const col = i % 10;
    box(
      out,
      `reserve-${i}`,
      'village',
      ANNEX_X.fort[0] + 0.15 + col * 0.11,
      0,
      0.65 + rank * 0.2,
      FIGURE.w,
      FIGURE.h,
      FIGURE.d,
      VILLAGE_TONE.ash,
    );
  }
}

/** Villagers in sparse single-file lanes; the Trade shows in what is carried. */
function villagers(
  out: Volume[],
  hands: Record<TradeId, number>,
  tick: number,
  still: boolean,
): void {
  const counts = figureCounts(hands);
  (Object.keys(counts) as TradeId[]).forEach((trade, t) => {
    const lane = LANES[trade];
    const n = counts[trade];
    for (let i = 0; i < n; i++) {
      const home = lane.x0 + ((i + 0.5) / n) * (lane.x1 - lane.x0);
      const dx = still ? 0 : pace(tick, i + t) * PIXEL;
      const x = home + dx;
      const id = `villager-${trade}-${i}`;
      box(out, id, 'village', x, 0, lane.z, FIGURE.w, FIGURE.h, FIGURE.d, VILLAGE_TONE.ash);
      carried(out, trade, id, x, lane.z);
    }
  });
}

function carried(out: Volume[], trade: TradeId, id: string, x: number, z: number): void {
  switch (trade) {
    case 'TILLER': // a basket on the hip
      box(out, `${id}-load`, 'village', x + 0.09, 0.1, z, 0.12, 0.08, 0.05, VILLAGE_TONE.ash);
      break;
    case 'BREAKER': // a stone on the shoulder, in the dull Quarry brown
      box(out, `${id}-load`, 'village', x, 0.35, z, 0.11, 0.1, 0.05, VILLAGE_TONE.heap);
      break;
    case 'SCRIBE': // a scroll, upright beside the figure
      box(out, `${id}-load`, 'village', x + 0.08, 0.08, z, 0.03, 0.22, 0.03, VILLAGE_TONE.ash);
      break;
    case 'SALTER': // a pale sack
      box(out, `${id}-load`, 'village', x - 0.08, 0.05, z, 0.1, 0.12, 0.05, VILLAGE_TONE.salt);
      break;
    case 'SPOREWARDEN': // a small lidded crate
      box(out, `${id}-load`, 'village', x - 0.08, 0.06, z, 0.08, 0.08, 0.05, VILLAGE_TONE.ash);
      break;
  }
}

/** The whole Village Elevation for one state, as boxes. */
export function buildVillage(input: VillageInput): Volume[] {
  const out: Volume[] = [];
  ground(out);
  tower(out);
  fort(out);
  silo(out, input.food);
  stair(out, input.villageTierLevel, input.still ? 0 : input.flicker);
  fields(out);
  quarry(out, input.quarryHeap, input.quarryHeapCap);
  study(out, input.hands.SCRIBE, input.posts.SCRIBE);
  vault(out, input.materials, input.relics);
  pans(out, input.saltPansOpen, input.salt);
  shortcutMarks(out, input.shortcuts, input.highlightedShortcut);
  growth(out, input.upgrades ?? {});
  reserves(out, input.reserves);
  villagers(out, input.hands, input.tick, input.still);
  if (input.hovered && input.hovered !== input.selected) {
    out.push(...outlineVolumes(out, input.hovered, 1, 'hover'));
  }
  if (input.selected) {
    out.push(...outlineVolumes(out, input.selected, 2, 'select'));
  }
  return out;
}

/** Horizontal extent of the Village volumes (the Tower and the ground excluded). */
export function villageBounds(volumes: Volume[]): {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
} {
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const v of volumes) {
    if (v.group !== 'village' || v.id === 'ground' || v.id.startsWith('outline-')) continue;
    minX = Math.min(minX, v.x - v.w / 2);
    maxX = Math.max(maxX, v.x + v.w / 2);
    minY = Math.min(minY, v.y - v.h / 2);
    maxY = Math.max(maxY, v.y + v.h / 2);
  }
  return { minX, maxX, minY, maxY };
}

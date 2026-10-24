/**
 * The Village Elevation (architecture doc section 5.2): a pure layout of
 * rectangular volumes. No three.js here, so the bounds and the Annex rules are
 * unit-testable. Every volume is a box; the renderer draws them as one ink
 * instanced mesh. World units: x across, y up from the ground top, z toward the
 * camera. The Tower stands behind the Village and rises into the dither.
 */
import type { PortState } from '../../interfaces/game-state';
import type { Quarter } from '../../interfaces/game-state';
import type { TradeId } from '../../interfaces/village';
import { bellObject, gateObject } from '../launch/launch-objects';
import { barGauge, GAUGE_MIN, notchGauge, slitGauge } from './gauge-kit';
import { RECORDS_TIER, stoneOfCount, wallOfNames } from './tier-four-records';
import { cubeOutlineVolumes } from './cube-outline';
import { EMPTY_PILLAR, referencePillar, type PillarInput } from './reference-pillar';
import {
  BASE_DOOR_ID,
  BASE_DOOR_SUBJECT,
  BELL_SUBJECT,
  outlineVolumes,
  SHORTCUT_MARK_ID,
} from './village-subjects';

export { SHORTCUT_MARK_ID };

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
/**
 * The doorway, in world units (one virtual pixel is 0.05): the lit opening, the
 * lintel over it, the ledge at its sill and the stub of a stair below, to its left.
 */
export const SHORTCUT_DOOR = {
  opening: { w: 0.3, h: 0.5 },
  lintel: { w: 0.5, h: 0.1 },
  ledge: { w: 0.5, h: 0.05 },
  step: { w: 0.2, h: 0.05, dx: 0.35 },
} as const;

/** `port` is the shore: sea, pier and Boat, drawn in the outer right-hand 20% of the frame. */
export type VolumeGroup = 'village' | 'tower' | 'port';

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
  /** How many of them are wounded, for the Fort's second gauge. */
  wounded?: number;
  /** Hands per Trade, from the Trade views. */
  hands: Record<TradeId, number>;
  /** Posts of the Quarry and the Study (null when uncapped). */
  posts: Partial<Record<TradeId, number | null>>;
  quarryHeap: number;
  quarryHeapCap: number;
  /** Materials and Relics in the Vault, and the Credits banked (the Vault Annex's bar). */
  materials: number;
  relics: number;
  credits?: number;
  /** The Spore Cellar is open (Village Tier III) and the Spores it holds, for its slit. */
  sporeCellarOpen?: boolean;
  spores?: number;
  villageTierLevel: number;
  /** Levels owned per Upgrade id (the catalogue's `UpgradeType` names); absent means none. */
  upgrades: Record<string, number>;
  saltPansOpen: boolean;
  /** Salt held, for the crust height. */
  salt: number;
  /** Open Shortcuts only; a locked Shortcut is never passed in, so it draws nothing. */
  shortcuts: VillageShortcut[];
  /** The Port from the frame: hidden draws no sea at all; footing and built follow it. */
  port: PortState;
  /**
   * The Boat lies docked at the pier (the frame's boat state is DOCKED). Only
   * drawn once the Port is built.
   */
  boatDocked: boolean;
  /**
   * The Quarter the ground and the rows are drawn for; null or absent in a Trial
   * (no Year), which draws the plain Village. The Tower never follows it.
   */
  quarter?: Quarter | null;
  /** The danger at sea, 0..1, which sets the sea's tone; null or absent draws it at rest. */
  seaRisk?: number | null;
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
  /** The Start Floor chosen: 0 is the Base door, otherwise an open Shortcut's Floor. Wears the bone outline. */
  startFloor?: number;
  /** Units on the Wall of Names (Village Tier IV), for the names carved on it. */
  fallenCount?: number;
  /** What the reference pillar is carved with: the Highest Floor, the Milestones and the Toll. */
  pillar?: PillarInput;
  /** The gate bell can be rung (set out); false draws it dark with a dashed outline. Default true. */
  bellUsable?: boolean;
  /** The Campaign has ended and waits in OFFER: the Roll stone stands at the Tower's foot. */
  offer?: boolean;
  /** The Thought-Binding Ring's level and highest level: sides of the ring lit on the stair. */
  ringLevel?: number;
  ringMax?: number;
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
  port: 'HIDDEN',
  boatDocked: false,
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
  /** At Village Tier IV the Vault splits into these two, within its own span. */
  strongbox: [2.0, 2.55],
  reliquary: [2.65, 3.2],
  /** The Quarry's Heap building, where the Materials wait from Tier IV, behind the Quarry. */
  stack: [0.2, 0.95],
  pans: [0.0, 1.1],
  /** The Spore Cellar lies at the Fort's foot, where its Sporewardens work. */
  cellar: [-3.1, -2.0],
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
/**
 * The open ground in front of the lanes, where the Cellar and the Pans already stand: the supply
 * store stands here, not behind the stair and the Tower's base, which hid it (#134).
 */
const FRONT_Z = 2.0;
/**
 * Tall Upgrade modules (the watchtower, the obelisk, the brazier) stand on this row, before the
 * store and the Pans, so no building stands in front of them; the low ones (roof, stockade) stand
 * on the row in front of it (#135).
 */
const TALL_ROW_Z = 2.35;
const FRONT_ROW_Z = 2.8;
/** The spear rack stands at the very front, so the taller modules behind it are never hidden by it. */
const RACK_Z = 3.1;
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

/** Fill of the Vault Annex's Credits bar, 0..1: `min(1, log10(1 + credits) / 4)`. */
export function creditsFill(credits: number): number {
  return Math.min(1, Math.log10(1 + Math.max(0, credits)) / 4);
}

/** Notches on the Vault Annex for Relics: one lit per Relic held, of this many. */
export const RELIC_NOTCHES = 5;
export function litRelicNotches(relics: number): number {
  return Math.min(RELIC_NOTCHES, Math.max(0, Math.floor(relics)));
}

/** Crust of the Salt Pans and slit of the Spore Cellar, 0..1: `min(1, log10(1 + stock) / 3)`. */
export function stockFill(stock: number): number {
  return Math.min(1, Math.log10(1 + Math.max(0, stock)) / 3);
}

/** Notches on the Salt Pans: lit in proportion to the crust, at least one when there is Salt. */
export const SALT_NOTCHES = 5;
export function litSaltNotches(salt: number): number {
  if (salt <= 0) return 0;
  return Math.max(1, Math.round(SALT_NOTCHES * stockFill(salt)));
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

function fort(out: Volume[], reserveCount: number, wounded: number): void {
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
  // Two gauges on the keep's front, left of the door: Reserves held, and the share of them wounded.
  const held = Math.max(0, reserveCount);
  barGauge(out, 'fort-reserves', {
    x: x - 0.2,
    bottom: 0.66,
    z: 0.42,
    length: 0.5,
    fill: held / MAX_RESERVE_FIGURES,
  });
  barGauge(out, 'fort-wounded', {
    x: x - 0.2,
    bottom: 0.48,
    z: 0.42,
    length: 0.5,
    fill: held === 0 ? 0 : Math.max(0, wounded) / held,
  });
  // The near-miss pad: an invisible box around the keep and its plinth, so a click just
  // beside the Fort still lands on it. It stops short of the yard, whose figures are the Fort too.
  box(out, 'fort-hit', 'village', x, 0, 0.1, w + 0.1, 1.05, 0.9, VILLAGE_TONE.dark, 0);
}

function silo(out: Volume[], food: number): void {
  const x = mid(ANNEX_X.silo);
  const w = span(ANNEX_X.silo);
  box(out, 'silo', 'village', x, 0, SILO_Z, w, 1.7, 0.3, VILLAGE_TONE.silo);
  // The slit is a dark channel; the bone fill rises inside it with the food.
  slitGauge(out, 'silo', {
    x,
    bottom: 0.25,
    z: SILO_Z + 0.16,
    height: 1.2,
    fill: siloFill(food),
  });
  // The near-miss pad: an invisible box around the silo, taller than it and reaching a
  // little to the right, so a click just beside its thin shaft still lands on it. The
  // bell's gate stands in front of its lower left, so the pad grows where nothing does.
  const left = ANNEX_X.silo[0] - 0.05;
  const right = ANNEX_X.silo[1] + 0.25;
  box(out, 'silo-hit', 'village', (left + right) / 2, 0, SILO_Z, right - left, 1.9, 0.3, VILLAGE_TONE.dark, 0);
}

/**
 * The causeway stair (#138): a narrow spine of many shallow treads, a party's width, climbing
 * from the Gate's foot to a landing before the Base door. A low parapet runs along its front edge
 * with the Tier lamps on it, and dark ground lies on every side of the spine and its landing,
 * which stand across a drop from the Tower's foot.
 */
export const STAIR = {
  x0: -1.85,
  run: 1.0,
  steps: 12,
  rise: 0.95,
  z: 0.2,
  d: 0.4,
  landing: { x0: -0.85, x1: -0.25 },
} as const;
/** The treads that carry a lamp, foot to head (12 is the landing): the Ring hangs between the second and third. */
const LAMP_TREADS = [1, 4, 10, 12] as const;

function stair(out: Volume[], tier: number, flicker: number): void {
  const { x0, run, steps, rise, z, d, landing } = STAIR;
  const tw = run / steps;
  const frontZ = z + d / 2 - 0.03;
  for (let i = 0; i < steps; i++) {
    const h = (rise * (i + 1)) / steps;
    const x = x0 + tw * (i + 0.5);
    box(out, `stair-${i}`, 'village', x, 0, z, tw, h, d, VILLAGE_TONE.stone);
    // A tread line: a thin bright strip across the step's leading edge, and a dark one at its
    // heel where the next tread rises, so the many shallow treads read from the front.
    box(out, `tread-${i}`, 'village', x - tw / 2 + 0.014, h, z, 0.028, 0.012, d, 0.9);
    box(out, `tread-heel-${i}`, 'village', x + tw / 2 - 0.01, h, z, 0.02, 0.012, d, VILLAGE_TONE.dark);
    // A dark seam on the front face at this tread's height, running on under the next treads and
    // the landing: the many treads read as courses seen from the front.
    const seamX0 = x - tw / 2;
    const seamX1 = landing.x1;
    box(out, `seam-${i}`, 'village', (seamX0 + seamX1) / 2, h - 0.012, z + d / 2 + 0.004, seamX1 - seamX0, 0.012, 0.01, VILLAGE_TONE.dark);
    // The parapet: a low wall along the front edge, in the dull footing tone.
    box(out, `parapet-${i}`, 'village', x, h, frontZ, tw, 0.07, 0.06, VILLAGE_TONE.footing);
  }
  // The landing before the Base door, flush with the Tower's foot: the threshold stands on it.
  const lx = (landing.x0 + landing.x1) / 2;
  box(out, 'stair-landing', 'village', lx, 0, 0.1, landing.x1 - landing.x0, rise - 0.05, 0.6, VILLAGE_TONE.stone);
  box(out, 'parapet-landing', 'village', lx, rise, frontZ, landing.x1 - landing.x0, 0.07, 0.06, VILLAGE_TONE.footing);
  // The drop: dark ground on every side of the spine and the landing.
  const left = x0 - 0.1;
  const right = landing.x1 + 0.1;
  const dark = (id: string, x: number, zz: number, w: number, dd: number) =>
    box(out, `chasm-${id}`, 'tower', x, 0, zz, w, 0.01, dd, VILLAGE_TONE.dark);
  dark('front', (left + right) / 2, z + d / 2 + 0.05, right - left, 0.1);
  dark('back', (left + x0 + run) / 2, -0.15, x0 + run - left, 0.1);
  dark('foot', left + 0.05, z, 0.1, d);
  dark('side', right - 0.05, 0.1, 0.1, 0.6);
  // Village Tier as lamps on the parapet, one more lit per level.
  const lit = litLamps(tier);
  for (let i = 0; i < VILLAGE_TIERS; i++) {
    const step = LAMP_TREADS[i];
    const onLanding = step >= steps;
    const x = onLanding ? landing.x0 + 0.08 : x0 + tw * (step + 0.5);
    const h = onLanding ? rise : (rise * (step + 1)) / steps;
    // Plus or minus 8% on a slow noise of about 0.3 Hz, per lamp.
    const wobble = 1 + 0.08 * Math.sin(flicker * 2 * Math.PI * 0.3 + i * 2.1);
    const on = i < lit;
    box(out, `lamp-${i}`, 'village', x, h + 0.17, frontZ, 0.07, 0.12, 0.07, on ? Math.min(1, VILLAGE_TONE.lit * wobble) : VILLAGE_TONE.lampOff);
    box(out, `lamp-post-${i}`, 'village', x, h + 0.07, frontZ, 0.02, 0.1, 0.02, VILLAGE_TONE.timber);
  }
}

function fields(out: Volume[], quarter: Quarter | null): void {
  const rows = quarter ? QUARTER_LOOK[quarter].rows : 0.95;
  const x = mid(ANNEX_X.fields);
  // Three stepped terraces in edge lines and furrow hatch, no building.
  for (let t = 0; t < 3; t++) {
    const w = span(ANNEX_X.fields) - t * 0.1;
    const z = 1.2 - t * 0.3;
    box(out, `field-${t}`, 'village', x, 0, z, w, 0.08 + t * 0.1, 0.28, VILLAGE_TONE.footing);
    const top = 0.08 + t * 0.1;
    for (let f = 0; f < 3; f++) {
      box(out, `furrow-${t}-${f}`, 'village', x, top, z - 0.09 + f * 0.09, w - 0.1, 0.012, 0.02, rows);
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
  // The Heap's scale: a bar on the ground before the blocks, filled as the Heap is (twelve blocks
  // read as a share; the bar gives the same share in one line, empty or full).
  barGauge(out, 'heap-scale', {
    x: x0 + 0.1 + 0.225,
    bottom: 0,
    z: 0.95,
    length: 0.6,
    fill: heapCap > 0 ? heap / heapCap : 0,
  });
  // The near-miss pad, as the silo's: the blocks are small, so a click near them lands on the Heap.
  box(out, 'heap-hit', 'village', x0 + 0.325, 0, 0.88, 0.8, 0.4, 0.3, VILLAGE_TONE.dark, 0);
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

function vault(out: Volume[], materials: number, relics: number, credits: number): void {
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
  // Credits have no form of their own: a bar across the wall's head. Relics are few: a row of
  // notches above the door, one lit for each. Materials are the crates, so they need no more.
  barGauge(out, 'vault-credits', {
    x,
    bottom: 0.76,
    z: 0.76,
    length: 0.9,
    fill: creditsFill(credits),
  });
  notchGauge(out, 'vault-relics', {
    x,
    bottom: 0.58,
    z: 0.76,
    count: RELIC_NOTCHES,
    lit: litRelicNotches(relics),
  });
  // The near-miss pad around the block and its roof crates.
  box(out, 'vault-hit', 'village', x, 0, 0.35, w + 0.1, 1.2, 0.8, VILLAGE_TONE.dark, 0);
}

/** The Village Tier at which the Vault splits (IV). */
export const SPLIT_TIER = 4;

/** A locked building is a hairline rectangle on the ground, no label (as the Pans'). */
function footing(out: Volume[], id: string, range: readonly [number, number], z: number, d: number): void {
  const [x0, x1] = range;
  const tone = VILLAGE_TONE.footing + 0.3;
  box(out, `${id}-footing-n`, 'village', mid(range), 0, z - d / 2, x1 - x0, 0.012, 0.012, tone);
  box(out, `${id}-footing-s`, 'village', mid(range), 0, z + d / 2, x1 - x0, 0.012, 0.012, tone);
  box(out, `${id}-footing-w`, 'village', x0, 0, z, 0.012, 0.012, d, tone);
  box(out, `${id}-footing-e`, 'village', x1, 0, z, 0.012, 0.012, d, tone);
  // A pad so the thin lines can be pointed at.
  box(out, `${id}-hit`, 'village', mid(range), 0, z, x1 - x0 + 0.1, 0.1, d + 0.1, VILLAGE_TONE.dark, 0);
}

/** One of the two blocks the Vault splits into: a stone block, one door slab. Returns its centre x. */
function vaultBlock(out: Volume[], id: string, range: readonly [number, number]): number {
  const x = mid(range);
  const w = span(range);
  box(out, id, 'village', x, 0, 0.35, w, 0.9, 0.8, VILLAGE_TONE.stone);
  box(out, `${id}-corbel`, 'village', x, 0.9, 0.35, w - 0.1, 0.08, 0.7, VILLAGE_TONE.stone);
  box(out, `${id}-door`, 'village', x, 0, 0.76, 0.2, 0.5, 0.02, VILLAGE_TONE.dark);
  return x;
}

/**
 * Village Tier IV: the Strongbox (Credits, a bar across its head), the Reliquary
 * (Relics, a row of notches) and the Quarry's Heap building (Materials, stacked
 * crates and a bar). Below Tier IV the three stand as footings: the Strongbox and
 * Reliquary in front of the Vault, the Heap building behind the Quarry.
 */
function splitVault(
  out: Volume[],
  tier: number,
  materials: number,
  relics: number,
  credits: number,
): void {
  if (tier < SPLIT_TIER) {
    footing(out, 'strongbox', ANNEX_X.strongbox, 1.45, 0.4);
    footing(out, 'reliquary', ANNEX_X.reliquary, 1.45, 0.4);
    footing(out, 'stack', ANNEX_X.stack, -0.55, 0.4);
    return;
  }
  const sx = vaultBlock(out, 'strongbox', ANNEX_X.strongbox);
  barGauge(out, 'strongbox-credits', {
    x: sx,
    bottom: 0.76,
    z: 0.76,
    length: 0.4,
    fill: creditsFill(credits),
  });
  box(out, 'strongbox-hit', 'village', sx, 0, 0.35, span(ANNEX_X.strongbox) + 0.05, 1.0, 0.8, VILLAGE_TONE.dark, 0);

  const rx = vaultBlock(out, 'reliquary', ANNEX_X.reliquary);
  // Five notches are wider than this block, so they stand in two rows: three, then two above.
  const litRelics = litRelicNotches(relics);
  notchGauge(out, 'reliquary-relics', { x: rx, bottom: 0.76, z: 0.76, count: 3, lit: Math.min(3, litRelics) });
  notchGauge(out, 'reliquary-relics-more', {
    x: rx,
    bottom: 0.76 + 2 * GAUGE_MIN,
    z: 0.76,
    count: RELIC_NOTCHES - 3,
    lit: Math.max(0, litRelics - 3),
  });
  box(out, 'reliquary-hit', 'village', rx, 0, 0.35, span(ANNEX_X.reliquary) + 0.05, 1.0, 0.8, VILLAGE_TONE.dark, 0);

  // The Quarry's Heap building: the Materials the Vault held, as crates stacked in two rows
  // on a low plinth, and a bar on the ground before them.
  const px = mid(ANNEX_X.stack);
  const [s0] = ANNEX_X.stack;
  box(out, 'stack-plinth', 'village', px, 0, -0.55, span(ANNEX_X.stack), 0.08, 0.4, VILLAGE_TONE.stone);
  const crates = vaultCrates(materials, 0);
  for (let i = 0; i < crates; i++) {
    const row = i < 6 ? 0 : 1;
    const col = row === 0 ? i : i - 6;
    box(out, `stack-crate-${i}`, 'village', s0 + 0.1 + col * 0.11, 0.08 + row * 0.1, -0.55, 0.09, 0.1, 0.12, VILLAGE_TONE.timber);
  }
  barGauge(out, 'stack-scale', {
    x: px,
    bottom: 0,
    z: -0.3,
    length: 0.6,
    fill: crates / 12,
  });
  box(out, 'stack-hit', 'village', px, 0, -0.45, span(ANNEX_X.stack) + 0.1, 0.4, 0.6, VILLAGE_TONE.dark, 0);
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
  const crust = stockFill(salt);
  for (let i = 0; i < 3; i++) {
    const x = x0 + 0.17 + i * 0.38;
    box(out, `pan-${i}`, 'village', x, 0, 2.05, 0.34, 0.12, 0.4, VILLAGE_TONE.stone);
    box(out, `pan-well-${i}`, 'village', x, 0.1, 2.05, 0.26, 0.02, 0.32, VILLAGE_TONE.dark);
    if (crust > 0) {
      box(out, `pan-crust-${i}`, 'village', x, 0.1, 2.05, 0.26, 0.02 * crust + 0.002, 0.32, VILLAGE_TONE.salt);
    }
  }
  // The crust is thin, so the Pans add a scale: a row of notches before them, lit with the crust.
  notchGauge(out, 'pan-scale', {
    x: mid(ANNEX_X.pans),
    bottom: 0,
    z: 2.4,
    count: SALT_NOTCHES,
    lit: litSaltNotches(salt),
  });
  // The near-miss pad around the basins and their scale.
  box(out, 'pan-hit', 'village', mid(ANNEX_X.pans), 0, 2.2, x1 - x0 + 0.1, 0.3, 0.7, VILLAGE_TONE.dark, 0);
}

/**
 * The Spore Cellar (Village Tier III): sunk below ground, a stair into a low
 * flat-roofed clad box with a stepped ceiling, and one slit that lights with the Spores.
 */
function cellar(out: Volume[], spores: number): void {
  const x = mid(ANNEX_X.cellar);
  const w = span(ANNEX_X.cellar);
  const z = 2.0;
  box(out, 'cellar', 'village', x, 0, z, w, 0.3, 0.5, VILLAGE_TONE.timber);
  // A corbelled ceiling: two narrower courses, never a curve.
  box(out, 'cellar-corbel-0', 'village', x, 0.3, z, w - 0.2, 0.05, 0.4, VILLAGE_TONE.timber);
  box(out, 'cellar-corbel-1', 'village', x, 0.35, z, w - 0.5, 0.05, 0.3, VILLAGE_TONE.timber);
  // The stair down: three dark treads in front, each lower than the last.
  for (let i = 0; i < 3; i++) {
    box(out, `cellar-stair-${i}`, 'village', x - 0.3 + i * 0.1, 0, z + 0.3 + i * 0.05, 0.1, 0.2 - i * 0.05, 0.05, VILLAGE_TONE.dark);
  }
  slitGauge(out, 'cellar', {
    x: x + 0.25,
    bottom: 0.05,
    z: z + 0.26,
    height: 0.2,
    fill: stockFill(spores),
  });
  box(out, 'cellar-hit', 'village', x, 0, z + 0.1, w + 0.1, 0.5, 0.8, VILLAGE_TONE.dark, 0);
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
  /**
   * The module stands on open ground in front of the buildings it projects over (in the far view),
   * so it is pointed at part by part and wins over them, instead of nested in their boxes (#134).
   */
  inFront?: boolean;
  /** Draws the module for the `n`th level owned (0-based). */
  build(out: Volume[], id: string, n: number): void;
}

/** The module table: one entry per Upgrade in the catalogue (see `INVISIBLE_UPGRADES`). */
export const UPGRADE_MODULES: Record<string, GrowthModule> = {
  // The Field Medic: a lean-to roof over the Spore Cellar's stair, on the open ground before it.
  UNLOCK_HEALER: {
    cap: 1,
    inFront: true,
    build(out, id) {
      const x = -2.4;
      box(out, `${id}:roof`, 'village', x, 0.45, FRONT_ROW_Z, 1.2, 0.04, 0.5, VILLAGE_TONE.timber);
      box(out, `${id}:post-a`, 'village', x - 0.55, 0, FRONT_ROW_Z - 0.15, 0.04, 0.45, 0.04, VILLAGE_TONE.timber);
      box(out, `${id}:post-b`, 'village', x + 0.55, 0, FRONT_ROW_Z - 0.15, 0.04, 0.45, 0.04, VILLAGE_TONE.timber);
    },
  },
  // The Ironclad: a stockade fence across the front of the Quarry and the Pans.
  UNLOCK_TANK: {
    cap: 1,
    inFront: true,
    build(out, id) {
      const [x0, x1] = ANNEX_X.pans;
      for (let i = 0; i < 6; i++) {
        box(out, `${id}:post-${i}`, 'village', x0 + 0.1 + (i * (x1 - x0 - 0.2)) / 5, 0, FRONT_ROW_Z, 0.05, 0.3, 0.05, VILLAGE_TONE.timber);
      }
      box(out, `${id}:rail`, 'village', mid(ANNEX_X.pans), 0.2, FRONT_ROW_Z, x1 - x0 - 0.1, 0.03, 0.03, VILLAGE_TONE.timber);
    },
  },
  // The Shadow: a watchtower on the open ground before the Vault, between its two halves.
  UNLOCK_ROGUE: {
    cap: 1,
    inFront: true,
    build(out, id) {
      box(out, `${id}:shaft`, 'village', 2.95, 0, TALL_ROW_Z, 0.3, 1.6, 0.3, VILLAGE_TONE.stone);
      box(out, `${id}:cap`, 'village', 2.95, 1.6, TALL_ROW_Z, 0.46, 0.16, 0.46, VILLAGE_TONE.timber);
      box(out, `${id}:slit`, 'village', 2.95, 1.2, TALL_ROW_Z + 0.17, 0.04, 0.2, 0.02, VILLAGE_TONE.dark);
    },
  },
  // The Necromancer: a lone dark obelisk on the open ground before the Study.
  UNLOCK_NECROMANCER: {
    cap: 1,
    inFront: true,
    build(out, id) {
      box(out, `${id}:obelisk`, 'village', 1.45, 0, TALL_ROW_Z, 0.16, 1.3, 0.16, VILLAGE_TONE.dark);
      box(out, `${id}:plinth`, 'village', 1.45, 0, TALL_ROW_Z, 0.3, 0.1, 0.3, VILLAGE_TONE.stone);
    },
  },
  // The Draco-Metamorph: a squat bone-lit brazier on the open ground beside the supply store, clear of the gate bell.
  UNLOCK_DRACO_METAMORPH: {
    cap: 1,
    inFront: true,
    build(out, id) {
      box(out, `${id}:bowl`, 'village', -1.18, 0, TALL_ROW_Z, 0.34, 0.5, 0.34, VILLAGE_TONE.stone);
      box(out, `${id}:ember`, 'village', -1.18, 0.5, TALL_ROW_Z, 0.2, 0.06, 0.2, VILLAGE_TONE.lit);
    },
  },
  // Supply Lines: a second silo before the Fields, then crates stood beside it.
  SUPPLY_LINES: {
    cap: 4,
    inFront: true,
    build(out, id, n) {
      if (n === 0) {
        box(out, `${id}:silo`, 'village', -0.85, 0, FRONT_Z, 0.3, 1.4, 0.3, VILLAGE_TONE.silo);
        return;
      }
      box(out, `${id}:crate`, 'village', -0.7 + n * 0.17, 0, FRONT_Z, 0.14, 0.14 + (n % 2) * 0.06, 0.14, VILLAGE_TONE.timber);
    },
  },
  // Sharpened Arms: a spear stood in the rack at the very front, one per level, nearest the Vault.
  SHARPENED_ARMS: {
    cap: 4,
    inFront: true,
    build(out, id, n) {
      const x = 1.75 + n * 0.15;
      box(out, `${id}:spear`, 'village', x, 0, RACK_Z, 0.03, 0.8, 0.03, VILLAGE_TONE.ash);
      box(out, `${id}:head`, 'village', x, 0.8, RACK_Z, 0.07, 0.1, 0.03, VILLAGE_TONE.lit);
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
    // An Upgrade the Village offers is a thing to point at: its footing until it is bought,
    // and a pad around where the module stands, for picking.
    if (upgrades[id] !== undefined) upgradeMarks(out, id, n);
  }
}

/** The least a footing or a pad is wide, so a thin post can still be pointed at. */
const MARK_MIN = 0.26;
const MARK_PAD = 0.08;

/**
 * The footing of an unbought Upgrade (a hairline rectangle on the ground where its
 * module will stand, no label) and the invisible pad that makes the module
 * pickable: `upgrade-footing:<id>:*` and `upgrade-hit:<id>`, both the Upgrade's building.
 */
function upgradeMarks(out: Volume[], id: string, owned: number): void {
  const module = UPGRADE_MODULES[id];
  const shown: Volume[] = [];
  for (let i = 0; i < Math.max(1, owned); i++) module.build(shown, `grow:${id}:${i}`, i);
  const x0 = Math.min(...shown.map((v) => v.x - v.w / 2));
  const x1 = Math.max(...shown.map((v) => v.x + v.w / 2));
  const z0 = Math.min(...shown.map((v) => v.z - v.d / 2));
  const z1 = Math.max(...shown.map((v) => v.z + v.d / 2));
  const top = Math.max(...shown.map((v) => v.y + v.h / 2));
  const cx = (x0 + x1) / 2;
  const cz = (z0 + z1) / 2;
  const w = Math.max(MARK_MIN, x1 - x0);
  const d = Math.max(MARK_MIN, z1 - z0);
  if (owned === 0) {
    const tone = VILLAGE_TONE.footing + 0.3;
    box(out, `upgrade-footing:${id}:n`, 'village', cx, 0, cz - d / 2, w, 0.012, 0.012, tone);
    box(out, `upgrade-footing:${id}:s`, 'village', cx, 0, cz + d / 2, w, 0.012, 0.012, tone);
    box(out, `upgrade-footing:${id}:w`, 'village', cx - w / 2, 0, cz, 0.012, 0.012, d, tone);
    box(out, `upgrade-footing:${id}:e`, 'village', cx + w / 2, 0, cz, 0.012, 0.012, d, tone);
  }
  // Unbought, the pad reaches no higher than the footing's hairline: nothing stands there to pick.
  const height = owned === 0 ? 0.3 : top + MARK_PAD;
  box(out, `upgrade-hit:${id}`, 'village', cx, 0, cz, w + 2 * MARK_PAD, height, d + 2 * MARK_PAD, VILLAGE_TONE.dark, 0);
}

/** Where the Thought-Binding Ring hangs: on a post set in the stair's eighth tread, between the lamps. */
const RING_SPOT = { x: STAIR.x0 + (STAIR.run / STAIR.steps) * 7.5, bottom: (STAIR.rise * 8) / STAIR.steps, z: 0.2 } as const;
/** Sides of the ring lit for a level: the share of the highest level, in quarters, rounded up. */
export function litRingSides(level: number, max: number): number {
  if (!(level > 0) || !(max > 0)) return 0;
  return Math.min(4, Math.ceil((4 * level) / max));
}

function ring(out: Volume[], level: number, max: number): void {
  const { x, bottom, z } = RING_SPOT;
  const size = 0.28;
  const t = 0.05;
  const lit = litRingSides(level, max);
  box(out, 'ring-post', 'village', x, bottom, z, 0.06, 0.12, 0.06, VILLAGE_TONE.stone);
  const sides: Array<[string, number, number, number, number]> = [
    ['n', x, bottom + 0.12 + size - t, size, t],
    ['e', x + (size - t) / 2, bottom + 0.12, t, size],
    ['s', x, bottom + 0.12, size, t],
    ['w', x - (size - t) / 2, bottom + 0.12, t, size],
  ];
  sides.forEach(([name, sx, sy, w, h], i) => {
    box(out, `ring-band-${name}`, 'village', sx, sy, z, w, h, t, i < lit ? VILLAGE_TONE.lit : VILLAGE_TONE.footing);
  });
  box(out, 'ring-hit', 'village', x, bottom, z, size + 0.16, 0.12 + size + 0.06, 0.3, VILLAGE_TONE.dark, 0);
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
 * A doorway cut into the Tower's face per open Shortcut, at its Floor's relative
 * height: a lit opening in a dark frame of two jambs and a lintel, a ledge at the sill and
 * a stair stub below it. No numeral. Locked Shortcuts are not in the input, so they
 * leave nothing, not even a hairline. Hover outlines come from the subjects module.
 */
function shortcutMarks(out: Volume[], shortcuts: VillageShortcut[]): void {
  for (const sc of shortcuts) {
    if (sc.floor <= 0) continue;
    doorway(out, `${SHORTCUT_MARK_ID}${sc.id}`, SHORTCUT_MARK_X, shortcutMarkHeight(sc.floor), SHORTCUT_MARK_Z);
  }
}

/** Where the bell's gate stands: at the foot of the stair, in front of it. */
export const BELL_SPOT = { x: -1.95, bottom: 0, z: 0.8 } as const;
/** The Base door: cut in the Base slab's front face, over the landing at the stair's head. */
export const BASE_DOOR_X = (STAIR.landing.x0 + STAIR.landing.x1) / 2;
const BASE_FRONT_Z = -0.2;

/**
 * The doorway kit shared by an open Shortcut and the Base door: a lit opening in a
 * dark frame of two jambs and a lintel, a ledge at the sill and a stair stub below.
 */
function doorway(out: Volume[], id: string, x: number, y: number, z: number): void {
  const d = SHORTCUT_DOOR;
  const part = (n: string, px: number, py: number, w: number, h: number, depth: number, pz: number, tone: number) =>
    box(out, `${id}:${n}`, 'tower', px, py, pz, w, h, depth, tone);
  part('opening', x, y, d.opening.w, d.opening.h, 0.04, z, VILLAGE_TONE.lit);
  const jamb = (d.lintel.w - d.opening.w) / 2;
  const jx = (d.opening.w + jamb) / 2;
  part('jamb-w', x - jx, y, jamb, d.opening.h, 0.05, z - 0.005, VILLAGE_TONE.dark);
  part('jamb-e', x + jx, y, jamb, d.opening.h, 0.05, z - 0.005, VILLAGE_TONE.dark);
  part('lintel', x, y + d.opening.h, d.lintel.w, d.lintel.h, 0.07, z + 0.01, VILLAGE_TONE.dark);
  part('ledge', x, y - d.ledge.h, d.ledge.w, d.ledge.h, 0.2, z + 0.08, VILLAGE_TONE.stone);
  part('step', x - d.step.dx, y - d.ledge.h - d.step.h, d.step.w, d.step.h, 0.16, z + 0.06, VILLAGE_TONE.stone);
}

/** Where the Roll stone stands: at the Tower's foot, in front of the Quarry, clear of the lanes. */
export const ROLL_STONE = { x: 0.05, z: 0.62 } as const;

/**
 * The Roll stone, only while the Succession waits: a tall pale slab on a plinth
 * with a timber cap, set at the Tower's foot, and a column of dark notches cut in
 * it, one per Course, the last one lit. Ids begin `roll-`; `roll-hit` is the
 * near-miss pad.
 */
function rollStone(out: Volume[]): void {
  const { x, z } = ROLL_STONE;
  box(out, 'roll-plinth', 'tower', x, 0, z, 0.62, 0.14, 0.4, VILLAGE_TONE.stone);
  box(out, 'roll-slab', 'tower', x, 0.14, z, 0.44, 1.0, 0.2, 0.9);
  box(out, 'roll-cap', 'tower', x, 1.14, z, 0.54, 0.08, 0.28, VILLAGE_TONE.timber);
  for (let i = 0; i < 4; i++) {
    box(out, `roll-notch-${i}`, 'tower', x, 0.32 + i * 0.2, z + 0.11, 0.28, 0.07, 0.02, i === 3 ? VILLAGE_TONE.lit : VILLAGE_TONE.dark);
  }
  box(out, 'roll-hit', 'tower', x, 0, z, 0.78, 1.3, 0.5, VILLAGE_TONE.dark, 0);
}

/** The Base slab's top: the door's slot climbs past it into the Tower's face. */
const SLAB_TOP = BASE_GAP + 1.25;
/** The Tower's own face, in front of which the door's upper blocks stand. */
const TOWER_FACE_Z = -0.3;
/** The Base slab's face is built of great blocks in these courses: `[bottom, top]`. */
const FACE_COURSES: ReadonlyArray<readonly [number, number]> = [
  [BASE_GAP + 0.02, 0.92],
  [STAIR.rise, 1.27],
  [1.3, SLAB_TOP],
];
/** Widths the great blocks of a course cycle through, so the joints never line up. */
const FACE_WIDTHS: ReadonlyArray<readonly number[]> = [
  [0.9, 0.6, 1.1, 0.7],
  [0.7, 1.0, 0.6, 0.9],
  [1.1, 0.6, 0.8, 0.7],
];
/** The door's frame, in world units: the slot's width and ends, and the width of one jamb block. */
const BASE_SLOT = { w: 0.26, bottom: STAIR.rise, top: 1.95, jamb: 0.46 } as const;

/**
 * The Start Floor 0 choice, the Base door (#138): a tall, narrow, dark slot cut between two
 * jambs of great stacked blocks, a lintel block over it and a threshold ledge at its sill, set
 * in a face of great blocks with dark joints. The slot climbs past the slab into the Tower's
 * face. Every part of the door is the one `base-door` subject; the blocks around it are the Tower's.
 */
function baseDoor(out: Volume[]): void {
  const id = BASE_DOOR_ID;
  const x = BASE_DOOR_X;
  const z = BASE_FRONT_Z;
  const { w, bottom, top, jamb } = BASE_SLOT;
  const part = (n: string, px: number, py: number, pw: number, ph: number, pd: number, pz: number, tone: number) =>
    box(out, `${id}:${n}`, 'tower', px, py, pz, pw, ph, pd, tone);
  const doorLeft = x - w / 2 - jamb;
  const doorRight = x + w / 2 + jamb;
  // The face's dark joints: a dark panel the blocks sit on, with a gap of one joint between them.
  box(out, 'base-face', 'tower', 0, BASE_GAP, z, 4.8, 1.25, 0.02, VILLAGE_TONE.dark);
  FACE_COURSES.forEach(([lo, hi], c) => {
    let bx = -2.4;
    for (let i = 0; bx < 2.39; i++) {
      const bw = Math.min(FACE_WIDTHS[c][i % FACE_WIDTHS[c].length], 2.4 - bx);
      const a0 = bx + 0.015;
      const a1 = bx + bw - 0.015;
      bx += bw;
      // The door's own blocks stand where the door is: the courses above the plinth give way to it.
      const pieces: Array<[number, number]> = [];
      if (c === 0 || a1 <= doorLeft || a0 >= doorRight) {
        pieces.push([a0, a1]);
      } else {
        if (a0 < doorLeft - 0.05) pieces.push([a0, doorLeft - 0.015]);
        if (a1 > doorRight + 0.05) pieces.push([doorRight + 0.015, a1]);
      }
      pieces.forEach(([x0, x1], k) => {
        box(out, `base-block-${c}-${i}-${k}`, 'tower', (x0 + x1) / 2, lo, z + 0.03, x1 - x0, hi - lo - 0.03, 0.06, 0.56 + 0.08 * ((i + c) % 2));
      });
    }
  });
  // The slot: dark, in the slab's face and on up the Tower's own.
  part('opening', x, bottom, w, SLAB_TOP - bottom, 0.03, z - 0.005, VILLAGE_TONE.dark);
  part('opening-up', x, SLAB_TOP, w, top - SLAB_TOP, 0.04, TOWER_FACE_Z + 0.02, VILLAGE_TONE.dark);
  // Each jamb is three great blocks stacked with a dark joint between, the top one on the Tower.
  const blocks: ReadonlyArray<readonly [number, number, number, number]> = [
    [bottom, 1.27, z + 0.05, 0.66],
    [1.3, SLAB_TOP, z + 0.05, 0.6],
    [SLAB_TOP + 0.03, top, TOWER_FACE_Z + 0.1, 0.66],
  ];
  for (const side of [-1, 1]) {
    const jx = x + side * (w / 2 + jamb / 2);
    blocks.forEach(([lo, hi, bz, tone], k) => {
      part(`jamb-${side < 0 ? 'w' : 'e'}-${k}`, jx - (k === 1 ? side * 0.03 : 0), lo, jamb - (k === 1 ? 0.06 : 0), hi - lo, 0.12, bz, tone);
    });
  }
  // The lintel: one huge block across both jambs, and the threshold ledge under the slot.
  part('lintel', x, top + 0.03, doorRight - doorLeft, 0.2, 0.14, TOWER_FACE_Z + 0.1, 0.62);
  part('ledge', x, bottom - 0.05, 0.5, 0.05, 0.24, z + 0.1, VILLAGE_TONE.stone);
}

/** The shore starts here: the right-hand 20% of the frame, beyond the Village's 60%. */
export const SHORE_X0 = FRAME_WIDTH * 0.3;
export const SHORE_X1 = FRAME_WIDTH / 2;
/** The pier starts just past the Vault and runs out over the water. */
const PIER_X0 = 3.35;
const PIER_Z = 0.9;
const PIER_DECK = 0.2;

/**
 * Sea bands: horizontal strips with gaps, no waves, so the ink dither reads as
 * still black water. Each is `[bottom, from, to, tone]`, `from` and `to` as a
 * share of the strip's width.
 */
const SEA_BANDS: ReadonlyArray<readonly [number, number, number, number]> = [
  [-0.22, 0.0, 0.7, 0.45],
  [-0.14, 0.25, 1.0, 0.6],
  [-0.06, 0.0, 0.45, 0.5],
  [-0.06, 0.6, 1.0, 0.5],
  [0.02, 0.15, 0.8, 0.62],
  [0.1, 0.0, 0.3, 0.4],
  [0.1, 0.5, 0.9, 0.4],
];

/**
 * The Port. Hidden: nothing, the Village is landlocked. Footing: a hairline
 * rectangle, a short pier and a strip of sea. Built: the full pier, and the Boat's
 * dark hull once it is docked. Everything sits at x >= 3.3 (the outer 20%).
 */
function port(out: Volume[], state: PortState, boatDocked: boolean, seaRisk: number | null | undefined): void {
  if (state === 'HIDDEN') return;
  const w = SHORE_X1 - SHORE_X0 - 0.1;
  const sx = SHORE_X0 + 0.05;
  // The sea: a dark body and bands, only in the outer right-hand 20%.
  // The danger at sea sets its tone: a calm sea is dark and quiet, a dangerous one
  // is darker still with bolder bands (the number is also in words in the UI).
  const unrest = seaUnrest(seaRisk);
  box(out, 'sea-body', 'port', sx + w / 2, -0.3, 1.2, w, 0.4, 2.0, VILLAGE_TONE.ground - 0.1 * unrest);
  SEA_BANDS.forEach(([bottom, a, b, tone], i) => {
    box(out, `sea-band-${i}`, 'port', sx + ((a + b) / 2) * w, bottom, 2.205, (b - a) * w, 0.035, 0.01, Math.min(1, tone + 0.3 * unrest));
  });
  // The footing is a hairline rectangle on the ground, as the Pans' is.
  const fx0 = PIER_X0;
  const fx1 = PIER_X0 + 0.7;
  const d = 0.5;
  const line = VILLAGE_TONE.footing + 0.3;
  box(out, 'port-footing-n', 'port', (fx0 + fx1) / 2, 0, PIER_Z - d / 2, fx1 - fx0, 0.012, 0.012, line);
  box(out, 'port-footing-s', 'port', (fx0 + fx1) / 2, 0, PIER_Z + d / 2, fx1 - fx0, 0.012, 0.012, line);
  box(out, 'port-footing-w', 'port', fx0, 0, PIER_Z, 0.012, 0.012, d, line);
  box(out, 'port-footing-e', 'port', fx1, 0, PIER_Z, 0.012, 0.012, d, line);
  const length = state === 'BUILT' ? SHORE_X1 - 0.35 - PIER_X0 : 0.9;
  const tone = state === 'BUILT' ? VILLAGE_TONE.timber : VILLAGE_TONE.footing;
  box(out, 'port-pier-deck', 'port', PIER_X0 + length / 2, PIER_DECK, PIER_Z, length, 0.05, 0.4, tone);
  const piles = state === 'BUILT' ? 4 : 2;
  for (let i = 0; i < piles; i++) {
    const px = PIER_X0 + 0.1 + (i * (length - 0.2)) / Math.max(1, piles - 1);
    box(out, `port-pier-pile-${i}`, 'port', px, -0.2, PIER_Z, 0.05, PIER_DECK + 0.2, 0.05, tone);
  }
  // The near-miss pad over the pier and the water beside it, where the Boat lies.
  const hitX1 = state === 'BUILT' ? SHORE_X1 - 0.3 : PIER_X0 + 0.9;
  box(out, 'port-hit', 'port', (PIER_X0 - 0.05 + hitX1) / 2, 0, PIER_Z + 0.3, hitX1 - PIER_X0 + 0.05, 0.4, 1.2, VILLAGE_TONE.dark, 0);
  if (state !== 'BUILT') return;
  box(out, 'port-pier-bollard', 'port', PIER_X0 + length - 0.1, PIER_DECK + 0.05, PIER_Z, 0.06, 0.1, 0.06, VILLAGE_TONE.stone);
  if (!boatDocked) return;
  // The Boat: a low dark hull alongside the pier, a raised bow and a bare mast.
  const hx = PIER_X0 + length - 0.75;
  box(out, 'port-boat-hull', 'port', hx, 0.02, 1.5, 1.2, 0.2, 0.4, VILLAGE_TONE.dark);
  box(out, 'port-boat-bow', 'port', hx + 0.55, 0.22, 1.5, 0.2, 0.14, 0.4, VILLAGE_TONE.dark);
  box(out, 'port-boat-mast', 'port', hx - 0.1, 0.22, 1.5, 0.03, 0.8, 0.03, VILLAGE_TONE.dark);
}

function ground(out: Volume[], quarter: Quarter | null): void {
  const tone = quarter ? QUARTER_LOOK[quarter].ground : VILLAGE_TONE.ground;
  box(out, 'ground', 'village', 0, -0.2, 1, FRAME_WIDTH * 2, 0.2, 5, tone);
}

/**
 * What each Quarter does to the picture, all through the ink ramp (tone and
 * dither only, no new colour). `ground` is the ground's ink density, `rows` the
 * furrows': Spring's are full ("green"), Summer's dry, Autumn's gold-grey, Winter's bare.
 */
export const QUARTER_LOOK: Record<Quarter, { ground: number; rows: number }> = {
  SPRING: { ground: 0.26, rows: 0.98 },
  SUMMER: { ground: 0.4, rows: 0.78 },
  AUTUMN: { ground: 0.32, rows: 0.58 },
  WINTER: { ground: 0.36, rows: 0.34 },
};

/** The sea's rest tone against the danger at sea (1% to 20%, see R32). */
const SEA_RISK_LOW = 0.01;
const SEA_RISK_HIGH = 0.2;

/** 0 at the calmest sea, 1 at the most dangerous; a missing risk reads as calm. */
export function seaUnrest(risk: number | null | undefined): number {
  if (risk == null || !Number.isFinite(risk)) return 0;
  return Math.min(1, Math.max(0, (risk - SEA_RISK_LOW) / (SEA_RISK_HIGH - SEA_RISK_LOW)));
}

/**
 * Ground marks of the Quarter: thaw pools in Spring, long light and dry cracks
 * in Summer, stubble in Autumn, snow in Winter. All low, flat and inside the
 * Village's width; none of them is a thing the player can point at.
 */
function quarterMarks(out: Volume[], quarter: Quarter | null): void {
  if (!quarter) return;
  const id = (n: string) => `season-${n}`;
  switch (quarter) {
    case 'SPRING':
      // Thaw: dark wet strips lying on the ground.
      for (let i = 0; i < 5; i++) {
        box(out, id(`thaw-${i}`), 'village', -2.6 + i * 1.3, 0, 2.5 - (i % 2) * 0.25, 0.5 - (i % 3) * 0.08, 0.012, 0.1, VILLAGE_TONE.dark);
      }
      break;
    case 'SUMMER':
      // Long light: pale low bars lying along the ground, and dry cracks between.
      for (let i = 0; i < 3; i++) {
        box(out, id(`light-${i}`), 'village', -0.4 + i * 0.3, 0, 2.55 + i * 0.2, 4.4 - i * 0.9, 0.012, 0.03, 0.6);
      }
      for (let i = 0; i < 4; i++) {
        box(out, id(`crack-${i}`), 'village', -2.4 + i * 1.6, 0, 2.45, 0.25, 0.012, 0.02, VILLAGE_TONE.dark);
      }
      break;
    case 'AUTUMN':
      // Stubble: short stalks standing in the cut rows of the Fields.
      for (let t = 0; t < 3; t++) {
        const top = 0.08 + t * 0.1;
        const z = 1.2 - t * 0.3;
        for (let k = 0; k < 6; k++) {
          box(out, id(`stubble-${t}-${k}`), 'village', mid(ANNEX_X.fields) - 0.45 + k * 0.18, top, z - 0.14 + (k % 3) * 0.14, 0.012, 0.05, 0.012, 0.7);
        }
      }
      break;
    case 'WINTER':
      // Snow: pale flat marks on the ground and caps on the Fort's merlons and the silo.
      for (let i = 0; i < 6; i++) {
        box(out, id(`snow-${i}`), 'village', -2.8 + i * 1.1, 0, 2.3 + (i % 2) * 0.3, 0.45 + (i % 3) * 0.1, 0.012, 0.14, VILLAGE_TONE.salt);
      }
      for (let i = 0; i < 4; i++) {
        const mx = ANNEX_X.fort[0] + 0.27 + (i * (span(ANNEX_X.fort) - 0.54)) / 3;
        box(out, id(`cap-${i}`), 'village', mx, 0.99, 0.35, 0.18, 0.02, 0.14, VILLAGE_TONE.salt);
      }
      box(out, id('cap-silo'), 'village', mid(ANNEX_X.silo), 1.7, SILO_Z, span(ANNEX_X.silo), 0.02, 0.3, VILLAGE_TONE.salt);
      break;
  }
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
  const quarter = input.quarter ?? null;
  ground(out, quarter);
  quarterMarks(out, quarter);
  tower(out);
  fort(out, input.reserves, input.wounded ?? 0);
  silo(out, input.food);
  stair(out, input.villageTierLevel, input.still ? 0 : input.flicker);
  const pillar = input.pillar ?? EMPTY_PILLAR;
  // At Tier IV the Toll bar moves to a Stone of Count and the Fallen to a Wall of Names.
  const records = input.villageTierLevel >= RECORDS_TIER;
  referencePillar(out, pillar, !records);
  if (records) {
    stoneOfCount(out, pillar.tollShare);
    wallOfNames(out, input.fallenCount ?? 0);
  }
  fields(out, quarter);
  quarry(out, input.quarryHeap, input.quarryHeapCap);
  study(out, input.hands.SCRIBE, input.posts.SCRIBE);
  if (input.villageTierLevel < SPLIT_TIER) {
    vault(out, input.materials, input.relics, input.credits ?? 0);
  }
  splitVault(out, input.villageTierLevel, input.materials, input.relics, input.credits ?? 0);
  pans(out, input.saltPansOpen, input.salt);
  if (input.sporeCellarOpen) cellar(out, input.spores ?? 0);
  port(out, input.port ?? 'HIDDEN', input.boatDocked ?? false, input.seaRisk);
  shortcutMarks(out, input.shortcuts);
  baseDoor(out);
  if (input.offer) rollStone(out);
  bellObject(out, BELL_SPOT, input.bellUsable ?? true);
  gateObject(out, BELL_SPOT);
  ring(out, input.ringLevel ?? 0, input.ringMax ?? 0);
  growth(out, input.upgrades ?? {});
  reserves(out, input.reserves);
  villagers(out, input.hands, input.tick, input.still);
  // The chosen Start Floor wears the bone outline: the Base door by default.
  const start = input.startFloor ?? 0;
  if (start <= 0) {
    out.push(...outlineVolumes(out, BASE_DOOR_SUBJECT, 2, 'start'));
  } else {
    for (const sc of input.shortcuts) {
      if (sc.floor === start) out.push(...outlineVolumes(out, sc.id, 2, 'start'));
    }
  }
  // A control that cannot be used is dark, with a dashed outline.
  if (input.bellUsable === false) {
    out.push(...outlineVolumes(out, BELL_SUBJECT, 1, 'unlit', 'dashed'));
  }
  if (input.hovered && input.hovered !== input.selected) {
    out.push(...cubeOutlineVolumes(out, input.hovered, 1, 'hover'));
  }
  if (input.selected) {
    out.push(...cubeOutlineVolumes(out, input.selected, 2, 'select'));
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

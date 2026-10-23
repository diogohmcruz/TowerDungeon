import { Tower } from '../../interfaces/tower';
import {
  cellIndex,
  cellOrigin,
  drawnArchitecture,
  PENCIL_CELL,
  StripLayer,
} from './architecture-atlas';
import { DIGIT_H, DIGIT_W } from './digit-atlas';

export const FLOOR_HEIGHT = 2.2;
/** World Y of the walkable surface above a floor's base (units stand here). */
export const FLOOR_SURFACE_OFFSET = 0.98;
export const GHOST_FLOORS_AHEAD = 8;

/** Strip plane: 4.4 wide, the 128 x 50 cell aspect (about 1.72 high). */
export const STRIP_W = 4.4;
export const STRIP_H = (STRIP_W * 50) / 128;
/**
 * A strip is the room of its Floor: its bottom edge is the walkable surface of
 * the Floor below, where the party stands while fighting Floor N.
 */
export const STRIP_CENTER_OFFSET =
  FLOOR_SURFACE_OFFSET - FLOOR_HEIGHT + STRIP_H / 2;
/** Back, structure and lip planes: a 0.7 unit spread: parallax that stays subtle (and keeps the floor lines of the layers together) while the camera looks down. */
export const LAYER_Z: readonly [number, number, number] = [-0.5, -0.15, 0.2];

export const NUMERAL_Z = 0.3;
/** World size of one digit texel. */
export const DIGIT_PX = 0.04;
export const NUMERAL_X = -STRIP_W / 2 + (12 / 128) * STRIP_W;

export const MAX_DISCOVERED = 120;
export const STRIP_CAPACITY = 3 * MAX_DISCOVERED + GHOST_FLOORS_AHEAD;
export const NUMERAL_CAPACITY = 3 * MAX_DISCOVERED;
export const PILLAR_COUNT = 12;
export const PILLAR_SPACING = 3.6;

export function floorToY(n: number): number {
  return (n - 1) * FLOOR_HEIGHT;
}

/** World Y of the centre of floor `n`'s strip. */
export function stripY(n: number): number {
  return floorToY(n) + STRIP_CENTER_OFFSET;
}

export interface StripInstance {
  y: number;
  z: number;
  col: number;
  row: number;
  layer: StripLayer;
  /** Dithered translucency, 0..1. */
  alpha: number;
  /** Ink density, 0..1. */
  tone: number;
  /** Floor number, or 0 for pencil. */
  floor: number;
  /** 0 no Guardian, 1 Guardian floor, 2 Guardian slain this campaign. */
  seal: 0 | 1 | 2;
}

export interface NumeralInstance {
  x: number;
  y: number;
  digit: number;
  struck: boolean;
}

export interface StripPlan {
  strips: StripInstance[];
  numerals: NumeralInstance[];
  /** Highest discovered floor (0 when none). */
  top: number;
}

/** Digits of a floor number, most significant first. */
export function digitsOf(n: number): number[] {
  return String(Math.max(0, Math.floor(n)))
    .split('')
    .map(Number);
}

/**
 * Everything drawn for the discovered floors, as plain data. The component
 * rebuilds this only when `layoutVersion` changes. Undiscovered floors get a
 * faint pencil strip and nothing else: no architecture, no numeral, no seal.
 */
export function planFloorStrips(tower: Tower | undefined): StripPlan {
  const strips: StripInstance[] = [];
  const numerals: NumeralInstance[] = [];
  const floors = Object.entries(tower?.floors ?? {})
    .map(([k, f]) => [Number(k), f] as const)
    .sort((a, b) => a[0] - b[0])
    .slice(0, MAX_DISCOVERED);
  const slain = new Set(tower?.slainGuardians ?? []);
  let top = 0;

  for (const [n, floor] of floors) {
    top = Math.max(top, n);
    const arch = drawnArchitecture(floor.architecture);
    const seal = floor.boss === true ? (slain.has(n) ? 2 : 1) : 0;
    for (const layer of [0, 1, 2] as StripLayer[]) {
      const { col, row } = cellOrigin(cellIndex(arch, floor.variant, layer));
      strips.push({
        y: stripY(n),
        z: LAYER_Z[layer],
        col,
        row,
        layer,
        alpha: 1,
        tone: 1,
        floor: n,
        seal: seal as 0 | 1 | 2,
      });
    }
    const y = stripY(n) + 0.05;
    const ds = digitsOf(n);
    const pitch = DIGIT_W * DIGIT_PX;
    ds.forEach((digit, i) =>
      numerals.push({
        x: NUMERAL_X + (i - (ds.length - 1) / 2) * pitch,
        y,
        digit,
        struck: seal === 2,
      }),
    );
  }

  if (top > 0) {
    const { col, row } = cellOrigin(PENCIL_CELL);
    for (let n = top + 1; n <= top + GHOST_FLOORS_AHEAD; n++) {
      const dist = n - top;
      strips.push({
        y: stripY(n),
        z: LAYER_Z[2],
        col,
        row,
        layer: 2,
        // The first undiscovered floor is the ceiling over the fight: barely there.
        alpha: dist === 1 ? 0.2 : Math.max(0.08, 0.5 - dist * 0.06),
        tone: 0.8,
        floor: 0,
        seal: 0,
      });
    }
  }
  return { strips, numerals, top };
}

export const NUMERAL_SIZE: readonly [number, number] = [
  DIGIT_W * DIGIT_PX,
  DIGIT_H * DIGIT_PX,
];

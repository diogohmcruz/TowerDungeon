import {
  COLUMN_CELL,
  digitCell,
  FIGURE_ASPECT,
  figureCell,
  FigureKind,
  SPECK_CELL,
} from './figure-atlas';
import { DIGIT_PX, NUMERAL_X } from '../floor-strip/floor-strip-plan';
import { DIGIT_W } from '../floor-strip/digit-atlas';

/** At most this many figures are drawn as sprites; the rest are specks. */
export const FIGURE_CAP = 24;
/** Capacity of the speck mesh; a garrison beyond it is not drawn past this. */
export const SPECK_CAPACITY = 256;
/** Capacity of the figure mesh: the cap plus Wave columns and their numerals. */
export const MARK_CAPACITY = FIGURE_CAP + 64;
/** World size of a speck. */
export const SPECK_SIZE = 0.07;

export const FIGURE_Z = 1.7;
/**
 * The strips are flat planes and the camera looks down, so a figure standing
 * (FIGURE_Z - lip z) in front of a strip's floor line would read as standing
 * below it. Lifting the feet by that depth times tan(camera pitch), plus the
 * slab's thickness, plants them on the drawn floor line (kept from #17).
 */
export const SURFACE_LIFT = 0.68;

/**
 * Formation rectangle used to lay out a group. The grid keeps this
 * columns:rows aspect ratio as the group grows, so with 5:3 a group of 15
 * becomes 5 x 3 and a group of 8 about 4 x 2.
 */
const FORMATION_RATIO = 5 / 3;
const GROUP_OFFSET_X = 1.9;
const COL_STEP = 0.52;
const ROW_STEP_Z = 0.6;
const ROW_STEP_X = 0.34;
/**
 * Figures are small against the huge structure (the strip room is 1.72 high):
 * a figure is about a third of it. Guardians are drawn larger, a shape cue that
 * does not rely on colour.
 */
export const FIGURE_SCALE = 0.58;
export const GUARDIAN_SCALE = 1.35;

/**
 * Pillar numerals sit at NUMERAL_X, up to three digits wide. The party
 * formation keeps its left edge clear of them (see `planFigures`).
 */
const NUMERAL_HALF_WIDTH = (3 * DIGIT_W * DIGIT_PX) / 2;
export const PARTY_CLEAR_X = NUMERAL_X + NUMERAL_HALF_WIDTH + 0.05;

export interface FigureMember {
  id: number | string;
  typeKey: string;
  /** Guardians are drawn before other enemies and larger. */
  guardian: boolean;
}

export interface WaveInput {
  /** Members in the Wave. */
  size: number;
  floor: number;
}

export interface FigureInstance {
  id: string;
  cell: number;
  /** World position of the centre. */
  x: number;
  y: number;
  z: number;
  /** World width and height. */
  w: number;
  h: number;
  /** 0 ramp, 1 Guardian crimson. */
  role: 0 | 1;
}

export interface FigurePlan {
  /** Sprites: figures, Wave columns and Wave numerals (one instanced draw). */
  marks: FigureInstance[];
  /** Figures over the cap (a second instanced draw). */
  specks: FigureInstance[];
  /** How many of `marks` are member figures (at most FIGURE_CAP). */
  figureCount: number;
  /** Party and garrison members that were not drawn as sprites. */
  overflow: number;
}

export interface FigureInput {
  party: FigureMember[];
  enemies: FigureMember[];
  waves: WaveInput[];
  /** World Y of the deck the front rank stands on, lift included. */
  surfaceY: number;
  /** World Y of a floor's walkable surface (waves climb the front of the tower). */
  floorSurfaceY: (floor: number) => number;
  /** Canvas width over height. */
  viewAspect: number;
  /** Highest floor a Wave column is drawn on (the floor the fight is drawn on). */
  anchorFloor: number;
}

interface Placed {
  member: FigureMember;
  kind: FigureKind;
  x: number;
  z: number;
  feetY: number;
  w: number;
  h: number;
  order: number;
}

/** Columns a group of `n` members occupies, preserving the formation ratio. */
export function gridCols(n: number): number {
  return Math.max(1, Math.round(Math.sqrt(n * FORMATION_RATIO)));
}

/** Arranges a group left to right with ranks receding into the tower. */
function layoutGroup(
  kind: FigureKind,
  members: FigureMember[],
  centerX: number,
  surfaceY: number,
  scale: number,
  diagSign: number,
): Placed[] {
  const n = members.length;
  const cols = gridCols(n);
  const rows = Math.ceil(n / cols);
  const colStep = COL_STEP * scale;
  const rowStepZ = ROW_STEP_Z * scale;
  const rowStepX = ROW_STEP_X * scale;
  return members.map((member, i) => {
    const c = i % cols;
    const r = Math.floor(i / cols);
    const itemsInRow = r === rows - 1 ? n - r * cols : cols;
    const rowWidth = (itemsInRow - 1) * colStep;
    const diag = diagSign * (r - (rows - 1) / 2) * rowStepX;
    const h = FIGURE_SCALE * scale * (member.guardian ? GUARDIAN_SCALE : 1);
    return {
      member,
      kind,
      x: centerX + c * colStep - rowWidth / 2 + diag,
      z: FIGURE_Z - r * rowStepZ,
      feetY: surfaceY,
      w: h * FIGURE_ASPECT,
      h,
      order: i,
    };
  });
}

function distance(a: Placed, b: { x: number; z: number }): number {
  return Math.hypot(a.x - b.x, a.z - b.z);
}

function centroid(group: Placed[]): { x: number; z: number } {
  if (!group.length) {
    return { x: 0, z: FIGURE_Z };
  }
  return {
    x: group.reduce((s, p) => s + p.x, 0) / group.length,
    z: group.reduce((s, p) => s + p.z, 0) / group.length,
  };
}

/**
 * Which placed members are drawn as sprites: the Party first, then Guardians,
 * then the rest by proximity to the other side. Within the Party, the members
 * nearest the garrison come first too. Ties keep the order they were given.
 */
function pickSprites(
  party: Placed[],
  enemies: Placed[],
  cap = FIGURE_CAP,
): Set<Placed> {
  const byProximity = (group: Placed[], to: { x: number; z: number }) =>
    [...group].sort(
      (a, b) => distance(a, to) - distance(b, to) || a.order - b.order,
    );
  const ranked = [
    ...byProximity(party, centroid(enemies)),
    ...enemies.filter((e) => e.member.guardian),
    ...byProximity(
      enemies.filter((e) => !e.member.guardian),
      centroid(party),
    ),
  ];
  return new Set(ranked.slice(0, cap));
}

/**
 * Everything drawn for the fight and the Reinforcement Waves, as plain data:
 * deterministic, so a known party and garrison pin to known positions.
 *
 * Open layout note from #17: the party formation used to sit over the numeral
 * on the current Floor's reference pillar. The party is shifted right just far
 * enough that its left edge clears the numeral, and the group-fit allowance
 * grows by the same distance so the two groups still fit the canvas.
 */
export function planFigures(input: FigureInput): FigurePlan {
  const { party, enemies, surfaceY } = input;

  const partyCols = gridCols(party.length || 1);
  const enemyCols = gridCols(enemies.length || 1);
  const maxCols = Math.max(partyCols, enemyCols, 1);
  const maxRows = Math.max(
    Math.ceil((party.length || 1) / partyCols),
    Math.ceil((enemies.length || 1) / enemyCols),
    1,
  );
  const diagHalf = ((maxRows - 1) / 2) * ROW_STEP_X;
  const naturalHalf =
    GROUP_OFFSET_X +
    ((maxCols - 1) * COL_STEP) / 2 +
    diagHalf +
    (FIGURE_SCALE * GUARDIAN_SCALE) / 2 +
    0.3;
  const visibleHalf = Math.max(1.05, 2.5 * input.viewAspect);
  const fit = Math.min(1, Math.max(0.24, (visibleHalf * 0.92) / naturalHalf));

  const placedParty = layoutGroup(
    'unit',
    party,
    -GROUP_OFFSET_X * fit,
    surfaceY,
    fit,
    1,
  );
  const left = Math.min(...placedParty.map((p) => p.x - p.w / 2));
  if (placedParty.length && left < PARTY_CLEAR_X) {
    const shift = PARTY_CLEAR_X - left;
    placedParty.forEach((p) => (p.x += shift));
  }
  const placedEnemies = layoutGroup(
    'enemy',
    enemies,
    GROUP_OFFSET_X * fit,
    surfaceY,
    fit,
    -1,
  );

  const drawn = pickSprites(placedParty, placedEnemies);
  const marks: FigureInstance[] = [];
  const specks: FigureInstance[] = [];
  for (const p of [...placedParty, ...placedEnemies]) {
    const id = `${p.kind}-${p.member.id}`;
    const y = p.feetY + p.h / 2;
    if (drawn.has(p)) {
      marks.push({
        id,
        cell: figureCell(p.kind, p.member.typeKey),
        x: p.x,
        y,
        z: p.z,
        w: p.w,
        h: p.h,
        role: p.member.guardian ? 1 : 0,
      });
    } else if (specks.length < SPECK_CAPACITY) {
      specks.push({
        id,
        cell: SPECK_CELL,
        x: p.x,
        y: p.feetY + SPECK_SIZE,
        z: p.z,
        w: SPECK_SIZE,
        h: SPECK_SIZE,
        role: p.member.guardian ? 1 : 0,
      });
    }
  }
  const figureCount = marks.length;

  planWaves(input, marks);
  return {
    marks,
    specks,
    figureCount,
    overflow: placedParty.length + placedEnemies.length - figureCount,
  };
}

/** Wave column height by size, in world units. */
export function columnHeight(size: number): number {
  return 0.5 + 0.05 * Math.min(Math.max(size, 1), 10);
}

/**
 * Reinforcement Waves climb the front of the tower toward the party as columns
 * marked with their number (1 for the first sent). They carry no hue; the
 * numeral and the height, which grows with the Wave's size, tell them apart.
 */
function planWaves(input: FigureInput, out: FigureInstance[]): void {
  const count = input.waves.length;
  const step = count > 1 ? 2.4 / (count - 1) : 0;
  input.waves.forEach((wave, index) => {
    const floor = Math.min(input.anchorFloor, Math.max(1, wave.floor));
    const h = columnHeight(wave.size);
    const x = count > 1 ? -1.2 + index * step : 0;
    const base = input.floorSurfaceY(floor) + SURFACE_LIFT;
    out.push({
      id: `wave-${index}`,
      cell: COLUMN_CELL,
      x,
      y: base + h / 2,
      z: FIGURE_Z + 0.4,
      w: h * FIGURE_ASPECT,
      h,
      role: 0,
    });
    const digits = String(index + 1)
      .split('')
      .map(Number);
    const nh = 0.3;
    const nw = nh * FIGURE_ASPECT;
    digits.forEach((digit, i) =>
      out.push({
        id: `wave-${index}-n${i}`,
        cell: digitCell(digit),
        x: x + (i - (digits.length - 1) / 2) * nw,
        y: base + h + 0.08 + nh / 2,
        z: FIGURE_Z + 0.4,
        w: nw,
        h: nh,
        role: 0,
      }),
    );
  });
}

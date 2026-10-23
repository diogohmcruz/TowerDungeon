import * as THREE from 'three';
import { SPRITE_ART } from '../catalogue';
import { FONT } from '../floor-strip/digit-atlas';

/** One figure cell: 12 x 16 virtual pixels, the size of the #39 index PNGs. */
export const FIGURE_W = 12;
export const FIGURE_H = 16;
export const FIGURE_COLS = 8;
export const FIGURE_ROWS = 4;
export const FIGURE_ASPECT = FIGURE_W / FIGURE_H;

/**
 * Texel values in a cell: 0 clear, 1 to 3 the ramp slots (line, detail, fill)
 * and 4 the plate, an opaque patch of slot 0 that keeps a numeral readable over
 * line work. Stored as `value * TEXEL_STEP` in an 8-bit red channel.
 */
export const PLATE = 4;
export const TEXEL_STEP = 40;

export type FigureKind = 'unit' | 'enemy';

export const PLACEHOLDER_CELL = 0;
/** A solid cell, drawn small for the figures that are over the cap. */
export const SPECK_CELL = 1;
/** The column that stands for a Reinforcement Wave. */
export const COLUMN_CELL = 2;
export const DIGIT_CELL_BASE = 3;

const UNIT_BASE = DIGIT_CELL_BASE + 10;
const ENEMY_BASE = UNIT_BASE + SPRITE_ART.unit.length;
export const FIGURE_CELLS = ENEMY_BASE + SPRITE_ART.enemy.length;

/** The lookup slug of a type: lowercased, non-alphanumerics collapsed to `-`. */
export function slugOf(typeKey: string): string {
  return typeKey
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** The atlas cell of a type, or the placeholder when the catalogue has no art for it. */
export function figureCell(kind: FigureKind, typeKey: string): number {
  const at = SPRITE_ART[kind].indexOf(slugOf(typeKey));
  if (at < 0) {
    return PLACEHOLDER_CELL;
  }
  return (kind === 'unit' ? UNIT_BASE : ENEMY_BASE) + at;
}

export function digitCell(digit: number): number {
  return DIGIT_CELL_BASE + digit;
}

export function cellColRow(cell: number): { col: number; row: number } {
  return { col: cell % FIGURE_COLS, row: Math.floor(cell / FIGURE_COLS) };
}

type Cell = number[][];

function blank(value = 0): Cell {
  return Array.from({ length: FIGURE_H }, () => Array(FIGURE_W).fill(value));
}

/** A hooded stand-in: line rim, detail body, fill eyes. Never a coloured token. */
function placeholderCell(): Cell {
  const c = blank();
  for (let y = 2; y < 15; y++) {
    for (let x = 3; x < 9; x++) {
      const edge = x === 3 || x === 8 || y === 2 || y === 14;
      c[y][x] = edge ? 1 : 2;
    }
  }
  c[5][5] = 3;
  c[5][6] = 3;
  return c;
}

/**
 * A Wave is a column: capped, with a stippled body that reads as ranks. It is
 * drawn in the figure ramp, so it needs no hue and a numeral sits above it.
 */
function columnCell(): Cell {
  const c = blank();
  for (let y = 0; y < FIGURE_H; y++) {
    for (let x = 3; x < 9; x++) {
      const side = x === 3 || x === 8;
      const cap = y < 2 || y === FIGURE_H - 1;
      c[y][x] = cap ? 3 : side ? 2 : y % 2 ? 2 : 1;
    }
  }
  return c;
}

/** The 5 x 7 numeral font, doubled, on a plate. */
function digitCellOf(digit: number): Cell {
  const c = blank(PLATE);
  FONT[digit].forEach((row, y) => {
    for (let x = 0; x < 5; x++) {
      if (row[x] === '1') {
        for (const [dy, dx] of [
          [0, 0],
          [0, 1],
          [1, 0],
          [1, 1],
        ]) {
          c[1 + y * 2 + dy][1 + x * 2 + dx] = 3;
        }
      }
    }
  });
  return c;
}

/** Writes a cell into the atlas slots (`FIGURE_COLS * FIGURE_W` texels per row). */
export function writeCell(slots: Uint8Array, cell: number, data: Cell): void {
  const { col, row } = cellColRow(cell);
  const stride = FIGURE_COLS * FIGURE_W;
  for (let y = 0; y < FIGURE_H; y++) {
    for (let x = 0; x < FIGURE_W; x++) {
      slots[(row * FIGURE_H + y) * stride + col * FIGURE_W + x] = data[y][x];
    }
  }
}

/** The atlas texel values with every cell its drawn stand-in (art arrives later, per slug). */
export function buildFigureSlots(): Uint8Array {
  const slots = new Uint8Array(FIGURE_COLS * FIGURE_W * FIGURE_ROWS * FIGURE_H);
  writeCell(slots, PLACEHOLDER_CELL, placeholderCell());
  writeCell(slots, SPECK_CELL, blank(3));
  writeCell(slots, COLUMN_CELL, columnCell());
  for (let d = 0; d < 10; d++) {
    writeCell(slots, digitCell(d), digitCellOf(d));
  }
  // Until a PNG arrives (or if one never does) a catalogued type shows the placeholder.
  for (let cell = UNIT_BASE; cell < FIGURE_CELLS; cell++) {
    writeCell(slots, cell, placeholderCell());
  }
  return slots;
}

/** Maps index-PNG RGBA pixels (grey 85 / 170 / 255, alpha 0 clear) to a cell's slots. */
export function cellFromRgba(rgba: ArrayLike<number>): Cell {
  const c = blank();
  for (let y = 0; y < FIGURE_H; y++) {
    for (let x = 0; x < FIGURE_W; x++) {
      const i = (y * FIGURE_W + x) * 4;
      if (rgba[i + 3] === 0) {
        continue;
      }
      const grey = rgba[i];
      c[y][x] = grey < 128 ? 1 : grey < 213 ? 2 : 3;
    }
  }
  return c;
}

export interface FigureAtlas {
  texture: THREE.DataTexture;
  slots: Uint8Array;
  /** Fetches the catalogued PNGs and swaps each into its cell as it arrives. */
  loadArt(baseUrl?: string): void;
  dispose(): void;
}

function toBytes(slots: Uint8Array, bytes: Uint8Array): void {
  for (let i = 0; i < slots.length; i++) {
    bytes[i] = slots[i] * TEXEL_STEP;
  }
}

export function createFigureAtlas(): FigureAtlas {
  const slots = buildFigureSlots();
  const bytes = new Uint8Array(slots.length);
  toBytes(slots, bytes);
  const texture = new THREE.DataTexture(
    bytes,
    FIGURE_COLS * FIGURE_W,
    FIGURE_ROWS * FIGURE_H,
    THREE.RedFormat,
    THREE.UnsignedByteType,
  );
  texture.minFilter = THREE.NearestFilter;
  texture.magFilter = THREE.NearestFilter;
  texture.generateMipmaps = false;
  texture.colorSpace = THREE.NoColorSpace;
  texture.unpackAlignment = 1;
  texture.needsUpdate = true;

  let disposed = false;
  const swapIn = (cell: number, image: HTMLImageElement) => {
    if (disposed || image.width !== FIGURE_W || image.height !== FIGURE_H) {
      return;
    }
    const canvas = document.createElement('canvas');
    canvas.width = FIGURE_W;
    canvas.height = FIGURE_H;
    const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
    ctx.drawImage(image, 0, 0);
    const { data } = ctx.getImageData(0, 0, FIGURE_W, FIGURE_H);
    writeCell(slots, cell, cellFromRgba(data));
    toBytes(slots, bytes);
    texture.needsUpdate = true;
  };

  return {
    texture,
    slots,
    loadArt(baseUrl = '/sprites') {
      const jobs: [FigureKind, string][] = [
        ['unit', 'units'],
        ['enemy', 'enemies'],
      ];
      for (const [kind, folder] of jobs) {
        for (const slug of SPRITE_ART[kind]) {
          const image = new Image();
          image.onload = () => swapIn(figureCell(kind, slug), image);
          // A listed PNG that fails keeps the placeholder cell.
          image.src = `${baseUrl}/${folder}/${slug}.png`;
        }
      }
    },
    dispose() {
      disposed = true;
      texture.dispose();
    },
  };
}

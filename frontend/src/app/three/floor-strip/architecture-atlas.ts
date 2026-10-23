import * as THREE from 'three';

import { ArchitectureId } from '../../interfaces/tower';

/**
 * Procedural atlas for the Floor strips (docs/brainstorm/architecture-and-visual-identity.md
 * section 3.3). One cell is 128 x 50 texels: about one texel per virtual pixel of a strip
 * in the default shot. A texel stores a ramp slot: 0 clear, 1 shadow or fill,
 * 2 detail, 3 line. Cells are written texel by texel, never through an
 * anti-aliased canvas, so no grey texel exists that has no ramp meaning.
 *
 * Four Architectures are drawn; the other eight fall back to the Colonnade.
 */
export const CELL_W = 128;
export const CELL_H = 50;
export const ATLAS_COLS = 16;
export const ATLAS_ROWS = 4;
export const ATLAS_W = CELL_W * ATLAS_COLS;
export const ATLAS_H = CELL_H * ATLAS_ROWS;

export type DrawnArchitecture = 'COLONNADE' | 'NAVE' | 'STAIR_SHAFT' | 'GATE';
export const DRAWN_ARCHITECTURES: readonly DrawnArchitecture[] = [
  'COLONNADE',
  'NAVE',
  'STAIR_SHAFT',
  'GATE',
];

/** Back (behind the structure), structure, lip (slab edge, seal ring, outline). */
export const LAYERS = 3;
export const VARIANTS = 4;
export type StripLayer = 0 | 1 | 2;

/** Cell of the generic pencil strip drawn above the Highest Floor. */
export const PENCIL_CELL = DRAWN_ARCHITECTURES.length * VARIANTS * LAYERS;

/** Texel column of the reference pillar's centre (the numeral is stencilled here). */
export const REF_PILLAR_X = 12;
/** Seal ring: centre and radius in texels (drawn by the shader, not stored). */
export const SEAL_CENTER: readonly [number, number] = [64, 21];
export const SEAL_RADIUS = 10;

export function drawnArchitecture(id: ArchitectureId): DrawnArchitecture {
  return (DRAWN_ARCHITECTURES as readonly string[]).includes(id)
    ? (id as DrawnArchitecture)
    : 'COLONNADE';
}

export function cellIndex(
  arch: DrawnArchitecture,
  variant: number,
  layer: StripLayer,
): number {
  const v = ((variant % VARIANTS) + VARIANTS) % VARIANTS;
  return (DRAWN_ARCHITECTURES.indexOf(arch) * VARIANTS + v) * LAYERS + layer;
}

export function cellOrigin(index: number): { col: number; row: number } {
  return { col: index % ATLAS_COLS, row: Math.floor(index / ATLAS_COLS) };
}

/** Small seeded generator (mulberry32), deterministic across runs. */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Writes ramp slots into one cell of the atlas, clipped to the cell. */
class Cell {
  constructor(
    private readonly data: Uint8Array,
    private readonly ox: number,
    private readonly oy: number,
  ) {}

  set(x: number, y: number, v: number): void {
    x = Math.round(x);
    y = Math.round(y);
    if (x < 0 || y < 0 || x >= CELL_W || y >= CELL_H) return;
    this.data[(this.oy + y) * ATLAS_W + this.ox + x] = v;
  }

  hline(x0: number, x1: number, y: number, v: number): void {
    for (let x = x0; x <= x1; x++) this.set(x, y, v);
  }

  vline(x: number, y0: number, y1: number, v: number): void {
    for (let y = y0; y <= y1; y++) this.set(x, y, v);
  }

  fill(x: number, y: number, w: number, h: number, v: number): void {
    for (let j = 0; j < h; j++) this.hline(x, x + w - 1, y + j, v);
  }

  box(x: number, y: number, w: number, h: number, v: number): void {
    this.hline(x, x + w - 1, y, v);
    this.hline(x, x + w - 1, y + h - 1, v);
    this.vline(x, y, y + h - 1, v);
    this.vline(x + w - 1, y, y + h - 1, v);
  }

  /** Checkerboard dither inside a rectangle. */
  dither(x: number, y: number, w: number, h: number, v: number): void {
    for (let j = 0; j < h; j++) {
      for (let i = 0; i < w; i++) {
        if ((i + j) % 2 === 0) this.set(x + i, y + j, v);
      }
    }
  }

  line(x0: number, y0: number, x1: number, y1: number, v: number): void {
    const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0), 1);
    for (let i = 0; i <= n; i++) {
      this.set(x0 + ((x1 - x0) * i) / n, y0 + ((y1 - y0) * i) / n, v);
    }
  }

  /** Upper half of an ellipse (an arch), from the left foot to the right foot. */
  arch(cx: number, cy: number, rx: number, ry: number, v: number): void {
    const steps = Math.ceil((rx + ry) * 3);
    for (let i = 0; i <= steps; i++) {
      const a = Math.PI * (i / steps);
      this.set(cx - Math.cos(a) * rx, cy - Math.sin(a) * ry, v);
    }
  }

  /** Quadratic curve, used for the pointed vault ribs. */
  curve(
    p0: readonly [number, number],
    p1: readonly [number, number],
    p2: readonly [number, number],
    v: number,
  ): void {
    for (let i = 0; i <= 90; i++) {
      const t = i / 90;
      const u = 1 - t;
      this.set(
        u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
        u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
        v,
      );
    }
  }
}

/** The reference pillar every strip carries: the numeral is stencilled on it. */
function referencePillar(c: Cell): void {
  const x = REF_PILLAR_X;
  c.fill(x - 5, 1, 11, 45, 1);
  c.vline(x - 5, 1, 46, 3);
  c.vline(x + 5, 1, 46, 3);
  c.vline(x + 4, 1, 46, 2);
  c.box(x - 7, 0, 15, 3, 3);
  c.box(x - 7, 44, 15, 3, 3);
}

/** Slab edge: a ceiling hairline, the floor line and a hatched slab below it. */
function slabEdge(c: Cell, rng: () => number): void {
  c.hline(0, CELL_W - 1, 0, 3);
  c.hline(0, CELL_W - 1, 47, 3);
  c.fill(0, 48, CELL_W, 2, 1);
  for (let x = 0; x < CELL_W; x += 4) c.set(x + Math.floor(rng() * 3), 48, 2);
}

function colonnade(c: Cell, layer: StripLayer, v: number): void {
  const rng = mulberry32(1000 + v * 17 + layer);
  const spacing = [24, 22, 26, 20][v];
  if (layer === 0) {
    for (let x = 32; x < CELL_W; x += 14) {
      c.vline(x, 12, 46, 1);
      c.hline(x - 2, x + 2, 11, 1);
    }
    return;
  }
  if (layer === 1) {
    referencePillar(c);
    const cols: number[] = [];
    for (let x = 36; x <= 118; x += spacing) cols.push(x);
    const broken = Math.floor(rng() * cols.length);
    c.hline(24, CELL_W - 1, 2, 3);
    c.hline(24, CELL_W - 1, 3, 2);
    cols.forEach((cx, i) => {
      const top = i === broken ? 22 + Math.floor(rng() * 8) : 5;
      c.fill(cx - 3, top, 6, 47 - top, 1);
      c.vline(cx - 4, top, 46, 3);
      c.vline(cx + 3, top, 46, 3);
      c.vline(cx + 1, top, 46, 2);
      if (i === broken) {
        for (let k = -3; k <= 2; k += 2) c.set(cx + k, top - 1, 3);
      } else {
        c.hline(cx - 5, cx + 4, 4, 3);
        c.hline(cx - 5, cx + 4, 45, 3);
      }
    });
    return;
  }
  slabEdge(c, rng);
  for (let k = 0; k < 7; k++) c.set(30 + rng() * 90, 45 + rng() * 2, 3);
}

function nave(c: Cell, layer: StripLayer, v: number): void {
  const rng = mulberry32(2000 + v * 17 + layer);
  const shift = [0, 6, -6, 3][v];
  if (layer === 0) {
    for (const x0 of [24, 40, 56]) {
      c.curve([x0 + shift, 46], [x0 + shift, 8], [64, 3], x0 === 40 ? 2 : 1);
      c.curve(
        [CELL_W - 1 - x0 - shift, 46],
        [CELL_W - 1 - x0 - shift, 8],
        [63, 3],
        x0 === 40 ? 2 : 1,
      );
    }
    // Three tiny figures at the far end.
    for (const fx of [59, 64, 69]) {
      c.set(fx, 40, 3);
      c.vline(fx, 41, 45, 3);
    }
    return;
  }
  if (layer === 1) {
    referencePillar(c);
    for (const px of [34, 94]) {
      c.fill(px - 2, 8, 5, 39, 1);
      c.vline(px - 3, 8, 46, 3);
      c.vline(px + 3, 8, 46, 3);
      c.hline(px - 4, px + 4, 7, 3);
    }
    // Main arch springing from both piers, two aisles either side.
    c.curve([34, 7], [34, -6], [64, 1], 3);
    c.curve([94, 7], [94, -6], [64, 1], 3);
    // One dithered shaft of light, falling off the left clerestory.
    const lx = 70 + shift;
    for (let y = 4; y < 47; y++) {
      const x0 = lx - y * 0.35;
      const w = 7 + y * 0.12;
      for (let x = Math.round(x0); x < x0 + w; x++) {
        if ((x + y) % 2 === 0) c.set(x, y, 2);
      }
    }
    return;
  }
  slabEdge(c, rng);
  c.hline(46, 82, 46, 2);
}

function stairShaft(c: Cell, layer: StripLayer, v: number): void {
  const rng = mulberry32(3000 + v * 17 + layer);
  if (layer === 0) {
    for (let y = 0; y < 47; y += 2) {
      for (let x = 36 + (y % 4); x < 92; x += 4) c.set(x, y, 1);
    }
    return;
  }
  if (layer === 1) {
    referencePillar(c);
    // Shaft walls run the full height so adjacent Stair Shafts join.
    c.vline(34, 0, 49, 3);
    c.vline(94, 0, 49, 3);
    c.vline(35, 0, 49, 1);
    c.vline(93, 0, 49, 1);
    const missingFlight = 1;
    const missingStep = 3 + ((v + Math.floor(rng() * 2)) % 4);
    // Three flights of six treads, switching back at each landing.
    for (let flight = 0; flight < 3; flight++) {
      const rightward = flight % 2 === 0;
      const baseY = 46 - flight * 14;
      for (let s = 0; s < 8; s++) {
        const y = baseY - s * 2;
        const x = rightward ? 38 + s * 7 : 88 - s * 7;
        if (flight === missingFlight && s === missingStep) continue;
        c.hline(x, x + 5, y, 3);
        c.vline(rightward ? x + 5 : x, y, y + 1, 2);
      }
    }
    return;
  }
  slabEdge(c, rng);
}

function gate(c: Cell, layer: StripLayer, v: number): void {
  const rng = mulberry32(4000 + v * 17 + layer);
  const rx = [34, 36, 38, 36][v];
  if (layer === 0) {
    // A stair behind the door, rising toward the centre.
    for (let s = 0; s < 7; s++) {
      c.hline(50 + s * 2, 78 - s * 2, 46 - s * 3, 1);
      c.vline(50 + s * 2, 46 - s * 3, 46 - s * 3 + 2, 1);
      c.vline(78 - s * 2, 46 - s * 3, 46 - s * 3 + 2, 1);
    }
    return;
  }
  if (layer === 1) {
    referencePillar(c);
    for (const px of [64 - rx, 64 + rx - 10]) {
      c.fill(px, 22, 10, 25, 1);
      c.vline(px, 22, 46, 3);
      c.vline(px + 9, 22, 46, 3);
    }
    // Monumental arch, drawn two texels thick.
    c.arch(64, 26, rx, 24, 3);
    c.arch(64, 27, rx - 1, 23, 3);
    c.arch(64, 29, rx - 9, 19, 2);
    // Two empty armour-shaped niches.
    for (const nx of [26, 102]) {
      c.box(nx - 5, 18, 11, 26, 2);
      c.box(nx - 2, 21, 5, 4, 2);
      c.hline(nx - 4, nx + 4, 26, 2);
      c.box(nx - 3, 27, 7, 8, 2);
      c.vline(nx - 2, 36, 42, 2);
      c.vline(nx + 2, 36, 42, 2);
    }
    return;
  }
  slabEdge(c, rng);
  c.hline(64 - rx - 2, 64 + rx + 1, 45, 2);
}

const DRAW: Record<
  DrawnArchitecture,
  (c: Cell, layer: StripLayer, v: number) => void
> = {
  COLONNADE: colonnade,
  NAVE: nave,
  STAIR_SHAFT: stairShaft,
  GATE: gate,
};

/** The generic pencil: a floor line, a dotted ceiling and faint dotted verticals. */
function pencil(c: Cell): void {
  c.hline(0, CELL_W - 1, 47, 3);
  for (let x = 0; x < CELL_W; x += 3) c.set(x, 2, 3);
  for (const x of [12, 40, 88, 116]) {
    for (let y = 4; y < 46; y++) if (y % 5 < 2) c.set(x, y, 3);
  }
}

/** Every cell, as ramp slots 0..3 (ATLAS_W x ATLAS_H, row 0 at the top). */
export function buildAtlasSlots(): Uint8Array {
  const data = new Uint8Array(ATLAS_W * ATLAS_H);
  for (const arch of DRAWN_ARCHITECTURES) {
    for (let v = 0; v < VARIANTS; v++) {
      for (const layer of [0, 1, 2] as StripLayer[]) {
        const { col, row } = cellOrigin(cellIndex(arch, v, layer));
        DRAW[arch](new Cell(data, col * CELL_W, row * CELL_H), layer, v);
      }
    }
  }
  const p = cellOrigin(PENCIL_CELL);
  pencil(new Cell(data, p.col * CELL_W, p.row * CELL_H));
  return data;
}

/** The R8 index texture: slot * 85, nearest-filtered, no mipmaps, no colour space. */
export function createAtlasTexture(): THREE.DataTexture {
  const slots = buildAtlasSlots();
  const bytes = new Uint8Array(slots.length);
  for (let i = 0; i < slots.length; i++) bytes[i] = slots[i] * 85;
  const tex = new THREE.DataTexture(
    bytes,
    ATLAS_W,
    ATLAS_H,
    THREE.RedFormat,
    THREE.UnsignedByteType,
  );
  tex.minFilter = THREE.NearestFilter;
  tex.magFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.colorSpace = THREE.NoColorSpace;
  tex.unpackAlignment = 1;
  tex.needsUpdate = true;
  return tex;
}

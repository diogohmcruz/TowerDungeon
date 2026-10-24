import type { Quarter } from '../../interfaces/game-state';

/**
 * The ambient landscape to the left of the Tower (architecture doc 5.5): an open
 * plain, far ridges, a few ruined towers that stand for nothing the player owns
 * (invented; the manga is inspiration, not canon), drifting haze and the
 * Quarter's weather. Pure data: a list of boxes in the shared ink, with no
 * gameplay information in it beyond the Quarter.
 *
 * Coordinates: `x` across, `y` the centre height above the ground top, `z` toward
 * the camera (the Tower and the Village stand near z 0, the world recedes to
 * negative z). One virtual pixel at the Village's vantage is about 0.05 units.
 */

/** One box of the landscape. */
export interface LandscapeVolume {
  id: string;
  x: number;
  y: number;
  z: number;
  w: number;
  h: number;
  d: number;
  /** Ink density, 0..1. */
  tone: number;
  /** Dithered translucency, 0..1. */
  alpha: number;
  /** Half the width of a slow sideways drift, in units (0 for a still box). */
  sway: number;
  /** Height of a fall that wraps (snowfall); 0 for a box that does not fall. */
  fall: number;
  /** 0..1, so the moving parts do not move in step. */
  phase: number;
}

/** Instances the landscape mesh can hold. */
export const LANDSCAPE_CAPACITY = 256;

/** Where the plain ends and the far ridges stand, in depth. */
export const PLAIN_FAR = -46;
/** Where the plain ends toward the camera by default (just in front of the Village's ground). */
export const PLAIN_NEAR = 3.5;
/** The plain's top sits this far under the ground top of the scene it joins, so the two never fight. */
export const PLAIN_DROP = 0.01;

/** Ground ink density per Quarter: the Village's own (`QUARTER_LOOK`), kept in step by a test. */
export const PLAIN_TONE: Record<Quarter | 'NONE', number> = {
  SPRING: 0.26,
  SUMMER: 0.4,
  AUTUMN: 0.32,
  WINTER: 0.36,
  NONE: 0.3,
};

/** The weather's haze: how many banks and how dense (dithered alpha), per Quarter. */
export const HAZE: Record<Quarter | 'NONE', { banks: number; alpha: number }> =
  {
    SPRING: { banks: 8, alpha: 0.42 },
    SUMMER: { banks: 4, alpha: 0.2 },
    AUTUMN: { banks: 6, alpha: 0.32 },
    WINTER: { banks: 5, alpha: 0.36 },
    NONE: { banks: 5, alpha: 0.3 },
  };

/** Specks of snow in the air in Winter (they fall and wrap). */
export const SNOWFALL_SPECKS = 30;

const SNOW_TONE = 0.95;
const DARK_TONE = 0.12;

/** Small seeded generator (mulberry32): the landscape is the same every time. */
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

type Out = LandscapeVolume[];

function put(
  out: Out,
  id: string,
  x: number,
  bottom: number,
  z: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  extra: Partial<
    Pick<LandscapeVolume, 'alpha' | 'sway' | 'fall' | 'phase'>
  > = {},
): void {
  out.push({
    id,
    x,
    y: bottom + h / 2,
    z,
    w,
    h,
    d,
    tone,
    alpha: extra.alpha ?? 1,
    sway: extra.sway ?? 0,
    fall: extra.fall ?? 0,
    phase: extra.phase ?? 0,
  });
}

/** The plain: one wide flat slab from the far ridges to just in front of the Village. */
function plain(out: Out, tone: number, near: number): void {
  put(
    out,
    'plain',
    0,
    -0.4 - PLAIN_DROP,
    (PLAIN_FAR + near) / 2,
    260,
    0.4,
    near - PLAIN_FAR,
    tone,
  );
}

/**
 * Three ranks of ridge, each a run of stepped blocks whose heights wander (a
 * silhouette, not a drawing of a mountain). Further ranks are lower and fainter.
 */
function ridges(out: Out): void {
  const ranks = [
    { z: -62, base: 9, span: 6, tone: 0.85, step: 14 },
    { z: -54, base: 6, span: 4.5, tone: 0.78, step: 11 },
    { z: -47, base: 3.5, span: 3, tone: 0.7, step: 8 },
  ];
  ranks.forEach((rank, r) => {
    const rand = mulberry32(900 + r);
    let h = rank.base;
    for (let x = -130; x < 130; x += rank.step) {
      // A bounded walk, so a ridge rises and falls over several blocks.
      h = Math.min(
        rank.base + rank.span,
        Math.max(rank.base * 0.4, h + (rand() - 0.5) * rank.span),
      );
      const w = rank.step * (0.9 + rand() * 0.5);
      put(out, `ridge-${r}-${x}`, x + w / 2, -0.01, rank.z, w, h, 7, rank.tone);
    }
  });
}

/**
 * A few ruined towers on the plain: a shaft with its top broken away, a stub of
 * a second wall, a window slit and a heap at the foot. Invented.
 */
const RUINS: readonly { x: number; z: number; h: number; w: number }[] = [
  { x: -17, z: -26, h: 5.5, w: 1.2 },
  { x: -31, z: -36, h: 8, w: 1.6 },
  { x: -42, z: -30, h: 4.5, w: 1.1 },
  { x: -57, z: -40, h: 9, w: 1.8 },
  { x: -72, z: -33, h: 6, w: 1.4 },
];

function ruins(out: Out): void {
  RUINS.forEach((t, i) => {
    const tone = 0.95;
    put(out, `ruin-${i}-shaft`, t.x, 0, t.z, t.w, t.h, t.w, tone);
    // The broken top: a shorter wall beside the shaft, and a notch of nothing between.
    put(
      out,
      `ruin-${i}-wall`,
      t.x + t.w * 0.9,
      0,
      t.z,
      t.w * 0.6,
      t.h * 0.55,
      t.w * 0.8,
      tone - 0.1,
    );
    put(
      out,
      `ruin-${i}-slit`,
      t.x,
      t.h * 0.55,
      t.z + t.w / 2 + 0.01,
      t.w * 0.16,
      t.h * 0.1,
      0.02,
      DARK_TONE,
    );
    put(
      out,
      `ruin-${i}-heap`,
      t.x - t.w * 0.4,
      0,
      t.z + 0.4,
      t.w * 2.4,
      0.5,
      t.w * 1.4,
      tone - 0.3,
    );
  });
}

/** Stones lying about the plain: low, dark, with no shape to read. */
function stones(out: Out): void {
  const rand = mulberry32(31);
  for (let i = 0; i < 14; i++) {
    const w = 0.6 + rand() * 1.4;
    put(
      out,
      `stone-${i}`,
      -14 - rand() * 52,
      0,
      -2 - rand() * 14,
      w,
      0.12 + rand() * 0.16,
      w * 0.6,
      0.18 + rand() * 0.12,
    );
  }
}

/**
 * Banks of haze lying along the plain; they drift slowly sideways. Each bank is
 * three nested slabs, so its edge thins out in steps instead of ending in a
 * hard rectangle.
 */
function haze(out: Out, quarter: Quarter | null): void {
  const { banks, alpha } = HAZE[quarter ?? 'NONE'];
  const rand = mulberry32(77);
  for (let i = 0; i < banks; i++) {
    const x = -8 - rand() * 60;
    const y = 0.2 + rand() * 1.4;
    const z = -10 - rand() * 28;
    const w = 14 + rand() * 12;
    const h = 0.5 + rand() * 0.9;
    const sway = 2 + rand() * 3;
    const phase = rand();
    HAZE_LAYERS.forEach(([scale, share], k) => {
      put(
        out,
        `haze-${i}-${k}`,
        x,
        y + (1 - scale) * h * 0.5,
        z,
        w * scale,
        h * scale,
        6,
        0.5,
        {
          alpha: alpha * share,
          sway,
          phase,
        },
      );
    });
  }
}

/** Width and height scale and share of the bank's density of each nested haze slab. */
const HAZE_LAYERS: readonly (readonly [number, number])[] = [
  [1, 0.4],
  [0.7, 0.7],
  [0.42, 1],
];

/** The Quarter's marks on the plain, in the same words as the Village's own. */
function weather(out: Out, quarter: Quarter | null): void {
  if (!quarter) return;
  const rand = mulberry32(5);
  const at = () => ({ x: -10 - rand() * 56, z: -1 - rand() * 12 });
  switch (quarter) {
    case 'SPRING':
      // Thaw: dark wet strips lying on the ground.
      for (let i = 0; i < 14; i++) {
        const { x, z } = at();
        put(
          out,
          `thaw-${i}`,
          x,
          0,
          z,
          1.4 + rand() * 1.8,
          0.012,
          0.3 + rand() * 0.3,
          DARK_TONE,
        );
      }
      break;
    case 'SUMMER':
      // Long light: pale bars along the ground, and dry cracks between them.
      for (let i = 0; i < 8; i++) {
        const { x, z } = at();
        put(out, `light-${i}`, x, 0, z, 6 + rand() * 9, 0.012, 0.08, 0.6);
      }
      for (let i = 0; i < 10; i++) {
        const { x, z } = at();
        put(
          out,
          `crack-${i}`,
          x,
          0,
          z,
          0.8 + rand() * 1.2,
          0.012,
          0.05,
          DARK_TONE,
        );
      }
      break;
    case 'AUTUMN':
      // Stubble: short stalks standing in the cut rows.
      for (let i = 0; i < 44; i++) {
        const { x, z } = at();
        put(out, `stubble-${i}`, x, 0, z, 0.05, 0.16 + rand() * 0.1, 0.05, 0.7);
      }
      break;
    case 'WINTER':
      // Snow: pale flat patches, and specks falling slowly through the air.
      for (let i = 0; i < 16; i++) {
        const { x, z } = at();
        put(
          out,
          `snow-${i}`,
          x,
          0,
          z,
          1.6 + rand() * 2.4,
          0.012,
          0.5 + rand() * 0.5,
          SNOW_TONE,
        );
      }
      for (let i = 0; i < SNOWFALL_SPECKS; i++) {
        const { x, z } = at();
        put(out, `flake-${i}`, x, 0.1, z, 0.08, 0.08, 0.08, SNOW_TONE, {
          fall: 9,
          sway: 0.2,
          phase: rand(),
        });
      }
      break;
  }
}

/**
 * Every box of the landscape for the Quarter (null in a Trial: no weather
 * marks). `nearZ` is how far toward the camera the plain reaches.
 */
export function planLandscape(
  quarter: Quarter | null,
  nearZ: number = PLAIN_NEAR,
): LandscapeVolume[] {
  const out: Out = [];
  plain(out, PLAIN_TONE[quarter ?? 'NONE'], nearZ);
  ridges(out);
  ruins(out);
  stones(out);
  weather(out, quarter);
  haze(out, quarter);
  return out;
}

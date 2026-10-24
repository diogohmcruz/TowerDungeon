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
export const LANDSCAPE_CAPACITY = 512;

/** Where the valley opens between the far ridges (x), and how far its lowland runs back (z). */
export const VALLEY_X0 = -80;
export const VALLEY_X1 = -34;
export const VALLEY_FAR = -112;

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
 * Two ranks of far ridge, each a run of stepped blocks whose heights wander (a
 * silhouette, not a drawing of a mountain). Further ranks are lower and fainter.
 */
function ridges(out: Out): void {
  const ranks = [
    { z: -62, base: 9, span: 6, tone: 0.85, step: 14 },
    { z: -54, base: 6, span: 4.5, tone: 0.78, step: 11 },
  ];
  ranks.forEach((rank, r) => {
    const rand = mulberry32(900 + r);
    let h = rank.base;
    for (let x = -130; x < 130; x += rank.step) {
      // The valley: the ranks part here and the lowland shows between them.
      if (x + rank.step > VALLEY_X0 && x < VALLEY_X1) {
        continue;
      }
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
 * A broken cliff (#139): a run of stepped rock blocks whose heights wander, each with a narrower
 * shelf set back on top, dark crevices cut down the face and, in Winter, snow on the tops. Boxes
 * only; the face shading of the ink does the rest. `scale` widens everything with distance.
 */
export interface Cliff {
  id: string;
  x0: number;
  x1: number;
  z: number;
  height: number;
  scale: number;
  seed: number;
  tone: number;
}

export const CLIFFS: readonly Cliff[] = [
  // Behind the ruins, on either side of the valley the river leaves by.
  { id: 'cliff-a', x0: -112, x1: -76, z: -50, height: 12, scale: 1.5, seed: 11, tone: 0.62 },
  { id: 'cliff-b', x0: -32, x1: 6, z: -50, height: 10, scale: 1.5, seed: 12, tone: 0.58 },
  // A nearer face, so the plain ends in rock on the left.
  { id: 'cliff-c', x0: -64, x1: -45, z: -21, height: 6.5, scale: 0.9, seed: 13, tone: 0.55 },
];

function cliff(out: Out, c: Cliff, snow: boolean): void {
  const rand = mulberry32(c.seed);
  const s = c.scale;
  let h = c.height * 0.6;
  let x = c.x0;
  let i = 0;
  while (x < c.x1) {
    const w = (2.2 + rand() * 3.6) * s;
    h = Math.min(c.height, Math.max(c.height * 0.3, h + (rand() - 0.5) * c.height * 0.8));
    const d = (4 + rand() * 2) * s;
    const z = c.z + (rand() - 0.5) * 1.6 * s;
    const tone = c.tone + (rand() - 0.5) * 0.14;
    put(out, `${c.id}-b${i}`, x + w / 2, -0.01, z, w, h, d, tone);
    // The shelf: a narrower block set back on the top, broken off short.
    const h2 = h * (0.2 + rand() * 0.35);
    const w2 = w * (0.4 + rand() * 0.35);
    const x2 = x + rand() * (w - w2) + w2 / 2;
    put(out, `${c.id}-t${i}`, x2, h - 0.01, z - d * 0.12, w2, h2, d * 0.7, tone + 0.08);
    // Crevices: dark, thin, cut down the front of the block (one wide, one narrow).
    const front = z + d / 2 + 0.01;
    put(out, `${c.id}-c${i}`, x + w * (0.2 + rand() * 0.6), h * 0.12, front, 0.26 * s, h * (0.45 + rand() * 0.4), 0.04, DARK_TONE);
    if (rand() < 0.5) {
      put(out, `${c.id}-n${i}`, x + w * (0.1 + rand() * 0.8), h * 0.5, front, 0.18 * s, h * 0.35, 0.04, DARK_TONE);
    }
    if (snow) {
      put(out, `${c.id}-s${i}`, x + w / 2, h - 0.01, z, w * 0.96, 0.05 * s, d * 0.96, SNOW_TONE);
    }
    x += w * 0.82;
    i++;
  }
}

function cliffs(out: Out, quarter: Quarter | null): void {
  CLIFFS.forEach((c) => cliff(out, c, quarter === 'WINTER'));
}

/** The river's look per Quarter: Spring's thaw swells it, Summer thins it, Winter freezes it pale. */
export const RIVER: Record<Quarter | 'NONE', { width: number; tone: number }> = {
  SPRING: { width: 1.6, tone: 0.06 },
  SUMMER: { width: 0.6, tone: 0.08 },
  AUTUMN: { width: 1, tone: 0.07 },
  WINTER: { width: 1, tone: 0.82 },
  NONE: { width: 1, tone: 0.07 },
};

/** The river's course: where it stands at a depth (control points, then a slow wander). */
const RIVER_COURSE: readonly (readonly [number, number])[] = [
  [VALLEY_FAR, -72],
  [-80, -64],
  [-50, -54],
  [-36, -49],
  [-26, -30],
  [-14, -22],
  [-4, -16],
  [6, -12],
];

export function riverX(z: number): number {
  let k = 0;
  while (k < RIVER_COURSE.length - 2 && z > RIVER_COURSE[k + 1][0]) k++;
  const [z0, x0] = RIVER_COURSE[k];
  const [z1, x1] = RIVER_COURSE[k + 1];
  const t = Math.min(1, Math.max(0, (z - z0) / (z1 - z0)));
  return x0 + (x1 - x0) * t + 1.8 * Math.sin(z * 0.37) * (0.3 + 0.7 * t);
}

/**
 * The valley (#139): the lowland between the parted far ridges, with a river winding down it
 * toward the camera as a thin dark line of small boxes, widening as it nears.
 */
function valley(out: Out, quarter: Quarter | null): void {
  const look = RIVER[quarter ?? 'NONE'];
  const near = PLAIN_FAR + 0.5;
  put(
    out,
    'valley-floor',
    (VALLEY_X0 + VALLEY_X1) / 2,
    -0.4 - PLAIN_DROP,
    (VALLEY_FAR + near) / 2,
    VALLEY_X1 - VALLEY_X0,
    0.4,
    near - VALLEY_FAR,
    PLAIN_TONE[quarter ?? 'NONE'] - 0.06,
  );
  // Beyond the lowland, one more faint ridge closes the valley.
  put(out, 'valley-ridge', (VALLEY_X0 + VALLEY_X1) / 2, -0.01, VALLEY_FAR - 4, VALLEY_X1 - VALLEY_X0 + 20, 7, 6, 0.88);
  const steps = 36;
  for (let i = 0; i < steps; i++) {
    const t = i / steps;
    // Denser toward the camera, where the river is wider.
    const z = VALLEY_FAR + (2 - VALLEY_FAR) * Math.pow(t, 1.6);
    const z2 = VALLEY_FAR + (2 - VALLEY_FAR) * Math.pow((i + 1) / steps, 1.6);
    const x = riverX(z);
    const x2 = riverX(z2);
    const wide = (0.4 + 1.5 * Math.pow(t, 1.4)) * look.width;
    put(
      out,
      `river-${i}`,
      (x + x2) / 2,
      0,
      (z + z2) / 2,
      Math.abs(x2 - x) + wide,
      0.012,
      Math.abs(z2 - z) + wide * 0.4,
      look.tone,
    );
  }
}

/** Flocks grazing on the plain, for scale: a pale body and a dark head each, drifting a little. */
const FLOCKS: readonly { x: number; z: number; n: number }[] = [
  { x: -30, z: -14, n: 4 },
  { x: -20, z: -7, n: 3 },
  { x: -42, z: -9, n: 4 },
];

function sheep(out: Out): void {
  const rand = mulberry32(21);
  FLOCKS.forEach((f, k) => {
    for (let i = 0; i < f.n; i++) {
      const x = f.x + (rand() - 0.5) * 4.5;
      const z = f.z + (rand() - 0.5) * 2;
      const phase = rand();
      const sway = 0.3 + rand() * 0.4;
      put(out, `sheep-${k}-${i}-body`, x, 0.06, z, 0.5, 0.3, 0.28, 0.88, { sway, phase });
      put(out, `sheep-${k}-${i}-head`, x + 0.3, 0.14, z, 0.16, 0.16, 0.14, 0.2, { sway, phase });
    }
  });
}

/** Where the crown stands: far behind the Village on the Tower's own column, so it meets the shaft's last dither. */
export const CROWN_Z = -60;
export const CROWN_WIDTH = 11;
export const CROWN_BASE = 38.9;
export const CROWN_SHAFT = 12;

/**
 * The crown (#139): the Tower seen from afar, where the shaft thins into dither: a short run of
 * shaft whose first stretch is almost nothing and thickens up to a broken top (uneven jagged
 * stubs, a few dark window slits), with one thin band of cloud across its foot and one below. Fixed
 * art: it stands for no Floor, no height and no state (architecture doc 5.4).
 */
function crown(out: Out): void {
  const w = CROWN_WIDTH;
  const slices = 5;
  const sh = CROWN_SHAFT / slices;
  for (let i = 0; i < slices; i++) {
    put(out, `crown-shaft-${i}`, 0, CROWN_BASE + i * sh, CROWN_Z, w, sh, w * 0.5, 0.62, { alpha: [0.1, 0.22, 0.4, 0.7, 1][i] });
  }
  // The broken top: stubs of uneven height (as shares of the width), jagged, with a notch of nothing.
  const top = CROWN_BASE + CROWN_SHAFT;
  const stubs: readonly [number, number, number][] = [
    [-0.4, 0.2, 0.04],
    [-0.22, 0.16, 0.1],
    [-0.07, 0.14, 0.2],
    [0.06, 0.12, 0.07],
    [0.18, 0.12, 0.15],
    [0.315, 0.15, 0.36],
    [0.445, 0.11, 0.12],
  ];
  stubs.forEach(([cx, ww, hh], i) => {
    put(out, `crown-stub-${i}`, cx * w, top - 0.01, CROWN_Z + 0.2 * (i % 2), ww * w, hh * w, w * 0.5 - 0.2 * (i % 3), 0.74 + 0.05 * (i % 3), { alpha: 1 });
  });
  // Window slits, dark: set in the top of the shaft.
  const slits: readonly [number, number, number][] = [
    [-0.28, 0.66, 0.2],
    [0.2, 0.8, 0.22],
    [-0.02, 0.52, 0.16],
  ];
  slits.forEach(([cx, at, hh], i) => {
    put(out, `crown-slit-${i}`, cx * w, CROWN_BASE + at * CROWN_SHAFT, CROWN_Z + w * 0.25 + 0.05, w * 0.07, hh * w, 0.05, DARK_TONE);
  });
}

/**
 * Thin bands of cloud (#139) drifting across the crown and lying at its foot, each three nested
 * slabs so its edge thins by steps; and a few drifting low over the cliffs. Weather's haze is
 * separate: these are the same in every Quarter.
 */
function clouds(out: Out, withCrown: boolean): void {
  const rand = mulberry32(61);
  const band = (id: string, x: number, y: number, z: number, w: number, h: number, sway: number, alpha: number) => {
    const phase = rand();
    HAZE_LAYERS.forEach(([scale, share], k) => {
      put(out, `${id}-${k}`, x, y + (1 - scale) * h * 0.5, z, w * scale, h * scale, 5, 0.7, {
        alpha: alpha * share,
        sway,
        phase,
      });
    });
  };
  if (withCrown) {
    band('cloud-crown-0', -3, CROWN_BASE + CROWN_SHAFT * 0.6, CROWN_Z + 6, 30, 0.5, 5, 0.5);
    band('cloud-crown-1', 3, CROWN_BASE - 0.5, CROWN_Z + 6, 42, 0.6, 4, 0.62);
  }
  for (let i = 0; i < 3; i++) {
    band(`cloud-low-${i}`, -20 - i * 34 - rand() * 10, 9 + rand() * 6, -52 - rand() * 6, 30 + rand() * 12, 0.5, 4 + rand() * 3, 0.38);
  }
}

/**
 * Every box of the landscape for the Quarter (null in a Trial: no weather
 * marks). `nearZ` is how far toward the camera the plain reaches. `withCrown`
 * is false where the Tower itself is drawn (the Tower scene has no crown to add).
 */
export function planLandscape(
  quarter: Quarter | null,
  nearZ: number = PLAIN_NEAR,
  withCrown = true,
): LandscapeVolume[] {
  const out: Out = [];
  plain(out, PLAIN_TONE[quarter ?? 'NONE'], nearZ);
  ridges(out);
  valley(out, quarter);
  cliffs(out, quarter);
  ruins(out);
  sheep(out);
  if (withCrown) crown(out);
  clouds(out, withCrown);
  stones(out);
  weather(out, quarter);
  haze(out, quarter);
  return out;
}

/**
 * The gauge drawing kit (architecture doc 5.6): a number is a gauge on a
 * building, and a gauge is a few boxes. Three forms, all built from the Village's
 * box kit: a slit (a dark channel a bone fill rises in), notches (a row of
 * squares, some lit) and a bar (a slit laid on its side). Pure, no three.js.
 *
 * Readable at pixel scale 2: no part is thinner than two virtual pixels, a fill
 * snaps to whole virtual pixels, and any fill above nothing shows at least one,
 * so a small stock is never drawn as an empty gauge. Fill is a 0..1 share; the
 * exact figure is for the hover caption, never the drawing.
 */
import type { Volume } from './village-layout';

/** One virtual pixel in world units (an 11-unit frame across about 220 pixels). */
export const GAUGE_PIXEL = 11 / 220;
/** The thinnest a gauge part is drawn: two virtual pixels. */
export const GAUGE_MIN = 2 * GAUGE_PIXEL;

/** Ink density of the channel and of the fill; as `VILLAGE_TONE.dark` and `.lit`. */
const DARK = 0.12;
const LIT = 1;
/** The fill sits this far in front of the channel, which sits just in front of the wall. */
const FILL_LIFT = 0.01;

const clamp01 = (n: number) =>
  Math.min(1, Math.max(0, Number.isFinite(n) ? n : 0));

/** A length of `fill` (0..1) of `length`, in whole virtual pixels; any fill above 0 shows one. */
export function fillExtent(length: number, fill: number): number {
  const f = clamp01(fill);
  if (f === 0) return 0;
  const pixels = Math.max(1, Math.round((length * f) / GAUGE_PIXEL));
  return Math.min(length, pixels * GAUGE_PIXEL);
}

function box(
  out: Volume[],
  id: string,
  x: number,
  y: number,
  z: number,
  w: number,
  h: number,
  d: number,
  tone: number,
): void {
  out.push({ id, group: 'village', x, y, z, w, h, d, tone, alpha: 1 });
}

export interface SlitSpec {
  /** Centre x, the foot of the channel, and the z of the wall it is cut in. */
  x: number;
  bottom: number;
  z: number;
  height: number;
  /** 0..1. */
  fill: number;
  /** Channel width; never below two virtual pixels. */
  width?: number;
}

/**
 * A vertical slit: ids `<id>-slit` (the channel) and `<id>-fill` (only when
 * there is something in it). The fill rises from the foot.
 */
export function slitGauge(out: Volume[], id: string, spec: SlitSpec): void {
  const w = Math.max(spec.width ?? GAUGE_MIN, GAUGE_MIN);
  box(out, `${id}-slit`, spec.x, spec.bottom + spec.height / 2, spec.z, w, spec.height, 0.02, DARK);
  const h = fillExtent(spec.height, spec.fill);
  if (h > 0) {
    box(out, `${id}-fill`, spec.x, spec.bottom + h / 2, spec.z + FILL_LIFT, w, h, 0.02, LIT);
  }
}

export interface BarSpec {
  /** Centre x, the foot of the bar, and its z. */
  x: number;
  bottom: number;
  z: number;
  length: number;
  fill: number;
  /** Bar thickness; never below two virtual pixels. */
  thickness?: number;
}

/** A horizontal bar: ids `<id>-bar` and `<id>-fill`, the fill growing from the left. */
export function barGauge(out: Volume[], id: string, spec: BarSpec): void {
  const t = Math.max(spec.thickness ?? GAUGE_MIN, GAUGE_MIN);
  box(out, `${id}-bar`, spec.x, spec.bottom + t / 2, spec.z, spec.length, t, 0.02, DARK);
  const w = fillExtent(spec.length, spec.fill);
  if (w > 0) {
    box(
      out,
      `${id}-fill`,
      spec.x - spec.length / 2 + w / 2,
      spec.bottom + t / 2,
      spec.z + FILL_LIFT,
      w,
      t,
      0.02,
      LIT,
    );
  }
}

export interface NotchSpec {
  /** Centre x of the row, its foot and z. */
  x: number;
  bottom: number;
  z: number;
  /** How many notches in all, and how many of them are lit (from the left). */
  count: number;
  lit: number;
  /** Side of one notch; never below two virtual pixels. */
  size?: number;
}

/** A row of square notches, ids `<id>-notch-<i>`; the first `lit` are bone, the rest dark. */
export function notchGauge(out: Volume[], id: string, spec: NotchSpec): void {
  const size = Math.max(spec.size ?? GAUGE_MIN, GAUGE_MIN);
  const gap = GAUGE_PIXEL * 2;
  const count = Math.max(0, Math.floor(spec.count));
  const lit = Math.min(count, Math.max(0, Math.floor(spec.lit)));
  const row = count * size + Math.max(0, count - 1) * gap;
  for (let i = 0; i < count; i++) {
    const x = spec.x - row / 2 + size / 2 + i * (size + gap);
    box(out, `${id}-notch-${i}`, x, spec.bottom + size / 2, spec.z, size, size, 0.02, i < lit ? LIT : DARK);
  }
}

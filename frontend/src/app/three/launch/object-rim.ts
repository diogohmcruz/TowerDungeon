/**
 * A rim of clear ground behind the launch and Expedition objects (issue #122).
 *
 * The bell, the recall lamp, the Supplies post and the crates stand just outside
 * the Floor strip, in front of the ambient landscape (#90). There the ridges, the
 * ruins and the plain are dithered through the same ramp slots as the objects, so
 * on the Floors where the horizon sits behind them (about Floors 1 to 20) the
 * object's dither dissolves into the ground's. Each part gets a plate a pixel or two
 * larger than itself, behind it, at ink density 0: ramp slot 0, the colour of
 * the clear ground. Round a part it is a flat dark outline at night and a lit rim
 * by day; no new colour, and the landscape and the Floor strips are not touched.
 *
 * Pure layout, no three.js.
 */
import type { Volume } from '../village/village-layout';

/** Boxes the launch mesh holds: the objects, their outlines and their rims. */
export const LAUNCH_CAPACITY = 256;

/** Parts thinner than this are decals on a part that has its own rim (a slat, a slit, a tally). */
export const RIM_MIN_DEPTH = 0.05;
/**
 * How far the rim reaches past a part on every side: one virtual pixel at the
 * coarsest pixel scale (4: about 0.07 units at the battle Shot), two at scale 2.
 */
export const RIM_PAD = 0.07;
/** The rim's thickness; it sits wholly behind the part's back face. */
export const RIM_DEPTH = 0.02;
/** Id suffix of a rim; the part's id stays in front, so a rim picks as its part. */
export const RIM_SUFFIX = '-rim';

/** The parts that get a rim: drawn, solid, and not an outline or a decal. */
export function needsRim(v: Volume): boolean {
  return (
    v.alpha > 0 &&
    v.d >= RIM_MIN_DEPTH &&
    !v.id.startsWith('outline-') &&
    !v.id.endsWith(RIM_SUFFIX)
  );
}

/** The rim plate of one part. */
export function rimOf(v: Volume): Volume {
  return {
    id: `${v.id}${RIM_SUFFIX}`,
    group: v.group,
    x: v.x,
    y: v.y,
    z: v.z - v.d / 2 - RIM_DEPTH,
    w: v.w + 2 * RIM_PAD,
    h: v.h + 2 * RIM_PAD,
    d: RIM_DEPTH,
    tone: 0,
    alpha: 1,
  };
}

/** The rims of every part in `volumes`, to be drawn with them (they all sit behind). */
export function rimVolumes(volumes: readonly Volume[]): Volume[] {
  return volumes.filter(needsRim).map(rimOf);
}

/**
 * The focus of a Village thing as a cube (#133, architecture doc 5.2 and 5.6). Pure, no three.js.
 *
 * A hovered or chosen thing is framed by the twelve edges of the box around all of its volumes
 * (its prefix group, `ofSubject`), as thin boxes in the same box kit: it shows the thing's width,
 * height and depth, not only where it stands on screen. The chosen one is two pixels thick and
 * solid; the hovered one is one pixel and dashed, so the two differ by more than colour, as the
 * unit view's outline does. The hit regions (`volume-regions.ts`) never read these volumes.
 *
 * Every `outline-` volume is drawn by the Village scene in its own pass above the buildings
 * (`VillageSceneGraph`), so no face of the thing hides one of its edges.
 */
import type { Volume } from './village-layout';
import { OUTLINE, OUTLINED_KINDS, OUTLINE_PIXEL, ofSubject } from './village-subjects';

/** Instances of the focus cube mesh (one draw call): a dashed cube, a solid one and the Start marks. */
export const VILLAGE_OVERLAY_CAPACITY = 384;

/** A cube never has an edge shorter than this, so a footing (a hairline) or a mark is still a box. */
export const CUBE_MIN_SIDE = 0.2;
/** Space kept between the cube and the volumes it holds, on each side but the ground. */
export const CUBE_PAD = 0.05;
/** Dash length and gap of the hovered cube, in world units (about four and three pixels). */
const CUBE_DASH = 0.2;
const CUBE_GAP = 0.14;

/** The corners of a thing's cube. */
export interface CubeBounds {
  x0: number;
  x1: number;
  y0: number;
  y1: number;
  z0: number;
  z1: number;
}

/** One axis of a cube: the parts' extent, padded, and widened about its middle to the minimum. */
function span(lo: number, hi: number, pad: number): [number, number] {
  const a = lo - pad;
  const b = hi + pad;
  if (b - a >= CUBE_MIN_SIDE) return [a, b];
  const mid = (a + b) / 2;
  return [mid - CUBE_MIN_SIDE / 2, mid + CUBE_MIN_SIDE / 2];
}

/**
 * The cube around some volumes: their axis-aligned bounds on all three axes, padded. It rests on
 * the lowest of them (no pad below) and rises from there if the thing is flat. Null when empty.
 */
export function cubeBounds(parts: readonly Volume[]): CubeBounds | null {
  if (parts.length === 0) return null;
  const [x0, x1] = span(
    Math.min(...parts.map((v) => v.x - v.w / 2)),
    Math.max(...parts.map((v) => v.x + v.w / 2)),
    CUBE_PAD,
  );
  const [z0, z1] = span(
    Math.min(...parts.map((v) => v.z - v.d / 2)),
    Math.max(...parts.map((v) => v.z + v.d / 2)),
    CUBE_PAD,
  );
  const lo = Math.min(...parts.map((v) => v.y - v.h / 2));
  const hi = Math.max(...parts.map((v) => v.y + v.h / 2)) + CUBE_PAD;
  return { x0, x1, y0: lo, y1: Math.max(hi, lo + CUBE_MIN_SIDE), z0, z1 };
}

/** The volume ids of one cube edge: `outline-<id>:<tag>:<kind>-<axis><l|h><l|h>` (+ a dash number). */
export const CUBE_EDGE = /^outline-.+:(?:hover|select):[a-z-]+-([xyz])([lh])([lh])(\d*)$/;

/**
 * The twelve edges of the cube of each kind a thing has (an Annex and its Villagers' lane are two).
 * Each edge is named by the axis it runs along, then its place on the other two axes: x edges by
 * (y, z), y edges by (x, z), z edges by (x, y), low `l` or high `h`. A dashed edge is cut into runs,
 * each with a number after.
 */
export function cubeOutlineVolumes(
  volumes: Volume[],
  id: string,
  weight: 1 | 2,
  tag: 'hover' | 'select',
): Volume[] {
  const out: Volume[] = [];
  const t = OUTLINE_PIXEL * weight;
  const dashed = tag === 'hover';
  for (const kind of OUTLINED_KINDS) {
    const b = cubeBounds(ofSubject(volumes, id, kind));
    if (!b) continue;
    const name = `${OUTLINE}${id}:${tag}:${kind}`;
    // An edge runs from `lo` to `hi` on its axis: a thickness past each end so the corners are
    // filled, but the uprights stop at the ground. `at` holds its place on the other two axes.
    const edge = (
      suffix: string,
      axis: 'x' | 'y' | 'z',
      at: { x: number; y: number; z: number },
      lo: number,
      hi: number,
    ) => {
      const piece = (key: string, from: number, run: number) => {
        out.push({
          id: key,
          group: 'village',
          ...at,
          [axis]: from + run / 2,
          w: axis === 'x' ? run : t,
          h: axis === 'y' ? run : t,
          d: axis === 'z' ? run : t,
          tone: 1,
          alpha: 1,
        });
      };
      const total = hi - lo;
      if (!dashed) {
        piece(`${name}-${suffix}`, lo, total);
        return;
      }
      const runs = Math.max(1, Math.round((total + CUBE_GAP) / (CUBE_DASH + CUBE_GAP)));
      const dash = (total + CUBE_GAP) / runs - CUBE_GAP;
      for (let i = 0; i < runs; i++) {
        piece(`${name}-${suffix}${i}`, lo + i * (dash + CUBE_GAP), dash);
      }
    };
    const mx = (b.x0 + b.x1) / 2;
    const my = (b.y0 + b.y1) / 2;
    const mz = (b.z0 + b.z1) / 2;
    const xs = [b.x0, b.x1] as const;
    // The low edges lie on the ground, not in it: their centre sits half a thickness up.
    const ys = [b.y0 + t / 2, b.y1] as const;
    const zs = [b.z0, b.z1] as const;
    const lh = ['l', 'h'] as const;
    for (let i = 0; i < 2; i++) {
      for (let j = 0; j < 2; j++) {
        edge(`x${lh[i]}${lh[j]}`, 'x', { x: mx, y: ys[i], z: zs[j] }, b.x0 - t / 2, b.x1 + t / 2);
        edge(`y${lh[i]}${lh[j]}`, 'y', { x: xs[i], y: my, z: zs[j] }, b.y0, b.y1 + t / 2);
        edge(`z${lh[i]}${lh[j]}`, 'z', { x: xs[i], y: ys[j], z: mz }, b.z0 - t / 2, b.z1 + t / 2);
      }
    }
  }
  return out;
}

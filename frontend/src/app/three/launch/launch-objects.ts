/**
 * The launch controls as objects (architecture doc 5.5), built from the same box
 * kit as the Village: a bell hung in a timber gate, and a post with a lamp. Pure
 * layout, no three.js. A control that can be used is lit (bone); one that cannot
 * is dark and wears a dashed outline, so its state is never carried by colour alone.
 */
import type { Volume } from '../village/village-layout';
import { BELL_SUBJECT, outlineVolumes, RECALL_SUBJECT } from '../village/village-subjects';

/** Ink density per part (0..1), as `VILLAGE_TONE` in the Village layout. */
const TONE = { lit: 1, bone: 0.9, dark: 0.12, timber: 0.55, stone: 0.72 } as const;

/** Where an object stands: its centre x, the height of its foot, and its depth. */
export interface LaunchSpot {
  x: number;
  bottom: number;
  z: number;
}

function box(
  out: Volume[],
  id: string,
  x: number,
  bottom: number,
  z: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  alpha = 1,
): void {
  out.push({ id, group: 'village', x, y: bottom + h / 2, z, w, h, d, tone, alpha });
}

/** Overall size of the bell gate, for the layout and the tests. */
export const BELL_GATE = { w: 1.0, h: 1.4, d: 0.2 } as const;
/** Overall size of the recall lamp's post. */
export const RECALL_POST = { w: 0.5, h: 1.5, d: 0.2 } as const;

/**
 * The gate bell: two timber posts, a beam and a bell hung from it with its
 * clapper. An invisible hit box (dithered away entirely) makes the whole gate
 * easy to pick. Ids begin `bell-`.
 */
export function bellObject(out: Volume[], at: LaunchSpot, usable: boolean): void {
  const { w, h, d } = BELL_GATE;
  const post = 0.1;
  box(out, 'bell-post-w', at.x - (w - post) / 2, at.bottom, at.z, post, h, post, TONE.timber);
  box(out, 'bell-post-e', at.x + (w - post) / 2, at.bottom, at.z, post, h, post, TONE.timber);
  box(out, 'bell-beam', at.x, at.bottom + h - 0.08, at.z, w, 0.08, post + 0.02, TONE.timber);
  const tone = usable ? TONE.bone : TONE.dark;
  // The bell: a wide lip, a narrower shoulder and a crown on the beam.
  box(out, 'bell-lip', at.x, at.bottom + 0.5, at.z + 0.01, 0.42, 0.12, 0.2, tone);
  box(out, 'bell-body', at.x, at.bottom + 0.62, at.z + 0.01, 0.3, 0.46, 0.18, tone);
  box(out, 'bell-crown', at.x, at.bottom + h - 0.3, at.z + 0.01, 0.12, 0.16, 0.1, tone);
  // The clapper hangs below the lip, lit with the bell.
  box(out, 'bell-clapper', at.x, at.bottom + 0.4, at.z + 0.01, 0.06, 0.12, 0.06, usable ? TONE.lit : TONE.timber);
  box(out, 'bell-hit', at.x, at.bottom, at.z, w, h, d, TONE.dark, 0);
}

/**
 * The recall lamp: a stone foot, a timber post, an arm, and a lamp in a dark
 * housing whose core is lit when it can be used. Ids begin `recall-`.
 */
export function recallObject(out: Volume[], at: LaunchSpot, usable: boolean): void {
  const { w, h, d } = RECALL_POST;
  box(out, 'recall-foot', at.x, at.bottom, at.z, 0.26, 0.1, 0.2, TONE.stone);
  box(out, 'recall-post', at.x, at.bottom + 0.1, at.z, 0.08, h - 0.45, 0.08, TONE.timber);
  box(out, 'recall-arm', at.x, at.bottom + h - 0.45, at.z, 0.3, 0.06, 0.08, TONE.timber);
  box(out, 'recall-housing', at.x, at.bottom + h - 0.4, at.z + 0.01, 0.3, 0.38, 0.14, TONE.dark);
  box(out, 'recall-core', at.x, at.bottom + h - 0.34, at.z + 0.09, 0.16, 0.24, 0.04, usable ? TONE.lit : TONE.timber);
  box(out, 'recall-cap', at.x, at.bottom + h - 0.02, at.z + 0.01, 0.36, 0.04, 0.18, TONE.timber);
  box(out, 'recall-hit', at.x, at.bottom, at.z, w, h, d, TONE.dark, 0);
}

/** What the Tower scene needs to draw its launch objects beside the Party's Floor. */
export interface TowerLaunchInput {
  /** World Y of the walkable surface the Party stands on. */
  surfaceY: number;
  /** The bell sends a Reinforcement Wave out here; it is dark when it cannot. */
  bellUsable: boolean;
  recallUsable: boolean;
  /** The subject id hovered, if any: its outline is one pixel. */
  hovered?: string | null;
}

/** Horizontal spot of the bell and of the lamp either side of the fight. */
export const TOWER_BELL_X = -2.4;
export const TOWER_RECALL_X = 2.4;
/** Depth in front of the Floor's backdrop, clear of the figures. */
export const TOWER_LAUNCH_Z = 0.5;

/**
 * The bell and the recall lamp beside the Party's Floor, as boxes, with a dashed
 * outline on whichever cannot be used and a line on whichever is hovered.
 */
export function towerLaunchVolumes(input: TowerLaunchInput): Volume[] {
  const out: Volume[] = [];
  bellObject(
    out,
    { x: TOWER_BELL_X, bottom: input.surfaceY, z: TOWER_LAUNCH_Z },
    input.bellUsable,
  );
  recallObject(
    out,
    { x: TOWER_RECALL_X, bottom: input.surfaceY, z: TOWER_LAUNCH_Z },
    input.recallUsable,
  );
  const frames: Array<[string, boolean]> = [
    [BELL_SUBJECT, input.bellUsable],
    [RECALL_SUBJECT, input.recallUsable],
  ];
  const marks: Volume[] = [];
  for (const [id, usable] of frames) {
    if (!usable) marks.push(...outlineVolumes(out, id, 1, 'unlit', 'dashed'));
    if (input.hovered === id) marks.push(...outlineVolumes(out, id, 1, 'hover'));
  }
  return [...out, ...marks];
}

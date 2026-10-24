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

/** Id prefix of the Gate frame (the Loadout building): `gate-`, with `gate-hit-*` for picking. */
export const GATE_PREFIX = 'gate-';
/** How far the Gate's stone piers stand from the bell's centre, and how tall the frame is. */
export const GATE_FRAME = { reach: 0.62, pier: 0.16, h: 1.6, lintel: 0.18 } as const;

/**
 * The Gate: a stone frame around the bell's timber gate, two piers and a lintel
 * with three bone tally marks (what is chosen here). It is the Loadout building;
 * the bell inside it still rings. The invisible near-miss pads cover only the
 * frame (the piers and a strip above the lintel), never the bell's own hit box,
 * and sit behind it, so a click on the bell rings it and a click on the frame
 * opens the popover. Ids begin `gate-`.
 */
export function gateObject(out: Volume[], at: LaunchSpot): void {
  const { reach, pier, h, lintel } = GATE_FRAME;
  const beam = BELL_GATE.h;
  box(out, 'gate-pier-w', at.x - reach, at.bottom, at.z, pier, h, 0.2, TONE.stone);
  box(out, 'gate-pier-e', at.x + reach, at.bottom, at.z, pier, h, 0.2, TONE.stone);
  box(out, 'gate-lintel', at.x, at.bottom + beam, at.z, reach * 2 + pier, lintel, 0.2, TONE.stone);
  for (const [i, dx] of [-0.2, 0, 0.2].entries()) {
    box(out, `gate-tally-${i}`, at.x + dx, at.bottom + beam + 0.06, at.z + 0.11, 0.08, 0.06, 0.03, TONE.lit);
  }
  // Behind the bell's own hit box (front face at z + 0.1), so the bell wins where they meet.
  const z = at.z - 0.05;
  const padH = h + 0.15;
  for (const [id, dx] of [['w', -reach], ['e', reach]] as const) {
    box(out, `gate-hit-${id}`, at.x + dx, at.bottom, z, pier + 0.2, padH, 0.2, TONE.dark, 0);
  }
  box(out, 'gate-hit-top', at.x, at.bottom + beam, z, (reach + pier) * 2 + 0.2, lintel + 0.27, 0.2, TONE.dark, 0);
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

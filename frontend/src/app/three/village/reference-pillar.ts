/**
 * The reference pillar (architecture doc 5.6, 5.2): a squat stone pillar behind
 * the stair's head, beside the Village Tier lamps, carved with the Highest Floor
 * and the Milestones and, at Tiers I to III, the Toll. Each number is a gauge cut
 * in its face, drawn by `gauge-kit.ts` and kept separate so that at Tier IV the
 * Toll bar can move onto a Stone of Count and leave the pillar with two. Pure.
 *
 * The exact figures are for the hover caption and the popover, never the drawing.
 */
import { barGauge, slitGauge } from './gauge-kit';
import type { Volume } from './village-layout';

/** The Floors the Tower's drawn height stands for, so the carved slit is a share of 100. */
export const PILLAR_FLOORS = 100;

/** Volume id prefix and subject id of the pillar; `-hit` is the near-miss pad. */
export const PILLAR_PREFIX = 'pillar';
export const PILLAR_ID = 'PILLAR';

/** Where the pillar stands and how big it is, in world units. */
export const PILLAR = {
  x: 0.3,
  z: -0.3,
  w: 0.46,
  h: 1.7,
  d: 0.26,
} as const;

/** What the pillar is carved with. */
export interface PillarInput {
  highestFloor: number;
  milestonesEarned: number;
  milestonesTotal: number;
  /** Share (0..1) of the way from the last whole point of Legacy to the next; 0 when no forecast. */
  tollShare: number;
}

export const EMPTY_PILLAR: PillarInput = {
  highestFloor: 0,
  milestonesEarned: 0,
  milestonesTotal: 0,
  tollShare: 0,
};

/** Ink density per part (0..1), as `VILLAGE_TONE` in the Village layout. */
const TONE = { stone: 0.72, footing: 0.5, dark: 0.12 } as const;

const share = (n: number, of: number) =>
  of > 0 ? Math.min(1, Math.max(0, n / of)) : 0;

function box(
  out: Volume[],
  id: string,
  bottom: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  alpha = 1,
): void {
  out.push({
    id,
    group: 'village',
    x: PILLAR.x,
    y: bottom + h / 2,
    z: PILLAR.z,
    w,
    h,
    d,
    tone,
    alpha,
  });
}

/** The z of the pillar's front face, where the gauges are cut. */
const FACE = PILLAR.z + PILLAR.d / 2 + 0.02;

/** The Highest Floor, a slit on the left of the face. */
export function floorGauge(out: Volume[], input: PillarInput): void {
  slitGauge(out, `${PILLAR_PREFIX}-floor`, {
    x: PILLAR.x - 0.1,
    bottom: 0.45,
    z: FACE,
    height: 1.05,
    fill: share(input.highestFloor, PILLAR_FLOORS),
  });
}

/** The Milestones earned, a slit on the right of the face. */
export function milestoneGauge(out: Volume[], input: PillarInput): void {
  slitGauge(out, `${PILLAR_PREFIX}-milestones`, {
    x: PILLAR.x + 0.1,
    bottom: 0.45,
    z: FACE,
    height: 1.05,
    fill: share(input.milestonesEarned, input.milestonesTotal),
  });
}

/** The Toll towards the next Legacy, a bar across the foot (Tiers I to III). */
export function tollGauge(out: Volume[], input: PillarInput): void {
  barGauge(out, `${PILLAR_PREFIX}-toll`, {
    x: PILLAR.x,
    bottom: 0.25,
    z: FACE,
    length: 0.34,
    fill: input.tollShare,
  });
}

/**
 * The pillar: plinth, shaft and cap, the gauges carved in, and the near-miss pad.
 * `tollOnPillar` is false at Tier IV, when the Toll has gone to a Stone of Count.
 */
export function referencePillar(
  out: Volume[],
  input: PillarInput,
  tollOnPillar = true,
): void {
  const { w, h, d } = PILLAR;
  box(out, `${PILLAR_PREFIX}-plinth`, 0, w + 0.1, 0.12, d + 0.08, TONE.footing);
  box(out, `${PILLAR_PREFIX}-shaft`, 0.12, w, h - 0.18, d, TONE.stone);
  box(out, `${PILLAR_PREFIX}-cap`, h - 0.06, w + 0.08, 0.06, d + 0.06, TONE.stone);
  floorGauge(out, input);
  milestoneGauge(out, input);
  if (tollOnPillar) tollGauge(out, input);
  // The near-miss pad: an invisible box a little larger than the pillar.
  box(out, `${PILLAR_PREFIX}-hit`, 0, w + 0.3, h + 0.1, d + 0.1, TONE.dark, 0);
}

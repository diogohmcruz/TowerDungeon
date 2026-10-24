/**
 * The Set out transition (architecture doc 5.5): the Village scene and the Tower
 * scene stay two scenes, joined by an ordered-dither dissolve and a camera that
 * rises (Set out) or drops (Extraction, Wipe) over about 1.6 s. Pure timeline
 * maths, so it is testable without a renderer. The dissolve is a threshold, not a
 * blur: a pixel is covered with the void when its Bayer threshold is under the
 * cover, so the picture is only ever the two scenes and the void.
 */

export type TransitionKind = 'set-out' | 'extract' | 'wipe';

/** Set out: the Village dissolves into the void, the Tower scene dissolves in as the camera rises. */
export const COVER_MS = 500;
export const REVEAL_MS = 500;
/** The camera rise or drop; with the cover this is the 1.6 s of the Events table. */
export const RISE_MS = 1100;
export const TOTAL_MS = COVER_MS + RISE_MS;
/** A Wipe lingers on the empty stair before the camera drops: nothing moves, nothing celebrates. */
export const WIPE_HOLD_MS = 1000;
/** How far below its resting height the Tower camera starts or ends, in world units. */
export const RISE_DROP = 4;

export interface TransitionFrame {
  /** Whether the Tower scene (rather than the Village) is the one drawn. */
  tower: boolean;
  /** 0 nothing covered, 1 everything covered by the void. */
  cover: number;
  /** 1 the camera at its resting height, 0 fully dropped. Eased, never past 1. */
  lift: number;
  /** Whether the transition is over at this time. */
  done: boolean;
}

/** Zero slope at both ends and no overshoot. */
export function smoothstep(x: number): number {
  const t = Math.min(1, Math.max(0, x));
  return t * t * (3 - 2 * t);
}

/** Total length of a transition of this kind, in milliseconds. */
export function durationOf(kind: TransitionKind): number {
  return TOTAL_MS + (kind === 'wipe' ? WIPE_HOLD_MS : 0);
}

/** The state of the transition `ms` after it began. */
export function frameAt(kind: TransitionKind, ms: number): TransitionFrame {
  const done = ms >= durationOf(kind);
  if (kind === 'set-out') {
    const t = Math.max(0, ms);
    const cover =
      t < COVER_MS
        ? t / COVER_MS
        : t < COVER_MS + REVEAL_MS
          ? 1 - (t - COVER_MS) / REVEAL_MS
          : 0;
    return {
      tower: t >= COVER_MS,
      cover,
      lift: t < COVER_MS ? 0 : smoothstep((t - COVER_MS) / RISE_MS),
      done,
    };
  }
  // The drop runs the rise backwards; the Wipe first holds on the empty stair.
  const t = Math.max(0, ms - (kind === 'wipe' ? WIPE_HOLD_MS : 0));
  const swapAt = RISE_MS;
  const coverFrom = RISE_MS - COVER_MS;
  const cover =
    t < coverFrom
      ? 0
      : t < swapAt
        ? (t - coverFrom) / COVER_MS
        : t < swapAt + REVEAL_MS
          ? 1 - (t - swapAt) / REVEAL_MS
          : 0;
  return {
    tower: t < swapAt,
    cover,
    lift: 1 - smoothstep(t / RISE_MS),
    done,
  };
}

/** The 4x4 Bayer matrix of the ink shaders (`ink.glsl.ts`), row by row. */
const BAYER4 = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];

/** Ordered 4x4 Bayer threshold in (0,1) for a virtual-pixel coordinate. */
export function bayerThreshold(x: number, y: number): number {
  return (BAYER4[(y & 3) * 4 + (x & 3)] + 0.5) / 16;
}

/** Whether the pixel is covered at this cover amount (the same threshold as the ink shaders). */
export function covered(x: number, y: number, cover: number): boolean {
  return bayerThreshold(x, y) < cover;
}

/** The cover snapped to the 16 dither steps, so the veil only redraws when the picture changes. */
export function quantiseCover(cover: number): number {
  return Math.round(Math.min(1, Math.max(0, cover)) * 16) / 16;
}

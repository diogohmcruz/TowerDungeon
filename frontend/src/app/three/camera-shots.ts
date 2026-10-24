/**
 * Camera Shots for the Tower scene (architecture doc 3.5): a table of poses and
 * scalars damped toward the active one. No overshoot: each scalar closes a
 * fraction of its gap per frame, which never passes the target. The unit view
 * (#95) is the third rung of the ladder.
 */

/** The rungs of the zoom ladder, outermost first. */
export type ShotName = 'battle' | 'closeup' | 'unit';

export interface Shot {
  /** Vertical field of view, degrees. */
  fov: number;
  /** Camera distance in front of the Tower (z); in the unit Shot, in front of the figure. */
  distance: number;
  /** Camera height above the focused Floor's base; in the unit Shot, above the figure's centre. */
  camUp: number;
  /** Look-at height above the focused Floor's base; in the unit Shot, above the figure's centre. */
  lookUp: number;
  /** 0 frames the Tower in its region on the right; 1 centres it on the whole screen. */
  spread: number;
}

export const SHOTS: Readonly<Record<ShotName, Shot>> = {
  battle: { fov: 64, distance: 12.5, camUp: 7, lookUp: 1.3, spread: 0 },
  // About one Floor tall with the Floor above and below just in frame.
  closeup: { fov: 42, distance: 5.2, camUp: 1.85, lookUp: 1.85, spread: 1 },
  // The camera centres on one figure, about a third of the screen high, and looks slightly down.
  unit: { fov: 30, distance: 1.9, camUp: 0.25, lookUp: 0, spread: 1 },
};

/** Closer than this to its target a scalar lands on it, so the camera comes to rest. */
const SETTLE = 1e-3;

/** Rate of the exponential approach, per second. */
const RATE = Math.log(1000);

/** Fraction of the remaining gap closed in `delta` seconds. */
export function dampFactor(delta: number): number {
  return 1 - Math.exp(-RATE * Math.max(0, delta));
}

export function prefersReducedMotion(): boolean {
  return (
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/** The shot scalars as they are now, easing toward the active Shot. */
export class ShotBlend {
  readonly current: Shot = { ...SHOTS.battle };

  /**
   * Moves toward `target`. `damp` is the fraction of the gap to close (1 is a
   * cut). Returns whether anything changed.
   */
  step(target: Shot, damp: number): boolean {
    let changed = false;
    for (const key of Object.keys(target) as (keyof Shot)[]) {
      const gap = target[key] - this.current[key];
      if (gap === 0) {
        continue;
      }
      const next =
        damp >= 1 || Math.abs(gap) < SETTLE
          ? target[key]
          : this.current[key] + gap * damp;
      if (next !== this.current[key]) {
        this.current[key] = next;
        changed = true;
      }
    }
    return changed;
  }
}

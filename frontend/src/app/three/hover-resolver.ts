import type { ScreenRect } from './figures/figure-pick';
import type { VillageSubject } from './village/village-subjects';

/**
 * Steady hover (#126). What the pointer is over is decided once per pointer move (and once per
 * frame in the Tower, where things move) from the pointer position alone, to one subject, by an
 * explicit priority, and it is held until the pointer has left it by a small margin (and a smaller
 * building standing in its region takes over only once the pointer is well inside it). It never
 * reads the order in which the scene's volumes fire pointer events, so moving one pixel inside a
 * building cannot change the result.
 *
 * Pure: regions in, one region out. No three.js, no DOM.
 */
export interface HoverRegion {
  subject: VillageSubject;
  /**
   * A thing the pointer is pointed straight at (the bell, the lamp, the post, a crate): it wins over
   * the building whose region it sits in. Its rects are its own parts; a building's single rect is
   * the box around every part, near-miss pads included.
   */
  direct: boolean;
  /** CSS px from the canvas's top left. */
  rects: readonly ScreenRect[];
}

/** CSS px the pointer may stray outside the current subject's region before it lets go. */
export const HOVER_HOLD_PX = 4;
/** CSS px added to a directly hovered thing: a bell is a few pixels across. */
export const HOVER_DIRECT_SLOP_PX = 1;

export function sameRegionSubject(
  a: VillageSubject | null | undefined,
  b: VillageSubject | null | undefined,
): boolean {
  return !!a && !!b && a.kind === b.kind && a.id === b.id;
}

function within(r: ScreenRect, x: number, y: number, grow: number): boolean {
  return (
    x >= r.left - grow &&
    x <= r.right + grow &&
    y >= r.top - grow &&
    y <= r.bottom + grow
  );
}

function reach(region: HoverRegion, x: number, y: number, grow: number): boolean {
  return region.rects.some((r) => within(r, x, y, grow));
}

/** Whether the point is inside one of the region's rects by more than `margin` (less, for a rect too small for it). */
function deep(region: HoverRegion, x: number, y: number, margin: number): boolean {
  return region.rects.some((r) => {
    const g = Math.min(margin, (r.right - r.left) / 2, (r.bottom - r.top) / 2);
    return x >= r.left + g && x <= r.right - g && y >= r.top + g && y <= r.bottom - g;
  });
}

/** How far the point is from the middle of the nearest of the region's rects: the tie-break. */
function distance(region: HoverRegion, x: number, y: number): number {
  let best = Infinity;
  for (const r of region.rects) {
    best = Math.min(best, Math.hypot(x - (r.left + r.right) / 2, y - (r.top + r.bottom) / 2));
  }
  return best;
}

function area(region: HoverRegion): number {
  return Math.min(...region.rects.map((r) => (r.right - r.left) * (r.bottom - r.top)));
}

/** The smallest region first (the thing nested in another), then the nearest middle. */
function pick(regions: HoverRegion[], x: number, y: number): HoverRegion {
  let best = regions[0];
  for (const r of regions) {
    const da = area(r) - area(best);
    if (da < -0.5 || (Math.abs(da) <= 0.5 && distance(r, x, y) < distance(best, x, y))) {
      best = r;
    }
  }
  return best;
}

/**
 * The one region under the point, or null. Priority: (1) a direct thing the pointer is over;
 * (2) the current subject, while it is a direct thing and the pointer is within its region plus
 * the hold margin; (3) the smallest region whose bounds hold the pointer, the current one first
 * if it is among them, unless the smaller one is entered by more than the hold margin (a building
 * that stands in another's region, the Ring in the Gate, is reached from it, and is held in turn
 * as the pointer leaves it); (4) the current region, while within the hold margin.
 */
export function resolveHover(
  regions: readonly HoverRegion[],
  x: number,
  y: number,
  current: VillageSubject | null,
  hold: number = HOVER_HOLD_PX,
): HoverRegion | null {
  const now = current ? regions.find((r) => sameRegionSubject(r.subject, current)) : undefined;
  const directs = regions.filter((r) => r.direct && reach(r, x, y, HOVER_DIRECT_SLOP_PX));
  if (directs.length) {
    return now && directs.includes(now) ? now : pick(directs, x, y);
  }
  if (now?.direct && reach(now, x, y, hold)) return now;
  const inside = regions.filter((r) => !r.direct && reach(r, x, y, 0));
  if (inside.length) {
    const best = pick(inside, x, y);
    if (now && inside.includes(now)) {
      return best === now || !deep(best, x, y, hold) ? now : best;
    }
    // Just out of a smaller region, into the larger one around it: the smaller is held a moment.
    if (now && !now.direct && area(now) < area(best) && reach(now, x, y, hold)) return now;
    return best;
  }
  if (now && !now.direct && reach(now, x, y, hold)) return now;
  return null;
}

import * as THREE from 'three';

import { FigureInstance } from './figure-layout';

/** A rectangle in CSS px from the top left of the canvas. */
export interface ScreenRect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

/** Which kind of figure a unit view is on, and its id in the game state. */
export interface UnitFocus {
  kind: 'unit' | 'enemy';
  id: number;
}

/** The id the figure plan gives a member's figure. */
export function figureId(focus: UnitFocus): string {
  return `${focus.kind}-${focus.id}`;
}

/**
 * The member a figure id names, or null for a Wave column, a numeral or anything else. A member's
 * id is an int from the server and may be negative ("enemy--1549096401"): it is a hash, not a count.
 */
export function parseFigureId(id: string): UnitFocus | null {
  const match = /^(unit|enemy)-(-?\d+)$/.exec(id);
  return match
    ? { kind: match[1] as UnitFocus['kind'], id: Number(match[2]) }
    : null;
}

const centre = new THREE.Vector3();
const corner = new THREE.Vector3();

/**
 * Where a figure's quad lands on the canvas. The figure shader keeps the quad
 * facing the camera, so its half sizes are offsets in view space, not world.
 * The camera's matrices must be current (they are, one frame into the scene).
 */
export function figureRect(
  camera: THREE.Camera,
  figure: Pick<FigureInstance, 'x' | 'y' | 'z' | 'w' | 'h'>,
  canvasWidth: number,
  canvasHeight: number,
): ScreenRect {
  camera.updateMatrixWorld();
  centre
    .set(figure.x, figure.y, figure.z)
    .applyMatrix4(camera.matrixWorldInverse);
  const toScreen = (dx: number, dy: number) => {
    corner.copy(centre);
    corner.x += dx;
    corner.y += dy;
    corner.applyMatrix4(camera.projectionMatrix);
    return {
      x: ((corner.x + 1) / 2) * canvasWidth,
      y: ((1 - corner.y) / 2) * canvasHeight,
    };
  };
  const a = toScreen(-figure.w / 2, figure.h / 2);
  const b = toScreen(figure.w / 2, -figure.h / 2);
  return {
    left: Math.min(a.x, b.x),
    top: Math.min(a.y, b.y),
    right: Math.max(a.x, b.x),
    bottom: Math.max(a.y, b.y),
  };
}

/**
 * A figure is a few screen pixels across in the battle view (8 by 11 at 1440 px), and the
 * canvas draws on a coarser grid than the screen, so a hit area of exactly the drawn bounds
 * misses most honest clicks. The hit area is the drawn rectangle grown to at least this
 * size on each side (CSS px), then by the margin.
 */
export const PICK_MIN_SIZE = 22;
/** CSS px added on every side of the drawn bounds (the caller adds the pixel scale, the grid's snap). */
export const PICK_MARGIN = 4;

export interface PickOptions {
  /** Added to every side of the (minimum-sized) rectangle, CSS px. */
  margin?: number;
  /** The hit area is never smaller than this, CSS px. */
  minSize?: number;
  /**
   * How far past its projected bounds a figure is drawn, CSS px: the canvas snaps it to its
   * own coarser grid. Defaults to the margin beyond `PICK_MARGIN`.
   */
  snap?: number;
}

/** The area that counts as a hit on a figure drawn in `rect`. */
export function hitRect(
  rect: ScreenRect,
  { margin = PICK_MARGIN, minSize = PICK_MIN_SIZE }: PickOptions = {},
): ScreenRect {
  const cx = (rect.left + rect.right) / 2;
  const cy = (rect.top + rect.bottom) / 2;
  const halfW = Math.max(rect.right - rect.left, minSize) / 2 + margin;
  const halfH = Math.max(rect.bottom - rect.top, minSize) / 2 + margin;
  return {
    left: cx - halfW,
    top: cy - halfH,
    right: cx + halfW,
    bottom: cy + halfH,
  };
}

/** Whether a point is inside a rectangle grown by `grow` on every side. */
function within(r: ScreenRect, x: number, y: number, grow = 0): boolean {
  return (
    x >= r.left - grow &&
    x <= r.right + grow &&
    y >= r.top - grow &&
    y <= r.bottom + grow
  );
}

/**
 * The member figure under a click at (`x`, `y`) on the canvas. Only the sprites are
 * given: specks, Wave columns and numerals are not clickable, so a click on them is a
 * click on empty space.
 *
 * What the pointer is over is what is drawn there, so a figure whose drawn bounds hold the
 * point comes first, and where several do the front-most wins (it is drawn over the others:
 * a front rank stands over the rank behind it, and the figures are far wider than the gaps
 * between ranks). Failing that, the point is in some figures' grown hit areas (the minimum
 * size and the margin, for the figures too small to aim at) and the nearest wins, by distance
 * from the point to the middle of the drawn bounds, then the front-most.
 */
export function pickFigure(
  camera: THREE.Camera,
  sprites: readonly FigureInstance[],
  x: number,
  y: number,
  canvasWidth: number,
  canvasHeight: number,
  options: PickOptions = {},
): FigureInstance | null {
  const snap =
    options.snap ?? Math.max(0, (options.margin ?? PICK_MARGIN) - PICK_MARGIN);
  let best: FigureInstance | null = null;
  let bestOnIt = false;
  let bestDistance = Infinity;
  for (const figure of sprites) {
    if (!parseFigureId(figure.id)) {
      continue;
    }
    const drawn = figureRect(camera, figure, canvasWidth, canvasHeight);
    const onIt = within(drawn, x, y, snap);
    if (!onIt && !within(hitRect(drawn, options), x, y)) {
      continue;
    }
    const distance = Math.hypot(
      x - (drawn.left + drawn.right) / 2,
      y - (drawn.top + drawn.bottom) / 2,
    );
    const better = !best
      ? true
      : onIt !== bestOnIt
        ? onIt
        : onIt
          ? figure.z > best.z + 1e-6 ||
            (Math.abs(figure.z - best.z) <= 1e-6 && distance < bestDistance)
          : distance < bestDistance - 0.5 ||
            (distance <= bestDistance + 0.5 && figure.z > best.z);
    if (better) {
      best = figure;
      bestOnIt = onIt;
      bestDistance = distance;
    }
  }
  return best;
}

/**
 * The one hit-test of the scene: the sprite a click opens and the sprite a hover
 * highlights, so the two can never disagree. The canvas snaps a figure to its own
 * coarser grid, so the hit area grows by one virtual pixel (`pixelScale`) more.
 */
export function pickSprite(
  camera: THREE.Camera,
  sprites: readonly FigureInstance[],
  x: number,
  y: number,
  canvasWidth: number,
  canvasHeight: number,
  pixelScale: number,
): FigureInstance | null {
  return pickFigure(camera, sprites, x, y, canvasWidth, canvasHeight, {
    margin: PICK_MARGIN + pixelScale,
  });
}

/** CSS px the pointer may stray past a hovered sprite's hit area before the hover lets go. */
export const SPRITE_HOLD_PX = 4;

/**
 * The sprite a hover is on (#126): the one `pickSprite` finds, except that the sprite already
 * hovered (`holdId`) keeps it while the pointer is inside its hit area, even where a neighbour's
 * overlaps and is nearer, and until the pointer has left that area by the hold margin. Moving one
 * pixel never changes it; entering a sprite's area when none is held takes it at once.
 */
export function pickSpriteSteady(
  camera: THREE.Camera,
  sprites: readonly FigureInstance[],
  x: number,
  y: number,
  canvasWidth: number,
  canvasHeight: number,
  pixelScale: number,
  holdId: string | null,
): FigureInstance | null {
  const hit = pickSprite(
    camera,
    sprites,
    x,
    y,
    canvasWidth,
    canvasHeight,
    pixelScale,
  );
  const held = holdId ? sprites.find((f) => f.id === holdId) : undefined;
  if (!held || !parseFigureId(held.id) || hit?.id === held.id) {
    return hit;
  }
  const area = hitRect(figureRect(camera, held, canvasWidth, canvasHeight), {
    margin: PICK_MARGIN + pixelScale,
  });
  const inside = (grow: number) =>
    x >= area.left - grow &&
    x <= area.right + grow &&
    y >= area.top - grow &&
    y <= area.bottom + grow;
  // A figure drawn right under the pointer is what the pointer is over, held or not: a front figure
  // is not given up to the one behind it only because the pointer came from there (#128).
  const drawnAt = (f: FigureInstance) =>
    within(figureRect(camera, f, canvasWidth, canvasHeight), x, y, pixelScale);
  if (hit && drawnAt(hit) && !drawnAt(held)) {
    return hit;
  }
  // Inside its own area it stays; just outside it stays only while nothing else is under the pointer.
  return inside(0) || (!hit && inside(SPRITE_HOLD_PX)) ? held : hit;
}

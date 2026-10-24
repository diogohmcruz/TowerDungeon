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

/** The member a figure id names, or null for a Wave column, a numeral or anything else. */
export function parseFigureId(id: string): UnitFocus | null {
  const match = /^(unit|enemy)-(\d+)$/.exec(id);
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

/**
 * The member figure under a click at (`x`, `y`) on the canvas. Only the sprites are
 * given: specks, Wave columns and numerals are not clickable, so a click on them is a
 * click on empty space. Where hit areas overlap the nearest figure wins (by distance
 * from the click to the middle of its drawn bounds), then the front-most.
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
  let best: FigureInstance | null = null;
  let bestDistance = Infinity;
  for (const figure of sprites) {
    if (!parseFigureId(figure.id)) {
      continue;
    }
    const drawn = figureRect(camera, figure, canvasWidth, canvasHeight);
    const r = hitRect(drawn, options);
    if (x < r.left || x > r.right || y < r.top || y > r.bottom) {
      continue;
    }
    const distance = Math.hypot(
      x - (drawn.left + drawn.right) / 2,
      y - (drawn.top + drawn.bottom) / 2,
    );
    if (
      !best ||
      distance < bestDistance - 0.5 ||
      (distance <= bestDistance + 0.5 && figure.z > best.z)
    ) {
      best = figure;
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

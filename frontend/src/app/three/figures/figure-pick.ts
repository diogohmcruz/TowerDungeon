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
 * The member figure under a click at (`x`, `y`) on the canvas, front-most
 * first. Only the sprites are given: specks, Wave columns and numerals are
 * not clickable, so a click on them is a click on empty space.
 */
export function pickFigure(
  camera: THREE.Camera,
  sprites: readonly FigureInstance[],
  x: number,
  y: number,
  canvasWidth: number,
  canvasHeight: number,
): FigureInstance | null {
  let best: FigureInstance | null = null;
  for (const figure of sprites) {
    if (!parseFigureId(figure.id)) {
      continue;
    }
    const r = figureRect(camera, figure, canvasWidth, canvasHeight);
    if (x < r.left || x > r.right || y < r.top || y > r.bottom) {
      continue;
    }
    if (!best || figure.z > best.z) {
      best = figure;
    }
  }
  return best;
}

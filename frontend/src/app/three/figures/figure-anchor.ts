import * as THREE from 'three';

import { LAYER_Z, STRIP_W } from '../floor-strip/floor-strip-plan';

/**
 * Where figures stand, for any camera (#136).
 *
 * The deck is not a plane the figures walk on: it is the floor line drawn on the lip strip, a
 * vertical plane at LAYER_Z[2]. The figures are billboards 1.5 units in front of it, so a figure
 * is drawn on the line only while its feet lie on the camera's ray through the line. How far
 * above the surface that is depends on the camera: about 0.7 from the battle Shot, which looks
 * down, but about 0.25 from the closeup, which looks level. A fixed lift (the old
 * SURFACE_LIFT, tuned for the battle Shot) left the figures floating in the closeup and the unit
 * view. The lift is therefore taken from the camera as it is now.
 */

/** The depth of the lip strip, whose floor line is the deck. */
export const DECK_Z = LAYER_Z[2];

/** The floor line sits on row 47 of 50 in a strip cell: 2.5 cell pixels above the strip's bottom edge, the surface. */
export const DECK_LINE_RISE = (2.5 * STRIP_W) / 128;

/** What the anchor needs of a camera: where it is, and where its up vector points, in the (y, z) plane. */
export interface DeckView {
  y: number;
  z: number;
  upY: number;
  upZ: number;
}

/** The camera never turns about the vertical, nor rolls: its pose is its height, depth and pitch. */
export function deckViewOf(camera: THREE.Camera): DeckView {
  camera.updateMatrixWorld();
  const e = camera.matrixWorld.elements;
  return { y: e[13], z: e[14], upY: e[5], upZ: e[6] };
}

/** Whether two views differ by more than a hundredth of a virtual pixel's worth of world. */
export function sameDeckView(a: DeckView, b: DeckView): boolean {
  const eps = 1e-5;
  return (
    Math.abs(a.y - b.y) < eps &&
    Math.abs(a.z - b.z) < eps &&
    Math.abs(a.upY - b.upY) < eps &&
    Math.abs(a.upZ - b.upZ) < eps
  );
}

/**
 * World Y of the point at depth `z` that the camera sees where it sees the deck line of the
 * Floor whose surface is at `surfaceY`: the height of the feet there.
 */
export function standFeetY(
  view: DeckView,
  surfaceY: number,
  z: number,
): number {
  const lineY = surfaceY + DECK_LINE_RISE;
  const run = DECK_Z - view.z;
  if (Math.abs(run) < 1e-6) {
    return lineY;
  }
  return view.y + ((lineY - view.y) * (z - view.z)) / run;
}

/**
 * World Y of the centre of a figure `h` high whose feet are on the deck line. The figure is a
 * quad facing the camera, so its feet are `h / 2` below its centre along the camera's up vector,
 * which tilts it toward the camera by the pitch, not straight down.
 */
export function standCentreY(
  view: DeckView,
  surfaceY: number,
  z: number,
  h: number,
): number {
  // The feet are at (centre - h/2 * up): their depth moves with the pitch, and so does the ray.
  const half = h / 2;
  const feetZ = z - half * view.upZ;
  return standFeetY(view, surfaceY, feetZ) + half * view.upY;
}

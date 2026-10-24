import * as THREE from 'three';

/**
 * The full-screen stage (architecture doc 5.5). The scene's canvas fills the
 * viewport, nothing lies over it but the popovers and the list view, and the
 * Tower stays where the #75 panel put it: on the right.
 */

/** Below this viewport width the narrow layout takes over: the bell has no clear lane, a bar carries the launch controls. */
export const NARROW_BELOW = 1100;

/** A popover's glass is this opaque over the scene (the building popover's CSS says the same; a test holds the contrast at 7:1). */
export const PANEL_OPACITY = 0.92;

/** The #75 Tower panel: 46vw, never under 440 px, of which the enemy list took the left. */
const TOWER_SHARE = 0.46;
const TOWER_MIN = 440;
export const ENEMIES_WIDTH = 230;
export const ENEMIES_GAP = 12;

/** The region of the canvas (CSS px from its left edge) the Tower and the Village are framed in. */
export function towerRegion(
  canvasWidth: number,
  narrow: boolean,
  spread = 0,
): { left: number; width: number } {
  if (narrow) {
    return { left: 0, width: canvasWidth };
  }
  const panel = Math.max(canvasWidth * TOWER_SHARE, TOWER_MIN);
  const width = Math.max(1, Math.min(canvasWidth, panel - ENEMIES_WIDTH - ENEMIES_GAP));
  const left = canvasWidth - width;
  if (spread <= 0) {
    return { left, width };
  }
  // A zoomed Shot widens the region toward the whole screen: 1 centres the Tower on it.
  const t = Math.min(1, spread);
  return { left: left * (1 - t), width: width + (canvasWidth - width) * t };
}

/**
 * Frames the camera for the Tower region while the canvas covers the whole
 * screen: the projection is that of a camera looking at the region alone, then
 * widened with a view offset, so what the player saw in the old panel stays
 * where it was and the rest of the screen shows more of the world.
 * Idempotent: call it every frame; it says whether it changed the camera.
 */
export function applyStageView(
  camera: THREE.PerspectiveCamera,
  canvasWidth: number,
  canvasHeight: number,
  narrow: boolean,
  spread = 0,
): boolean {
  if (!(canvasWidth > 0) || !(canvasHeight > 0)) {
    return false;
  }
  const region = towerRegion(canvasWidth, narrow, spread);
  const aspect = region.width / canvasHeight;
  const view = camera.view;
  if (
    view?.enabled &&
    camera.aspect === aspect &&
    view.fullWidth === region.width &&
    view.fullHeight === canvasHeight &&
    view.offsetX === -region.left &&
    view.width === canvasWidth &&
    view.height === canvasHeight
  ) {
    return false;
  }
  camera.aspect = aspect;
  camera.setViewOffset(
    region.width,
    canvasHeight,
    -region.left,
    0,
    canvasWidth,
    canvasHeight,
  );
  return true;
}

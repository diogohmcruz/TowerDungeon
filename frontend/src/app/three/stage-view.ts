import * as THREE from 'three';

/**
 * The full-screen stage (architecture doc 5.5). The scene's canvas fills the
 * viewport, the menu column scrolls over its left, and the Tower stays where
 * the #75 panel put it: on the right.
 */

/** Width of the management column, and the margin left of it. */
export const MENU_WIDTH = 440;
export const MENU_MARGIN = 24;
/** Below this viewport width the menus take the full width over a dimmed backdrop. */
export const NARROW_BELOW = 1100;

/** Panels in the column are this opaque, over the dither scrim. */
export const PANEL_OPACITY = 0.88;
/** How much of the scene the scrim's flat tone takes away; the dither adds to it. */
export const SCRIM_TONE = 0.7;
/** The dither's own strength: this opaque on half the cells (a 50% ordered dither). */
export const SCRIM_DITHER = 0.6;
/** The narrow layout dims the whole backdrop, flatly, and the column paints no ground of its own. */
export const NARROW_DIM = 0.97;
/** The scrim fades out over this width beyond the column, in a sparser dither. */
export const SCRIM_FADE = 64;

/** The #75 Tower panel: 46vw, never under 440 px, of which the enemy list took the left. */
const TOWER_SHARE = 0.46;
const TOWER_MIN = 440;
export const ENEMIES_WIDTH = 230;
export const ENEMIES_GAP = 12;

/** The region of the canvas (CSS px from its left edge) the Tower and the Village are framed in. */
export function towerRegion(
  canvasWidth: number,
  narrow: boolean,
): { left: number; width: number } {
  if (narrow) {
    return { left: 0, width: canvasWidth };
  }
  const panel = Math.max(canvasWidth * TOWER_SHARE, TOWER_MIN);
  const width = Math.max(1, Math.min(canvasWidth, panel - ENEMIES_WIDTH - ENEMIES_GAP));
  return { left: canvasWidth - width, width };
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
): boolean {
  if (!(canvasWidth > 0) || !(canvasHeight > 0)) {
    return false;
  }
  const region = towerRegion(canvasWidth, narrow);
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

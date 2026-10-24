import * as THREE from 'three';

/**
 * The full-screen stage (architecture doc 5.5). The scene's canvas fills the
 * viewport, the card layer lies over it, and the Tower stays where
 * the #75 panel put it: on the right.
 */

/** The card layer's margin from the left edge, the gap between cards, and the top bar it clears. */
export const CARD_MARGIN = 24;
export const CARD_GAP = 12;
export const TOP_BAR = 52;
/** The narrowest a card is drawn, so the grid fits as many columns as the width allows. */
export const CARD_MIN_WIDTH = 300;
/** Below this viewport width the menus take the full width over a dimmed backdrop. */
export const NARROW_BELOW = 1100;

/** Cards are this opaque, over their own dither scrim. */
export const PANEL_OPACITY = 0.88;
/** How much of the scene the scrim's flat tone takes away; the dither adds to it. */
export const SCRIM_TONE = 0.7;
/** The dither's own strength: this opaque on half the cells (a 50% ordered dither). */
export const SCRIM_DITHER = 0.6;
/** The narrow layout dims the whole backdrop, flatly, and the cards sit on it with no scrim of their own. */
export const NARROW_DIM = 0.97;

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

/** The cards stop this fraction of the Tower region in from its left edge; the rest is the Tower's lane. */
const LANE_INSET = 0.1;

/**
 * How far right (CSS px from the left edge) the cards may reach. Past it lies the
 * Tower's lane, kept clear so no card hides the bell or the recall lamp. Narrow
 * screens have no lane: the cards take the full width and the bar below carries the controls.
 */
export function cardLayerWidth(canvasWidth: number, narrow: boolean): number {
  if (narrow) {
    return canvasWidth;
  }
  const region = towerRegion(canvasWidth, false);
  return Math.round(region.left + region.width * LANE_INSET);
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

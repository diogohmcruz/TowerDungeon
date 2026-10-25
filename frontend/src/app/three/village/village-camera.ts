import * as THREE from 'three';

import { FRAME_WIDTH, type Volume } from './village-layout';

/** Vertical field of view of the Village vantage. */
export const VILLAGE_FOV = 40;
/** The camera looks up this far, so most of the frame is the Tower into dither. */
export const VILLAGE_PITCH_DEG = 15;
/** Where the Village stands in depth. */
const VILLAGE_Z = 1;

/**
 * The Village closeup (#130), the far view's own rung of the zoom ladder. The far view keeps its
 * low, upward look at a fixed 11 units; the closeup takes the whole screen, frames the built
 * Village by its own extent, and looks down on it, so a building nearer the camera stands lower in
 * the picture instead of in front of the one behind it.
 */
export const CLOSEUP_PITCH_DEG = -24;
/** The height the closeup looks at: about the middle of the tallest building. */
export const CLOSEUP_LOOK_Y = 0.5;
/** Empty ground kept on each side of the built Village in the closeup, in world units. */
export const CLOSEUP_MARGIN = 0.3;
/**
 * How much wider a volume shows per world unit it stands nearer the camera than the Village's depth,
 * in the closeup (about 1 / the camera's distance, 6 units for the usual screen).
 */
export const CLOSEUP_NEAR_GAIN = 0.12;
/** The closeup never shows fewer than this many units of height, so a wide screen keeps the roofs. */
export const CLOSEUP_MIN_HEIGHT = 3.4;

/** What the closeup frames: the middle of the built Village and how wide it stands. */
export interface CloseupFrame {
  centerX: number;
  span: number;
}

/** The whole-frame default: the far view's own 11 units, centred. */
export const FAR_FRAME: CloseupFrame = { centerX: 0, span: FRAME_WIDTH };

/**
 * The extent of what is built, from its volumes: every Village and Port volume but the ground,
 * the outlines, the zones' ground dressing and the Shortcut marks on the Tower's flank. The frame is that extent and a margin,
 * so each Tier is framed by what it has (the Port's sea, the Strongbox and Reliquary, the Wall and
 * the Stone at Tier IV), not by a fixed rectangle.
 */
export function closeupFrame(volumes: readonly Volume[]): CloseupFrame {
  const built = volumes.filter(
    (v) =>
      (v.group === 'village' || v.group === 'port') &&
      v.id !== 'ground' &&
      !v.id.startsWith('outline-') &&
      !v.id.startsWith('shortcut-') &&
      // The zones' ground dressing (#151) is ground, not a building: it never widens the frame.
      !v.id.startsWith('zone-'),
  );
  const extent = (reach: (v: Volume) => [number, number]): [number, number] => {
    let min = Infinity;
    let max = -Infinity;
    for (const v of built) {
      const [a, b] = reach(v);
      min = Math.min(min, a);
      max = Math.max(max, b);
    }
    return [min, max];
  };
  const [flatMin, flatMax] = extent((v) => [v.x - v.w / 2, v.x + v.w / 2]);
  if (!(flatMax > flatMin)) return FAR_FRAME;
  // A volume nearer the camera than the Village's depth stands wider on the screen, so it is
  // counted at the width it shows: the nearer, the further from the middle (#135).
  const middle = (flatMin + flatMax) / 2;
  const [minX, maxX] = extent((v) => {
    const k = 1 + CLOSEUP_NEAR_GAIN * Math.max(0, v.z + v.d / 2 - VILLAGE_Z);
    return [middle + (v.x - v.w / 2 - middle) * k, middle + (v.x + v.w / 2 - middle) * k];
  });
  return {
    centerX: (minX + maxX) / 2,
    span: maxX - minX + 2 * CLOSEUP_MARGIN,
  };
}

/**
 * The pose of the Village vantage for a canvas aspect (width / height). `closeness` is 0 at the
 * far view and 1 at the closeup; between, every scalar is a straight blend, so an eased approach
 * never passes either end.
 */
export function villagePose(
  aspect: number,
  closeness = 0,
  frame: CloseupFrame = FAR_FRAME,
): {
  position: [number, number, number];
  target: [number, number, number];
  distance: number;
} {
  const t = Math.min(1, Math.max(0, closeness));
  const tanV = Math.tan(THREE.MathUtils.degToRad(VILLAGE_FOV / 2));
  const a = Math.max(0.2, aspect);
  // The frame shows FRAME_WIDTH units across at the Village's depth in the far view; the closeup
  // shows the built Village's span, and never less height than the roofs need.
  const closeSpan = Math.max(frame.span, CLOSEUP_MIN_HEIGHT * a);
  const span = FRAME_WIDTH + (closeSpan - FRAME_WIDTH) * t;
  const distance = span / 2 / (tanV * a);
  const centerX = frame.centerX * t;
  // Far: low enough that the ground sits just above the bottom edge, looking up. Close: looking
  // down on the middle of the buildings.
  const farY = distance * 0.045;
  const farPitch = THREE.MathUtils.degToRad(VILLAGE_PITCH_DEG);
  const farLook = farY + distance * Math.tan(farPitch);
  const closePitch = THREE.MathUtils.degToRad(CLOSEUP_PITCH_DEG);
  const pitch = farPitch + (closePitch - farPitch) * t;
  const lookY = farLook + (CLOSEUP_LOOK_Y - farLook) * t;
  const y = lookY - distance * Math.tan(pitch);
  return {
    position: [centerX, y, VILLAGE_Z + distance],
    target: [centerX, lookY, VILLAGE_Z],
    distance,
  };
}

/**
 * How thick a focus line (#133) is, as a share of its far-view thickness, for a canvas aspect and a
 * blend: the closeup shows fewer world units across, so a world unit is more pixels, and the same
 * line would be heavier. Scaled by this it stays one or two virtual pixels in both views.
 */
export function focusLineScale(
  aspect: number,
  closeness = 0,
  frame: CloseupFrame = FAR_FRAME,
): number {
  const a = Math.max(0.2, aspect);
  const t = Math.min(1, Math.max(0, closeness));
  const closeSpan = Math.max(frame.span, CLOSEUP_MIN_HEIGHT * a);
  return (FRAME_WIDTH + (closeSpan - FRAME_WIDTH) * t) / FRAME_WIDTH;
}

/** Places a camera at the Village vantage (called when the aspect or the blend changes). */
export function fitVillageCamera(
  camera: THREE.PerspectiveCamera,
  closeness = 0,
  frame: CloseupFrame = FAR_FRAME,
): void {
  const pose = villagePose(camera.aspect, closeness, frame);
  camera.fov = VILLAGE_FOV;
  camera.near = 0.5;
  camera.far = pose.distance + 120;
  camera.position.set(...pose.position);
  camera.lookAt(...pose.target);
  camera.updateProjectionMatrix();
}

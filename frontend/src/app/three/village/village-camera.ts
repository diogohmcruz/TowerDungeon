import * as THREE from 'three';

import { FRAME_WIDTH } from './village-layout';

/** Vertical field of view of the Village vantage. */
export const VILLAGE_FOV = 40;
/** The camera looks up this far, so most of the frame is the Tower into dither. */
export const VILLAGE_PITCH_DEG = 15;
/** Where the Village stands in depth. */
const VILLAGE_Z = 1;

/** The pose of the fixed Village vantage for a canvas aspect (width / height). */
export function villagePose(aspect: number): {
  position: [number, number, number];
  target: [number, number, number];
  distance: number;
} {
  const halfV = THREE.MathUtils.degToRad(VILLAGE_FOV / 2);
  // The frame shows FRAME_WIDTH units across at the Village's depth.
  const distance = FRAME_WIDTH / 2 / (Math.tan(halfV) * Math.max(0.2, aspect));
  // Low enough that the ground sits just above the bottom edge.
  const y = distance * 0.045;
  const lookY =
    y + distance * Math.tan(THREE.MathUtils.degToRad(VILLAGE_PITCH_DEG));
  return {
    position: [0, y, VILLAGE_Z + distance],
    target: [0, lookY, VILLAGE_Z],
    distance,
  };
}

/** Places a camera at the Village vantage (called when the aspect changes). */
export function fitVillageCamera(camera: THREE.PerspectiveCamera): void {
  const pose = villagePose(camera.aspect);
  camera.fov = VILLAGE_FOV;
  camera.near = 0.5;
  camera.far = pose.distance + 80;
  camera.position.set(...pose.position);
  camera.lookAt(...pose.target);
  camera.updateProjectionMatrix();
}

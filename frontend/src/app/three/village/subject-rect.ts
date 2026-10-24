import * as THREE from 'three';

import type { ScreenRect } from '../figures/figure-pick';
import type { Volume } from './village-layout';
import { ofSubject, type VillageSubject } from './village-subjects';

const corner = new THREE.Vector3();

/**
 * Where a subject's volumes land on the canvas, in CSS px from its top left:
 * the box around the projection of every corner of every part. The popover
 * sits beside this and follows it. Null when the subject has no volumes, or
 * every one is behind the camera.
 */
export function subjectRect(
  camera: THREE.Camera,
  volumes: readonly Volume[],
  subject: VillageSubject,
  canvasWidth: number,
  canvasHeight: number,
): ScreenRect | null {
  const parts = ofSubject(volumes as Volume[], subject.id, subject.kind);
  if (parts.length === 0 || !(canvasWidth > 0) || !(canvasHeight > 0)) {
    return null;
  }
  camera.updateMatrixWorld();
  let left = Infinity;
  let top = Infinity;
  let right = -Infinity;
  let bottom = -Infinity;
  for (const v of parts) {
    for (const sx of [-0.5, 0.5]) {
      for (const sy of [-0.5, 0.5]) {
        for (const sz of [-0.5, 0.5]) {
          corner.set(v.x + sx * v.w, v.y + sy * v.h, v.z + sz * v.d);
          corner.project(camera);
          if (corner.z > 1) continue;
          const x = ((corner.x + 1) / 2) * canvasWidth;
          const y = ((1 - corner.y) / 2) * canvasHeight;
          left = Math.min(left, x);
          right = Math.max(right, x);
          top = Math.min(top, y);
          bottom = Math.max(bottom, y);
        }
      }
    }
  }
  return Number.isFinite(left) ? { left, top, right, bottom } : null;
}

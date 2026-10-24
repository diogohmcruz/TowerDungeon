import * as THREE from 'three';

import type { ScreenRect } from '../figures/figure-pick';
import type { HoverRegion } from '../hover-resolver';
import { volumeRect } from './subject-rect';
import { UPGRADE_MODULES, type Volume } from './village-layout';
import { subjectOfVolume, type SubjectKind } from './village-subjects';

/**
 * Kinds the pointer is pointed straight at, volume by volume: the launch objects (bell, lamp,
 * Base door), a Shortcut's mark and the Expedition objects (the post and the crates). The rest
 * (buildings, Annexes, the Villagers' lane) answer to the box around all their parts.
 */
const DIRECT_KINDS: readonly SubjectKind[] = [
  'bell',
  'recall',
  'base-door',
  'shortcut',
  'expedition',
];

/**
 * An Upgrade building that stands on open ground in front of others (`inFront`: its module, its
 * footing and its pad) is pointed at part by part like the bell: in the far view it projects over
 * the boxes of the buildings behind it, which would otherwise take the pointer from it (#134).
 */
const UPGRADE_VOLUME = /^(?:grow:|upgrade-footing:|upgrade-hit:)([A-Z_]+)/;
const standsInFront = (volumeId: string): boolean => {
  const id = UPGRADE_VOLUME.exec(volumeId)?.[1];
  return !!id && !!UPGRADE_MODULES[id]?.inFront;
};

/**
 * The hover regions of a scene's volumes, on the canvas now (CSS px). One region per subject:
 * a direct thing keeps one rectangle per part, anything else is the box around every part, its
 * invisible near-miss pads included, so the region has no holes between the volumes of a building.
 * Outlines and volumes that stand for nothing (the ground, the Tower, the rim) are not regions.
 */
export function volumeRegions(
  camera: THREE.Camera,
  volumes: readonly Volume[],
  canvasWidth: number,
  canvasHeight: number,
): HoverRegion[] {
  if (!(canvasWidth > 0) || !(canvasHeight > 0)) return [];
  camera.updateMatrixWorld();
  const regions = new Map<string, { subject: HoverRegion['subject']; direct: boolean; rects: ScreenRect[] }>();
  // An Upgrade with a module standing is picked by its parts; its pad is for the footing alone.
  const standing = new Set(
    volumes.filter((v) => v.id.startsWith('grow:')).map((v) => subjectOfVolume(v.id)?.id),
  );
  for (const v of volumes) {
    if (v.id.startsWith('outline-')) continue;
    const subject = subjectOfVolume(v.id);
    if (!subject) continue;
    if (v.id.startsWith('upgrade-hit:') && standing.has(subject.id)) continue;
    const rect = volumeRect(camera, v, canvasWidth, canvasHeight);
    if (!rect) continue;
    const key = `${subject.kind}:${subject.id}`;
    const direct = DIRECT_KINDS.includes(subject.kind) || standsInFront(v.id);
    const have = regions.get(key);
    if (!have) {
      regions.set(key, { subject, direct, rects: [rect] });
    } else if (direct) {
      have.rects.push(rect);
    } else {
      const box = have.rects[0];
      box.left = Math.min(box.left, rect.left);
      box.top = Math.min(box.top, rect.top);
      box.right = Math.max(box.right, rect.right);
      box.bottom = Math.max(box.bottom, rect.bottom);
    }
  }
  return [...regions.values()];
}

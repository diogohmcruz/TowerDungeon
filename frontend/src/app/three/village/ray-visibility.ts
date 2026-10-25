import * as THREE from 'three';

import { applyStageView } from '../stage-view';
import { closeupFrame, fitVillageCamera } from './village-camera';
import { EMPTY_VILLAGE, type VillageInput, type Volume } from './village-layout';

/** A Village with every Annex fed and every Tier's buildings standing, for the ray tests (#134, #135). */
export const BASE: VillageInput = {
  ...EMPTY_VILLAGE,
  food: 400,
  reserves: 18,
  hands: { TILLER: 14, BREAKER: 8, SCRIBE: 4, SALTER: 6, SPOREWARDEN: 3 },
  posts: { SCRIBE: 6 },
  quarryHeap: 24,
  quarryHeapCap: 40,
  materials: 300,
  relics: 3,
  credits: 40,
  spores: 7,
  salt: 40,
  pillar: { highestFloor: 37, milestonesEarned: 2, milestonesTotal: 4, tollShare: 0.67 },
  fallenCount: 5,
  ringLevel: 2,
  ringMax: 5,
  shortcuts: [{ id: 'a', floor: 3 }],
  bellUsable: true,
  startFloor: 0,
  offer: true,
};

export function input(tier: number, upgrades: Record<string, number>): VillageInput {
  return {
    ...BASE,
    villageTierLevel: tier,
    upgrades,
    port: tier >= 4 ? 'BUILT' : 'HIDDEN',
    boatDocked: tier >= 4,
    sporeCellarOpen: tier >= 3,
    saltPansOpen: tier >= 2,
  };
}

export const SIZES: Array<[number, number]> = [
  [1440, 900],
  [1920, 1080],
];

function boxOf(v: Volume): THREE.Box3 {
  return new THREE.Box3(
    new THREE.Vector3(v.x - v.w / 2, v.y - v.h / 2, v.z - v.d / 2),
    new THREE.Vector3(v.x + v.w / 2, v.y + v.h / 2, v.z + v.d / 2),
  );
}

/**
 * Share of rays that reach the store but meet another opaque volume first, and the rays tried. The
 * sea and the frame (the cliff, the fog and the foliage, #149) are scenery that stands behind or
 * below every building, so they are left out; the layout spec holds the frame behind them. The
 * landmarks (#150) are far scenery too (Yuva's house behind the foliage, the fortress far back).
 */
export function covered(
  volumes: Volume[],
  isTarget: (v: Volume) => boolean,
  closeness: number,
  w: number,
  h: number,
  step = 1,
) {
  const cam = new THREE.PerspectiveCamera();
  applyStageView(cam, w, h, false, closeness);
  fitVillageCamera(cam, closeness, closeupFrame(volumes));
  cam.updateMatrixWorld();
  const store = volumes.filter(isTarget);
  const others = volumes.filter((v) => !isTarget(v) && v.alpha > 0.5 && v.id !== 'ground' && v.group !== 'sea' && v.group !== 'frame' && v.group !== 'landmark');
  const storeBoxes = store.map(boxOf);
  const otherBoxes = others.map(boxOf);
  const hit = new THREE.Vector3();
  // Only the screen rectangle around the target can reach it: sample that, a pixel at a time.
  const corner = new THREE.Vector3();
  let x0 = w;
  let x1 = 0;
  let y0 = h;
  let y1 = 0;
  for (const b of storeBoxes) {
    for (const cx of [b.min.x, b.max.x]) {
      for (const cy of [b.min.y, b.max.y]) {
        for (const cz of [b.min.z, b.max.z]) {
          corner.set(cx, cy, cz).project(cam);
          x0 = Math.min(x0, ((corner.x + 1) / 2) * w);
          x1 = Math.max(x1, ((corner.x + 1) / 2) * w);
          y0 = Math.min(y0, ((1 - corner.y) / 2) * h);
          y1 = Math.max(y1, ((1 - corner.y) / 2) * h);
        }
      }
    }
  }
  let reach = 0;
  let blocked = 0;
  const blockers = new Set<string>();
  for (let px = Math.max(0, Math.floor(x0) - 1); px < Math.min(w, x1 + 1); px += step) {
    for (let py = Math.max(0, Math.floor(y0) - 1); py < Math.min(h, y1 + 1); py += step) {
      const ray = new THREE.Raycaster();
      ray.setFromCamera(new THREE.Vector2((px / w) * 2 - 1, 1 - (py / h) * 2), cam);
      let near = Infinity;
      for (const b of storeBoxes) {
        if (ray.ray.intersectBox(b, hit)) near = Math.min(near, hit.distanceTo(ray.ray.origin));
      }
      if (!Number.isFinite(near)) continue;
      reach++;
      let best = Infinity;
      let who = '';
      otherBoxes.forEach((b, i) => {
        if (ray.ray.intersectBox(b, hit)) {
          const d = hit.distanceTo(ray.ray.origin);
          if (d < best) {
            best = d;
            who = others[i].id;
          }
        }
      });
      if (best < near - 1e-6) {
        blocked++;
        blockers.add(who);
      }
    }
  }
  return { reach, blocked, blockers: [...blockers] };
}


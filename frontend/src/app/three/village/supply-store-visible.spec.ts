import * as THREE from 'three';

import { applyStageView } from '../stage-view';
import { closeupFrame, fitVillageCamera } from './village-camera';
import { resolveHover } from '../hover-resolver';
import { volumeRegions } from './volume-regions';
import { buildVillage, EMPTY_VILLAGE, type VillageInput, type Volume } from './village-layout';

/**
 * #134: the supply store (the second silo and crates Supply Lines adds) stood behind the stair and
 * the Base slab, so the owner could not see or pick it. A ray through every sampled point of its
 * projected rectangle that reaches the store must reach it first: nothing opaque stands in front.
 */
const BASE: VillageInput = {
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

function input(tier: number, upgrades: Record<string, number>): VillageInput {
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

let prefix = '';
const isStore = (v: Volume) => v.id.startsWith(prefix);
const SIZES: Array<[number, number]> = [
  [1440, 900],
  [1920, 1080],
];

function boxOf(v: Volume): THREE.Box3 {
  return new THREE.Box3(
    new THREE.Vector3(v.x - v.w / 2, v.y - v.h / 2, v.z - v.d / 2),
    new THREE.Vector3(v.x + v.w / 2, v.y + v.h / 2, v.z + v.d / 2),
  );
}

/** Share of rays that reach the store but meet another opaque volume first, and the rays tried. */
function covered(volumes: Volume[], closeness: number, w: number, h: number) {
  const cam = new THREE.PerspectiveCamera();
  applyStageView(cam, w, h, false, closeness);
  fitVillageCamera(cam, closeness, closeupFrame(volumes));
  cam.updateMatrixWorld();
  const store = volumes.filter(isStore);
  const others = volumes.filter((v) => !isStore(v) && v.alpha > 0.5 && v.id !== 'ground');
  const storeBoxes = store.map(boxOf);
  const otherBoxes = others.map(boxOf);
  const hit = new THREE.Vector3();
  let reach = 0;
  let blocked = 0;
  const blockers = new Set<string>();
  for (let px = 0; px < w; px += 2) {
    for (let py = 0; py < h; py += 2) {
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

function expectClear(id: string, tier: number, level: number) {
  prefix = `grow:${id}:`;
  for (const [w, h] of SIZES) {
    for (const closeness of [0, 1]) {
      const r = covered(buildVillage(input(tier, { [id]: level })), closeness, w, h);
      const where = `${id} level ${level} Tier ${tier} ${closeness ? 'closeup' : 'far view'} ${w}x${h}`;
      expect(r.reach).withContext(where).toBeGreaterThan(50);
      expect(r.blockers).withContext(where).toEqual([]);
    }
  }
}

describe('the supply store stands clear (#134)', () => {
  for (const tier of [1, 2, 3, 4]) {
    for (const level of [1, 2, 3, 4]) {
      it(`Tier ${tier}, Supply Lines ${level}: nothing stands in front, in the far view and the closeup`, () => {
        expectClear('SUPPLY_LINES', tier, level);
      });
    }
  }
});

describe('a click on the supply store opens it (#134)', () => {
  for (const tier of [1, 4]) {
    for (const closeness of [0, 1]) {
      it(`Tier ${tier}, ${closeness ? 'closeup' : 'far view'}: the pointer on its silo and crates picks Supply Lines`, () => {
        const [w, h] = [1440, 900];
        const volumes = buildVillage(input(tier, { SUPPLY_LINES: 4 }));
        const cam = new THREE.PerspectiveCamera();
        applyStageView(cam, w, h, false, closeness);
        fitVillageCamera(cam, closeness, closeupFrame(volumes));
        cam.updateMatrixWorld();
        const regions = volumeRegions(cam, volumes, w, h);
        for (const part of volumes.filter((v) => /^grow:SUPPLY_LINES:\d:(silo|crate)$/.test(v.id))) {
          const p = new THREE.Vector3(part.x, part.y, part.z).project(cam);
          const hit = resolveHover(regions, ((p.x + 1) / 2) * w, ((1 - p.y) / 2) * h, null)?.subject;
          expect(hit?.id).withContext(part.id).toBe('SUPPLY_LINES');
        }
      });
    }
  }
});

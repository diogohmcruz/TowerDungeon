import * as THREE from 'three';

import { applyStageView } from '../stage-view';
import { closeupFrame, fitVillageCamera } from './village-camera';
import { resolveHover } from '../hover-resolver';
import { volumeRegions } from './volume-regions';
import { buildVillage } from './village-layout';
import { covered, input, SIZES } from './ray-visibility';

/**
 * #134: the supply store (the second silo and crates Supply Lines adds) stood behind the stair and
 * the Base slab, so the owner could not see or pick it. A ray through every sampled point of its
 * projected rectangle that reaches the store must reach it first: nothing opaque stands in front.
 */
let prefix = '';

function expectClear(id: string, tier: number, level: number) {
  prefix = `grow:${id}:`;
  for (const [w, h] of SIZES) {
    for (const closeness of [0, 1]) {
      const r = covered(
        buildVillage(input(tier, { [id]: level })),
        (v) => v.id.startsWith(prefix),
        closeness,
        w,
        h,
      );
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

import * as THREE from 'three';

import { resolveHover } from '../hover-resolver';
import { applyStageView } from '../stage-view';
import { closeupFrame, fitVillageCamera } from './village-camera';
import { buildVillage, UPGRADE_MODULES, type Volume } from './village-layout';
import { covered, input, SIZES } from './ray-visibility';
import { volumeRegions } from './volume-regions';

/**
 * #135: the watchtower, obelisk, brazier, roof and stockade stood behind the Vault, the Quarry, the
 * Fields and the Fort, hidden from the owner. For every Upgrade module, at every Village Tier, level
 * owned, view and size: of the rays that reach the module, at most `MARGIN` meet another opaque
 * volume first. The Upgrade stands alone, and again with every Upgrade bought (modules hide each other).
 */
const MARGIN = 0.05;
const TIERS = [1, 2, 3, 4];

const levelsOf = (id: string) => Array.from({ length: UPGRADE_MODULES[id].cap }, (_, i) => i + 1);
const isModule = (id: string) => (v: Volume) => v.id.startsWith(`grow:${id}:`);
const ALL_BOUGHT = Object.fromEntries(Object.entries(UPGRADE_MODULES).map(([id, m]) => [id, m.cap]));

function expectVisible(id: string, tier: number, upgrades: Record<string, number>, label: string) {
  for (const [w, h] of SIZES) {
    for (const closeness of [0, 1]) {
      const r = covered(buildVillage(input(tier, upgrades)), isModule(id), closeness, w, h);
      const where = `${id} ${label} Tier ${tier} ${closeness ? 'closeup' : 'far view'} ${w}x${h}`;
      expect(r.reach).withContext(where).toBeGreaterThan(10);
      expect(r.blocked / r.reach)
        .withContext(`${where}, blocked by ${r.blockers.join(', ')}`)
        .toBeLessThanOrEqual(MARGIN);
    }
  }
}

describe('every Upgrade module stands in sight (#135)', () => {
  for (const id of Object.keys(UPGRADE_MODULES)) {
    for (const tier of TIERS) {
      for (const level of levelsOf(id)) {
        it(`${id}, level ${level}, Tier ${tier}: nothing stands in front, in the far view and the closeup`, () => {
          expectVisible(id, tier, { [id]: level }, `level ${level}`);
        });
      }
      it(`${id}, Tier ${tier}, with every Upgrade bought: nothing stands in front`, () => {
        expectVisible(id, tier, ALL_BOUGHT, 'with all bought');
      });
    }
  }
});

describe('a pointer on an Upgrade module opens it (#135)', () => {
  for (const tier of [1, 4]) {
    for (const closeness of [0, 1]) {
      it(`Tier ${tier}, ${closeness ? 'closeup' : 'far view'}: the pointer on each part of a module picks its Upgrade`, () => {
        const [w, h] = SIZES[0];
        const volumes = buildVillage(input(tier, ALL_BOUGHT));
        const cam = new THREE.PerspectiveCamera();
        applyStageView(cam, w, h, false, closeness);
        fitVillageCamera(cam, closeness, closeupFrame(volumes));
        cam.updateMatrixWorld();
        const regions = volumeRegions(cam, volumes, w, h);
        const picks = (id: string) =>
          volumes
            .filter((v) => v.id.startsWith(`grow:${id}:`))
            .map((part) => {
              const p = new THREE.Vector3(part.x, part.y, part.z).project(cam);
              return resolveHover(regions, ((p.x + 1) / 2) * w, ((1 - p.y) / 2) * h, null)?.subject?.id;
            });
        for (const id of Object.keys(UPGRADE_MODULES)) {
          const hits = picks(id);
          // The closeup picks every part. The far view is a few dozen pixels across: a post two pixels
          // wide may sit under the bell's own rectangle, so there it is enough that the module's bulk picks.
          const ok = hits.filter((h) => h === id).length;
          if (closeness) {
            expect(ok).withContext(`${id}: ${hits.join()}`).toBe(hits.length);
          } else {
            expect(ok / hits.length).withContext(`${id}: ${hits.join()}`).toBeGreaterThanOrEqual(0.5);
          }
        }
      });
    }
  }
});

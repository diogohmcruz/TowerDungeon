import * as THREE from 'three';

import { resolveHover } from '../hover-resolver';
import { applyStageView } from '../stage-view';
import {
  closeupFrame,
  fitVillageCamera,
  villagePose,
} from './village-camera';
import { buildVillage, EMPTY_VILLAGE, type VillageInput } from './village-layout';
import { volumeRegions } from './volume-regions';

const BASE: VillageInput = {
  ...EMPTY_VILLAGE,
  food: 400,
  reserves: 18,
  wounded: 3,
  hands: { TILLER: 14, BREAKER: 8, SCRIBE: 4, SALTER: 6, SPOREWARDEN: 3 },
  posts: { SCRIBE: 6 },
  quarryHeap: 24,
  quarryHeapCap: 40,
  materials: 300,
  relics: 3,
  credits: 40,
  spores: 7,
  pillar: { highestFloor: 37, milestonesEarned: 2, milestonesTotal: 4, tollShare: 0.67 },
  upgrades: { UNLOCK_HEALER: 1, UNLOCK_TANK: 0, UNLOCK_ROGUE: 1, SUPPLY_LINES: 3, SHARPENED_ARMS: 0 },
  fallenCount: 5,
  ringLevel: 2,
  ringMax: 5,
  shortcuts: [{ id: 'a', floor: 3 }],
  bellUsable: true,
  startFloor: 0,
  tick: 2,
  flicker: 0.5,
  still: false,
};
const TIERS: Array<[string, VillageInput]> = [
  ['Tier I', { ...BASE, villageTierLevel: 1, port: 'HIDDEN', sporeCellarOpen: false, saltPansOpen: false }],
  ['Tier IV', { ...BASE, villageTierLevel: 4, port: 'BUILT', boatDocked: true, sporeCellarOpen: true, saltPansOpen: true }],
];
const W = 1440;
const H = 900;

function camera(closeness: number, input: VillageInput): THREE.PerspectiveCamera {
  const cam = new THREE.PerspectiveCamera();
  applyStageView(cam, W, H, false, closeness);
  fitVillageCamera(cam, closeness, closeupFrame(buildVillage(input)));
  cam.updateMatrixWorld();
  return cam;
}

function isBuilding(kind: string): boolean {
  return kind === 'building' || kind === 'annex';
}

/** The pixels (2 px grid) each building and annex wins under the steady hover, by subject id. */
function pickable(cam: THREE.PerspectiveCamera, input: VillageInput): Map<string, number> {
  const regions = volumeRegions(cam, buildVillage(input), W, H);
  const won = new Map<string, number>();
  for (const r of regions) {
    if (isBuilding(r.subject.kind) && !r.direct) won.set(r.subject.id, 0);
  }
  for (let x = 0; x < W; x += 2) {
    for (let y = 0; y < H; y += 2) {
      const hit = resolveHover(regions, x, y, null)?.subject;
      if (hit && isBuilding(hit.kind) && won.has(hit.id)) {
        won.set(hit.id, won.get(hit.id)! + 4);
      }
    }
  }
  return won;
}

describe('the Village closeup camera (#130)', () => {
  it('is the far pose at 0, and comes to the closeup without passing it', () => {
    expect(villagePose(0.5, 0, { centerX: 3, span: 5 })).toEqual(villagePose(0.5));
    const frame = closeupFrame(buildVillage(TIERS[1][1]));
    const near = villagePose(1.6, 1, frame);
    let last = Infinity;
    for (let t = 0; t <= 1.0001; t += 0.1) {
      const d = Math.abs(villagePose(1.6, t, frame).distance - near.distance);
      expect(d).toBeLessThanOrEqual(last + 1e-9);
      last = d;
    }
  });

  it('frames the built Village of each Tier by its extent, wider with the Port', () => {
    const one = closeupFrame(buildVillage(TIERS[0][1]));
    const four = closeupFrame(buildVillage(TIERS[1][1]));
    expect(one.span).toBeGreaterThan(4);
    expect(four.span).toBeGreaterThan(one.span);
    expect(four.centerX).toBeGreaterThan(one.centerX);
  });

  for (const [name, input] of TIERS) {
    describe(name, () => {
      it('gives every building and annex ground of its own to pick, far more than the far view', () => {
        const near = pickable(camera(1, input), input);
        const far = pickable(camera(0, input), input);
        const worstNear = Math.min(...near.values());
        const worstFar = Math.min(...far.values());
        // Each has at least a 40 x 40 px patch the steady hover gives to it alone.
        expect(worstNear)
          .withContext([...near].filter(([, v]) => v < 1600).join())
          .toBeGreaterThanOrEqual(1600);
        expect(worstNear).toBeGreaterThan(worstFar * 10);
      });

      it('keeps every building on the screen', () => {
        const regions = volumeRegions(camera(1, input), buildVillage(input), W, H).filter((r) =>
          isBuilding(r.subject.kind),
        );
        for (const r of regions) {
          expect(r.rects[0].left).withContext(r.subject.id).toBeGreaterThanOrEqual(-1);
          expect(r.rects[0].right).withContext(r.subject.id).toBeLessThanOrEqual(W + 1);
        }
      });

      it('leaves some rectangles overlapping where a building stands in another (the Gate frames the Fort), a small share of the screen', () => {
        const rs = volumeRegions(camera(1, input), buildVillage(input), W, H).filter(
          (r) => isBuilding(r.subject.kind) && !r.direct,
        );
        let area = 0;
        for (let i = 0; i < rs.length; i++) {
          for (let j = i + 1; j < rs.length; j++) {
            const a = rs[i].rects[0];
            const b = rs[j].rects[0];
            const w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
            const h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
            if (w > 0 && h > 0) area += w * h;
          }
        }
        expect(area / (W * H)).toBeLessThan(0.3);
      });
    });
  }
});

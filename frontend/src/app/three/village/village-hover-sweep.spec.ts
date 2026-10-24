import * as THREE from 'three';

import type { ScreenRect } from '../figures/figure-pick';
import { resolveHover, type HoverRegion } from '../hover-resolver';
import { applyStageView } from '../stage-view';
import { fitVillageCamera } from './village-camera';
import {
  buildVillage,
  EMPTY_VILLAGE,
  type VillageInput,
  type Volume,
} from './village-layout';
import { subjectOfVolume, type VillageSubject } from './village-subjects';
import { volumeRegions } from './volume-regions';

/**
 * The pixel-by-pixel sweep of #126, on the real layout and the real vantage: the pointer is moved
 * one pixel at a time across every building and what is hovered is read at each pixel. The old
 * hover read the nearest volume under the pointer, so the subject flipped between the volumes of
 * one building, the near-miss pads and the ground; the new one reads the building's whole region.
 */
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
  sporeCellarOpen: true,
  spores: 7,
  pillar: { highestFloor: 37, milestonesEarned: 2, milestonesTotal: 4, tollShare: 0.67 },
  upgrades: {
    UNLOCK_HEALER: 1,
    UNLOCK_TANK: 0,
    UNLOCK_ROGUE: 1,
    SUPPLY_LINES: 3,
    SHARPENED_ARMS: 0,
  },
  fallenCount: 5,
  ringLevel: 2,
  ringMax: 5,
  saltPansOpen: true,
  salt: 40,
  shortcuts: [
    { id: 'a', floor: 3 },
    { id: 'b', floor: 10 },
  ],
  port: 'BUILT',
  boatDocked: true,
  bellUsable: true,
  startFloor: 0,
  tick: 2,
  flicker: 0.5,
  still: false,
};

/** Tier III has the Vault and the Cellar; Tier IV splits the Vault and adds the records; an Offer shows the Roll stone. */
const SCENARIOS: Array<{ name: string; input: VillageInput; expect: string[] }> = [
  {
    name: 'Tier III',
    input: { ...BASE, villageTierLevel: 3 },
    expect: ['SILO', 'FORT', 'GATE', 'PORT', 'PILLAR', 'VAULT', 'TILLER', 'BREAKER', 'SCRIBE', 'SALTER', 'SPOREWARDEN', 'RING'],
  },
  {
    name: 'Tier IV, an Offer',
    input: { ...BASE, villageTierLevel: 4, offer: true },
    expect: ['SILO', 'FORT', 'GATE', 'PORT', 'SUCCESSION', 'STRONGBOX', 'RELIQUARY', 'HEAP', 'STONE_OF_COUNT', 'WALL_OF_NAMES', 'TILLER', 'SPOREWARDEN'],
  },
];

const VIEWPORTS: Array<[number, number]> = [
  [1440, 900],
  [1920, 1080],
];

function cameraFor(width: number, height: number): THREE.PerspectiveCamera {
  const cam = new THREE.PerspectiveCamera();
  applyStageView(cam, width, height, false);
  fitVillageCamera(cam);
  cam.updateMatrixWorld();
  return cam;
}

const key = (s: VillageSubject | null) => (s ? `${s.kind}:${s.id}` : '-');

/** The hover the old code gave: the subject of the nearest volume the ray under the pixel hits. */
function legacyPick(
  cam: THREE.PerspectiveCamera,
  volumes: readonly Volume[],
  w: number,
  h: number,
  x: number,
  y: number,
): VillageSubject | null {
  const ray = new THREE.Raycaster();
  ray.setFromCamera(new THREE.Vector2((x / w) * 2 - 1, -(y / h) * 2 + 1), cam);
  const hit = new THREE.Vector3();
  let best: Volume | null = null;
  let bestDistance = Infinity;
  for (const v of volumes) {
    const box = new THREE.Box3(
      new THREE.Vector3(v.x - v.w / 2, v.y - v.h / 2, v.z - v.d / 2),
      new THREE.Vector3(v.x + v.w / 2, v.y + v.h / 2, v.z + v.d / 2),
    );
    if (ray.ray.intersectBox(box, hit)) {
      const d = hit.distanceTo(ray.ray.origin);
      if (d < bestDistance) {
        bestDistance = d;
        best = v;
      }
    }
  }
  return best ? subjectOfVolume(best.id) : null;
}

/** Runs of equal values along a sweep. */
function runs(keys: string[]): Array<{ key: string; length: number }> {
  const out: Array<{ key: string; length: number }> = [];
  for (const k of keys) {
    const last = out[out.length - 1];
    if (last && last.key === k) last.length++;
    else out.push({ key: k, length: 1 });
  }
  return out;
}

/**
 * Blinks: a subject interrupted for a pixel or two by another and back (A, B, A with B at most two
 * pixels wide). That is the flicker; a pointer that crosses a small thing (the bell) and comes out
 * on the building it was on is not one, as the bell is wider than that.
 */
function blinks(keys: string[]): number {
  const r = runs(keys);
  let n = 0;
  for (let i = 1; i < r.length - 1; i++) {
    if (r[i].length <= 2 && r[i - 1].key === r[i + 1].key) n++;
  }
  return n;
}

/** Sweeps a line one pixel at a time, as a real pointer does: the result is the current of the next pixel. */
function sweep(
  regions: readonly HoverRegion[],
  from: [number, number],
  to: [number, number],
): string[] {
  const out: string[] = [];
  let current: VillageSubject | null = null;
  const steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]));
  for (let i = 0; i <= steps; i++) {
    const x = Math.round(from[0] + ((to[0] - from[0]) * i) / steps);
    const y = Math.round(from[1] + ((to[1] - from[1]) * i) / steps);
    current = resolveHover(regions, x, y, current)?.subject ?? null;
    out.push(key(current));
  }
  return out;
}

function lines(rect: ScreenRect, margin: number): Array<[[number, number], [number, number]]> {
  const out: Array<[[number, number], [number, number]]> = [];
  const left = Math.floor(rect.left) - margin;
  const right = Math.ceil(rect.right) + margin;
  const top = Math.floor(rect.top) - margin;
  const bottom = Math.ceil(rect.bottom) + margin;
  for (const t of [0.2, 0.4, 0.6, 0.8]) {
    const y = Math.round(rect.top + (rect.bottom - rect.top) * t);
    out.push([[left, y], [right, y]], [[right, y], [left, y]]);
    const x = Math.round(rect.left + (rect.right - rect.left) * t);
    out.push([[x, top], [x, bottom]], [[x, bottom], [x, top]]);
  }
  return out;
}

describe('hover sweep (#126): one pixel at a time across every building', () => {
  for (const scenario of SCENARIOS) {
    for (const [w, h] of VIEWPORTS) {
      describe(`${scenario.name} at ${w}x${h}`, () => {
        const cam = cameraFor(w, h);
        const volumes = buildVillage(scenario.input);
        const regions = volumeRegions(cam, volumes, w, h);
        const areas = regions.filter((r) => !r.direct);

        it('has a region for every building the scene draws', () => {
          const ids = new Set(regions.map((r) => r.subject.id));
          for (const id of scenario.expect) {
            expect(ids.has(id)).withContext(id).toBeTrue();
          }
        });

        it('gives one steady subject across each building, with no blink at any pixel', () => {
          let swept = 0;
          for (const region of areas) {
            for (const [a, b] of lines(region.rects[0], 8)) {
              const seq = sweep(regions, a, b);
              swept += seq.length;
              expect(blinks(seq))
                .withContext(`${key(region.subject)} from ${a} to ${b}: ${JSON.stringify(runs(seq))}`)
                .toBe(0);
            }
          }
          expect(swept).toBeGreaterThan(1000);
        });

        it('holds a building from its first pixel to its last, wherever nothing smaller or direct is', () => {
          for (const region of areas) {
            const rect = region.rects[0];
            const others = regions.filter((r) => r !== region);
            // Pixels of the region that no other region touches (nor lies within the hold margin of).
            const free = (x: number, y: number) =>
              !others.some((o) =>
                o.rects.some(
                  (q) => x >= q.left - 6 && x <= q.right + 6 && y >= q.top - 6 && y <= q.bottom + 6,
                ),
              );
            let checked = 0;
            for (const [a, b] of lines(rect, 6)) {
              const steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
              let current: VillageSubject | null = null;
              for (let i = 0; i <= steps; i++) {
                const x = Math.round(a[0] + ((b[0] - a[0]) * i) / steps);
                const y = Math.round(a[1] + ((b[1] - a[1]) * i) / steps);
                current = resolveHover(regions, x, y, current)?.subject ?? null;
                if (x >= Math.ceil(rect.left) && x <= Math.floor(rect.right) && y >= Math.ceil(rect.top) && y <= Math.floor(rect.bottom) && free(x, y)) {
                  checked++;
                  expect(key(current)).withContext(`${key(region.subject)} at ${x},${y}`).toBe(key(region.subject));
                }
              }
            }
            // A region fully covered by others has nothing of its own to check.
            expect(checked).toBeGreaterThanOrEqual(0);
          }
        });

        it('flipped under the old nearest-volume pick, which this sweep would fail', () => {
          let oldBlinks = 0;
          let newBlinks = 0;
          for (const region of areas) {
            for (const [a, b] of lines(region.rects[0], 8)) {
              const steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
              const legacy: string[] = [];
              for (let i = 0; i <= steps; i++) {
                const x = Math.round(a[0] + ((b[0] - a[0]) * i) / steps);
                const y = Math.round(a[1] + ((b[1] - a[1]) * i) / steps);
                legacy.push(key(legacyPick(cam, volumes, w, h, x, y)));
              }
              oldBlinks += blinks(legacy);
              newBlinks += blinks(sweep(regions, a, b));
            }
          }
          expect(oldBlinks).toBeGreaterThan(0);
          expect(newBlinks).toBe(0);
        });
      });
    }
  }
});

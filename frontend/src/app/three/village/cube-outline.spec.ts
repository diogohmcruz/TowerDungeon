import { OUTLINE_PIXEL } from './village-subjects';
import { BUILDINGS } from './buildings';
import {
  CUBE_EDGE,
  CUBE_MIN_SIDE,
  CUBE_PAD,
  cubeBounds,
  cubeOutlineVolumes,
  VILLAGE_OVERLAY_CAPACITY,
} from './cube-outline';
import {
  buildVillage,
  EMPTY_VILLAGE,
  UPGRADE_MODULES,
  type VillageInput,
  type Volume,
} from './village-layout';
import { ofSubject, subjectOfVolume, type SubjectKind } from './village-subjects';
import { volumeRegions } from './volume-regions';
import * as THREE from 'three';

/** A Village with everything built that a Tier can hold, so every building has volumes. */
function rich(tier: number): VillageInput {
  return {
    ...EMPTY_VILLAGE,
    villageTierLevel: tier,
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
    sporeCellarOpen: tier >= 3,
    spores: 7,
    pillar: { highestFloor: 37, milestonesEarned: 2, milestonesTotal: 4, tollShare: 0.67 },
    upgrades: Object.fromEntries(Object.keys(UPGRADE_MODULES).map((id) => [id, 2])),
    fallenCount: 5,
    ringLevel: 2,
    ringMax: 5,
    saltPansOpen: tier >= 2,
    salt: 40,
    shortcuts: [
      { id: 'a', floor: 3 },
      { id: 'b', floor: 10 },
    ],
    port: 'BUILT',
    boatDocked: true,
    offer: tier >= 4,
    startFloor: 0,
  };
}

interface Thing {
  kind: SubjectKind;
  id: string;
}

/** Every thing the scene draws that an outline can frame, drawn volumes only. */
function things(volumes: Volume[]): Thing[] {
  const seen = new Map<string, Thing>();
  for (const v of volumes) {
    if (v.id.startsWith('outline-') || v.alpha === 0) continue;
    const s = subjectOfVolume(v.id);
    if (s) seen.set(`${s.kind}:${s.id}`, s);
  }
  return [...seen.values()];
}

const T = OUTLINE_PIXEL;
const EPS = 1e-9;

/** The edges of one kind's cube out of a list of outline volumes, by edge name (dash numbers dropped). */
function edgesOf(lines: Volume[], kind: string): Map<string, Volume[]> {
  const out = new Map<string, Volume[]>();
  for (const v of lines) {
    if (!v.id.includes(`:${kind}-`)) continue;
    const m = CUBE_EDGE.exec(v.id)!;
    const name = `${m[1]}${m[2]}${m[3]}`;
    out.set(name, [...(out.get(name) ?? []), v]);
  }
  return out;
}

describe('the focus cube (#133)', () => {
  for (const tier of [1, 4]) {
    describe(`at Village Tier ${['', 'I', 'II', 'III', 'IV'][tier]}`, () => {
      const volumes = buildVillage(rich(tier));
      const all = things(volumes);

      it('draws the registered buildings it can show, and frames each', () => {
        const ids = new Set(all.map((x) => x.id));
        // Present at every Tier: the Silo, the Fort, the Gate, the Port, the Pillar, the Ring.
        for (const id of ['SILO', 'FORT', 'GATE', 'PORT', 'PILLAR', 'RING']) {
          expect(ids.has(id)).withContext(id).toBeTrue();
        }
        expect(BUILDINGS.length).toBeGreaterThan(10);
        expect(all.length).toBeGreaterThan(15);
      });

      it('is twelve edges that hold the bounds of every volume of the thing, chosen and hovered', () => {
        for (const thing of all) {
          const parts = ofSubject(volumes, thing.id, thing.kind);
          const b = cubeBounds(parts)!;
          expect(b).withContext(thing.id).not.toBeNull();
          // Never below a minimum side: a footing's hairline is a box all the same.
          expect(b.x1 - b.x0).toBeGreaterThanOrEqual(CUBE_MIN_SIDE - EPS);
          expect(b.y1 - b.y0).toBeGreaterThanOrEqual(CUBE_MIN_SIDE - EPS);
          expect(b.z1 - b.z0).toBeGreaterThanOrEqual(CUBE_MIN_SIDE - EPS);
          // It holds every part (the padding is outside them, but for the ground).
          for (const v of parts) {
            expect(b.x0).withContext(`${thing.id} ${v.id}`).toBeLessThanOrEqual(v.x - v.w / 2 + EPS);
            expect(b.x1).toBeGreaterThanOrEqual(v.x + v.w / 2 - EPS);
            expect(b.y0).toBeLessThanOrEqual(v.y - v.h / 2 + EPS);
            expect(b.y1).toBeGreaterThanOrEqual(v.y + v.h / 2 - EPS);
            expect(b.z0).toBeLessThanOrEqual(v.z - v.d / 2 + EPS);
            expect(b.z1).toBeGreaterThanOrEqual(v.z + v.d / 2 - EPS);
          }
          // And it fits them: a part's bounds, plus the padding, and no more (unless a minimum side).
          const wide = Math.max(...parts.map((v) => v.x + v.w / 2)) - Math.min(...parts.map((v) => v.x - v.w / 2));
          expect(b.x1 - b.x0).toBeLessThanOrEqual(Math.max(wide + 2 * CUBE_PAD, CUBE_MIN_SIDE) + EPS);

          for (const tag of ['select', 'hover'] as const) {
            const weight = tag === 'select' ? 2 : 1;
            const lines = cubeOutlineVolumes(volumes, thing.id, weight, tag).filter((v) =>
              v.id.includes(`:${thing.kind}-`),
            );
            const edges = edgesOf(lines, thing.kind);
            expect(edges.size).withContext(`${tag} ${thing.kind}:${thing.id}`).toBe(12);
            const t = T * weight;
            // The twelve edges together span exactly the cube (a thickness at the corners).
            const lo = (f: (v: Volume) => number) => Math.min(...lines.map(f));
            const hi = (f: (v: Volume) => number) => Math.max(...lines.map(f));
            expect(lo((v) => v.x - v.w / 2)).toBeCloseTo(b.x0 - t / 2, 6);
            expect(hi((v) => v.x + v.w / 2)).toBeCloseTo(b.x1 + t / 2, 6);
            expect(lo((v) => v.y - v.h / 2)).toBeCloseTo(b.y0, 6);
            expect(hi((v) => v.y + v.h / 2)).toBeCloseTo(b.y1 + t / 2, 6);
            expect(lo((v) => v.z - v.d / 2)).toBeCloseTo(b.z0 - t / 2, 6);
            expect(hi((v) => v.z + v.d / 2)).toBeCloseTo(b.z1 + t / 2, 6);
            // Each edge is thin on two axes and as long as the cube on the third.
            for (const [name, pieces] of edges) {
              const axis = name[0];
              const along = (v: Volume) => (axis === 'x' ? v.w : axis === 'y' ? v.h : v.d);
              const across = (v: Volume) =>
                [v.w, v.h, v.d].filter((_, i) => 'xyz'[i] !== axis);
              const len = axis === 'x' ? b.x1 - b.x0 : axis === 'y' ? b.y1 - b.y0 : b.z1 - b.z0;
              for (const v of pieces) {
                expect(across(v)).toEqual([t, t]);
                expect(v.tone).toBe(1);
                expect(v.alpha).toBe(1);
                expect(subjectOfVolume(v.id)?.id).toBe(thing.id);
              }
              if (tag === 'select') {
                expect(pieces.length).toBe(1);
                expect(along(pieces[0])).toBeCloseTo(len + (axis === 'y' ? t / 2 : t), 6);
              } else {
                // Dashed: runs along the whole edge, never longer than a dash, with gaps between.
                expect(pieces.length).toBeGreaterThanOrEqual(1);
                const total = pieces.reduce((sum, v) => sum + along(v), 0);
                expect(total).toBeLessThanOrEqual(len + t + EPS);
                if (len + t > 0.5) expect(pieces.length).toBeGreaterThan(1);
              }
            }
          }
        }
      });

      it('differs for the hovered and the chosen by thickness and by dashes, not by colour', () => {
        const thing = all.find((x) => x.id === 'SILO')!;
        const chosen = cubeOutlineVolumes(volumes, thing.id, 2, 'select');
        const hovered = cubeOutlineVolumes(volumes, thing.id, 1, 'hover');
        expect(chosen.length).toBe(12);
        expect(hovered.length).toBeGreaterThan(12);
        const thin = (v: Volume) => Math.min(v.w, v.h, v.d);
        expect(Math.max(...hovered.map(thin))).toBeLessThan(Math.min(...chosen.map(thin)));
        expect(new Set([...hovered, ...chosen].map((v) => v.tone))).toEqual(new Set([1]));
      });

      it('leaves the hit regions and the budget alone', () => {
        const cam = new THREE.PerspectiveCamera(40, 16 / 9, 0.1, 100);
        cam.position.set(0, 1, 12);
        cam.lookAt(0, 1, 0);
        cam.updateMatrixWorld();
        const plain = volumeRegions(cam, volumes, 1600, 900);
        for (const thing of all.slice(0, 12)) {
          const framed = buildVillage({
            ...rich(tier),
            hovered: thing.id,
            selected: all.find((x) => x.id !== thing.id)!.id,
          });
          expect(volumeRegions(cam, framed, 1600, 900))
            .withContext(thing.id)
            .toEqual(plain);
        }
        // Worst case: the two biggest cubes, hovered and chosen, with the Start marks.
        const count = (thing: Thing, weight: 1 | 2, tag: 'hover' | 'select') =>
          cubeOutlineVolumes(volumes, thing.id, weight, tag).length;
        const hover = Math.max(...all.map((x) => count(x, 1, 'hover')));
        const chosen = Math.max(...all.map((x) => count(x, 2, 'select')));
        const start = buildVillage(rich(tier)).filter((v) => v.id.startsWith('outline-')).length;
        expect(hover + chosen + start).toBeLessThan(VILLAGE_OVERLAY_CAPACITY);
      });
    });
  }

  it('gives a hairline footing and a single mark a visible cube', () => {
    const empty = buildVillage({ ...EMPTY_VILLAGE, saltPansOpen: false, villageTierLevel: 1 });
    const footing = things(empty).filter((x) => x.id === 'SALTER' || x.id === 'PORT');
    for (const thing of footing) {
      const b = cubeBounds(ofSubject(empty, thing.id, thing.kind))!;
      expect(b.y1 - b.y0).toBeGreaterThanOrEqual(CUBE_MIN_SIDE - EPS);
      expect(b.z1 - b.z0).toBeGreaterThanOrEqual(CUBE_MIN_SIDE - EPS);
    }
  });

  it('frames the Port by its pier and Boat, and the Pillar by its parts, not the sea or the frame', () => {
    const v = buildVillage(rich(1));
    const port = cubeBounds(ofSubject(v, 'PORT', 'building'))!;
    const sea = v.filter((x) => x.group === 'port' && !x.id.startsWith('port-'));
    expect(sea.length).toBeGreaterThan(0);
    const seaWidth = Math.max(...sea.map((x) => x.w));
    expect(port.x1 - port.x0).toBeLessThan(seaWidth);
    const pillar = cubeBounds(ofSubject(v, 'PILLAR', 'building'))!;
    expect(pillar.x1 - pillar.x0).toBeLessThan(3);
    expect(pillar.y1 - pillar.y0).toBeLessThan(3);
  });

  it('puts only focus lines in the cube pass: every outline id, nothing else', () => {
    const v = buildVillage({ ...rich(4), hovered: 'SILO', selected: 'FORT' });
    const lines = v.filter((x) => x.id.startsWith('outline-'));
    expect(lines.some((x) => x.id.startsWith('outline-SILO:hover'))).toBeTrue();
    expect(lines.some((x) => x.id.startsWith('outline-FORT:select'))).toBeTrue();
    expect(v.slice(0, v.length - lines.length).some((x) => x.id.startsWith('outline-'))).toBeFalse();
  });
});

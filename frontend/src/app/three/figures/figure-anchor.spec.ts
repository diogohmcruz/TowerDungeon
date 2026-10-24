import * as THREE from 'three';

import { SHOTS, ShotName } from '../camera-shots';
import {
  FLOOR_SURFACE_OFFSET,
  floorToY,
} from '../floor-strip/floor-strip-plan';
import {
  DECK_LINE_RISE,
  DECK_Z,
  deckViewOf,
  standCentreY,
} from './figure-anchor';
import { figureRect } from './figure-pick';
import {
  FIGURE_Z,
  FigureInput,
  FigureMember,
  planFigures,
} from './figure-layout';

const SIZES: ReadonlyArray<{ w: number; h: number }> = [
  { w: 1440, h: 900 },
  { w: 800, h: 1000 },
  { w: 1920, h: 1080 },
];
const SCALES = [2, 3, 4];
const FLOORS = [1, 7, 50];

/** Members with ids as the server gives them: one is negative and large, a hash. */
function members(n: number, base: number, guardian = -1): FigureMember[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i === 2 ? -1549096401 - base : base + i,
    typeKey: 'SLIME',
    guardian: i === guardian,
  }));
}

/** The camera as the scene poses it for a Shot, `focusY` being the anchor Floor's base. */
function cameraFor(
  shot: ShotName,
  size: { w: number; h: number },
  focusY: number,
  subject?: { x: number; y: number; z: number },
): THREE.PerspectiveCamera {
  const pose = SHOTS[shot];
  const cam = new THREE.PerspectiveCamera(pose.fov, size.w / size.h, 0.1, 200);
  if (shot === 'unit' && subject) {
    cam.position.set(subject.x, subject.y + pose.camUp, subject.z + pose.distance);
    cam.lookAt(subject.x, subject.y + pose.lookUp, subject.z);
  } else {
    cam.position.set(0, focusY + pose.camUp, pose.distance);
    cam.lookAt(0, focusY + pose.lookUp, 0);
  }
  cam.updateMatrixWorld();
  return cam;
}

/** Screen Y (CSS px) of the deck line of the Floor whose surface is at `surfaceY`. */
function deckScreenY(cam: THREE.Camera, surfaceY: number, h: number): number {
  const v = new THREE.Vector3(0, surfaceY + DECK_LINE_RISE, DECK_Z).project(cam);
  return ((1 - v.y) / 2) * h;
}

function plan(cam: THREE.Camera, anchor: number, over: Partial<FigureInput>) {
  const view = deckViewOf(cam);
  return planFigures({
    party: members(8, 1),
    enemies: members(5, 5000, 0),
    waves: [],
    surfaceY: floorToY(anchor) + FLOOR_SURFACE_OFFSET,
    standY: (s, z, h) => standCentreY(view, s, z, h),
    floorSurfaceY: (n) => floorToY(n) + FLOOR_SURFACE_OFFSET,
    viewAspect: 0.6,
    anchorFloor: anchor,
    ...over,
  });
}

describe('figures standing on the deck (#136)', () => {
  for (const shot of ['battle', 'closeup'] as const) {
    for (const size of SIZES) {
      for (const floor of FLOORS) {
        for (const scale of SCALES) {
          it(`the front rank's feet are on the deck line: ${shot}, ${size.w}x${size.h}, Floor ${floor}, scale ${scale}`, () => {
            const anchor = floor - 1;
            const cam = cameraFor(shot, size, floorToY(anchor));
            const surfaceY = floorToY(anchor) + FLOOR_SURFACE_OFFSET;
            const p = plan(cam, anchor, { figureBoost: scale / 2 });
            const line = deckScreenY(cam, surfaceY, size.h);
            const front = p.marks.filter(
              (m) => Math.abs(m.z - FIGURE_Z) < 1e-9,
            );
            // The Party's and the garrison's front ranks, the Guardian (larger) among them.
            expect(front.length).toBeGreaterThan(3);
            for (const f of front) {
              const gap = figureRect(cam, f, size.w, size.h).bottom - line;
              // One virtual pixel is `scale` CSS pixels; the anchor is exact to far less.
              expect(Math.abs(gap)).toBeLessThanOrEqual(scale);
              expect(Math.abs(gap)).toBeLessThan(0.01);
            }
          });
        }
      }
    }
  }

  it('the fixed lift of the battle Shot left the figures floating in the closeup', () => {
    const size = SIZES[0];
    const cam = cameraFor('closeup', size, floorToY(6));
    const surfaceY = floorToY(6) + FLOOR_SURFACE_OFFSET;
    const old = planFigures({
      party: members(4, 1),
      enemies: [],
      waves: [],
      surfaceY,
      floorSurfaceY: (n) => floorToY(n) + FLOOR_SURFACE_OFFSET,
      viewAspect: 0.6,
      anchorFloor: 6,
    });
    const gap =
      figureRect(cam, old.marks[0], size.w, size.h).bottom -
      deckScreenY(cam, surfaceY, size.h);
    // The default (the lift of the battle Shot) puts the feet over a hundred CSS pixels above the line.
    expect(gap).toBeLessThan(-50);
  });

  it('puts the figure a unit view is on on the line, whatever its rank and its id', () => {
    for (const size of SIZES) {
      for (const floor of FLOORS) {
        const anchor = floor - 1;
        const surfaceY = floorToY(anchor) + FLOOR_SURFACE_OFFSET;
        const party = members(12, 1);
        for (const standOn of party.map((m) => `unit-${m.id}`)) {
          // The camera follows the figure and the figure follows the camera: settle it.
          let cam = cameraFor('battle', size, floorToY(anchor));
          let subject = { x: 0, y: surfaceY + 0.3, z: FIGURE_Z };
          for (let i = 0; i < 12; i++) {
            cam = cameraFor('unit', size, 0, subject);
            const found = plan(cam, anchor, { party, standOn }).marks.find(
              (m) => m.id === standOn,
            );
            expect(found).toBeDefined();
            subject = found!;
          }
          const f = plan(cam, anchor, { party, standOn }).marks.find(
            (m) => m.id === standOn,
          )!;
          const gap =
            figureRect(cam, f, size.w, size.h).bottom -
            deckScreenY(cam, surfaceY, size.h);
          expect(Math.abs(gap)).toBeLessThan(0.5);
        }
      }
    }
  });

  it('puts the Wave columns on the line of their Floor too', () => {
    for (const shot of ['battle', 'closeup'] as const) {
      const size = SIZES[0];
      const anchor = 6;
      const cam = cameraFor(shot, size, floorToY(anchor));
      const p = plan(cam, anchor, {
        waves: [
          { size: 3, floor: anchor },
          { size: 9, floor: anchor },
        ],
      });
      const line = deckScreenY(
        cam,
        floorToY(anchor) + FLOOR_SURFACE_OFFSET,
        size.h,
      );
      const columns = p.marks.filter((m) => /^wave-\d$/.test(m.id));
      expect(columns.length).toBe(2);
      for (const c of columns) {
        expect(
          Math.abs(figureRect(cam, c, size.w, size.h).bottom - line),
        ).toBeLessThan(0.01);
      }
    }
  });
});

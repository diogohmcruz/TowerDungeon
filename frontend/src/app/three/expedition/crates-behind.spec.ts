import * as THREE from 'three';

import { floorToY, FLOOR_SURFACE_OFFSET } from '../floor-strip/floor-strip-plan';
import {
  FIGURE_CAP,
  FigureMember,
  FIGURE_Z,
  planFigures,
} from '../figures/figure-layout';
import { DECK_LINE_RISE, deckViewOf, standCentreY, standFeetY } from '../figures/figure-anchor';
import { figureRect, hitRect, PICK_MARGIN, ScreenRect } from '../figures/figure-pick';
import { SHOTS, ShotName } from '../camera-shots';
import { figureBoost } from '../ink/pixel-scale';
import { applyStageView, towerRegion } from '../stage-view';
import { rimVolumes } from '../launch/object-rim';
import { TOWER_LAUNCH_Z } from '../launch/launch-objects';
import type { Volume } from '../village/village-layout';
import { CRATE_CEILING_RISE, CRATE_ID, CRATE_Z, expeditionVolumes } from './expedition-objects';

/** The battle view, the closeup and the unit view, as the scene frames them (#127). */

const ANCHOR = 10;
const SURFACE = floorToY(ANCHOR) + FLOOR_SURFACE_OFFSET;

function members(n: number, prefix: string, guardians = 0): FigureMember[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    typeKey: prefix === 'u' ? 'ARCHER' : 'SLIME',
    guardian: i < guardians,
  }));
}

function camera(
  shot: ShotName,
  canvas: { w: number; h: number },
  narrow: boolean,
  at?: { x: number; y: number; z: number },
): THREE.PerspectiveCamera {
  const pose = SHOTS[shot];
  const cam = new THREE.PerspectiveCamera(pose.fov, 1, 0.1, 200);
  const viewY = floorToY(ANCHOR);
  if (shot === 'unit' && at) {
    cam.position.set(at.x, at.y + pose.camUp, at.z + pose.distance);
    cam.lookAt(at.x, at.y + pose.lookUp, at.z);
  } else {
    cam.position.set(0, viewY + pose.camUp, pose.distance);
    cam.lookAt(0, viewY + pose.lookUp, 0);
  }
  applyStageView(cam, canvas.w, canvas.h, narrow, pose.spread);
  cam.updateProjectionMatrix();
  cam.updateMatrixWorld();
  return cam;
}

/** Where a box lands on the canvas: the rectangle of its eight projected corners. */
function boxRect(cam: THREE.Camera, v: Volume, w: number, h: number): ScreenRect {
  const r = { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity };
  for (const sx of [-1, 1]) {
    for (const sy of [-1, 1]) {
      for (const sz of [-1, 1]) {
        const p = new THREE.Vector3(
          v.x + (sx * v.w) / 2,
          v.y + (sy * v.h) / 2,
          v.z + (sz * v.d) / 2,
        ).project(cam);
        const x = ((p.x + 1) / 2) * w;
        const y = ((1 - p.y) / 2) * h;
        r.left = Math.min(r.left, x);
        r.right = Math.max(r.right, x);
        r.top = Math.min(r.top, y);
        r.bottom = Math.max(r.bottom, y);
      }
    }
  }
  return r;
}

const overlaps = (a: ScreenRect, b: ScreenRect) =>
  a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;

const SIZES: ReadonlyArray<[string, number, number]> = [
  ['a small Party and garrison', 3, 2],
  ['the capped sprites', FIGURE_CAP, FIGURE_CAP],
  ['far past the cap', 60, 120],
];

const CANVASES = [
  { name: 'a wide screen', w: 1440, h: 900, narrow: false },
  { name: 'a narrow screen', w: 440, h: 1000, narrow: true },
];

describe('The crates stand behind the figures (#127)', () => {
  for (const canvas of CANVASES) {
    for (const [name, party, garrison] of SIZES) {
      for (const pixelScale of [1, 2, 4]) {
        it(`never meet a figure's drawn or hit rectangle: ${name}, ${canvas.name}, pixel scale ${pixelScale}`, () => {
          const aspect = towerRegion(canvas.w, canvas.narrow).width / canvas.h;
          /** The figures and the crates as the scene places them for this camera (#136), the camera following the figure in the unit view. */
          const stage = (shot: ShotName, focusId?: string) => {
            let at: { x: number; y: number; z: number } | undefined;
            for (let pass = 0; pass < (focusId ? 12 : 1); pass++) {
              const cam = camera(shot, canvas, canvas.narrow, at);
              const view = deckViewOf(cam);
              const plan = planFigures({
                party: members(party, 'u'),
                enemies: members(garrison, 'e', 1),
                waves: [],
                surfaceY: SURFACE,
                standY: (s, z, h) => standCentreY(view, s, z, h),
                standOn: focusId,
                floorSurfaceY: (n) => floorToY(n) + FLOOR_SURFACE_OFFSET,
                viewAspect: aspect,
                figureBoost: figureBoost(pixelScale),
                anchorFloor: ANCHOR,
              });
              const sprites = plan.marks.slice(0, plan.figureCount);
              const subject = sprites.find((f) => f.id === focusId);
              if (focusId && subject && pass < 11) {
                at = subject;
                continue;
              }
              return { cam, view, sprites };
            }
            throw new Error('unreachable');
          };
          const first = stage('battle');
          const partyXs = first.sprites.filter((f) => f.id.startsWith('unit-')).map((f) => f.x);
          const partyX = partyXs.length
            ? (Math.min(...partyXs) + Math.max(...partyXs)) / 2
            : -1.9;

          for (const shot of ['battle', 'closeup', 'unit'] as ShotName[]) {
            // The unit view centres on one figure at a time.
            const focuses = shot === 'unit' ? first.sprites.map((f) => f.id) : [undefined];
            for (const focusId of focuses) {
              const { cam, view, sprites } = stage(shot, focusId);
              const volumes = expeditionVolumes({
                surfaceY: standFeetY(view, SURFACE, TOWER_LAUNCH_Z),
                crateSurfaceY: standFeetY(view, SURFACE, FIGURE_Z),
                crateCeilingY: standFeetY(view, SURFACE + CRATE_CEILING_RISE - DECK_LINE_RISE, CRATE_Z),
                supplies: 100,
                maxSupplies: 200,
                credits: 5000,
                materials: 500,
                relics: 9,
                partyX,
              });
              const crates = volumes.filter((v) => v.id.startsWith(CRATE_ID));
              const parts = [...crates, ...rimVolumes(crates)];
              expect(crates.length).toBeGreaterThan(0);
              for (const f of sprites) {
                const drawn = figureRect(cam, f, canvas.w, canvas.h);
                const hit = hitRect(drawn, { margin: PICK_MARGIN + pixelScale });
                for (const p of parts) {
                  const r = boxRect(cam, p, canvas.w, canvas.h);
                  expect(overlaps(r, drawn))
                    .withContext(`${shot} ${f.id} drawn vs ${p.id}`)
                    .toBe(false);
                  expect(overlaps(r, hit))
                    .withContext(`${shot} ${f.id} hit vs ${p.id}`)
                    .toBe(false);
                }
              }
            }
          }
        });
      }
    }
  }
});

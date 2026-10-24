import * as THREE from 'three';

import { FigureInstance } from './figure-layout';
import { SHOTS, ShotName } from '../camera-shots';
import { applyStageView } from '../stage-view';
import {
  figureId,
  figureRect,
  hitRect,
  parseFigureId,
  PICK_MARGIN,
  PICK_MIN_SIZE,
  pickFigure,
  pickSprite,
} from './figure-pick';

const W = 800;
const H = 600;

function camera(): THREE.PerspectiveCamera {
  const cam = new THREE.PerspectiveCamera(60, W / H, 0.1, 100);
  cam.position.set(0, 0, 10);
  cam.lookAt(0, 0, 0);
  cam.updateMatrixWorld();
  return cam;
}

function figure(id: string, x: number, z = 0, size = 1): FigureInstance {
  return { id, cell: 0, x, y: 0, z, w: size, h: size, role: 0 };
}

describe('figure picking', () => {
  it('names member figures and nothing else', () => {
    expect(parseFigureId('unit-4')).toEqual({ kind: 'unit', id: 4 });
    expect(parseFigureId('enemy-12')).toEqual({ kind: 'enemy', id: 12 });
    expect(parseFigureId('wave-0')).toBeNull();
    expect(parseFigureId('wave-0-n1')).toBeNull();
    expect(figureId({ kind: 'enemy', id: 12 })).toBe('enemy-12');
  });

  it('puts a figure at the centre of the screen on the camera axis', () => {
    const r = figureRect(camera(), figure('unit-1', 0), W, H);
    expect((r.left + r.right) / 2).toBeCloseTo(W / 2, 3);
    expect((r.top + r.bottom) / 2).toBeCloseTo(H / 2, 3);
    expect(r.right).toBeGreaterThan(r.left);
    expect(r.bottom).toBeGreaterThan(r.top);
  });

  it('picks the sprite under the click and nothing on empty space', () => {
    const cam = camera();
    const sprites = [figure('unit-1', 0)];
    expect(pickFigure(cam, sprites, W / 2, H / 2, W, H)?.id).toBe('unit-1');
    expect(pickFigure(cam, sprites, 5, 5, W, H)).toBeNull();
  });

  it('does not pick a Wave column or a numeral', () => {
    const cam = camera();
    const marks = [figure('wave-0', 0), figure('wave-0-n0', 0)];
    expect(pickFigure(cam, marks, W / 2, H / 2, W, H)).toBeNull();
  });

  it('picks the front-most of two figures that overlap exactly', () => {
    const cam = camera();
    const marks = [figure('unit-1', 0, 0), figure('enemy-2', 0, 1)];
    expect(pickFigure(cam, marks, W / 2, H / 2, W, H)?.id).toBe('enemy-2');
  });

  it('follows the figure as the camera moves', () => {
    const cam = camera();
    const f = figure('unit-1', 0);
    const before = figureRect(cam, f, W, H);
    cam.position.x = 2;
    cam.lookAt(0, 0, 0);
    cam.updateMatrixWorld();
    const after = figureRect(cam, f, W, H);
    expect(after.left).not.toBeCloseTo(before.left, 1);
  });

  describe('one hit-test for the click and the hover', () => {
    it('names the same figure to both, wherever the pointer is', () => {
      const cam = camera();
      const sprites = [
        figure('unit-1', -0.2),
        figure('enemy-2', 0.2),
        figure('wave-0', 0),
      ];
      const scale = 3;
      for (let x = 300; x <= 500; x += 5) {
        for (let y = 240; y <= 360; y += 10) {
          // A click is picked as `pickFigure` with the scene's margin; the hover calls `pickSprite`.
          const click = pickFigure(cam, sprites, x, y, W, H, {
            margin: PICK_MARGIN + scale,
          });
          const hover = pickSprite(cam, sprites, x, y, W, H, scale);
          expect(hover?.id).toBe(click?.id);
        }
      }
    });

    it('takes the nearest of two overlapping figures, and nothing off them', () => {
      const cam = camera();
      const sprites = [figure('unit-1', -0.05), figure('enemy-2', 0.05)];
      const left = W / 2 - 4;
      const right = W / 2 + 4;
      expect(pickSprite(cam, sprites, left, H / 2, W, H, 2)?.id).toBe('unit-1');
      expect(pickSprite(cam, sprites, right, H / 2, W, H, 2)?.id).toBe(
        'enemy-2',
      );
      expect(pickSprite(cam, sprites, 5, 5, W, H, 2)).toBeNull();
    });
  });

  describe('the hit area on the real stage', () => {
    const SCREEN_W = 1440;
    const SCREEN_H = 900;

    /** The camera of a Shot as the scene frames it: the Tower region view offset and the spread. */
    function shotCamera(name: ShotName): THREE.PerspectiveCamera {
      const shot = SHOTS[name];
      const cam = new THREE.PerspectiveCamera(shot.fov, 1, 0.1, 200);
      cam.position.set(0, shot.camUp, shot.distance);
      cam.lookAt(0, shot.lookUp, 0);
      applyStageView(cam, SCREEN_W, SCREEN_H, false, shot.spread);
      cam.updateProjectionMatrix();
      cam.updateMatrixWorld();
      return cam;
    }

    /** Party figures of the size the layout gives them, standing at the Shot's look-at height. */
    function party(name: ShotName): FigureInstance[] {
      const y = SHOTS[name].lookUp;
      return [-0.6, -0.3, 0, 0.3, 0.6].map((x, i) => ({
        id: `unit-${i + 1}`,
        cell: 0,
        x,
        y,
        z: 0,
        w: 0.22,
        h: 0.3,
        role: 0,
      }));
    }

    for (const shot of ['battle', 'closeup'] as ShotName[]) {
      for (const scale of [2, 3, 4]) {
        it(`picks a click anywhere on a figure as the ${shot} draws it at pixel scale ${scale}`, () => {
          const cam = shotCamera(shot);
          const sprites = party(shot);
          // The canvas rasterises on a grid of `scale` CSS px: the drawn figure fills whole cells,
          // so it reaches up to one cell past the projected rectangle on every side.
          const down = (v: number) => Math.floor(v / scale) * scale;
          const up = (v: number) => Math.ceil(v / scale) * scale;
          for (const f of sprites) {
            const r = figureRect(cam, f, SCREEN_W, SCREEN_H);
            const xs = [
              down(r.left) + 0.5,
              (r.left + r.right) / 2,
              up(r.right) - 0.5,
            ];
            const ys = [
              down(r.top) + 0.5,
              (r.top + r.bottom) / 2,
              up(r.bottom) - 0.5,
            ];
            for (const x of xs) {
              for (const y of ys) {
                const hit = pickFigure(cam, [f], x, y, SCREEN_W, SCREEN_H, {
                  margin: PICK_MARGIN + scale,
                });
                expect(hit?.id).toBe(f.id);
              }
            }
          }
        });
      }
    }

    it('gives a battle figure, which is smaller than a fingertip, a hit area of its own', () => {
      const cam = shotCamera('battle');
      const f = party('battle')[2];
      const r = figureRect(cam, f, SCREEN_W, SCREEN_H);
      expect(r.right - r.left).toBeLessThan(PICK_MIN_SIZE);
      const hit = hitRect(r);
      expect(hit.right - hit.left).toBeGreaterThanOrEqual(PICK_MIN_SIZE);
      expect(hit.bottom - hit.top).toBeGreaterThanOrEqual(PICK_MIN_SIZE);
      // The middle of the drawn bounds stays the middle of the hit area.
      expect((hit.left + hit.right) / 2).toBeCloseTo((r.left + r.right) / 2, 6);
    });

    it('still reads a click well clear of every figure as empty space', () => {
      const cam = shotCamera('battle');
      expect(
        pickFigure(cam, party('battle'), 40, 40, SCREEN_W, SCREEN_H),
      ).toBeNull();
    });

    it('gives a click between two close figures to the nearer one, whatever their depth', () => {
      const cam = shotCamera('battle');
      const a = party('battle')[2];
      // The other figure is in front, but the click is on a's side of the gap between them.
      const front: FigureInstance = {
        ...a,
        id: 'enemy-9',
        z: 2,
        x: a.x + 0.05,
      };
      const ra = figureRect(cam, a, SCREEN_W, SCREEN_H);
      const rf = figureRect(cam, front, SCREEN_W, SCREEN_H);
      const x = (ra.left + ra.right) / 2 + 0.5;
      const y = (ra.top + ra.bottom) / 2;
      expect(Math.abs(x - (ra.left + ra.right) / 2)).toBeLessThan(
        Math.abs(x - (rf.left + rf.right) / 2),
      );
      expect(pickFigure(cam, [front, a], x, y, SCREEN_W, SCREEN_H)?.id).toBe(
        a.id,
      );
    });
  });
});

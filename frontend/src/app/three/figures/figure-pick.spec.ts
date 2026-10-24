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
  pickSpriteSteady,
  SPRITE_HOLD_PX,
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
    expect(parseFigureId('unit-')).toBeNull();
    expect(parseFigureId('enemy--')).toBeNull();
    expect(figureId({ kind: 'enemy', id: 12 })).toBe('enemy-12');
  });

  // The server draws a member's id from the whole int range, hash codes included, so about half of
  // all members have a negative one ("enemy--1549096401"). Those must be hovered and clicked too (#131).
  it('names a member whose id is negative, which half of the server ids are', () => {
    expect(parseFigureId('unit--859253032')).toEqual({ kind: 'unit', id: -859253032 });
    expect(parseFigureId('enemy--1549096401')).toEqual({ kind: 'enemy', id: -1549096401 });
    expect(figureId({ kind: 'enemy', id: -70482493 })).toBe('enemy--70482493');
    expect(parseFigureId(figureId({ kind: 'unit', id: -7 }))).toEqual({ kind: 'unit', id: -7 });
  });

  it('picks and hovers a lone Warrior and a lone Slime whose ids are negative (a 1 and 1 battle, #131)', () => {
    const sprites = [figure('unit--859253032', -1.5), figure('enemy--1549096401', 1.5)];
    const at = (f: FigureInstance) => {
      const r = figureRect(camera(), f, W, H);
      return [(r.left + r.right) / 2, (r.top + r.bottom) / 2];
    };
    for (const f of sprites) {
      const [x, y] = at(f);
      expect(pickFigure(camera(), sprites, x, y, W, H)?.id).toBe(f.id);
      expect(pickSprite(camera(), sprites, x, y, W, H, 3)?.id).toBe(f.id);
      expect(pickSpriteSteady(camera(), sprites, x, y, W, H, 3, null)?.id).toBe(f.id);
    }
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

    it('gives a click between two close figures to the nearer one when it is clear of the other', () => {
      const cam = shotCamera('battle');
      const a = party('battle')[2];
      // The other figure is in front but off to the side: the click is on a, outside its bounds.
      const front: FigureInstance = {
        ...a,
        id: 'enemy-9',
        z: 2,
        x: a.x + 0.3,
      };
      const ra = figureRect(cam, a, SCREEN_W, SCREEN_H);
      const rf = figureRect(cam, front, SCREEN_W, SCREEN_H);
      const x = (ra.left + ra.right) / 2 + 0.5;
      const y = (ra.top + ra.bottom) / 2;
      expect(rf.left).toBeGreaterThan(x + PICK_MARGIN);
      expect(pickFigure(cam, [front, a], x, y, SCREEN_W, SCREEN_H)?.id).toBe(
        a.id,
      );
    });

    describe('a front rank that stands over the rank behind it (#128)', () => {
      /** A front rank at z 0.6, and a figure at z 0 standing between and behind two of its figures. */
      function ranks(
        name: ShotName,
        spread = 0.15,
      ): {
        front: FigureInstance[];
        back: FigureInstance[];
      } {
        const y = SHOTS[name].lookUp;
        const fig = (id: string, x: number, z: number): FigureInstance => ({
          id,
          cell: 0,
          x,
          y,
          z,
          w: 0.22,
          h: 0.3,
          role: 0,
        });
        return {
          front: [fig('unit-1', -spread, 0.6), fig('unit-2', spread, 0.6)],
          back: [fig('unit-3', 0, 0)],
        };
      }

      for (const shot of ['battle', 'closeup'] as ShotName[]) {
        for (const scale of [2, 3, 4]) {
          it(`hovers a front figure anywhere on it, even beside the middle of a figure behind, in the ${shot} at pixel scale ${scale}`, () => {
            const cam = shotCamera(shot);
            const { front, back } = ranks(shot);
            const all = [...back, ...front];
            for (const f of front) {
              const r = figureRect(cam, f, SCREEN_W, SCREEN_H);
              const w = r.right - r.left;
              const h = r.bottom - r.top;
              for (const fx of [0.1, 0.3, 0.5, 0.7, 0.9]) {
                for (const fy of [0.1, 0.5, 0.9]) {
                  const hit = pickSprite(
                    cam,
                    all,
                    r.left + w * fx,
                    r.top + h * fy,
                    SCREEN_W,
                    SCREEN_H,
                    scale,
                  );
                  expect(hit?.id).toBe(f.id);
                }
              }
            }
          });

          it(`still hovers the figure behind where only it is drawn, in the ${shot} at pixel scale ${scale}`, () => {
            const cam = shotCamera(shot);
            const { front, back } = ranks(shot, 0.2);
            const r = figureRect(cam, back[0], SCREEN_W, SCREEN_H);
            // Between the two front figures: only the figure behind shows there.
            const gapLeft = figureRect(cam, front[0], SCREEN_W, SCREEN_H).right;
            const gapRight = figureRect(cam, front[1], SCREEN_W, SCREEN_H).left;
            expect(gapRight).toBeGreaterThan(gapLeft + 2 * scale);
            const hit = pickSprite(
              cam,
              [...back, ...front],
              (gapLeft + gapRight) / 2,
              (r.top + r.bottom) / 2,
              SCREEN_W,
              SCREEN_H,
              scale,
            );
            expect(hit?.id).toBe(back[0].id);
          });
        }
      }
    });
  });
});

describe('the steady sprite hover (#126)', () => {
  const cam = camera();
  // Two close sprites whose hit areas overlap: the nearer one wins a plain pick, pixel by pixel.
  const a = figure('unit-1', -0.1);
  const b = figure('unit-2', 0.1);
  const centre = (f: FigureInstance) => {
    const r = figureRect(cam, f, W, H);
    return { x: (r.left + r.right) / 2, y: (r.top + r.bottom) / 2, r };
  };
  const pick = (x: number, y: number, hold: string | null) =>
    pickSpriteSteady(cam, [a, b], x, y, W, H, 1, hold)?.id ?? null;

  it('flips between two overlapping sprites under the plain pick, and does not once one is held', () => {
    const { y } = centre(a);
    const xs: number[] = [];
    for (let x = centre(a).x - 20; x <= centre(b).x + 20; x++) xs.push(x);
    const plain = xs.map(
      (x) => pickSprite(cam, [a, b], x, y, W, H, 1)?.id ?? null,
    );
    expect(new Set(plain).size).toBeGreaterThan(1);
    // Sweeping left to right with the hover held: it changes only where the held sprite's area ends.
    let hold: string | null = null;
    const steady = xs.map((x) => (hold = pick(x, y, hold)));
    const changes = steady.filter(
      (v, i) => i > 0 && v !== steady[i - 1],
    ).length;
    expect(changes).toBeLessThanOrEqual(2); // none, a, b
    expect(steady.indexOf('unit-1')).toBeGreaterThanOrEqual(0);
  });

  it('does not keep a figure behind when the pointer has moved onto a figure drawn in front of it (#128)', () => {
    const back = figure('unit-1', 0, 0);
    const front = figure('unit-2', 1.03, 1);
    const rf = figureRect(cam, front, W, H);
    const rb = figureRect(cam, back, W, H);
    // On the front figure's drawn bounds, still inside the held back figure's (larger) hit area.
    const x = rf.left;
    const y = (rf.top + rf.bottom) / 2;
    expect(x).toBeGreaterThan(rb.right);
    expect(x).toBeLessThan(hitRect(rb, { margin: PICK_MARGIN + 1 }).right);
    expect(
      pickSpriteSteady(cam, [back, front], x, y, W, H, 1, 'unit-1')?.id,
    ).toBe('unit-2');
  });

  it('keeps the held sprite just outside its area, and lets go past the margin', () => {
    const far = figure('unit-1', -3);
    const rr = figureRect(cam, far, W, H);
    const edge = hitRect(rr, { margin: PICK_MARGIN + 1 });
    const yy = (rr.top + rr.bottom) / 2;
    const at = (x: number) =>
      pickSpriteSteady(cam, [far], x, yy, W, H, 1, 'unit-1')?.id ?? null;
    expect(at(edge.right + SPRITE_HOLD_PX)).toBe('unit-1');
    expect(at(edge.right + SPRITE_HOLD_PX + 1)).toBeNull();
  });

  it('takes a sprite on its first pixel when none is held', () => {
    const far = figure('unit-1', -3);
    const rr = figureRect(cam, far, W, H);
    const edge = hitRect(rr, { margin: PICK_MARGIN + 1 });
    const yy = (rr.top + rr.bottom) / 2;
    expect(
      pickSpriteSteady(cam, [far], edge.left - 1, yy, W, H, 1, null),
    ).toBeNull();
    expect(
      pickSpriteSteady(cam, [far], edge.left + 0.5, yy, W, H, 1, null)?.id,
    ).toBe('unit-1');
  });
});

import * as THREE from 'three';

import { FigureInstance } from './figure-layout';
import { figureId, figureRect, parseFigureId, pickFigure } from './figure-pick';

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

  it('picks the front-most of two overlapping figures', () => {
    const cam = camera();
    const marks = [figure('unit-1', 0, 0), figure('enemy-2', 0.1, 1)];
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
});

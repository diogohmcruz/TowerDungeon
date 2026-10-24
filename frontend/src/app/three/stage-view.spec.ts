import * as THREE from 'three';

import { applyStageView, towerRegion } from './stage-view';

describe('stage view', () => {
  it('keeps the Tower region where the #75 panel put the scene', () => {
    // 46vw panel, less the 230 px enemy list and its 12 px gap, against the right edge.
    const wide = towerRegion(1440, false);
    expect(wide.width).toBeCloseTo(1440 * 0.46 - 242, 6);
    expect(wide.left + wide.width).toBeCloseTo(1440, 6);
    const small = towerRegion(900, false);
    expect(small.width).toBe(440 - 242);
    expect(small.left + small.width).toBe(900);
  });

  it('frames the whole canvas in a narrow layout', () => {
    expect(towerRegion(800, true)).toEqual({ left: 0, width: 800 });
  });

  it('projects the Tower axis to the middle of the region, not of the screen', () => {
    const w = 1600;
    const h = 900;
    const camera = new THREE.PerspectiveCamera(64, 1, 0.1, 200);
    camera.position.set(0, 0, 12);
    camera.lookAt(0, 0, 0);
    expect(applyStageView(camera, w, h, false)).toBeTrue();
    camera.updateMatrixWorld();
    const ndc = new THREE.Vector3(0, 0, 0).project(camera);
    const region = towerRegion(w, false);
    const wantX = ((region.left + region.width / 2) / w) * 2 - 1;
    expect(ndc.x).toBeCloseTo(wantX, 5);
    expect(ndc.y).toBeCloseTo(0, 5);
  });

  it('keeps the framing of the old panel at the same height', () => {
    const h = 900;
    const wide = new THREE.PerspectiveCamera(64, 1, 0.1, 200);
    applyStageView(wide, 2000, h, false);
    wide.updateMatrixWorld();
    const panel = new THREE.PerspectiveCamera(
      64,
      towerRegion(2000, false).width / h,
      0.1,
      200,
    );
    panel.updateMatrixWorld();
    const point = new THREE.Vector3(0, 3, -12);
    expect(point.clone().project(wide).y).toBeCloseTo(
      point.clone().project(panel).y,
      5,
    );
  });

  it('is idempotent, and notices when the canvas or layout changes', () => {
    const camera = new THREE.PerspectiveCamera(64, 1, 0.1, 200);
    expect(applyStageView(camera, 1600, 900, false)).toBeTrue();
    expect(applyStageView(camera, 1600, 900, false)).toBeFalse();
    camera.aspect = 1; // what a canvas resize does
    expect(applyStageView(camera, 1600, 900, false)).toBeTrue();
    expect(applyStageView(camera, 1000, 900, true)).toBeTrue();
    expect(applyStageView(camera, 0, 0, false)).toBeFalse();
  });
});

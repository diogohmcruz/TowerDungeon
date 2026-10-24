import type { Quarter } from '../../interfaces/game-state';
import { QUARTER_LOOK } from '../village/village-layout';
import { LANDSCAPE_FRAGMENT, LANDSCAPE_VERTEX } from './landscape.glsl';
import { towerBodyAlpha } from '../village/village-layout';
import { TOWER_SEGMENTS } from '../village/village-layout';
import {
  CLIFFS,
  CROWN_BASE,
  CROWN_SHAFT,
  CROWN_WIDTH,
  CROWN_Z,
  HAZE,
  LANDSCAPE_CAPACITY,
  mulberry32,
  planLandscape,
  PLAIN_DROP,
  PLAIN_TONE,
  RIVER,
  riverX,
  SNOWFALL_SPECKS,
  VALLEY_FAR,
  VALLEY_X0,
  VALLEY_X1,
} from './landscape-plan';
import { INK_FRAGMENT } from '../ink/ink.glsl';

const QUARTERS: Quarter[] = ['SPRING', 'SUMMER', 'AUTUMN', 'WINTER'];

describe('landscape plan', () => {
  it('fits the instanced mesh in every Quarter and in a Trial', () => {
    for (const q of [...QUARTERS, null]) {
      expect(planLandscape(q).length).toBeLessThanOrEqual(LANDSCAPE_CAPACITY);
    }
  });

  it('is deterministic', () => {
    expect(planLandscape('WINTER')).toEqual(planLandscape('WINTER'));
    const a = mulberry32(3);
    const b = mulberry32(3);
    expect([a(), a(), a()]).toEqual([b(), b(), b()]);
  });

  it('draws only valid ink: tone and alpha in 0..1, finite sizes', () => {
    for (const q of [...QUARTERS, null]) {
      for (const v of planLandscape(q)) {
        expect(v.tone).toBeGreaterThanOrEqual(0);
        expect(v.tone).toBeLessThanOrEqual(1);
        expect(v.alpha).toBeGreaterThan(0);
        expect(v.alpha).toBeLessThanOrEqual(1);
        for (const n of [v.x, v.y, v.z, v.w, v.h, v.d]) {
          expect(Number.isFinite(n)).toBe(true);
        }
        expect(v.w).toBeGreaterThan(0);
        expect(v.h).toBeGreaterThan(0);
        expect(v.d).toBeGreaterThan(0);
      }
    }
  });

  it('matches the Village ground tone, so the plain joins it seamlessly', () => {
    for (const q of QUARTERS) {
      expect(PLAIN_TONE[q]).toBe(QUARTER_LOOK[q].ground);
      const plain = planLandscape(q).find((v) => v.id === 'plain');
      expect(plain?.tone).toBe(QUARTER_LOOK[q].ground);
    }
  });

  it('keeps the plain just under the ground it joins', () => {
    const plain = planLandscape('SPRING').find((v) => v.id === 'plain');
    expect(plain!.y + plain!.h / 2).toBeCloseTo(-PLAIN_DROP, 6);
  });

  it('has ridges and ruined towers in every Quarter', () => {
    for (const q of [...QUARTERS, null]) {
      const ids = planLandscape(q).map((v) => v.id);
      expect(ids.filter((i) => i.startsWith('ridge-')).length).toBeGreaterThan(
        20,
      );
      expect(
        ids.filter((i) => i.endsWith('-shaft')).length,
      ).toBeGreaterThanOrEqual(3);
    }
  });

  it("shows each Quarter's weather and no other", () => {
    const marks = (q: Quarter | null, prefix: string) =>
      planLandscape(q).filter((v) => v.id.startsWith(prefix)).length;
    expect(marks('SPRING', 'thaw-')).toBeGreaterThan(0);
    expect(marks('SUMMER', 'light-')).toBeGreaterThan(0);
    expect(marks('AUTUMN', 'stubble-')).toBeGreaterThan(0);
    expect(marks('WINTER', 'snow-')).toBeGreaterThan(0);
    expect(marks('WINTER', 'flake-')).toBe(SNOWFALL_SPECKS);
    expect(marks('SPRING', 'snow-')).toBe(0);
    expect(marks('WINTER', 'thaw-')).toBe(0);
    expect(marks('AUTUMN', 'flake-')).toBe(0);
    // A Trial has no Quarter, so no weather marks.
    for (const p of ['thaw-', 'light-', 'stubble-', 'snow-', 'flake-']) {
      expect(marks(null, p)).toBe(0);
    }
  });

  it('thickens the haze with the Quarter', () => {
    const banks = (q: Quarter) =>
      planLandscape(q).filter((v) => v.id.startsWith('haze-'));
    for (const q of QUARTERS) {
      expect(banks(q).length).toBe(HAZE[q].banks * 3);
      expect(Math.max(...banks(q).map((v) => v.alpha))).toBeCloseTo(
        HAZE[q].alpha,
        6,
      );
    }
    expect(HAZE.SPRING.alpha).toBeGreaterThan(HAZE.SUMMER.alpha);
  });

  it('moves only the haze, the snowfall, the clouds and the grazing sheep', () => {
    for (const v of planLandscape('WINTER')) {
      const moves = v.sway > 0 || v.fall > 0;
      expect(moves).toBe(/^(haze|flake|cloud|sheep)-/.test(v.id));
    }
  });

  it('carries no game state: the plan depends on the Quarter alone', () => {
    // Same Quarter, same landscape: nothing else can be passed in.
    expect(planLandscape.length).toBe(1);
    expect(planLandscape('AUTUMN')).toEqual(planLandscape('AUTUMN'));
  });
});

describe('landscape cliffs, valley and crown (#139)', () => {
  const ids = (q: Quarter | null, prefix: string, crown = true) =>
    planLandscape(q, undefined, crown).filter((v) => v.id.startsWith(prefix));

  it('builds broken cliffs: stepped blocks, shelves and dark crevices', () => {
    for (const q of [...QUARTERS, null]) {
      for (const c of CLIFFS) {
        const blocks = ids(q, `${c.id}-b`);
        const shelves = ids(q, `${c.id}-t`);
        const crevices = ids(q, `${c.id}-c`);
        expect(blocks.length).toBeGreaterThanOrEqual(4);
        expect(shelves.length).toBe(blocks.length);
        expect(crevices.length).toBe(blocks.length);
        // Crevices are dark and thin; heights wander, so the top is stepped, not one slab.
        for (const v of crevices) {
          expect(v.tone).toBeLessThan(0.2);
          expect(v.d).toBeLessThan(0.1);
        }
        expect(new Set(blocks.map((b) => b.h.toFixed(2))).size).toBeGreaterThanOrEqual(2);
      }
    }
  });

  it('caps the cliffs with snow in Winter only', () => {
    const capped = (q: Quarter | null) =>
      ids(q, 'cliff-').some((v) => /-s\d+$/.test(v.id));
    expect(capped('WINTER')).toBe(true);
    for (const q of ['SPRING', 'SUMMER', 'AUTUMN', null] as const) {
      expect(capped(q)).toBe(false);
    }
  });

  it('opens a valley in the far ridges, with a lowland floor and a winding river', () => {
    for (const v of ids('AUTUMN', 'ridge-')) {
      expect(v.x < VALLEY_X0 - 1 || v.x > VALLEY_X1 - 1).toBe(true);
    }
    const floor = ids('AUTUMN', 'valley-floor')[0];
    expect(floor.z - floor.d / 2).toBeCloseTo(VALLEY_FAR, 6);
    const river = ids('AUTUMN', 'river-');
    expect(river.length).toBeGreaterThanOrEqual(20);
    // Thin boxes lying on the ground, dark against it, and the line winds (x is not monotonic).
    for (const v of river) {
      expect(v.h).toBeLessThan(0.05);
      expect(v.tone).toBeLessThan(PLAIN_TONE.AUTUMN);
    }
    const xs = river.map((v) => v.x);
    const turns = xs.filter(
      (x, i) => i > 1 && (x - xs[i - 1]) * (xs[i - 1] - xs[i - 2]) < 0,
    );
    expect(turns.length).toBeGreaterThan(1);
    // It widens toward the camera.
    expect(river[river.length - 1].d).toBeGreaterThan(river[0].d);
    expect(Number.isFinite(riverX(-60))).toBe(true);
  });

  it("makes the river follow the Quarter's weather: swollen in Spring, thin in Summer, frozen in Winter", () => {
    const width = (q: Quarter) => Math.max(...ids(q, 'river-').map((v) => v.d));
    expect(width('SPRING')).toBeGreaterThan(width('AUTUMN'));
    expect(width('SUMMER')).toBeLessThan(width('AUTUMN'));
    expect(RIVER.WINTER.tone).toBeGreaterThan(PLAIN_TONE.WINTER);
    expect(ids('WINTER', 'river-')[0].tone).toBe(RIVER.WINTER.tone);
  });

  it("puts a broken crown on the Tower's own column, dithering in from almost nothing", () => {
    const shaft = ids('SUMMER', 'crown-shaft-');
    const stubs = ids('SUMMER', 'crown-stub-');
    const slits = ids('SUMMER', 'crown-slit-');
    expect(shaft.length).toBeGreaterThanOrEqual(4);
    expect(stubs.length).toBeGreaterThanOrEqual(5);
    expect(slits.length).toBeGreaterThanOrEqual(3);
    for (const v of [...shaft, ...stubs, ...slits]) {
      expect(v.z).toBeGreaterThanOrEqual(CROWN_Z - 1);
    }
    // The foot is nearly nothing and it thickens upward: no hard edge where it begins.
    const alphas = shaft.map((v) => v.alpha);
    expect(alphas[0]).toBeLessThan(0.15);
    for (let i = 1; i < alphas.length; i++) {
      expect(alphas[i]).toBeGreaterThan(alphas[i - 1]);
    }
    // An uneven top: stubs of at least four different heights, one clearly the tallest.
    const heights = stubs.map((v) => v.h).sort((a, b) => a - b);
    expect(new Set(heights.map((h) => h.toFixed(2))).size).toBeGreaterThanOrEqual(4);
    expect(heights[heights.length - 1]).toBeGreaterThan(heights[0] * 4);
    for (const v of slits) expect(v.tone).toBeLessThan(0.2);
    // It stands over the shaft's column (x 0) and the stubs stay within the shaft's width.
    expect(shaft[0].x).toBe(0);
    expect(shaft[0].w).toBe(CROWN_WIDTH);
    for (const v of stubs) {
      expect(Math.abs(v.x) + v.w / 2).toBeLessThanOrEqual(CROWN_WIDTH / 2 + 1e-6);
    }
    expect(Math.min(...shaft.map((v) => v.y - v.h / 2))).toBeCloseTo(CROWN_BASE, 6);
    expect(Math.max(...shaft.map((v) => v.y + v.h / 2))).toBeCloseTo(
      CROWN_BASE + CROWN_SHAFT,
      6,
    );
  });

  it('draws thin bands of cloud across the crown and over the cliffs', () => {
    const bands = ids('AUTUMN', 'cloud-crown-');
    expect(bands.length).toBe(6);
    for (const v of bands) {
      expect(v.h).toBeLessThan(1);
      expect(v.sway).toBeGreaterThan(0);
      expect(v.alpha).toBeLessThan(1);
    }
    expect(ids('AUTUMN', 'cloud-low-').length).toBeGreaterThan(0);
  });

  it('keeps the crown art only: the same in every Quarter and in a Trial', () => {
    const art = (q: Quarter | null) =>
      planLandscape(q).filter((v) => /^(crown|cloud-crown)-/.test(v.id));
    for (const q of [...QUARTERS, null]) {
      expect(art(q)).toEqual(art('SPRING'));
    }
  });

  it('leaves the crown out where the Tower itself is drawn', () => {
    expect(ids('AUTUMN', 'crown-', false).length).toBe(0);
    expect(ids('AUTUMN', 'cloud-crown-', false).length).toBe(0);
    expect(ids('AUTUMN', 'cliff-', false).length).toBeGreaterThan(0);
  });

  it('thins the Tower body into dither by the top of the Village view, so the crown stands clear', () => {
    const alphas = Array.from({ length: TOWER_SEGMENTS }, (_, i) =>
      towerBodyAlpha(i),
    );
    expect(alphas[0]).toBe(1);
    for (let i = 1; i < alphas.length; i++) {
      expect(alphas[i]).toBeLessThanOrEqual(alphas[i - 1]);
    }
    expect(alphas[7]).toBeLessThanOrEqual(0.1);
    expect(alphas[TOWER_SEGMENTS - 1]).toBeLessThan(0.2);
  });
});

describe('landscape shader', () => {
  it('shares the ink fragment shader (ramp, dither and fog; no new colour)', () => {
    expect(LANDSCAPE_FRAGMENT).toBe(INK_FRAGMENT);
  });

  it('moves with a single time uniform, so a frozen clock freezes it', () => {
    expect(LANDSCAPE_VERTEX).toContain('uniform float uTime');
    expect(LANDSCAPE_VERTEX).toContain('attribute vec3 aMove');
  });
});

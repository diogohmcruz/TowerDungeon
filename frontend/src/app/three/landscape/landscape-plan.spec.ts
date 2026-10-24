import type { Quarter } from '../../interfaces/game-state';
import { QUARTER_LOOK } from '../village/village-layout';
import { LANDSCAPE_FRAGMENT, LANDSCAPE_VERTEX } from './landscape.glsl';
import {
  HAZE,
  LANDSCAPE_CAPACITY,
  mulberry32,
  planLandscape,
  PLAIN_DROP,
  PLAIN_TONE,
  SNOWFALL_SPECKS,
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

  it('moves only the haze and the snowfall', () => {
    for (const v of planLandscape('WINTER')) {
      const moves = v.sway > 0 || v.fall > 0;
      expect(moves).toBe(v.id.startsWith('haze-') || v.id.startsWith('flake-'));
    }
  });

  it('carries no game state: the plan depends on the Quarter alone', () => {
    // Same Quarter, same landscape: nothing else can be passed in.
    expect(planLandscape.length).toBe(1);
    expect(planLandscape('AUTUMN')).toEqual(planLandscape('AUTUMN'));
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

import { FRAME_WIDTH, Volume } from './village-layout';
import {
  barGauge,
  fillExtent,
  GAUGE_MIN,
  GAUGE_PIXEL,
  notchGauge,
  slitGauge,
} from './gauge-kit';

describe('gauge kit', () => {
  it('uses the Village pixel', () => {
    expect(GAUGE_PIXEL).toBeCloseTo(FRAME_WIDTH / 220, 10);
    expect(GAUGE_MIN).toBeCloseTo(2 * GAUGE_PIXEL, 10);
  });

  it('snaps a fill to whole virtual pixels and never hides a small stock', () => {
    expect(fillExtent(1.2, 0)).toBe(0);
    expect(fillExtent(1.2, 1)).toBeLessThanOrEqual(1.2);
    expect(fillExtent(1.2, 0.0001)).toBeCloseTo(GAUGE_PIXEL, 10);
    const half = fillExtent(1.2, 0.5) / GAUGE_PIXEL;
    expect(half).toBeCloseTo(Math.round(half), 8);
    expect(fillExtent(1.2, Number.NaN)).toBe(0);
    expect(fillExtent(1.2, 7)).toBeLessThanOrEqual(1.2);
  });

  it('draws a slit with a fill only when something is in it', () => {
    const empty: Volume[] = [];
    slitGauge(empty, 'x', { x: 0, bottom: 0, z: 0, height: 1, fill: 0 });
    expect(empty.map((v) => v.id)).toEqual(['x-slit']);

    const some: Volume[] = [];
    slitGauge(some, 'x', { x: 0, bottom: 0, z: 0, height: 1, fill: 0.5 });
    expect(some.map((v) => v.id)).toEqual(['x-slit', 'x-fill']);
    const [slit, fill] = some;
    expect(fill.y - fill.h / 2).toBeCloseTo(slit.y - slit.h / 2, 10);
    expect(fill.h).toBeLessThan(slit.h);
    expect(fill.z).toBeGreaterThan(slit.z);
  });

  it('never draws a part thinner than two virtual pixels', () => {
    const out: Volume[] = [];
    slitGauge(out, 's', { x: 0, bottom: 0, z: 0, height: 1, fill: 1, width: 0.01 });
    barGauge(out, 'b', { x: 0, bottom: 0, z: 0, length: 1, fill: 1, thickness: 0.01 });
    notchGauge(out, 'n', { x: 0, bottom: 0, z: 0, count: 3, lit: 1, size: 0.01 });
    for (const v of out) {
      expect(Math.min(v.w, v.h)).toBeGreaterThanOrEqual(GAUGE_MIN - 1e-9);
    }
  });

  it('grows a bar from its left end', () => {
    const out: Volume[] = [];
    barGauge(out, 'b', { x: 2, bottom: 0, z: 0, length: 1, fill: 0.5 });
    const [bar, fill] = out;
    expect(fill.x - fill.w / 2).toBeCloseTo(bar.x - bar.w / 2, 10);
    expect(fill.w).toBeCloseTo(0.5, 1);
  });

  it('lights notches from the left and tones the rest dark', () => {
    const out: Volume[] = [];
    notchGauge(out, 'n', { x: 0, bottom: 0, z: 0, count: 4, lit: 2 });
    expect(out.length).toBe(4);
    expect(out.map((v) => v.tone)).toEqual([1, 1, 0.12, 0.12]);
    expect(out[0].x).toBeLessThan(out[3].x);
    // Centred on x.
    expect((out[0].x + out[3].x) / 2).toBeCloseTo(0, 10);
  });

  it('clamps a lit count to the notches there are', () => {
    const out: Volume[] = [];
    notchGauge(out, 'n', { x: 0, bottom: 0, z: 0, count: 2, lit: 9 });
    expect(out.map((v) => v.tone)).toEqual([1, 1]);
  });
});

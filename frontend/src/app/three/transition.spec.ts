import {
  bayerThreshold,
  COVER_MS,
  covered,
  durationOf,
  frameAt,
  quantiseCover,
  RISE_MS,
  TOTAL_MS,
  WIPE_HOLD_MS,
} from './transition';

describe('the Set out transition', () => {
  it('takes about 1.6 s, and a Wipe holds on the empty stair first', () => {
    expect(TOTAL_MS).toBe(1600);
    expect(durationOf('set-out')).toBe(1600);
    expect(durationOf('extract')).toBe(1600);
    expect(durationOf('wipe')).toBe(1600 + WIPE_HOLD_MS);
  });

  it('on Set out covers the Village, swaps to the Tower, then rises and reveals', () => {
    expect(frameAt('set-out', 0)).toEqual(
      jasmine.objectContaining({ tower: false, cover: 0, lift: 0 }),
    );
    expect(frameAt('set-out', COVER_MS - 1).tower).toBeFalse();
    expect(frameAt('set-out', COVER_MS - 1).cover).toBeGreaterThan(0.99);
    const swap = frameAt('set-out', COVER_MS);
    expect(swap.tower).toBeTrue();
    expect(swap.cover).toBe(1);
    expect(frameAt('set-out', TOTAL_MS - 1).lift).toBeGreaterThan(0.99);
    expect(frameAt('set-out', TOTAL_MS).done).toBeTrue();
  });

  it('never overshoots: the camera lift stays within 0..1 and only climbs on Set out', () => {
    let last = 0;
    for (let t = 0; t <= TOTAL_MS; t += 10) {
      const lift = frameAt('set-out', t).lift;
      expect(lift).toBeGreaterThanOrEqual(0);
      expect(lift).toBeLessThanOrEqual(1);
      expect(lift).toBeGreaterThanOrEqual(last);
      last = lift;
    }
  });

  it('on Extraction drops the camera, then covers and swaps to the Village', () => {
    expect(frameAt('extract', 0)).toEqual(
      jasmine.objectContaining({ tower: true, cover: 0, lift: 1 }),
    );
    let last = 1;
    for (let t = 0; t <= RISE_MS; t += 10) {
      const lift = frameAt('extract', t).lift;
      expect(lift).toBeLessThanOrEqual(last);
      expect(lift).toBeGreaterThanOrEqual(0);
      last = lift;
    }
    expect(frameAt('extract', RISE_MS - 1).tower).toBeTrue();
    expect(frameAt('extract', RISE_MS).tower).toBeFalse();
    expect(frameAt('extract', RISE_MS).cover).toBe(1);
    expect(frameAt('extract', TOTAL_MS - 1).cover).toBeLessThan(0.05);
  });

  it('a Wipe keeps the empty stair: nothing moves or covers until the hold ends', () => {
    for (const t of [0, WIPE_HOLD_MS / 2, WIPE_HOLD_MS - 1]) {
      expect(frameAt('wipe', t)).toEqual(
        jasmine.objectContaining({ tower: true, cover: 0, lift: 1 }),
      );
    }
    expect(frameAt('wipe', WIPE_HOLD_MS + 600).lift).toBeLessThan(1);
    expect(frameAt('wipe', durationOf('wipe')).done).toBeTrue();
  });

  it('dissolves by the ordered Bayer threshold, so each step only adds covered pixels', () => {
    const count = (c: number) => {
      let n = 0;
      for (let y = 0; y < 4; y++)
        for (let x = 0; x < 4; x++) if (covered(x, y, c)) n++;
      return n;
    };
    expect(count(0)).toBe(0);
    expect(count(0.5)).toBe(8);
    expect(count(1)).toBe(16);
    for (let y = 0; y < 4; y++)
      for (let x = 0; x < 4; x++) {
        expect(covered(x, y, 0.5) && !covered(x, y, 0.75)).toBeFalse();
        expect(bayerThreshold(x, y)).toBe(bayerThreshold(x + 4, y + 8));
      }
  });

  it('quantises the cover to 16 dither steps', () => {
    expect(quantiseCover(0.51)).toBe(0.5);
    expect(quantiseCover(2)).toBe(1);
    expect(quantiseCover(-1)).toBe(0);
  });
});

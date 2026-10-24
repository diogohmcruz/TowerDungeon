import {
  autoPixelScale,
  figureBoost,
  MAX_FIGURE_BOOST,
  parseQuality,
  readPixelOverride,
  VIRTUAL_PIXEL_BUDGET,
  virtualPixels,
} from './pixel-scale';

describe('pixel scale', () => {
  const screens: [number, number, number][] = [
    [1280, 720, 2],
    [1920, 1080, 3],
    [2560, 1440, 4],
  ];

  for (const [w, h, expected] of screens) {
    it(`keeps ${w}x${h} near the 250k virtual-pixel budget`, () => {
      const scale = autoPixelScale(w, h);
      expect(scale).toBe(expected as 2 | 3 | 4);
      const ratio = virtualPixels(w, h, scale) / VIRTUAL_PIXEL_BUDGET;
      expect(ratio).toBeGreaterThan(0.75);
      expect(ratio).toBeLessThan(1.3);
    });
  }

  it('is finer on a small screen and never leaves 1 to 4', () => {
    expect(autoPixelScale(480, 640)).toBe(1);
    expect(autoPixelScale(7680, 4320)).toBe(4);
  });

  it('lets the dev override and the stored quality choose a fixed scale', () => {
    expect(readPixelOverride('')).toBeNull();
    expect(readPixelOverride('?pixelScale=1')).toBe(1);
    expect(readPixelOverride('?pixelScale=4')).toBe(4);
    expect(readPixelOverride('?pixelScale=5')).toBeNull();
    expect(readPixelOverride('?pixelScale=abc')).toBeNull();
    expect(parseQuality('3')).toBe(3);
    expect(parseQuality('auto')).toBe('auto');
    expect(parseQuality(null)).toBe('auto');
    expect(parseQuality('9')).toBe('auto');
  });

  it('draws figures bigger, not sharper, as the scale grows', () => {
    expect(figureBoost(1)).toBe(1);
    expect(figureBoost(2)).toBe(1);
    expect(figureBoost(3)).toBe(1.5);
    expect(figureBoost(4)).toBe(MAX_FIGURE_BOOST);
  });
});

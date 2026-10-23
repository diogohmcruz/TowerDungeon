/**
 * Screen pixels per virtual pixel. The canvas draws at `1 / PIXEL_SCALE` of its
 * CSS size and the browser scales it up with `image-rendering: pixelated`.
 * Recommended default is recorded in `docs/research/ink-spike.md`.
 */
const DEFAULT_PIXEL_SCALE = 2;

/** Dev tooling for the spike: `?pixelScale=1|2|3` overrides the default. */
export function readPixelScale(search: string): number {
  const raw = Number(new URLSearchParams(search).get('pixelScale'));
  return raw === 1 || raw === 2 || raw === 3 ? raw : DEFAULT_PIXEL_SCALE;
}

export const PIXEL_SCALE = readPixelScale(
  typeof location === 'undefined' ? '' : location.search,
);

/** The `dpr` handed to `ngt-canvas`: a plain number goes straight to `setPixelRatio`. */
export const INK_DPR = 1 / PIXEL_SCALE;

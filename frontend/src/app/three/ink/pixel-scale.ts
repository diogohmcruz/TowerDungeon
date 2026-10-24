/**
 * Screen pixels per virtual pixel. The canvas draws at `1 / scale` of its CSS
 * size and the browser scales it up with `image-rendering: pixelated`.
 *
 * The scene is a full-screen backdrop, so its cost follows the screen: the
 * scale is chosen automatically to keep the virtual pixels near the budget
 * measured in `docs/research/ink-spike.md` (3.7), and the quality setting (or
 * the dev tooling's `?pixelScale=`) can override it.
 */
export type PixelScale = 1 | 2 | 3 | 4;

/** Virtual pixels the scene should draw (ink spike 3.7). */
export const VIRTUAL_PIXEL_BUDGET = 250_000;

export const PIXEL_SCALES: readonly PixelScale[] = [1, 2, 3, 4];

/** What the player picks in Settings: the automatic scale, or a fixed one. */
export type Quality = 'auto' | PixelScale;

/** Virtual pixels of a `width` x `height` (CSS px) canvas at `scale`. */
export function virtualPixels(
  width: number,
  height: number,
  scale: number,
): number {
  return (width * height) / (scale * scale);
}

/**
 * The scale whose virtual pixel count is nearest the budget (by ratio, so a
 * double is as bad as a half). 1280 x 720 gets 2, 1920 x 1080 gets 3 and
 * 2560 x 1440 gets 4.
 */
export function autoPixelScale(width: number, height: number): PixelScale {
  let best: PixelScale = 2;
  let bestMiss = Infinity;
  for (const scale of PIXEL_SCALES) {
    const miss = Math.abs(
      Math.log(
        Math.max(1, virtualPixels(width, height, scale)) /
          VIRTUAL_PIXEL_BUDGET,
      ),
    );
    if (miss < bestMiss) {
      best = scale;
      bestMiss = miss;
    }
  }
  return best;
}

/** Figures keep their pixels 1:1 as the scale grows, so they grow bigger, not sharper (up to this much). */
export const MAX_FIGURE_BOOST = 1.5;

/** Figures were drawn for scale 2; a coarser scale draws them proportionally larger. */
export function figureBoost(scale: number): number {
  return Math.min(MAX_FIGURE_BOOST, Math.max(1, scale / 2));
}

/** Dev tooling: `?pixelScale=1|2|3|4` forces a scale; anything else is no override. */
export function readPixelOverride(search: string): PixelScale | null {
  const raw = Number(new URLSearchParams(search).get('pixelScale'));
  return raw === 1 || raw === 2 || raw === 3 || raw === 4 ? raw : null;
}

/** A stored quality, or `auto` for anything unreadable. */
export function parseQuality(raw: string | null): Quality {
  const n = Number(raw);
  return n === 1 || n === 2 || n === 3 || n === 4 ? n : 'auto';
}

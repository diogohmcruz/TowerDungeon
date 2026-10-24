import { ScreenRect } from '../../three/figures/figure-pick';

/** Width the floating card asks for, and the space it keeps from the screen's edge and from the figure. */
export const CARD_WIDTH = 280;
export const CARD_MARGIN = 12;
export const CARD_GAP = 16;
/** The least height worth showing; below it the card still sits fully on screen and scrolls. */
const CARD_MIN_HEIGHT = 120;

export interface CardPlacement {
  left: number;
  top: number;
  width: number;
  /** The card scrolls inside this much height, so it never runs off the bottom. */
  maxHeight: number;
}

/**
 * Where the floating card sits: beside the figure (to its right, or to its
 * left when the right has no room), level with the figure's top, and always
 * wholly on screen. With no room beside it on a narrow screen it overlaps the
 * figure instead of leaving the screen. Without an anchor the card is centred.
 */
export function placeCard(
  anchor: ScreenRect | null,
  viewportWidth: number,
  viewportHeight: number,
  /** The height the card wants; it is slid up to fit that much on screen, and scrolls past it. */
  minHeight = CARD_MIN_HEIGHT,
): CardPlacement {
  const width = Math.max(
    1,
    Math.min(CARD_WIDTH, viewportWidth - 2 * CARD_MARGIN),
  );
  const maxLeft = Math.max(CARD_MARGIN, viewportWidth - width - CARD_MARGIN);
  const clampLeft = (left: number) =>
    Math.min(maxLeft, Math.max(CARD_MARGIN, left));

  let left = clampLeft((viewportWidth - width) / 2);
  let top = CARD_MARGIN;
  if (anchor) {
    const right = anchor.right + CARD_GAP;
    const beside =
      right + width + CARD_MARGIN <= viewportWidth
        ? right
        : anchor.left - CARD_GAP - width;
    left = clampLeft(beside);
    top = anchor.top;
  } else {
    top = viewportHeight / 4;
  }
  const maxTop = Math.max(
    CARD_MARGIN,
    viewportHeight - minHeight - CARD_MARGIN,
  );
  top = Math.min(maxTop, Math.max(CARD_MARGIN, top));
  return {
    left,
    top,
    width,
    maxHeight: Math.max(1, viewportHeight - top - CARD_MARGIN),
  };
}

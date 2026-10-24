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
 * Where the floating card sits: beside the figure (on the side with more room),
 * level with the figure's top and slid up to show all its height, and always
 * wholly on screen. With no room beside it on a narrow screen it goes below or
 * above the figure, and overlaps it only when there is no room at all. Without an anchor the card is centred.
 */
export function placeCard(
  anchor: ScreenRect | null,
  viewportWidth: number,
  viewportHeight: number,
  /** The height the card wants; it is slid up to fit that much on screen, and scrolls past it. */
  minHeight = CARD_MIN_HEIGHT,
  /** The width the card asks for (a wide card, such as the Succession's). */
  wanted = CARD_WIDTH,
): CardPlacement {
  const width = Math.max(1, Math.min(wanted, viewportWidth - 2 * CARD_MARGIN));
  const maxLeft = Math.max(CARD_MARGIN, viewportWidth - width - CARD_MARGIN);
  const clampLeft = (left: number) =>
    Math.min(maxLeft, Math.max(CARD_MARGIN, left));

  let left = clampLeft((viewportWidth - width) / 2);
  let top = CARD_MARGIN;
  if (anchor) {
    // Beside the figure, on the side with more room (the card then covers the
    // least of the scene); only when neither side fits does it go above or below.
    const roomRight = viewportWidth - anchor.right - CARD_GAP - CARD_MARGIN;
    const roomLeft = anchor.left - CARD_GAP - CARD_MARGIN;
    const fitsRight = roomRight >= width;
    const fitsLeft = roomLeft >= width;
    top = anchor.top;
    if (fitsRight && (!fitsLeft || roomRight >= roomLeft)) {
      left = clampLeft(anchor.right + CARD_GAP);
    } else if (fitsLeft) {
      left = clampLeft(anchor.left - CARD_GAP - width);
    } else {
      const centre = (anchor.left + anchor.right) / 2 - width / 2;
      left = clampLeft(centre);
      const below = anchor.bottom + CARD_GAP;
      const above = anchor.top - CARD_GAP - minHeight;
      if (below + minHeight + CARD_MARGIN <= viewportHeight) top = below;
      else if (above >= CARD_MARGIN) top = above;
      else
        left = clampLeft(
          roomRight >= roomLeft
            ? anchor.right + CARD_GAP
            : anchor.left - CARD_GAP - width,
        );
    }
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

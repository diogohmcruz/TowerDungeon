import {
  CARD_GAP,
  CARD_MARGIN,
  CARD_WIDTH,
  CardPlacement,
  placeCard,
} from './card-placement';

function onScreen(p: CardPlacement, w: number, h: number): void {
  expect(p.left).toBeGreaterThanOrEqual(0);
  expect(p.top).toBeGreaterThanOrEqual(0);
  expect(p.left + p.width).toBeLessThanOrEqual(w);
  expect(p.top + p.maxHeight).toBeLessThanOrEqual(h);
}

describe('card placement', () => {
  const figure = { left: 600, top: 200, right: 680, bottom: 360 };

  it('sits to the right of the figure when there is room', () => {
    const p = placeCard(
      { left: 300, top: 200, right: 380, bottom: 360 },
      1280,
      720,
    );
    expect(p.left).toBe(380 + CARD_GAP);
    expect(p.top).toBe(200);
    expect(p.width).toBe(CARD_WIDTH);
  });

  it('moves to the left of the figure when the right has no room', () => {
    const p = placeCard(figure, 800, 720);
    expect(p.left).toBe(figure.left - CARD_GAP - CARD_WIDTH);
    onScreen(p, 800, 720);
  });

  it('stays on screen at every size, including a figure at the edges', () => {
    const anchors = [
      null,
      { left: -50, top: -20, right: 30, bottom: 100 },
      { left: 1250, top: 700, right: 1330, bottom: 860 },
      { left: 0, top: 0, right: 320, bottom: 240 },
    ];
    for (const [w, h] of [
      [1920, 1080],
      [1280, 720],
      [768, 600],
      [390, 700],
      [320, 480],
    ]) {
      for (const a of anchors) {
        const p = placeCard(a, w, h);
        onScreen(p, w, h);
        expect(p.left).toBeGreaterThanOrEqual(Math.min(CARD_MARGIN, w));
      }
    }
  });

  it('shrinks to the screen on a very narrow viewport', () => {
    const p = placeCard(figure, 200, 400);
    expect(p.width).toBe(200 - 2 * CARD_MARGIN);
    onScreen(p, 200, 400);
  });

  it('is centred sideways when there is no anchor yet', () => {
    const p = placeCard(null, 1000, 800);
    expect(p.left).toBe((1000 - CARD_WIDTH) / 2);
  });
});

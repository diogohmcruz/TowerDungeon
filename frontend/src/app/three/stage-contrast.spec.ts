import { RAMPS } from './ink/palette';
import { PANEL_OPACITY } from './stage-view';

/**
 * Text on a popover (a translucent panel straight over the scene) must stay
 * readable over the worst-case backdrop. Winter is the worst case: its snow is
 * the palest tone the Village draws (`VILLAGE_TONE.salt`, 0.95), the top of the
 * ink ramp. So every ramp slot of the theme is tried as the backdrop and the
 * weakest result is held to 7:1. (The list view is opaque and needs no check.)
 */
type Rgb = [number, number, number];

const MIN_CONTRAST = 7;

function parse(hex: string): Rgb {
  const h = hex.trim().replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as Rgb;
}

function over(top: Rgb, alpha: number, under: Rgb): Rgb {
  return top.map((c, i) => c * alpha + under[i] * (1 - alpha)) as Rgb;
}

function luminance([r, g, b]: Rgb): number {
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function contrast(a: Rgb, b: Rgb): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe('popover contrast over the Winter backdrop', () => {
  const root = document.documentElement;
  const saved = root.dataset['theme'];
  afterEach(() => {
    if (saved === undefined) delete root.dataset['theme'];
    else root.dataset['theme'] = saved;
  });

  function token(name: string): Rgb {
    return parse(getComputedStyle(root).getPropertyValue(name));
  }

  for (const [theme, ramp, dataTheme] of [
    ['night', RAMPS.night, 'dark'],
    ['day', RAMPS.day, 'light'],
  ] as const) {
    describe(theme, () => {
      beforeEach(() => (root.dataset['theme'] = dataTheme));

      /** Every layer a line of text can sit on: the panel's glass over each backdrop. */
      function grounds(): { name: string; ground: Rgb }[] {
        const panel = token('--panel');
        return [...ramp.entries()].map(([slot, hex]) => ({
          name: `popover over slot ${slot}`,
          ground: over(panel, PANEL_OPACITY, parse(hex)),
        }));
      }

      for (const ink of ['--text', '--bone']) {
        it(`keeps ${ink} at ${MIN_CONTRAST}:1 or better on every ground`, () => {
          const text = token(ink);
          for (const { name, ground } of grounds()) {
            expect(contrast(text, ground))
              .withContext(`${ink} on ${name}`)
              .toBeGreaterThanOrEqual(MIN_CONTRAST);
          }
        });
      }
    });
  }

  it('fails when a panel is too clear: the check can fail', () => {
    root.dataset['theme'] = 'dark';
    const text = token('--text');
    const clearPanel = over(token('--panel'), 0.3, parse(RAMPS.night[3]));
    expect(contrast(text, clearPanel)).toBeLessThan(MIN_CONTRAST);
  });
});

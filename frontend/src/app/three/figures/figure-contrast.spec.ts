import type { Quarter } from '../../interfaces/game-state';
import { SHOTS } from '../camera-shots';
import { FOG_DENSITY, TONE } from '../ink/ink-material';
import { RAMPS, type InkTheme } from '../ink/palette';
import { planLandscape } from '../landscape/landscape-plan';
import {
  figureTone,
  PARTY_EDGE_SLOT_DAY,
  PARTY_EDGE_SLOT_NIGHT,
  FigureRole,
  PARTY_DAY_SLOTS,
  ROLE_GUARDIAN,
  ROLE_PARTY,
  ROLE_RAMP,
} from './figure-tone';
import { planFigures } from './figure-layout';
import { FIGURE_FRAGMENT, FIGURE_VERTEX } from './figure.glsl';

/**
 * Issue #144: by day the Party was drawn in the detail slot, a pale mid-grey on the
 * pale ground and the mid-grey pillars, while the garrison stood out in ink. As in
 * #122 the guard works in ramp levels (0 to 3, what the ink shader works out before
 * it dithers): a figure reads against a ground when its body or its lit edge (the Party's, inside its
 * silhouette on its left side; the garrison's baked rim) is a clear
 * distance from the ground's level. Every figure is held to it in both themes, in the
 * battle view and in the closeup.
 */

/** The least distance, in ramp levels, a figure's body or edge keeps from a ground. */
const MIN_SEPARATION = 0.4;
/** Fixed face shade of the ink shader: tops catch the most ink, fronts less. */
const FACE = { top: 1, front: 0.72 } as const;
/** The landscape's fog in the Tower scene (`LANDSCAPE_FOG` of the scene graph). */
const LANDSCAPE_FOG = 0.012;
const LANDSCAPE_NEAR = 20;
const QUARTERS: ReadonlyArray<Quarter | null> = [
  null,
  'SPRING',
  'SUMMER',
  'AUTUMN',
  'WINTER',
];

function level(
  tone: number,
  face: number,
  fogDensity: number,
  depth: number,
): number {
  const d = depth * fogDensity;
  return Math.min(1, Math.max(0, tone * face * Math.exp(-d * d))) * 3;
}

interface Ground {
  name: string;
  level: number;
}

/** What can stand behind a figure from a camera `cameraZ` in front of the Tower. */
function grounds(theme: InkTheme, cameraZ: number): Ground[] {
  const out: Ground[] = [];
  const fog = FOG_DENSITY[theme];
  // The Floor strip's own texels (slots 1 to 3) at its two tones, and the open interior (slot 0).
  out.push({ name: 'open ground (slot 0)', level: 0 });
  for (const tone of [1, 0.8]) {
    for (const slot of [1, 2, 3]) {
      out.push({
        name: `strip slot ${slot} at tone ${tone}`,
        level: slot * tone,
      });
    }
  }
  // The slab boxes behind the fight: the backdrop wall and the pillars (tone 0.8 or 1, z -6 and -3).
  for (const [name, tone, z] of [
    ['backdrop wall', TONE.backdrop, -4],
    ['pillar z -6', 0.8, -6],
    ['pillar z -3', 1, -3],
  ] as const) {
    for (const face of ['top', 'front'] as const) {
      out.push({
        name: `${name} ${face}`,
        level: level(tone, FACE[face], fog, cameraZ - z),
      });
    }
  }
  const seen = new Set<string>();
  for (const quarter of QUARTERS) {
    for (const v of planLandscape(quarter, LANDSCAPE_NEAR)) {
      if (v.alpha < 1 || v.fall > 0 || v.sway > 0) continue;
      const depth = Math.max(1, cameraZ - v.z);
      for (const face of ['top', 'front'] as const) {
        const key = `${v.tone.toFixed(2)}:${face}:${Math.round(depth)}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({
          name: `${quarter ?? 'no Quarter'} ${v.id} ${face}`,
          level: level(v.tone, FACE[face], LANDSCAPE_FOG, depth),
        });
      }
    }
  }
  return out;
}

const ROLES: [string, FigureRole][] = [
  ['a Party member', ROLE_PARTY],
  ['a garrison member', ROLE_RAMP],
  ['a Guardian', ROLE_GUARDIAN],
];

describe('the figures against the grounds behind them (#144)', () => {
  for (const shot of ['battle', 'closeup'] as const) {
    for (const theme of ['night', 'day'] as const) {
      describe(`in the ${shot} view by ${theme}`, () => {
        const list = grounds(theme, SHOTS[shot].distance);

        it('has grounds to test against, from the open ground to full ink', () => {
          expect(list.length).toBeGreaterThan(20);
          expect(Math.min(...list.map((g) => g.level))).toBe(0);
          expect(Math.max(...list.map((g) => g.level))).toBeGreaterThan(2.5);
        });

        for (const [who, role] of ROLES) {
          it(`keeps ${who}, body or edge, clear of every ground`, () => {
            const { body, edge } = figureTone(role, theme);
            for (const ground of list) {
              const separation = Math.max(
                Math.abs(body - ground.level),
                Math.abs(edge - ground.level),
              );
              expect(separation)
                .withContext(
                  `${who} (body ${body}, edge ${edge}) on ${ground.name} (level ${ground.level.toFixed(2)})`,
                )
                .toBeGreaterThanOrEqual(MIN_SEPARATION);
            }
          });
        }

        it('shows the problem on the old tones: the Party in its detail slot with no lit edge matches a ground', () => {
          const old = figureTone(ROLE_PARTY, theme, {
            partyEdge: false,
            partyLift: false,
          });
          expect(old).toEqual({ body: 2, edge: 2 });
          const hidden = list.filter(
            (g) => Math.abs(old.body - g.level) < MIN_SEPARATION,
          );
          expect(hidden.length).toBeGreaterThan(0);
        });
      });
    }
  }

  it('draws the Party in the ink by day, as dark as the garrison, and unchanged by night', () => {
    expect(figureTone(ROLE_PARTY, 'day').body).toBe(
      figureTone(ROLE_RAMP, 'day').body,
    );
    expect(figureTone(ROLE_PARTY, 'night').body).toBe(2);
    // The day ramp is darkest at slot 3, so the body slot is the darkest the ramp has.
    expect(RAMPS.day[3] < RAMPS.day[0]).toBeTrue();
    expect(PARTY_DAY_SLOTS[0]).toBe(0);
    expect(PARTY_DAY_SLOTS.length).toBe(4);
  });

  it('keeps the Party apart from the garrison by more than tone: its lit edge is not the garrison rim slot', () => {
    for (const theme of ['night', 'day'] as const) {
      expect(figureTone(ROLE_PARTY, theme).edge).not.toBe(
        figureTone(ROLE_RAMP, theme).edge,
      );
    }
  });

  it('lights the Party\'s edge a full ramp step off its body, so any ground is clear of one of the two', () => {
    expect(PARTY_EDGE_SLOT_NIGHT).toBe(3);
    expect(PARTY_EDGE_SLOT_DAY).toBe(2);
    for (const theme of ['night', 'day'] as const) {
      const { body, edge } = figureTone(ROLE_PARTY, theme);
      // Two levels 2 * MIN_SEPARATION apart leave no ground within MIN_SEPARATION of both.
      expect(Math.abs(body - edge)).toBeGreaterThanOrEqual(2 * MIN_SEPARATION);
    }
  });

  it('draws no rim outside the Party\'s silhouette: its sprite quad is the garrison\'s size', () => {
    expect(FIGURE_VERTEX).not.toContain('RIM_PAD');
    expect(FIGURE_FRAGMENT).not.toContain('RIM_PAD');
    expect(FIGURE_FRAGMENT).toContain('if (v < 0.5) discard;');
  });

  it('marks every Party sprite as the Party and no garrison sprite', () => {
    const plan = planFigures({
      party: [
        { id: 1, typeKey: 'WARRIOR', guardian: false },
        { id: 2, typeKey: 'MAGE', guardian: false },
      ],
      enemies: [
        { id: 7, typeKey: 'SLIME', guardian: false },
        { id: 8, typeKey: 'CHIMERA', guardian: true },
      ],
      waves: [],
      surfaceY: 0,
      floorSurfaceY: (floor) => floor,
      viewAspect: 1.6,
      anchorFloor: 1,
    });
    const roleOf = (id: string) => plan.marks.find((m) => m.id === id)?.role;
    expect(roleOf('unit-1')).toBe(ROLE_PARTY);
    expect(roleOf('unit-2')).toBe(ROLE_PARTY);
    expect(roleOf('enemy-7')).toBe(ROLE_RAMP);
    expect(roleOf('enemy-8')).toBe(ROLE_GUARDIAN);
  });
});

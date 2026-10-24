import type { Quarter } from '../../interfaces/game-state';
import { expeditionVolumes } from '../expedition/expedition-objects';
import { FOG_DENSITY } from '../ink/ink-material';
import { planLandscape } from '../landscape/landscape-plan';
import { subjectOfVolume } from '../village/village-subjects';
import type { Volume } from '../village/village-layout';
import { TOWER_LAUNCH_Z, towerLaunchVolumes } from './launch-objects';
import {
  LAUNCH_CAPACITY,
  needsRim,
  RIM_DEPTH,
  RIM_PAD,
  rimOf,
  rimVolumes,
  RIM_SUFFIX,
} from './object-rim';

/**
 * Issue #122: the bell, the lamp, the Supplies post and the crates stand outside
 * the Floor strip, in front of the landscape and the twelve pillars. On Floors
 * where the horizon sits behind them their dither matched the ground's and they
 * vanished. The guard works in ramp levels (0 to 3, what the ink shader computes
 * before it dithers): a part reads against a ground when its level or its rim's
 * level (0, flat) is a clear distance from the ground's.
 */

/** The camera's distance from the Tower plane in the battle Shot (`SHOTS.battle`). */
const CAMERA_Z = 12.5;
/** The landscape's fog in the Tower scene (`LANDSCAPE_FOG` of the scene graph). */
const LANDSCAPE_FOG = 0.012;
/** The plain reaches this far toward the camera in the Tower scene. */
const LANDSCAPE_NEAR = 20;
/** Fixed face shade of the ink shader: tops catch the most ink, fronts less. */
const FACE = { top: 1, front: 0.72 } as const;
/** The least distance, in ramp levels, a part or its rim keeps from the ground behind it. */
const MIN_SEPARATION = 0.4;

/** The ramp level the ink shader works out for a box face (`INK_FRAGMENT`). */
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
  level: (theme: 'night' | 'day') => number;
}

const QUARTERS: ReadonlyArray<Quarter | null> = [
  null,
  'SPRING',
  'SUMMER',
  'AUTUMN',
  'WINTER',
];

/** What can stand behind the objects: the landscape's solid boxes and the twelve pillars. */
function grounds(): Ground[] {
  const out: Ground[] = [];
  const seen = new Set<string>();
  for (const quarter of QUARTERS) {
    for (const v of planLandscape(quarter, LANDSCAPE_NEAR)) {
      // Haze is translucent and snow falls: neither is a ground.
      if (v.alpha < 1 || v.fall > 0 || v.sway > 0) continue;
      const kind = v.id.replace(/-?\d+.*$/, '');
      const depth = Math.max(1, CAMERA_Z - v.z);
      for (const face of ['top', 'front'] as const) {
        const key = `${kind}:${v.tone.toFixed(2)}:${face}:${Math.round(depth)}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({
          name: `${quarter ?? 'no Quarter'} ${v.id} ${face}`,
          level: () => level(v.tone, FACE[face], LANDSCAPE_FOG, depth),
        });
      }
    }
  }
  // The twelve: tone 0.8 or 1, standing at z -6 and -3, in the scene's own fog.
  for (const [tone, z] of [
    [0.8, -6],
    [1, -3],
  ] as const) {
    out.push({
      name: `pillar z ${z}`,
      level: (theme) =>
        level(tone, FACE.front, FOG_DENSITY[theme], CAMERA_Z - z),
    });
  }
  return out;
}

/** Every launch and Expedition box in its busiest state: both controls out of use, everything stacked, all marks drawn. */
function worstCase(hovered: string | null = null): Volume[] {
  const surfaceY = 10;
  return [
    ...towerLaunchVolumes({
      surfaceY,
      bellUsable: false,
      recallUsable: false,
      hovered,
    }),
    ...expeditionVolumes({
      surfaceY,
      supplies: 150,
      maxSupplies: 200,
      credits: 5000,
      materials: 500,
      relics: 9,
      partyX: -1.9,
      hovered,
    }),
  ];
}

/** The parts that carry the picture, by the ink density they are given (the dark ones are silhouettes by design). */
function litParts(volumes: Volume[]): Volume[] {
  return volumes.filter((v) => needsRim(v) && v.tone >= 0.5);
}

describe('the rim of the launch and Expedition objects (#122)', () => {
  it('puts a flat tone-0 plate one pixel proud of every part, wholly behind it', () => {
    const volumes = worstCase();
    const rims = rimVolumes(volumes);
    const parts = volumes.filter(needsRim);
    expect(parts.length).toBeGreaterThan(20);
    expect(rims.length).toBe(parts.length);
    parts.forEach((part, i) => {
      const rim = rims[i];
      expect(rim.tone).toBe(0);
      expect(rim.alpha).toBe(1);
      expect(rim.w).toBeGreaterThanOrEqual(part.w + 2 * RIM_PAD - 1e-9);
      expect(rim.h).toBeGreaterThanOrEqual(part.h + 2 * RIM_PAD - 1e-9);
      expect(rim.x).toBe(part.x);
      expect(rim.y).toBe(part.y);
      // The plate's front face is behind the part's back face, so it never covers any part of the object.
      expect(rim.z + rim.d / 2).toBeLessThan(part.z - part.d / 2);
      expect(rim.d).toBe(RIM_DEPTH);
    });
  });

  it('leaves out the hit pads, the outlines and the decals', () => {
    const volumes = worstCase('BELL');
    const rimmed = new Set(rimVolumes(volumes).map((r) => r.id));
    for (const v of volumes) {
      const skipped =
        v.alpha === 0 || v.id.startsWith('outline-') || v.d < 0.05;
      expect(rimmed.has(`${v.id}${RIM_SUFFIX}`))
        .withContext(v.id)
        .toBe(!skipped);
    }
  });

  it('picks as the part it backs, and never as something else', () => {
    for (const part of worstCase().filter(needsRim)) {
      expect(subjectOfVolume(rimOf(part).id))
        .withContext(part.id)
        .toEqual(subjectOfVolume(part.id));
    }
  });

  it('fits the launch mesh with every mark drawn', () => {
    for (const hovered of [
      null,
      'BELL',
      'RECALL',
      'SUPPLIES',
      'CREDITS',
      'MATERIALS',
      'RELICS',
    ]) {
      const volumes = worstCase(hovered);
      expect(volumes.length + rimVolumes(volumes).length).toBeLessThanOrEqual(
        LAUNCH_CAPACITY,
      );
    }
  });

  for (const theme of ['night', 'day'] as const) {
    describe(`by ${theme}`, () => {
      const depth = CAMERA_Z - TOWER_LAUNCH_Z;
      const fog = FOG_DENSITY[theme];
      const parts = litParts(worstCase());

      it('keeps every lit part, with its rim, clear of every ground behind it', () => {
        for (const ground of grounds()) {
          const g = ground.level(theme);
          for (const part of parts) {
            const own = level(part.tone, FACE.front, fog, depth);
            const separation = Math.max(Math.abs(own - g), Math.abs(0 - g));
            expect(separation)
              .withContext(
                `${part.id} (level ${own.toFixed(2)}) on ${ground.name} (level ${g.toFixed(2)})`,
              )
              .toBeGreaterThanOrEqual(MIN_SEPARATION);
          }
        }
      });

      it('shows the problem without the rim: some part matches a ground it stands before', () => {
        // The check can fail: with the plate left out, timber on the far ridges is within the margin.
        let hidden = 0;
        for (const ground of grounds()) {
          const g = ground.level(theme);
          for (const part of parts) {
            const own = level(part.tone, FACE.front, fog, depth);
            if (Math.abs(own - g) < MIN_SEPARATION) hidden++;
          }
        }
        expect(hidden).toBeGreaterThan(0);
      });
    });
  }
});

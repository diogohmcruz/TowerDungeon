import {
  BASE_DOOR_X,
  BASE_GAP,
  BELL_SPOT,
  buildVillage,
  EMPTY_VILLAGE,
  MAX_VILLAGE_HEIGHT,
  STAIR,
  VILLAGE_TONE,
  villageBounds,
  Volume,
} from './village-layout';
import { BASE_DOOR_SUBJECT, subjectOfVolume } from './village-subjects';

/** The Tower's entrance (#138): a stair of shallow treads to a tall dark doorway between great blocks. */
describe('the Tower entrance', () => {
  const village = buildVillage({ ...EMPTY_VILLAGE, villageTierLevel: 4, ringLevel: 2, ringMax: 5 });
  const byId = (id: string): Volume => village.find((v) => v.id === id)!;
  const top = (v: Volume) => v.y + v.h / 2;

  it('climbs in many shallow treads, a party wide, to a landing before the door', () => {
    const treads = village.filter((v) => /^stair-\d+$/.test(v.id));
    expect(treads.length).toBeGreaterThanOrEqual(12);
    expect(STAIR.rise / STAIR.steps).toBeLessThanOrEqual(0.1);
    // Each tread stands higher than the one before, and the last meets the landing.
    for (let i = 1; i < treads.length; i++) {
      expect(top(byId(`stair-${i}`))).toBeGreaterThan(top(byId(`stair-${i - 1}`)));
    }
    expect(top(byId(`stair-${STAIR.steps - 1}`))).toBeCloseTo(STAIR.rise, 5);
    expect(STAIR.d).toBeLessThanOrEqual(0.5);
    const landing = byId('stair-landing');
    expect(top(landing)).toBeCloseTo(STAIR.rise - 0.05, 5);
    // The landing reaches the Tower's foot and lies under the door.
    expect(landing.z - landing.d / 2).toBeLessThanOrEqual(-0.2 + 1e-6);
    expect(Math.abs(landing.x - BASE_DOOR_X)).toBeLessThan(1e-6);
  });

  it('has a low parapet along the stair and the Tier lamps on it', () => {
    expect(village.filter((v) => v.id.startsWith('parapet-')).length).toBeGreaterThanOrEqual(STAIR.steps);
    for (let i = 0; i < 4; i++) {
      const post = byId(`lamp-post-${i}`);
      const parapet = byId(i === 3 ? 'parapet-landing' : `parapet-${[1, 4, 10][i]}`);
      expect(post.y - post.h / 2).toBeCloseTo(top(parapet), 5);
      expect(Math.abs(post.x - parapet.x)).toBeLessThanOrEqual(parapet.w / 2 + 1e-6);
    }
  });

  it('crosses a drop: dark ground lies on each side of the spine', () => {
    const dark = village.filter((v) => v.id.startsWith('chasm-'));
    expect(dark.length).toBeGreaterThanOrEqual(3);
    for (const v of dark) expect(v.tone).toBe(VILLAGE_TONE.dark);
  });

  it('keeps the bell and the Gate clear of the spine', () => {
    const foot = byId('stair-0');
    expect(BELL_SPOT.z).toBeGreaterThan(foot.z + foot.d / 2);
  });

  describe('the Base door', () => {
    const parts = village.filter((v) => v.id.startsWith('base-door:'));
    const part = (n: string) => parts.find((v) => v.id === `base-door:${n}`)!;

    it('is one subject, the Start Floor 0 choice, in every part', () => {
      expect(parts.length).toBeGreaterThanOrEqual(10);
      for (const v of parts) {
        expect(subjectOfVolume(v.id)).toEqual({ kind: 'base-door', id: BASE_DOOR_SUBJECT });
      }
    });

    it('is a tall, narrow, dark slot, taller than it is wide by three to one', () => {
      const slot = [part('opening'), part('opening-up')];
      const height = top(slot[1]) - (slot[0].y - slot[0].h / 2);
      expect(height / slot[0].w).toBeGreaterThanOrEqual(3);
      for (const v of slot) expect(v.tone).toBe(VILLAGE_TONE.dark);
      // The slot climbs past the slab into the Tower's face.
      expect(top(slot[1])).toBeGreaterThan(BASE_GAP + 1.25);
    });

    it('stands between two jambs of stacked great blocks, under a lintel and over a threshold', () => {
      for (const side of ['w', 'e']) {
        expect(parts.filter((v) => v.id.startsWith(`base-door:jamb-${side}-`)).length).toBeGreaterThanOrEqual(3);
      }
      const slot = part('opening');
      expect(part('jamb-w-0').x).toBeLessThan(slot.x);
      expect(part('jamb-e-0').x).toBeGreaterThan(slot.x);
      expect(part('lintel').y - part('lintel').h / 2).toBeGreaterThanOrEqual(top(part('opening-up')));
      expect(top(part('ledge'))).toBeCloseTo(STAIR.rise, 5);
      expect(top(part('ledge'))).toBeCloseTo(slot.y - slot.h / 2, 5);
    });

    it('is set in a face of great blocks with dark joints', () => {
      const blocks = village.filter((v) => v.id.startsWith('base-block-'));
      expect(blocks.length).toBeGreaterThanOrEqual(10);
      expect(byId('base-face').tone).toBe(VILLAGE_TONE.dark);
      for (const b of blocks) expect(b.tone).toBeGreaterThan(VILLAGE_TONE.tower);
    });
  });

  it('stays inside the Village bounds and height, and well under the instance budget', () => {
    const bounds = villageBounds(village);
    expect(bounds.maxY).toBeLessThan(MAX_VILLAGE_HEIGHT);
    // The frame's (#149) and the landmarks' (#150) scenery have their own budget (the capacity test in village-layout.spec).
    expect(village.filter((v) => v.group !== 'frame' && v.group !== 'landmark').length).toBeLessThan(400);
  });
});

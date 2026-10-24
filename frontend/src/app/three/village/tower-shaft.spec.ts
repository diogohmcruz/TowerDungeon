import {
  buildVillage,
  EMPTY_VILLAGE,
  MAX_VILLAGE_HEIGHT,
  SHAFT_COLUMNS,
  SHAFT_RELIEF,
  SHAFT_TIERS,
  SHORTCUT_MARK_ID,
  VILLAGE_TONE,
  villageBounds,
  Volume,
} from './village-layout';
import { covered, input, SIZES } from './ray-visibility';

/** The Tower's shaft (#137): a vast cylinder of fluted columns, dark recesses, stepped arches and brick bands. */
describe('the Tower shaft', () => {
  const village = buildVillage({ ...EMPTY_VILLAGE, villageTierLevel: 4 });
  const shaft = village.filter((v) => v.id.startsWith('shaft-'));
  const of = (re: RegExp) => shaft.filter((v) => re.test(v.id));
  const face = village.find((v) => v.id === 'tower-0')!;
  const faceZ = face.z + face.d / 2;

  it('is a tall fluted colonnade: lit columns on every tier, narrowing toward the edges like a cylinder', () => {
    const cols = of(/^shaft-0-col-\d+$/);
    expect(cols.length).toBe(SHAFT_COLUMNS);
    for (const c of cols) expect(c.tone).toBeGreaterThan(VILLAGE_TONE.stone);
    const mid = cols[Math.floor(SHAFT_COLUMNS / 2)];
    expect(cols[0].w).toBeLessThan(mid.w / 2);
    expect(cols[SHAFT_COLUMNS - 1].w).toBeLessThan(mid.w / 2);
    for (let t = 0; t < SHAFT_TIERS; t++) expect(of(new RegExp(`^shaft-${t}-col-`)).length).toBe(SHAFT_COLUMNS);
    expect(Math.max(...cols.map((c) => c.h))).toBeGreaterThan(1.2);
  });

  it('cuts a black recess between the columns, each topped by an arch of stepped boxes that narrows upward', () => {
    const recesses = of(/^shaft-0-recess-\d+$/);
    expect(recesses.length).toBe(SHAFT_COLUMNS - 1);
    for (const r of recesses) {
      expect(r.tone).toBe(VILLAGE_TONE.dark);
      const j = r.id.split('-')[3];
      const [a, b] = [`shaft-0-arch-${j}-0`, `shaft-0-arch-${j}-1`].map((id) => shaft.find((v) => v.id === id)!);
      expect(a.y - a.h / 2).toBeCloseTo(r.y + r.h / 2, 5);
      expect(b.y - b.h / 2).toBeCloseTo(a.y + a.h / 2, 5);
      expect(a.w).toBeLessThan(r.w);
      expect(b.w).toBeLessThan(a.w);
      expect(a.tone).toBe(VILLAGE_TONE.dark);
    }
  });

  it('rings the Tower with a band of brickwork on dark joints, a lit lip along its top, tier on tier', () => {
    for (let t = 0; t < SHAFT_TIERS; t++) {
      const joint = shaft.find((v) => v.id === `shaft-${t}-joint`)!;
      const bricks = of(new RegExp(`^shaft-${t}-brick-`));
      expect(joint.tone).toBe(VILLAGE_TONE.dark);
      expect(joint.w).toBeCloseTo(face.w, 5);
      expect(bricks.length).toBeGreaterThanOrEqual(7);
      for (const b of bricks) {
        expect(b.w).toBeLessThan(0.6);
        expect(b.x - b.w / 2).toBeGreaterThanOrEqual(joint.x - joint.w / 2 - 1e-6);
        expect(b.x + b.w / 2).toBeLessThanOrEqual(joint.x + joint.w / 2 + 1e-6);
      }
      expect(shaft.find((v) => v.id === `shaft-${t}-lip`)!.tone).toBe(VILLAGE_TONE.lit);
      if (t > 0) expect(joint.y).toBeGreaterThan(shaft.find((v) => v.id === `shaft-${t - 1}-joint`)!.y);
    }
  });

  it('is flat relief: nothing stands more than a hair before the Tower face, so the Shortcut marks stay in front', () => {
    for (const v of shaft) {
      expect(v.z + v.d / 2).toBeLessThanOrEqual(faceZ + SHAFT_RELIEF + 1e-9);
      expect(v.z - v.d / 2).toBeGreaterThanOrEqual(faceZ - 1e-9);
    }
    const marked = buildVillage({ ...EMPTY_VILLAGE, shortcuts: [{ id: 'a', floor: 3 }, { id: 'b', floor: 20 }] });
    const marks = marked.filter((v) => v.id.startsWith(SHORTCUT_MARK_ID));
    expect(marks.length).toBeGreaterThan(0);
    const shaftFront = Math.max(...shaft.map((v) => v.z + v.d / 2));
    for (const m of marks) expect(m.z + m.d / 2).toBeGreaterThan(shaftFront);
  });

  it('thins into the dither with the Tower: opacity never rises with height and ends faint', () => {
    const sorted = [...shaft].sort((a, b) => a.y - b.y);
    for (let i = 1; i < sorted.length; i++) expect(sorted[i].alpha).toBeLessThanOrEqual(sorted[i - 1].alpha + 1e-9);
    expect(sorted[0].alpha).toBeGreaterThan(0.85);
    expect(sorted[sorted.length - 1].alpha).toBeLessThan(0.25);
  });

  it('stays inside the Tower and out of the Village: bounds, height and the instance budget hold', () => {
    for (const v of shaft) {
      expect(v.group).toBe('tower');
      expect(Math.abs(v.x) + v.w / 2).toBeLessThanOrEqual(face.w / 2 + 1e-6);
    }
    expect(shaft.length).toBeLessThanOrEqual(90);
    expect(villageBounds(village).maxY).toBeLessThanOrEqual(MAX_VILLAGE_HEIGHT);
    expect(new Set(village.map((v) => v.id)).size).toBe(village.length);
  });

  it('never hides a Shortcut mark: the rays that reach a mark meet nothing in front, far view and closeup', () => {
    const marks = { ...input(4, {}), shortcuts: [{ id: 'a', floor: 3 }, { id: 'b', floor: 8 }, { id: 'c', floor: 15 }] };
    for (const [w, h] of SIZES) {
      for (const closeness of [0, 1]) {
        const r = covered(buildVillage(marks), (v: Volume) => v.id.startsWith('shortcut-'), closeness, w, h);
        expect(r.reach).toBeGreaterThan(10);
        // A sliver of a doorway's own back stands inside the relief layer; the front is clear.
        expect(r.blocked / r.reach).withContext(`blocked by ${r.blockers.join(', ')}`).toBeLessThanOrEqual(0.05);
      }
    }
  });
});

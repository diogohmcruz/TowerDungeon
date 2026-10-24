import type { GameState } from '../../interfaces/game-state';
import { captionFor, captionText } from '../village/village-captions';
import {
  subjectOfVolume,
  outlineVolumes,
} from '../village/village-subjects';
import { VILLAGE_TONE } from '../village/village-layout';
import {
  casketCount,
  coinColumns,
  CRATE_KINDS,
  crateCount,
  heapCourses,
  CrateKind,
  expeditionCaption,
  finishSubject,
  expeditionVolumes,
  groupAt,
  groupSubject,
  isGroupSubject,
  PARTY,
  suppliesFill,
  unionRects,
} from './expedition-objects';

const unit = (id: number, type: string, hp: number, max: number) =>
  ({ id, name: type, currentHealth: hp, maxHealth: max, stats: { type } }) as any;
const enemy = (id: number, type: string, hp: number, max: number, boss = false) =>
  ({ ...unit(id, type, hp, max), boss }) as any;

function state(over: Partial<GameState> = {}): GameState {
  return {
    expeditionActive: true,
    supplies: 120,
    maxSupplies: 200,
    carriedCredits: 1234,
    carriedLoot: { MATERIALS: 45, RELICS: 0 },
    unitsOnTower: {
      ARCHER: [unit(1, 'ARCHER', 7, 10), unit(2, 'ARCHER', 10, 10)],
      TANK: [unit(3, 'TANK', 20, 30)],
    },
    tower: {
      currentFloor: 4,
      floors: {
        4: {
          enemies: [
            enemy(10, 'SLIME', 3, 5),
            enemy(11, 'SLIME', 5, 5),
            enemy(12, 'STONE_GOLEM', 40, 50, true),
          ],
        },
      },
    },
    ...over,
  } as unknown as GameState;
}

const input = {
  surfaceY: 10,
  supplies: 120,
  maxSupplies: 200,
  credits: 340,
  materials: 45,
  relics: 1,
  partyX: -1.9,
};

describe('Expedition objects', () => {
  it('draws the Supplies post with a gauge whose fill follows the share left', () => {
    const volumes = expeditionVolumes(input);
    const fill = (v: typeof volumes) =>
      v.find((x) => x.id === 'supplies-gauge-fill');
    const slit = volumes.find((x) => x.id === 'supplies-gauge-slit')!;
    expect(fill(volumes)!.h).toBeGreaterThan(0);
    expect(fill(volumes)!.h).toBeLessThan(slit.h);
    expect(fill(expeditionVolumes({ ...input, supplies: 0 }))).toBeUndefined();
    expect(fill(expeditionVolumes({ ...input, supplies: 200 }))!.h).toBeCloseTo(slit.h, 5);
    expect(suppliesFill(5, 0)).toBe(0);
    expect(suppliesFill(500, 200)).toBe(1);
  });

  it('stands the post beyond the recall lamp and the crates at the Party', () => {
    const volumes = expeditionVolumes(input);
    const post = volumes.find((v) => v.id === 'supplies-post')!;
    expect(post.x).toBeGreaterThan(2.4);
    const crates = CRATE_KINDS.map(
      (k) => volumes.find((v) => v.id === `carried-${k.toLowerCase()}-base`)!.x,
    );
    expect(crates[0]).toBeLessThan(crates[1]);
    expect(crates[1]).toBeCloseTo(-1.9, 5);
    expect(crates[2]).toBeGreaterThan(crates[1]);
  });

  const count = (v: ReturnType<typeof expeditionVolumes>, prefix: string) =>
    v.filter((x) => x.id.startsWith(prefix) && x.alpha > 0).length;

  it('shows a larger load as a larger pile and nothing for nothing', () => {
    const kinds: CrateKind[] = ['CREDITS', 'MATERIALS', 'RELICS'];
    for (const kind of kinds) {
      expect(crateCount(kind, 0)).toBe(0);
      expect(crateCount(kind, 1)).toBe(1);
      expect(crateCount(kind, 1e6)).toBe(3);
    }
    const credits = (n: number) => count(expeditionVolumes({ ...input, credits: n }), 'carried-credits-coin-');
    const blocks = (n: number) => count(expeditionVolumes({ ...input, materials: n }), 'carried-materials-block-');
    const pieces = (n: number) => count(expeditionVolumes({ ...input, relics: n }), 'carried-relics-casket-');
    // The thresholds are unchanged: 100 and 1000 Credits, 20 and 200 Materials, 2 and 5 Relics.
    expect([0, 1, 99, 100, 999, 1000].map(credits)).toEqual([0, 3, 3, 7, 7, 11]);
    expect([0, 1, 19, 20, 199, 200].map(blocks)).toEqual([0, 3, 3, 5, 5, 6]);
    expect([0, 1, 1, 2, 4, 5].map(pieces)).toEqual([0, 4, 4, 8, 8, 12]);
  });

  it('grows each pile through its tiers without ever leaving its shelf', () => {
    expect(coinColumns(0)).toEqual([]);
    expect(coinColumns(3).length).toBe(3);
    expect(heapCourses(3)).toEqual([3, 2, 1]);
    expect(casketCount(3)).toBe(3);
    for (const tier of [1, 2, 3]) {
      expect(coinColumns(tier).reduce((a, b) => a + b, 0)).toBeGreaterThan(
        coinColumns(tier - 1).reduce((a, b) => a + b, 0),
      );
      expect(heapCourses(tier).reduce((a, b) => a + b, 0)).toBeGreaterThan(
        heapCourses(tier - 1).reduce((a, b) => a + b, 0),
      );
    }
    const volumes = expeditionVolumes({ ...input, credits: 1e6, materials: 1e6, relics: 1e6 });
    for (const kind of CRATE_KINDS) {
      const id = `carried-${kind.toLowerCase()}`;
      const shelf = volumes.find((v) => v.id === `${id}-base`)!;
      const hit = volumes.find((v) => v.id === `${id}-hit`)!;
      for (const v of volumes.filter((x) => x.id.startsWith(`${id}-`) && x.alpha > 0 && x !== shelf)) {
        expect(v.x - v.w / 2).withContext(v.id).toBeGreaterThanOrEqual(shelf.x - shelf.w / 2 - 1e-9);
        expect(v.x + v.w / 2).withContext(v.id).toBeLessThanOrEqual(shelf.x + shelf.w / 2 + 1e-9);
        // Nothing stands taller than the pick pad, so the hover reaches the whole pile.
        expect(v.y + v.h / 2).withContext(v.id).toBeLessThanOrEqual(hit.y + hit.h / 2 + 1e-9);
        expect(v.y - v.h / 2).withContext(v.id).toBeGreaterThanOrEqual(shelf.y - shelf.h / 2 - 1e-9);
      }
    }
  });

  it('tells the three kinds apart by shape: coin columns, a tapering heap, caskets with a lit slit', () => {
    const volumes = expeditionVolumes({ ...input, credits: 5000, materials: 500, relics: 9 });
    const of = (k: string) => volumes.filter((v) => v.id.startsWith(`carried-${k}-`) && v.alpha > 0);
    const coins = of('credits').filter((v) => v.id.includes('-coin-'));
    const blocks = of('materials').filter((v) => v.id.includes('-block-'));
    // Coins are flat discs: wider than tall, in columns of the same footprint.
    expect(coins.every((v) => v.w > 2 * v.h)).toBeTrue();
    expect(new Set(coins.map((v) => v.id.split('-')[3])).size).toBe(3);
    // The heap tapers: every course holds fewer blocks than the one under it.
    const perCourse = heapCourses(3);
    expect(perCourse.every((n, i) => i === 0 || n < perCourse[i - 1])).toBeTrue();
    expect(blocks.length).toBe(perCourse.reduce((a, b) => a + b, 0));
    // A casket is taller than a coin and carries a lit slit the other two kinds have not.
    // A slit is cut into a face: a thin decal at the lit density.
    const lit = (k: string) => of(k).filter((v) => v.tone === VILLAGE_TONE.lit && v.d < 0.05);
    expect(lit('relics').length).toBe(3);
    expect(lit('credits').length).toBe(0);
    expect(lit('materials').length).toBe(0);
  });

  it('draws only the Village\'s own ink densities', () => {
    const allowed = new Set<number>(Object.values(VILLAGE_TONE));
    const volumes = expeditionVolumes({ ...input, credits: 5000, materials: 500, relics: 9 });
    for (const v of volumes.filter((x) => x.id.startsWith('carried-'))) {
      expect(allowed.has(v.tone)).withContext(`${v.id} tone ${v.tone}`).toBeTrue();
    }
  });

  it('maps every volume to an expedition subject, and the hit boxes are invisible', () => {
    const volumes = expeditionVolumes(input);
    for (const v of volumes) {
      expect(subjectOfVolume(v.id)?.kind).toBe('expedition');
    }
    expect(subjectOfVolume('supplies-post')?.id).toBe('SUPPLIES');
    expect(subjectOfVolume('carried-relics-hit')?.id).toBe('RELICS');
    expect(volumes.filter((v) => v.id.endsWith('-hit')).every((v) => v.alpha === 0)).toBe(true);
  });

  it('frames a hovered object with a line that hugs what is drawn, not the hit box', () => {
    const plain = expeditionVolumes(input);
    const hovered = expeditionVolumes({ ...input, hovered: 'SUPPLIES' });
    const lines = hovered.filter((v) => v.id.startsWith('outline-SUPPLIES:hover:expedition'));
    expect(lines.length).toBe(4);
    expect(hovered.length).toBe(plain.length + 4);
    const frame = outlineVolumes(plain, 'SUPPLIES', 1, 'hover');
    const top = frame.find((v) => v.id.endsWith('-n'))!;
    const post = plain.find((v) => v.id === 'supplies-post')!;
    expect(top.y).toBeLessThan(post.y + post.h / 2 + 0.2);
    expect(subjectOfVolume(lines[0].id)).toEqual({ kind: 'expedition', id: 'SUPPLIES' });
  });
});

describe('Expedition captions', () => {
  it('states Supplies exactly', () => {
    const c = expeditionCaption({ kind: 'expedition', id: 'SUPPLIES' }, state())!;
    expect(captionText(c)).toContain('Supplies 120 of 200.');
  });

  it('states each load carried exactly', () => {
    const say = (id: string) =>
      captionText(expeditionCaption({ kind: 'expedition', id }, state())!);
    expect(say('CREDITS')).toContain('Credits carried 1,234.');
    expect(say('MATERIALS')).toContain('Materials carried 45.');
    expect(say('RELICS')).toContain('Relics carried 0.');
  });

  it('states the Party size and health, by type', () => {
    const text = captionText(expeditionCaption(PARTY, state())!);
    expect(text).toContain('3 Units. Health 37 of 50.');
    expect(text).toContain('Archer 2, Tank 1');
  });

  it('states an enemy group count and health, and names a Guardian', () => {
    const slimes = captionText(expeditionCaption(groupSubject('SLIME'), state())!);
    expect(slimes).toContain('Slime ×2.');
    expect(slimes).toContain('2 enemies. Health 8 of 10.');
    const golem = captionText(expeditionCaption(groupSubject('STONE_GOLEM'), state())!);
    expect(golem).toContain('Stone golem ×1.');
    expect(golem).toContain('1 enemy.');
    expect(golem).toContain('Guardian');
  });

  it('says nothing when no Expedition is out or the group is gone', () => {
    expect(expeditionCaption(PARTY, state({ expeditionActive: false }))).toBeNull();
    expect(expeditionCaption(groupSubject('CHIMERA'), state())).toBeNull();
  });

  it('is reached through the shared caption builder', () => {
    const c = captionFor({ kind: 'expedition', id: 'SUPPLIES' }, state());
    expect(c?.name).toBe('The Supplies post');
  });
});

describe('Finish Floor mark caption', () => {
  it('says what the mark is, in words, for a listed Floor', () => {
    const c = expeditionCaption(finishSubject(30), state({ pillarMarks: [30] }))!;
    expect(captionText(c)).toContain('The Finish Floor of a Race you have run');
    expect(isGroupSubject(finishSubject(30))).toBeTrue();
  });

  it('says nothing for a Floor not listed', () => {
    expect(expeditionCaption(finishSubject(31), state({ pillarMarks: [30] }))).toBeNull();
  });
});

describe('Hover on groups', () => {
  const rect = (left: number, top: number, right: number, bottom: number) => ({
    left,
    top,
    right,
    bottom,
  });
  const slime = groupSubject('SLIME');
  const groups = unionRects([
    { subject: PARTY, rect: rect(10, 100, 30, 130) },
    { subject: PARTY, rect: rect(40, 90, 60, 130) },
    { subject: slime, rect: rect(200, 100, 220, 130) },
  ]);

  it('joins a group figures into one rectangle', () => {
    expect(groups.length).toBe(2);
    expect(groups[0].rect).toEqual(rect(10, 90, 60, 130));
  });

  it('finds the group under the pointer, between its figures as well', () => {
    expect(groupAt(groups, 35, 110)).toBe(PARTY);
    expect(groupAt(groups, 210, 115)).toBe(slime);
  });

  it('finds nothing in the empty room between groups', () => {
    expect(groupAt(groups, 120, 110)).toBeNull();
    expect(groupAt(groups, 35, 10)).toBeNull();
  });

  it('knows a group subject from an object', () => {
    expect(isGroupSubject(PARTY)).toBe(true);
    expect(isGroupSubject(slime)).toBe(true);
    expect(isGroupSubject({ kind: 'expedition', id: 'SUPPLIES' })).toBe(false);
    expect(isGroupSubject({ kind: 'bell', id: 'BELL' })).toBe(false);
    expect(isGroupSubject(null)).toBe(false);
  });
});

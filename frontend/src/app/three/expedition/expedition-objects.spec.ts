import type { GameState } from '../../interfaces/game-state';
import { captionFor, captionText } from '../village/village-captions';
import {
  subjectOfVolume,
  outlineVolumes,
} from '../village/village-subjects';
import {
  CRATE_KINDS,
  crateCount,
  CrateKind,
  expeditionCaption,
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

  it('stacks more crates for a larger load and none for nothing', () => {
    const kinds: CrateKind[] = ['CREDITS', 'MATERIALS', 'RELICS'];
    for (const kind of kinds) {
      expect(crateCount(kind, 0)).toBe(0);
      expect(crateCount(kind, 1)).toBe(1);
      expect(crateCount(kind, 1e6)).toBe(3);
    }
    const boxes = (v: ReturnType<typeof expeditionVolumes>, k: string) =>
      v.filter((x) => x.id.startsWith(`carried-${k}-box-`)).length;
    expect(boxes(expeditionVolumes({ ...input, credits: 0 }), 'credits')).toBe(0);
    expect(boxes(expeditionVolumes({ ...input, credits: 5 }), 'credits')).toBe(1);
    expect(boxes(expeditionVolumes({ ...input, credits: 5000 }), 'credits')).toBe(3);
  });

  it('tells the crates apart by shape: Credits one slat, Materials two, Relics three', () => {
    const volumes = expeditionVolumes({ ...input, credits: 5, materials: 5, relics: 5 });
    const slats = (k: string) =>
      volumes.filter((x) => x.id.startsWith(`carried-${k}-slat-0-`)).length;
    expect([slats('credits'), slats('materials'), slats('relics')]).toEqual([1, 2, 3]);
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

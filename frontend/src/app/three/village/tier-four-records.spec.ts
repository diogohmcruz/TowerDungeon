import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState, TollForecast } from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import { buildingViews } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import { PILLAR_ID, PILLAR_PREFIX } from './reference-pillar';
import { pillarInput } from './reference-pillar-building';
import {
  fallenLine,
  RECORDS_TIER,
  STONE,
  STONE_ID,
  STONE_OF_COUNT,
  WALL,
  WALL_ID,
  WALL_OF_NAMES,
  WALL_SLOTS,
} from './tier-four-records';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE, FRAME_WIDTH, villageBounds } from './village-layout';
import { ofSubject, subjectOfVolume } from './village-subjects';

const forecast = (over: Partial<TollForecast> = {}): TollForecast => ({
  toll: {
    foodMarks: 2,
    materialsMarks: 3,
    creditsMarks: 1.5,
    relicsMarks: 0.5,
    expeditionsMarks: 4,
    ascentMarks: 10,
    guardianMarks: 3,
    marks: 24,
    toll: 24,
    newGround: 2,
    legacy: 2,
    residueNext: 6,
  },
  pointsDivisor: 9,
  nextLegacyAt: 27,
  exodusMinimum: 3,
  canExodus: false,
  ...over,
});

const FALLEN = [
  { name: 'Aldric', type: 'WARRIOR', floor: 12 },
  { name: 'Brisa', type: 'DRAGON_SPORES', floor: 0 },
  { name: 'Tomas', type: 'ARCHER', floor: 9 },
];

describe('the Wall of Names and the Stone of Count', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };
  const shown = () => buildingViews(BUILDINGS, state);
  const view = (id: string) => shown().find((b) => b.id === id);
  const atTier = (n: number) => load({ villageTierLevel: n });

  beforeEach(() => load({ wallOfNames: FALLEN, forecast: forecast(), highestFloor: 37 }));

  describe('below Tier IV', () => {
    for (const tier of [1, 2, 3]) {
      it(`neither stands nor is listed at Tier ${tier}, and the pillar keeps the Toll and Exodus`, () => {
        atTier(tier);
        expect(view(WALL_ID)).toBeUndefined();
        expect(view(STONE_ID)).toBeUndefined();
        const pillar = view(PILLAR_ID)!;
        expect(pillar.gauges.map((g) => g.label)).toEqual(['Highest Floor', 'Milestones', 'Toll']);
        expect(pillar.actions.map((a) => a.id)).toEqual(['exodus']);
        expect(pillar.lines.some((l) => l.id.startsWith('toll-'))).toBeTrue();
      });

      it(`draws neither at Tier ${tier} and the pillar keeps its Toll bar`, () => {
        const ids = buildVillage({
          ...EMPTY_VILLAGE,
          villageTierLevel: tier,
          pillar: pillarInput(state),
          fallenCount: 3,
        }).map((v) => v.id);
        expect(ids.some((i) => i.startsWith('names-') || i.startsWith('count-'))).toBeFalse();
        expect(ids).toContain(`${PILLAR_PREFIX}-toll-bar`);
      });
    }
  });

  describe('at Tier IV', () => {
    beforeEach(() => atTier(RECORDS_TIER));

    it('registers both, and lists them', () => {
      expect(buildingDefinition(WALL_ID)).toBe(WALL_OF_NAMES);
      expect(buildingDefinition(STONE_ID)).toBe(STONE_OF_COUNT);
      expect(view(WALL_ID)!.title).toBe('The Wall of Names');
      expect(view(STONE_ID)!.title).toBe('The Stone of Count');
    });

    it('lists the fallen on the Wall, newest first, with Type and where they fell', () => {
      const section = view(WALL_ID)!.sections[0];
      expect(section.lines.map((l) => l.text)).toEqual([
        'Tomas, Archer, fell on Floor 9.',
        'Brisa, Dragon spores, starved at home.',
        'Aldric, Warrior, fell on Floor 12.',
      ]);
      expect(section.lines.every((l) => l.mark === '-')).toBeTrue();
      expect(view(WALL_ID)!.actions).toEqual([]);
      expect(view(WALL_ID)!.gauges[0]).toEqual(
        jasmine.objectContaining({ label: 'Names', figure: '3' }),
      );
    });

    it('says so when no name is carved', () => {
      load({ wallOfNames: [] });
      const section = view(WALL_ID)!.sections[0];
      expect(section.lines).toEqual([]);
      expect(section.empty).toBe('No name is carved yet.');
      expect(fallenLine(FALLEN[0]).loss).toBeTrue();
    });

    it('leaves the Study with only the Ledger', () => {
      const study = buildingDefinition('SCRIBE')!;
      expect((study.sections ?? []).map((x) => x.id)).toEqual(['ledger']);
    });

    it('moves the Toll, its lines and Exodus to the Stone, with its words', () => {
      const stone = view(STONE_ID)!;
      expect(stone.gauges.map((g) => [g.label, g.figure])).toEqual([['Toll', '24.0']]);
      expect(stone.gauges[0].caption).toBeTrue();
      expect(stone.lines[0].label).toBe('The Tower keeps count.');
      expect(stone.lines.filter((l) => l.id.startsWith('toll-')).length).toBeGreaterThan(5);
      expect(stone.actions.map((a) => a.id)).toEqual(['exodus']);
      expect(stone.actions[0].reason).toBe('Exodus opens at 3 Legacy.');
    });

    it('keeps only the Highest Floor and the Milestones on the pillar', () => {
      const pillar = view(PILLAR_ID)!;
      expect(pillar.gauges.map((g) => g.label)).toEqual(['Highest Floor', 'Milestones']);
      expect(pillar.actions).toEqual([]);
      expect(pillar.lines.some((l) => l.id.startsWith('toll-'))).toBeFalse();
    });

    it('keeps Exodus confirmed, in words, from the Stone', () => {
      load({ forecast: forecast({ canExodus: true }) });
      const a = view(STONE_ID)!.actions[0];
      expect(a.usable).toBeTrue();
      expect(a.confirm?.confirmLabel).toBe('Leave');
      const send = jasmine.createSpy('send');
      STONE_OF_COUNT.actions[0].run({ send }, state);
      expect(send).toHaveBeenCalledOnceWith(GameAction.EXODUS, null);
    });

    it('captions the Stone with the Toll and the pillar without it', () => {
      const stone = captionText(captionFor({ kind: 'building', id: STONE_ID }, state)!);
      expect(stone).toContain('Toll 24.0');
      const pillar = captionText(captionFor({ kind: 'building', id: PILLAR_ID }, state)!);
      expect(pillar).not.toContain('Toll');
    });
  });

  describe('the picture', () => {
    const input = () => ({
      ...EMPTY_VILLAGE,
      villageTierLevel: RECORDS_TIER,
      pillar: pillarInput(state),
      fallenCount: 3,
    });

    it('stands both inside the Village bounds', () => {
      const b = villageBounds(buildVillage(input()));
      expect(b.maxX - b.minX).toBeLessThanOrEqual(0.6 * FRAME_WIDTH);
      expect(b.maxX).toBeLessThanOrEqual(0.3 * FRAME_WIDTH);
      expect(b.minX).toBeGreaterThanOrEqual(-0.3 * FRAME_WIDTH);
      expect(b.maxY).toBeLessThan(2);
      expect(STONE.x + STONE.w / 2).toBeLessThan(WALL.x - WALL.w / 2);
    });

    it('takes the Toll bar off the pillar and puts it on the Stone', () => {
      const ids = buildVillage(input()).map((v) => v.id);
      expect(ids).not.toContain(`${PILLAR_PREFIX}-toll-bar`);
      expect(ids).toContain('count-toll-bar');
    });

    it('carves one name for each of the fallen, to the room on the Wall', () => {
      const lit = (n: number) =>
        buildVillage({ ...input(), fallenCount: n }).filter(
          (v) => v.id.startsWith('names-name-') && v.tone === 1,
        ).length;
      expect(lit(0)).toBe(0);
      expect(lit(3)).toBe(3);
      expect(lit(99)).toBe(WALL_SLOTS);
    });

    it('picks each as its own building, by the volumes and the near-miss pad', () => {
      const volumes = buildVillage(input());
      for (const [prefix, id] of [
        ['count-', STONE_ID],
        ['names-', WALL_ID],
      ]) {
        const own = volumes.filter((v) => v.id.startsWith(prefix));
        expect(own.some((v) => v.id === `${prefix}hit`)).toBeTrue();
        for (const v of own) {
          expect(subjectOfVolume(v.id)).toEqual({ kind: 'building', id });
        }
        expect(ofSubject(volumes, id, 'building').length).toBeGreaterThan(0);
      }
    });
  });
});

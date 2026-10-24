import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type {
  GameState,
  MilestoneOffer,
  Toll,
  TollForecast,
} from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import { buildingViews } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import { EMPTY_PILLAR, PILLAR, PILLAR_ID } from './reference-pillar';
import {
  EXODUS_ACTION,
  exodusReason,
  pillarInput,
  REFERENCE_PILLAR,
  tollShare,
} from './reference-pillar-building';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE, villageBounds } from './village-layout';
import { ofSubject, subjectOfVolume } from './village-subjects';

const toll = (over: Partial<Toll> = {}): Toll => ({
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
  ...over,
});

const forecast = (over: Partial<TollForecast> = {}): TollForecast => ({
  toll: toll(),
  pointsDivisor: 9,
  nextLegacyAt: 27,
  exodusMinimum: 3,
  canExodus: false,
  ...over,
});

const milestone = (id: string, achieved: boolean, companion = false): MilestoneOffer => ({
  id,
  name: `Milestone ${id}`,
  description: `About ${id}.`,
  trigger: `Do ${id}`,
  achieved,
  companion,
});

describe('the reference pillar', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };
  const view = () => buildingViews(BUILDINGS, state).find((b) => b.id === PILLAR_ID)!;

  beforeEach(() =>
    load({
      highestFloor: 37,
      milestones: [milestone('a', true), milestone('b', true, true), milestone('c', false)],
      forecast: forecast(),
    }),
  );

  describe('as a building', () => {
    it('is registered and always shown', () => {
      expect(BUILDINGS).toContain(REFERENCE_PILLAR);
      expect(buildingDefinition(PILLAR_ID)).toBe(REFERENCE_PILLAR);
      expect(view().title).toBe('The reference pillar');
    });

    it('states the Highest Floor, the Milestones and the Toll as gauges', () => {
      const [floor, milestones, tollGauge] = view().gauges;
      expect(floor).toEqual(
        jasmine.objectContaining({ label: 'Highest Floor', figure: '37' }),
      );
      expect(floor.fill).toBeCloseTo(0.37);
      expect(milestones).toEqual(
        jasmine.objectContaining({ label: 'Milestones', figure: '2 of 3' }),
      );
      expect(milestones.fill).toBeCloseTo(2 / 3);
      expect(tollGauge).toEqual(
        jasmine.objectContaining({ label: 'Toll', figure: '24.0' }),
      );
      expect(tollGauge.fill).toBeCloseTo(0.67, 2);
      expect(tollGauge.leaves).toContain('about 2 Legacy');
    });

    it('lists every Milestone with how it was reached or what earns it', () => {
      const rows = view().lines.filter((l) => l.id.startsWith('milestone-'));
      expect(rows.map((r) => [r.label, r.value])).toEqual([
        ['Milestone a', 'Earned'],
        ['Milestone b', 'Joined'],
        ['Milestone c', 'Do c'],
      ]);
      expect(rows[0].heading).toBe('Milestones');
      expect(rows[1].heading).toBeNull();
      expect(rows[2].detail).toBe('About c.');
    });

    it('breaks the Toll down by counter, with New Ground and the residue', () => {
      const rows = view()
        .lines.filter((l) => l.id.startsWith('toll-'))
        .map((l) => `${l.label} ${l.value}`);
      expect(rows).toContain('Food grown 2.0');
      expect(rows).toContain('Ascent 10.0');
      expect(rows).toContain('New Ground +2');
      expect(rows).toContain('Residue carried on 6.0');
      expect(view().lines.find((l) => l.id === 'toll-new-ground')?.heading).toBeNull();
      expect(view().lines.find((l) => l.id === 'toll-food-grown')?.heading).toBe('Toll');
    });

    it('draws no Toll lines once the Campaign has ended', () => {
      load({ forecast: null });
      expect(view().lines.some((l) => l.id.startsWith('toll-'))).toBeFalse();
    });
  });

  describe('the hover caption', () => {
    it('states the Highest Floor and the Milestones, not the Toll', () => {
      const text = captionText(
        captionFor({ kind: 'building', id: PILLAR_ID }, state)!,
      );
      expect(text).toContain('Highest Floor 37');
      expect(text).toContain('Milestones 2 of 3');
      expect(text).not.toContain('Toll');
      expect(text).not.toContain('Legacy');
    });
  });

  describe('Exodus', () => {
    it('is unreachable while closed, with the reason in words', () => {
      const a = view().actions[0];
      expect(a.usable).toBeFalse();
      expect(a.reason).toBe('Exodus opens at 3 Legacy.');
      expect(a.confirm).toBeNull();
      load({ forecast: forecast({ toll: toll({ legacy: 3 }) }) });
      expect(exodusReason(state)).toBe(
        'Extract first. Exodus cannot leave a Party on the Tower.',
      );
      expect(EXODUS_ACTION.availability(state).usable).toBeFalse();
    });

    it('is closed with a reason once the Campaign has ended', () => {
      load({ forecast: null });
      expect(view().actions[0].usable).toBeFalse();
      expect(view().actions[0].reason).toContain('has ended');
    });

    it('opens when the forecast says so, asks once, and sends EXODUS', () => {
      load({ forecast: forecast({ canExodus: true }) });
      const a = view().actions[0];
      expect(a.usable).toBeTrue();
      expect(a.reason).toBeNull();
      expect(a.confirm).toEqual({
        prompt:
          'The village leaves in the night. The Tower keeps whoever stayed. The Campaign ends and pays about 2 Legacy.',
        confirmLabel: 'Leave',
        cancelLabel: 'Stay',
      });
      const send = jasmine.createSpy('send');
      EXODUS_ACTION.run({ send }, state);
      expect(send).toHaveBeenCalledOnceWith(GameAction.EXODUS, null);
    });
  });

  describe('what is carved', () => {
    it('reads the picture from the state', () => {
      expect(pillarInput(state)).toEqual({
        highestFloor: 37,
        milestonesEarned: 2,
        milestonesTotal: 3,
        tollShare: jasmine.any(Number),
      });
      expect(tollShare(null)).toBe(0);
      expect(tollShare(forecast({ pointsDivisor: 0 }))).toBe(0);
      expect(tollShare(forecast({ toll: toll({ toll: 99 }) }))).toBe(1);
      expect(tollShare(forecast({ toll: toll({ toll: 1 }) }))).toBe(0);
    });

    it('draws the pillar with its gauges and a near-miss pad that is picked as the pillar', () => {
      const volumes = buildVillage({ ...EMPTY_VILLAGE, pillar: pillarInput(state) });
      const ids = volumes.map((v) => v.id);
      for (const id of [
        'pillar-shaft',
        'pillar-floor-slit',
        'pillar-floor-fill',
        'pillar-milestones-slit',
        'pillar-milestones-fill',
        'pillar-toll-bar',
        'pillar-toll-fill',
        'pillar-hit',
      ]) {
        expect(ids).toContain(id);
        expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: PILLAR_ID });
      }
      const hit = volumes.find((v) => v.id === 'pillar-hit')!;
      expect(hit.alpha).toBe(0);
      expect(ofSubject(volumes, PILLAR_ID, 'building').some((v) => v.id === 'pillar-hit')).toBeFalse();
    });

    it('carves a gauge, not a number: an empty pillar has channels and no fill', () => {
      const ids = buildVillage({ ...EMPTY_VILLAGE, pillar: EMPTY_PILLAR }).map((v) => v.id);
      expect(ids).toContain('pillar-floor-slit');
      expect(ids).not.toContain('pillar-floor-fill');
      expect(ids).not.toContain('pillar-toll-fill');
    });

    it('fills the Highest Floor slit in proportion', () => {
      const fill = (floor: number) =>
        buildVillage({
          ...EMPTY_VILLAGE,
          pillar: { ...EMPTY_PILLAR, highestFloor: floor },
        }).find((v) => v.id === 'pillar-floor-fill')?.h ?? 0;
      expect(fill(0)).toBe(0);
      expect(fill(50)).toBeGreaterThan(fill(10));
      expect(fill(100)).toBeGreaterThan(fill(50));
    });

    it('keeps the pillar inside the Village bounds and behind the stair head', () => {
      const bounds = villageBounds(buildVillage({ ...EMPTY_VILLAGE, pillar: pillarInput(state) }));
      expect(bounds.maxY).toBeLessThan(2);
      expect(PILLAR.x - PILLAR.w / 2).toBeGreaterThanOrEqual(0);
    });

    it('outlines the whole pillar when hovered', () => {
      const out = buildVillage({
        ...EMPTY_VILLAGE,
        pillar: pillarInput(state),
        hovered: PILLAR_ID,
      }).filter((v) => v.id.startsWith(`outline-${PILLAR_ID}:hover`));
      expect(out.length).toBe(4);
    });
  });
});

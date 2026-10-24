import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState } from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import {
  BuildingCommands,
  buildingViews,
  defineBuilding,
  formatFigure,
  formatRate,
} from './building-registry';
import { BUILDINGS, buildingDefinition, foodAfterSale, SILO } from './buildings';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE } from './village-layout';
import { subjectOfVolume } from './village-subjects';

describe('building registry', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };

  beforeEach(() => load({}));

  it('writes figures and rates exactly', () => {
    expect(formatFigure(1234)).toBe('1,234');
    expect(formatFigure(1234.5)).toBe('1,234.50');
    expect(formatFigure(0)).toBe('0');
    expect(formatRate(1.2)).toBe('+1.20/s');
    expect(formatRate(-0.4)).toBe('-0.40/s');
    expect(formatRate(0)).toBe('0.00/s');
    expect(formatRate(-0.001)).toBe('0.00/s');
  });

  it('registers the Silo with a Food gauge and a sell action', () => {
    expect(BUILDINGS).toContain(SILO);
    expect(buildingDefinition('SILO')).toBe(SILO);
    expect(buildingDefinition('NOPE')).toBeUndefined();
    load({ village: { ...state.village, food: 400.25 }, netFoodPerSecond: 1.2 });
    const [view] = buildingViews(BUILDINGS, state);
    expect(view.title).toBe('The Pantry silo');
    expect(view.gauges.map((g) => g.id)).toEqual(['food', 'upkeep', 'pantry-hold', 'villagers']);
    expect(view.gauges[0]).toEqual(
      jasmine.objectContaining({ label: 'Food', figure: '400.25', rate: '+1.20/s' }),
    );
    expect(view.gauges[0].fill).toBeGreaterThan(0.8);
    expect(view.gauges[0].leaves).toContain('0.25');
    expect(view.actions.map((a) => a.id)).toEqual(['sell-food', 'buy-villager']);
    expect(view.actions[0]).toEqual({
      id: 'sell-food',
      label: 'Sell Food',
      usable: true,
      reason: null,
      preview: null,
    });
  });

  it('says in words why Selling Food cannot be used', () => {
    load({ village: { ...state.village, food: 1 } });
    const [view] = buildingViews(BUILDINGS, state);
    expect(view.actions[0].usable).toBeFalse();
    expect(view.actions[0].reason).toContain('one Food or less');
    expect(view.gauges[0].leaves).toBeNull();
  });

  it('leaves what a sale leaves', () => {
    expect(foodAfterSale(5.3)).toBeCloseTo(0.3, 6);
    expect(foodAfterSale(0.7)).toBe(0.7);
  });

  it('runs the same command the panel sends', () => {
    const send = jasmine.createSpy('send');
    load({ village: { ...state.village, food: 10 } });
    SILO.actions[0].run({ send } as BuildingCommands, state);
    expect(send).toHaveBeenCalledOnceWith(GameAction.SELL_FOOD);
  });

  it('hides a building whose gate is closed, and lists the rest in order', () => {
    const gated = defineBuilding({
      id: 'CELLAR',
      title: 'The Spore Cellar',
      visible: (s) => s.villageTierLevel >= 3,
      gauges: [],
      actions: [],
    });
    load({ villageTierLevel: 1 });
    expect(buildingViews([SILO, gated], state).map((b) => b.id)).toEqual(['SILO']);
    load({ villageTierLevel: 3 });
    expect(buildingViews([SILO, gated], state).map((b) => b.id)).toEqual([
      'SILO',
      'CELLAR',
    ]);
  });

  describe('as a subject', () => {
    it('is picked from every part of the silo, its slit and its near-miss pad', () => {
      const ids = buildVillage(EMPTY_VILLAGE)
        .map((v) => v.id)
        .filter((id) => id.startsWith('silo'));
      expect(ids).toContain('silo-hit');
      for (const id of ids) {
        expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: 'SILO' });
      }
    });

    it('is outlined like any other thing when hovered or selected', () => {
      const out = buildVillage({ ...EMPTY_VILLAGE, hovered: 'SILO', selected: null });
      const outline = out.filter((v) => v.id.startsWith('outline-SILO:hover:building'));
      expect(outline.length).toBe(4);
      expect(subjectOfVolume(outline[0].id)).toEqual({ kind: 'building', id: 'SILO' });
    });

    it('has a caption with the exact figure, its rate and what a sale leaves', () => {
      load({ village: { ...state.village, food: 400.25 }, netFoodPerSecond: 1.2 });
      const caption = captionFor({ kind: 'building', id: 'SILO' }, state);
      expect(caption).toEqual(
        jasmine.objectContaining({
          name: 'The Pantry silo',
          status: jasmine.stringContaining('Food 400.25'),
          rates: 'Food +1.20/s',
        }),
      );
      const text = captionText(caption!);
      expect(text).toContain('Food 400.25');
      expect(text).toContain('Food +1.20/s.');
      expect(text).toContain('leaves 0.25 in the Pantry');
    });

    it('has no caption for a building that is not registered', () => {
      expect(captionFor({ kind: 'building', id: 'NOPE' }, state)).toBeNull();
    });
  });
});

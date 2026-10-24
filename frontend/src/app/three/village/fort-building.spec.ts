import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { Unit, UnitStats } from '../../interfaces/unit.enum';
import { GameStateService } from '../../services/game-state.service';
import { buildingView } from './building-registry';
import { BUILDINGS, buildingDefinition } from './buildings';
import { FORT, gateOf, reserveTotal, woundedTotal } from './fort-building';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE } from './village-layout';
import { subjectOfVolume } from './village-subjects';

const member = (id: number, hp: number, max = 10): Member =>
  ({ id, name: `M${id}`, currentHealth: hp, maxHealth: max }) as unknown as Member;
const stats = (cost: number) => ({ cost }) as UnitStats;

describe('the Fort', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };
  const view = () => buildingView(FORT, state);
  const action = (id: string) => view().actions.find((a) => a.id === id)!;

  beforeEach(() => {
    load({
      credit: 10,
      unlockedUnitTypes: ['WARRIOR', 'ARCHER', 'PORTER'],
      unitCatalog: { WARRIOR: stats(3), ARCHER: stats(5), PORTER: stats(2), TANK: stats(25) },
    });
  });

  it('is registered, and its parts are its subject', () => {
    expect(buildingDefinition('FORT')).toBe(FORT);
    expect(BUILDINGS).toContain(FORT);
    for (const id of ['fort-keep', 'fort-hit', 'fort-reserves-bar', 'reserve-0']) {
      expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: 'FORT' });
    }
  });

  it('counts the Reserves and the wounded', () => {
    load({
      units: {
        ...state.units,
        [Unit.WARRIOR]: [member(1, 10), member(2, 4)],
        [Unit.ARCHER]: [member(3, 10), member(4, 0.5)],
      },
    });
    expect(reserveTotal(state)).toBe(4);
    expect(woundedTotal(state)).toBe(2);
    const [reserves, wounded] = view().gauges;
    expect(reserves).toEqual(jasmine.objectContaining({ label: 'Reserves', figure: '4' }));
    expect(wounded).toEqual(jasmine.objectContaining({ label: 'Wounded', figure: '2', fill: 0.5 }));
  });

  it('says both counts on hover', () => {
    load({ units: { ...state.units, [Unit.WARRIOR]: [member(1, 10), member(2, 4)] } });
    const text = captionText(captionFor({ kind: 'building', id: 'FORT' }, state)!);
    expect(text).toContain('Reserves 2');
    expect(text).toContain('Wounded 1');
  });

  it('lists the open Types with cost and count, and sends the same command as the card', () => {
    load({ units: { ...state.units, [Unit.WARRIOR]: [member(1, 10)] } });
    expect(view().actions.map((a) => a.id)).toEqual([
      'hire-WARRIOR',
      'hire-ARCHER',
      'hire-PORTER',
    ]);
    expect(action('hire-WARRIOR').label).toBe('Recruit warrior, 3 Credits (1 in the Reserves)');
    expect(action('hire-WARRIOR').usable).toBeTrue();
    const sent: unknown[][] = [];
    const spec = FORT.actions.find((a) => a.id === 'hire-ARCHER')!;
    spec.run({ send: (a, p) => sent.push([a, p]) }, state);
    expect(sent).toEqual([[GameAction.BUY, { unitStats: 'ARCHER', quantity: 1 }]]);
  });

  it('refuses in words when the Credits do not exceed the cost', () => {
    load({ credit: 5 });
    expect(action('hire-WARRIOR').usable).toBeTrue();
    expect(action('hire-ARCHER').usable).toBeFalse();
    expect(action('hire-ARCHER').reason).toContain('must exceed the cost');
    expect(action('hire-ARCHER').reason).toContain('costs 5');
  });

  it('names an Upgrade gate in words, and hides a Type with no gate', () => {
    expect(view().actions.some((a) => a.id === 'hire-TANK')).toBeFalse();
    load({
      availableUpgrades: [
        {
          id: 'UNLOCK_TANK',
          name: 'Recruit the Tank',
          description: '',
          level: 0,
          maxed: false,
          repeatable: false,
          unlockUnit: 'TANK',
          nextCost: { MATERIALS: 30 },
        },
      ],
    });
    const tank = action('hire-TANK');
    expect(tank.usable).toBeFalse();
    expect(tank.reason).toContain('Recruit the Tank');
    expect(tank.reason).toContain('30 Materials');
  });

  it('names a Milestone gate in words', () => {
    load({
      milestones: [
        {
          id: 'SARGAN_JOINS',
          name: 'Sargan the Fire Mage joins',
          description: '',
          trigger: 'Reach floor 5',
          achieved: false,
          companion: true,
        },
      ],
    });
    expect(gateOf(state, 'MAGE')).toContain('Sargan the Fire Mage joins');
    expect(gateOf(state, 'MAGE')).toContain('reach floor 5');
    expect(action('hire-MAGE').usable).toBeFalse();
  });

  it('draws two gauges and a pad that fill with the Reserves', () => {
    const few = buildVillage({ ...EMPTY_VILLAGE, reserves: 0, wounded: 0 });
    const many = buildVillage({ ...EMPTY_VILLAGE, reserves: 15, wounded: 15 });
    expect(few.some((v) => v.id === 'fort-reserves-fill')).toBeFalse();
    expect(many.some((v) => v.id === 'fort-reserves-fill')).toBeTrue();
    expect(many.some((v) => v.id === 'fort-wounded-fill')).toBeTrue();
    expect(many.find((v) => v.id === 'fort-hit')?.alpha).toBe(0);
  });
});

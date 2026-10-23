import { TestBed } from '@angular/core/testing';

import { GameState } from '../../interfaces/game-state';
import { TradeView } from '../../interfaces/village';
import { GameStateService } from '../../services/game-state.service';
import { captionFor, captionText, lockOf } from './village-captions';

const view = (
  trade: TradeView['trade'],
  plural: string,
  workplace: string,
  hands: number,
  posts: number,
): TradeView => ({
  trade,
  plural,
  workplace,
  hands,
  posts,
  settling: 0,
  settlingSeconds: 0,
});

describe('village captions', () => {
  let state: GameState;

  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update({
      villageTierLevel: 1,
      villageTierName: 'Huddled',
      quarryRate: 0.4,
      tradeViews: [
        view('BREAKER', 'Breakers', 'Quarry', 3, 10),
        view('SCRIBE', 'Scribes', "Scribe's Study", 0, 10),
        view('SALTER', 'Salters', 'Salt Pans', 0, 6),
        view('SPOREWARDEN', 'Sporewardens', 'Spore Cellar', 0, 4),
      ],
      postUpgrades: [
        {
          trade: 'BREAKER',
          workplace: 'Quarry',
          level: 1,
          posts: 10,
          nextPosts: 14,
          cost: 120,
          blocked: 'Not enough Materials.',
        },
      ],
      ...over,
    });
    state = service.state();
  };

  it('says what a working Annex is, who works it and what it makes', () => {
    load({});

    const c = captionFor({ kind: 'annex', id: 'BREAKER' }, state)!;

    expect(captionText(c)).toBe(
      'Quarry. Working: 3 Breakers of 10 Posts. Materials into the Heap, +0.40/s.',
    );
  });

  it('says an Annex with no hands is idle and makes nothing', () => {
    load({});

    const c = captionFor({ kind: 'annex', id: 'SCRIBE' }, state)!;

    expect(c.status).toBe('Idle: no hands.');
    expect(c.makes).toBe('');
  });

  it('states the real unlock condition of a locked Annex, from the Village Tier', () => {
    load({});

    const c = captionFor({ kind: 'annex', id: 'SALTER' }, state)!;

    expect(c.lock).toBe(
      'The Salt Pans open at Village Tier II, Sheltered. The Village stands at Tier 1, Huddled.',
    );
    expect(lockOf(state, 'SPOREWARDEN')).toContain('Village Tier III, Fortified');
  });

  it('drops the lock once the Tier is reached', () => {
    load({ villageTierLevel: 2, villageTierName: 'Sheltered' });

    expect(lockOf(state, 'SALTER')).toBeNull();
    expect(lockOf(state, 'SPOREWARDEN')).not.toBeNull();
  });

  it('captions Villagers and an Upgrade row', () => {
    load({});

    expect(
      captionText(captionFor({ kind: 'villagers', id: 'BREAKER' }, state)!),
    ).toContain('3 at the Quarry.');
    expect(
      captionText(captionFor({ kind: 'upgrade', id: 'BREAKER' }, state)!),
    ).toBe(
      'Posts of the Quarry. 10 Posts, 14 after the next. Not enough Materials.',
    );
  });

  it('returns nothing for no subject or an unknown one', () => {
    load({});

    expect(captionFor(null, state)).toBeNull();
    expect(captionFor({ kind: 'annex', id: 'NOWHERE' }, state)).toBeNull();
  });
});

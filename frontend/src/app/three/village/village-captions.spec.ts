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

    expect(captionText(c)).toContain(
      'Quarry. Working: 3 Breakers of 10 Posts. Materials in the Heap held 0. Materials into the Heap, +0.40/s.',
    );
    expect(captionText(c)).toContain('Carried down on Extraction');
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
    expect(lockOf(state, 'SPOREWARDEN')).toContain(
      'Village Tier III, Fortified',
    );
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

  describe('Port', () => {
    const portView = (state: 'HIDDEN' | 'FOOTING' | 'BUILT') => ({
      state,
      footingExpeditions: 12,
      expeditionsCompleted: 12,
      materialsCost: 600,
      creditsCost: 300,
    });

    it('states in words what finishes the footing', () => {
      load({ port: portView('FOOTING') });

      const c = captionFor({ kind: 'building', id: 'PORT' }, state)!;

      expect(c.name).toBe('The Port');
      expect(c.lock).toBe(
        'Finishing it costs 600 Materials and 300 Credits from the Vault. ' +
          'The footing opened at 12 completed Expeditions; 12 are behind the Village.',
      );
    });

    it('says the finished Port stands, and whether the Boat lies at the pier', () => {
      load({ port: portView('BUILT') });
      expect(captionFor({ kind: 'building', id: 'PORT' }, state)!.lock).toBeNull();
      expect(
        captionText(captionFor({ kind: 'building', id: 'PORT' }, state)!),
      ).toContain('pier stands');

      load({
        port: portView('BUILT'),
        boat: { state: 'DOCKED', dockTicks: 600, ticksToDock: 0 },
      });
      expect(
        captionText(captionFor({ kind: 'building', id: 'PORT' }, state)!),
      ).toContain('A Boat lies at the pier');
    });

    it('says in plain words that the Boat is due, and when', () => {
      const say = (ticksToDock: number) => {
        load({
          port: portView('BUILT'),
          boat: { state: 'DUE', dockTicks: 600, ticksToDock },
        });
        return captionText(captionFor({ kind: 'building', id: 'PORT' }, state)!);
      };
      expect(say(300)).toContain('The Boat is due, in about 5 minutes.');
      expect(say(61)).toContain('in about 2 minutes.');
      expect(say(59)).toContain('in less than a minute.');
    });

    it('does not mention a Boat when it is not built or the frame has none', () => {
      load({
        port: portView('BUILT'),
        boat: { state: 'NOT_BUILT', dockTicks: 600, ticksToDock: 0 },
      });
      expect(
        captionText(captionFor({ kind: 'building', id: 'PORT' }, state)!),
      ).not.toContain('Boat');
      load({ port: portView('BUILT'), boat: undefined });
      expect(
        captionText(captionFor({ kind: 'building', id: 'PORT' }, state)!),
      ).not.toContain('Boat');
    });

    it('has no caption while hidden or when the frame has no Port', () => {
      load({ port: portView('HIDDEN') });
      expect(captionFor({ kind: 'building', id: 'PORT' }, state)).toBeNull();
      load({ port: undefined });
      expect(captionFor({ kind: 'building', id: 'PORT' }, state)).toBeNull();
    });
  });

  describe('Shortcut', () => {
    const offer = (unlocked: boolean) => ({
      id: 'NARROW_STAIR',
      name: 'Narrow Stair',
      description: 'A way up.',
      trigger: '10 enemies',
      floor: 21,
      unlocked,
    });

    it('says in words that an open Shortcut is a place to start from', () => {
      load({ shortcuts: [offer(true)] });
      const c = captionFor({ kind: 'shortcut', id: 'NARROW_STAIR' }, state)!;
      expect(c.name).toBe('Narrow Stair');
      expect(c.lock).toBeNull();
      expect(captionText(c)).toContain(
        'A Shortcut stands open. An Expedition can start from here.',
      );
      expect(captionText(c)).toContain('Start Floor 21');
    });

    it('has no caption for a locked or unknown Shortcut', () => {
      load({ shortcuts: [offer(false)] });
      expect(
        captionFor({ kind: 'shortcut', id: 'NARROW_STAIR' }, state),
      ).toBeNull();
      expect(captionFor({ kind: 'shortcut', id: 'NOPE' }, state)).toBeNull();
    });
  });
});

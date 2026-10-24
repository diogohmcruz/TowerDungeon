import { TestBed } from '@angular/core/testing';

import { GameAction } from '../interfaces/game-action.enum';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import { LaunchService } from './launch.service';
import { isEmptySpaceClick } from '../components/tower-scene/tower-scene-graph.component';
import {
  bellAvailability,
  extractAvailability,
  waveAvailability,
} from '../three/launch/launch-controls';
import {
  bellObject,
  recallObject,
  towerLaunchVolumes,
} from '../three/launch/launch-objects';
import { captionFor, captionText } from '../three/village/village-captions';
import {
  buildVillage,
  EMPTY_VILLAGE,
  Volume,
} from '../three/village/village-layout';
import { subjectOfVolume } from '../three/village/village-subjects';
import type { GameState } from '../interfaces/game-state';

describe('launching from the picture', () => {
  let sendAction: jasmine.Spy;
  let launch: LaunchService;
  let states: GameStateService;
  const state = (): GameState => states.state();
  const shortcut = {
    id: 'NARROW_STAIR',
    name: 'Narrow Stair',
    description: '',
    trigger: '',
    floor: 10,
    unlocked: true,
  };

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    states = TestBed.inject(GameStateService);
    launch = TestBed.inject(LaunchService);
    states.update({ shortcuts: [shortcut], homeUnitCount: 4 });
  });

  describe('availability', () => {
    it('rings Set out only while no Expedition is out, as the panel button does', () => {
      expect(bellAvailability(state()).usable).toBeTrue();
      states.update({ expeditionActive: true });
      expect(bellAvailability(state())).toEqual(waveAvailability(state()));
    });

    it('refuses a Wave with no Reserves, or while the Party is Held, with words', () => {
      states.update({ expeditionActive: true, homeUnitCount: 0 });
      expect(waveAvailability(state()).reason).toContain('No Reserves');
      states.update({
        expeditionActive: true,
        homeUnitCount: 3,
        session: {
          resumed: true,
          savedAt: null,
          schema: 1,
          notices: [],
          held: true,
        },
      });
      expect(waveAvailability(state()).reason).toContain('has not moved');
      expect(extractAvailability(state()).usable).toBeTrue();
    });

    it('refuses Extract with no Expedition out', () => {
      expect(extractAvailability(state()).reason).toContain('No Expedition');
    });
  });

  describe('the command wiring', () => {
    it('sends the same Set out the button sends, with the chosen Start Floor and Salt', () => {
      launch.chooseStartFloor(10);
      launch.ring();
      expect(sendAction).toHaveBeenCalledTimes(1);
      const [action, payload] = sendAction.calls.mostRecent().args;
      expect(action).toBe(GameAction.INVADE);
      expect(payload.startFloor).toBe(10);
      expect(payload.salt).toBe(0);
      expect(payload.panacea).toBeFalse();
    });

    it('sends a Reinforcement Wave while an Expedition is out', () => {
      states.update({ expeditionActive: true });
      launch.ring();
      expect(sendAction).toHaveBeenCalledWith(GameAction.REINFORCE, {
        units: {},
      });
    });

    it('does nothing when the panel would refuse, and says why in one line', () => {
      states.update({ expeditionActive: true, homeUnitCount: 0 });
      launch.ring();
      expect(sendAction).not.toHaveBeenCalled();
      expect(launch.refusal()?.text).toContain('No Reserves');
      expect(launch.ledgerLine()?.text).toBe(launch.refusal()?.text);
    });

    it('lights the lamp to Extract, and refuses it when nothing is out', () => {
      launch.light();
      expect(sendAction).not.toHaveBeenCalled();
      expect(launch.refusal()?.command).toBe('extract');
      states.update({ expeditionActive: true });
      launch.light();
      expect(sendAction).toHaveBeenCalledWith(GameAction.EXTRACT, null);
      expect(launch.refusal()).toBeNull();
    });

    it('chooses the Start Floor from a doorway and the Base door, and not while out', () => {
      expect(
        launch.activate({ kind: 'shortcut', id: 'NARROW_STAIR' }),
      ).toBeFalse();
      expect(launch.startFloor()).toBe(10);
      expect(launch.activate({ kind: 'base-door', id: 'BASE' })).toBeTrue();
      expect(launch.startFloor()).toBe(0);
      states.update({ expeditionActive: true });
      launch.activate({ kind: 'shortcut', id: 'NARROW_STAIR' });
      expect(launch.startFloor()).toBe(0);
      expect(launch.refusal()?.command).toBe('start-floor');
    });

    it('handles a click on a control, and lets any other subject pass', () => {
      expect(launch.activate({ kind: 'bell', id: 'BELL' })).toBeTrue();
      expect(launch.activate({ kind: 'recall', id: 'RECALL' })).toBeTrue();
      expect(launch.activate({ kind: 'annex', id: 'TILLER' })).toBeFalse();
    });
  });

  describe('captions', () => {
    it('names Party size, Start Floor, Supplies carried and what the Pantry holds', () => {
      const text = captionText(
        captionFor({ kind: 'bell', id: 'BELL' }, state(), { startFloor: 10 })!,
      );
      expect(text).toContain('Party');
      expect(text).toContain('Start Floor 10');
      expect(text).toContain('Supplies carried');
      expect(text).toContain('Pantry holds');
    });

    it('says why a dark control is dark', () => {
      states.update({ expeditionActive: true, homeUnitCount: 0 });
      const bell = captionFor({ kind: 'bell', id: 'BELL' }, state())!;
      expect(bell.lock).toContain('No Reserves');
      states.update({ expeditionActive: false });
      const lamp = captionFor({ kind: 'recall', id: 'RECALL' }, state())!;
      expect(lamp.lock).toContain('No Expedition');
    });

    it('marks the chosen Start Floor on the Base door and on a doorway', () => {
      const base = captionFor({ kind: 'base-door', id: 'BASE' }, state(), {
        startFloor: 0,
      })!;
      expect(base.status).toContain('Chosen');
      const door = captionFor(
        { kind: 'shortcut', id: 'NARROW_STAIR' },
        state(),
        { startFloor: 10 },
      )!;
      expect(door.makes).toContain('Chosen');
    });
  });

  describe('subjects and objects', () => {
    it('makes every part of the bell, the lamp and the Base door one subject', () => {
      const volumes: Volume[] = [];
      bellObject(volumes, { x: 0, bottom: 0, z: 0 }, true);
      for (const v of volumes) {
        expect(subjectOfVolume(v.id)).toEqual({ kind: 'bell', id: 'BELL' });
      }
      const lamp: Volume[] = [];
      recallObject(lamp, { x: 0, bottom: 0, z: 0 }, true);
      for (const v of lamp) {
        expect(subjectOfVolume(v.id)).toEqual({
          kind: 'recall',
          id: 'RECALL',
        });
      }
      const village = buildVillage(EMPTY_VILLAGE);
      expect(
        village.filter((v) => v.id.startsWith('base-door')).length,
      ).toBeGreaterThan(4);
      expect(subjectOfVolume('base-door:lintel')).toEqual({
        kind: 'base-door',
        id: 'BASE',
      });
    });

    it('lights the bell when usable and darkens it with a dashed outline when not', () => {
      const tone = (v: Volume[]) => v.find((x) => x.id === 'bell-body')!.tone;
      const lit = buildVillage({ ...EMPTY_VILLAGE, bellUsable: true });
      const dark = buildVillage({ ...EMPTY_VILLAGE, bellUsable: false });
      expect(tone(dark)).toBeLessThan(tone(lit));
      expect(lit.some((v) => v.id.includes('outline-BELL:unlit'))).toBeFalse();
      const dashes = dark.filter((v) => v.id.startsWith('outline-BELL:unlit'));
      expect(dashes.length).toBeGreaterThan(8);
      // Dashed, not solid: the top edge is broken into runs with gaps between.
      const top = dashes
        .filter((v) => v.id.includes('-n'))
        .sort((a, b) => a.x - b.x);
      expect(top.length).toBeGreaterThan(1);
      expect(top[1].x - top[0].x).toBeGreaterThan(top[0].w);
    });

    it('draws the bone outline on the Base door by default and on the chosen doorway', () => {
      const start = (v: Volume[]) =>
        new Set(
          v
            .filter((x) => x.id.includes(':start:'))
            .map((x) => x.id.split(':')[0]),
        );
      expect(start(buildVillage(EMPTY_VILLAGE))).toEqual(
        new Set(['outline-BASE']),
      );
      const chosen = buildVillage({
        ...EMPTY_VILLAGE,
        shortcuts: [{ id: 'NARROW_STAIR', floor: 10 }],
        startFloor: 10,
      });
      expect(start(chosen)).toEqual(new Set(['outline-NARROW_STAIR']));
    });

    it('draws the Tower scene bell and lamp dark with dashes when they cannot be used', () => {
      const lit = towerLaunchVolumes({
        surfaceY: 0,
        bellUsable: true,
        recallUsable: true,
      });
      const dark = towerLaunchVolumes({
        surfaceY: 0,
        bellUsable: false,
        recallUsable: false,
      });
      expect(lit.some((v) => v.id.includes(':unlit:'))).toBeFalse();
      expect(dark.some((v) => v.id.includes('outline-BELL:unlit'))).toBeTrue();
      expect(
        dark.some((v) => v.id.includes('outline-RECALL:unlit')),
      ).toBeTrue();
      expect(dark.length).toBeLessThanOrEqual(128);
    });
  });

  describe('the closeup toggle', () => {
    const press = { x: 10, y: 10, at: 100 };
    const click = { clientX: 11, clientY: 10, timeStamp: 200 };

    it('is toggled by a short, still click on empty space', () => {
      expect(isEmptySpaceClick(press, click, false)).toBeTrue();
    });

    it('is not toggled by a click that landed on a launch control', () => {
      expect(isEmptySpaceClick(press, click, true)).toBeFalse();
    });

    it('is not toggled by a drag, a long press or no press', () => {
      expect(
        isEmptySpaceClick(press, { ...click, clientX: 60 }, false),
      ).toBeFalse();
      expect(
        isEmptySpaceClick(press, { ...click, timeStamp: 900 }, false),
      ).toBeFalse();
      expect(isEmptySpaceClick(null, click, false)).toBeFalse();
    });
  });
});

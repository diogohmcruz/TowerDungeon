import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type { LoadoutPreview } from '../../interfaces/village';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { LaunchService } from '../../services/launch.service';
import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import { captionFor, captionText } from './village-captions';
import { GATE, GATE_ID } from './gate-building';
import { buildingView } from './building-registry';
import { BUILDINGS } from './buildings';
import { gateObject } from '../launch/launch-objects';
import {
  buildVillage,
  EMPTY_VILLAGE,
  type Volume,
} from './village-layout';
import { ofSubject, subjectOfVolume } from './village-subjects';

const loadout = (over: Partial<LoadoutPreview> = {}): LoadoutPreview => ({
  carryingCapacity: 160,
  suppliesTaken: 100,
  pantryAfter: 300,
  partySize: 6,
  reservesStaying: 0,
  pantryHoldSeconds: 90,
  drainPerUnit: 1.5,
  saltedDrainPerUnit: 1.25,
  suppliesPerSalt: 10,
  saltUseful: 10,
  saltHeld: 4,
  saltPansOpen: true,
  trustHeld: 12,
  packCapacity: 10,
  bearerTypes: ['TANK', 'WARRIOR'],
  sporesHeld: 5,
  panaceaSpores: 4,
  panaceaMaterials: 40,
  vaultMaterials: 100,
  panaceaCapacity: 10,
  panaceaBlocked: null,
  ...over,
});

describe('the Gate building', () => {
  let sendAction: jasmine.Spy;
  let game: GameStateService;
  let launch: LaunchService;
  let popover: PopoverService;

  const give = (over: Partial<LoadoutPreview> = {}, rest = {}) =>
    game.update({ loadout: loadout(over), ...rest });
  const view = () => buildingView(GATE, game.state());
  const gauge = (id: string) => view().gauges.find((g) => g.id === id)!;
  const action = (id: string) => view().actions.find((a) => a.id === id)!;

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    launch = TestBed.inject(LaunchService);
    popover = TestBed.inject(PopoverService);
    give();
  });
  afterEach(() => TestBed.inject(StageService).showMenus());

  it('is registered, and shown while the frame carries a Loadout', () => {
    expect(BUILDINGS).toContain(GATE);
    expect(popover.buildings().some((b) => b.id === GATE_ID)).toBeTrue();
  });

  it('states the Carrying Capacity, the Supplies and the runway, and what the Pantry holds after', () => {
    expect(gauge('capacity').figure).toBe('160');
    expect(gauge('supplies').figure).toBe('100');
    expect(gauge('supplies').leaves).toContain('Supplies last about 11 s at Party size 6.');
    expect(gauge('supplies').leaves).toContain('11 s plain, 13 s if fully salted.');
    expect(gauge('pantry').figure).toBe('300');
    expect(gauge('pantry').leaves).toContain('Setting out takes 100 Food.');
    expect(gauge('pantry').leaves).toContain('Pantry would hold 90 s.');
  });

  it('says the Fields cover Upkeep when the Pantry never runs out', () => {
    give({ pantryHoldSeconds: null });
    expect(gauge('pantry').leaves).toContain('Fields cover Upkeep.');
  });

  it('chooses Salt one at a time, never beyond what is held, and the runway follows', () => {
    give({ saltHeld: 10 });
    for (let i = 0; i < 12; i++) {
      if (action('salt-more').usable) popover.perform(GATE_ID, 'salt-more');
    }
    expect(launch.saltToUse()).toBe(10);
    expect(gauge('salt').figure).toBe('10');
    expect(gauge('supplies').leaves).toContain('Supplies last about 13 s');
    expect(action('salt-more').usable).toBeFalse();
    expect(action('salt-more').reason).toContain('No more Salt');
    expect(popover.perform(GATE_ID, 'salt-more')).toBeFalse();

    popover.perform(GATE_ID, 'salt-less');
    expect(launch.saltToUse()).toBe(9);
  });

  it('packs Salt from what the Supplies left, for the chosen bearer', () => {
    popover.perform(GATE_ID, 'salt-more');
    for (let i = 0; i < 6; i++) {
      if (action('pack-more').usable) popover.perform(GATE_ID, 'pack-more');
    }
    expect(launch.packSalt()).toBe(3);
    expect(action('bearer-TANK').usable).toBeFalse();
    expect(action('bearer-TANK').reason).toContain('already carries');

    popover.perform(GATE_ID, 'bearer-WARRIOR');
    expect(launch.packBearer()).toBe('WARRIOR');
    expect(gauge('pack').leaves).toContain('Marked: WARRIOR');
  });

  it('offers no Pack with no Unit to carry it, and no Salt before the Salt Pans open', () => {
    give({ bearerTypes: [] });
    expect(action('pack-more').usable).toBeFalse();
    expect(action('pack-more').reason).toContain('No Unit');
    expect(view().actions.some((a) => a.id.startsWith('bearer-'))).toBeFalse();

    give({ saltPansOpen: false });
    expect(action('salt-more').usable).toBeFalse();
    expect(action('salt-more').reason).toBe('The Salt Pans are not open yet.');
    expect(gauge('salt').leaves).toBe('The Salt Pans are not open yet.');
  });

  it('carries or leaves the Panacea, and takes its Carrying Capacity', () => {
    expect(action('panacea').label).toBe('Carry a Panacea');
    popover.perform(GATE_ID, 'panacea');

    expect(launch.panaceaChosen()).toBeTrue();
    expect(gauge('panacea').figure).toBe('Carried');
    expect(gauge('capacity').figure).toBe('150');
    expect(action('panacea').label).toBe('Leave the Panacea');

    popover.perform(GATE_ID, 'panacea');
    expect(launch.panaceaChosen()).toBeFalse();
  });

  it('says why a Panacea cannot be carried', () => {
    give({ panaceaBlocked: 'The Village holds too few Spores.' });

    expect(action('panacea').usable).toBeFalse();
    expect(action('panacea').reason).toBe('The Village holds too few Spores.');
    expect(popover.perform(GATE_ID, 'panacea')).toBeFalse();
    expect(launch.panaceaChosen()).toBeFalse();
  });

  it('freezes the Loadout, in words, while an Expedition is out', () => {
    game.update({ expeditionActive: true });

    for (const id of ['salt-more', 'pack-more', 'panacea', 'bearer-WARRIOR']) {
      expect(action(id).usable).withContext(id).toBeFalse();
      expect(action(id).reason).withContext(id).toContain('An Expedition is already out.');
    }
    expect(popover.perform(GATE_ID, 'salt-more')).toBeFalse();
  });

  it('rings the bell with the choices made at the Gate', () => {
    popover.perform(GATE_ID, 'salt-more');
    popover.perform(GATE_ID, 'salt-more');
    popover.perform(GATE_ID, 'panacea');

    launch.ring();

    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ salt: 2, panacea: true }),
    );
  });

  it('speaks the same figures in its hover caption', () => {
    popover.perform(GATE_ID, 'salt-more');
    const caption = captionText(captionFor({ kind: 'building', id: GATE_ID }, game.state())!);

    expect(caption).toContain('The Gate: the Loadout.');
    expect(caption).toContain('Carrying Capacity 160');
    expect(caption).toContain('Salt for the Supplies 1');
    expect(caption).toContain('Setting out takes 100 Food.');
    expect(caption).toContain('Supplies last about');
  });
});

describe('the Gate in the picture', () => {
  const village = () => buildVillage({ ...EMPTY_VILLAGE, saltPansOpen: true });

  it('is drawn beside the bell and maps to the Gate building', () => {
    const volumes = village();
    const parts = ofSubject(volumes, GATE_ID, 'building');

    expect(parts.length).toBeGreaterThanOrEqual(3);
    expect(subjectOfVolume('gate-pier-w')).toEqual({ kind: 'building', id: GATE_ID });
    expect(subjectOfVolume('gate-hit-top')).toEqual({ kind: 'building', id: GATE_ID });
    expect(subjectOfVolume('bell-hit')?.kind).toBe('bell');
  });

  it('keeps its pads behind the bell and off the bell box, so the bell rings and the frame opens', () => {
    const volumes = village();
    const bellHit = volumes.find((v) => v.id === 'bell-hit') as Volume;
    const pads = volumes.filter((v) => v.id.startsWith('gate-hit'));

    expect(pads.length).toBe(3);
    for (const p of pads) {
      expect(p.alpha).toBe(0);
      expect(p.z + p.d / 2).toBeLessThan(bellHit.z + bellHit.d / 2);
    }
    // The pads that stand beside the bell do not take its middle.
    const sides = pads.filter((p) => p.id !== 'gate-hit-top');
    for (const p of sides) {
      const overlap = Math.min(p.x + p.w / 2, bellHit.x + bellHit.w / 2) - Math.max(p.x - p.w / 2, bellHit.x - bellHit.w / 2);
      expect(overlap).toBeLessThan(0.2);
    }
  });

  it('frames the Gate from its drawn parts only (the pads are for picking)', () => {
    const out: Volume[] = [];
    gateObject(out, { x: 0, bottom: 0, z: 0 });

    expect(out.filter((v) => v.alpha === 0).length).toBe(3);
    expect(ofSubject(out, GATE_ID, 'building').every((v) => v.alpha !== 0)).toBeTrue();
  });
});

import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type { LoadoutPreview, QuartermastersTally } from '../../interfaces/village';
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

describe('the Gate: Overreach, the Tally and Muster Roll', () => {
  let sendAction: jasmine.Spy;
  let game: GameStateService;
  let launch: LaunchService;
  let popover: PopoverService;

  const OVERREACH = {
    supplyDrainMultiplier: 1.3,
    starvationDamageMultiplier: 2,
    lootMultiplier: 1.5,
  };
  const tally = (over: Partial<QuartermastersTally> = {}): QuartermastersTally => ({
    slots: 3,
    saved: [],
    runwayTicks: 11,
    saltedRunwayTicks: 13,
    overreachRunwayTicks: 8,
    ...over,
  });
  const give = (over: Partial<LoadoutPreview> = {}) =>
    game.update({ loadout: loadout(over) });
  const view = () => buildingView(GATE, game.state());
  const gauge = (id: string) => view().gauges.find((g) => g.id === id);
  const action = (id: string) => view().actions.find((a) => a.id === id);

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    launch = TestBed.inject(LaunchService);
    popover = TestBed.inject(PopoverService);
  });
  afterEach(() => TestBed.inject(StageService).showMenus());

  it('shows nothing of Overreach, the Tally or Muster Roll while the Roll holds none', () => {
    give();

    expect(gauge('overreach')).toBeUndefined();
    expect(action('overreach')).toBeUndefined();
    expect(view().actions.some((a) => a.id.startsWith('loadout-') || a.id === 'muster-roll')).toBeFalse();
    expect(view().lines).toEqual([]);
  });

  it('offers the Overreach switch, says its three effects and the runway under it, and the bell sets out with it', () => {
    give({ overreach: OVERREACH });

    expect(action('overreach')!.label).toBe('Set out under Overreach');
    expect(gauge('overreach')!.figure).toBe('Not set');
    expect(gauge('overreach')!.leaves).toContain('The Supplies drain 1.3 times as fast.');
    expect(gauge('overreach')!.leaves).toContain('Starvation does 2 times the damage.');
    expect(gauge('overreach')!.leaves).toContain('All Loot found is 1.5 times as much.');
    expect(gauge('overreach')!.leaves).toContain('Under it the Supplies last about 8 s.');
    expect(gauge('supplies')!.leaves).toContain('Under Overreach, about 8 s.');

    popover.perform(GATE_ID, 'overreach');

    expect(launch.overreachChosen()).toBeTrue();
    expect(gauge('overreach')!.figure).toBe('Set');
    expect(gauge('supplies')!.leaves).toContain('Supplies last about 8 s');
    expect(action('overreach')!.label).toBe('Set out without Overreach');
    const caption = captionText(captionFor({ kind: 'building', id: GATE_ID }, game.state())!);
    expect(caption).toContain('Overreach Set');
    expect(caption).toContain('Under it the Supplies last about 8 s.');

    launch.ring();

    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ overreach: true }),
    );
    expect(launch.overreachChosen()).toBeFalse();
  });

  it('does not send Overreach the Roll no longer holds', () => {
    give({ overreach: OVERREACH });
    popover.perform(GATE_ID, 'overreach');
    give();

    launch.ring();

    expect(sendAction.calls.mostRecent().args[1].overreach).toBeUndefined();
  });

  it('freezes the switch while an Expedition is out', () => {
    give({ overreach: OVERREACH });
    game.update({ expeditionActive: true });

    expect(action('overreach')!.usable).toBeFalse();
    expect(action('overreach')!.reason).toContain('An Expedition is already out.');
  });

  it('keeps three slots, each saying in words what it holds', () => {
    give({
      tally: tally({
        saved: [
          { slot: 1, salt: 3, saltPack: 2, saltBearer: 'TANK', panacea: true, overreach: true },
        ],
      }),
    });
    const lines = view().lines.filter((l) => l.id.startsWith('slot-'));

    expect(lines.length).toBe(3);
    expect(lines[0].value).toBe('Nothing is saved here.');
    expect(lines[1].value).toBe(
      '3 Salt on the Supplies, a Salt Pack of 2 carried by TANK, a Panacea, Overreach.',
    );
    expect(view().lines[0].heading).toBe("Quartermaster's Tally");
  });

  it('saves the current choices in a slot through SAVE_LOADOUT', () => {
    give({ overreach: OVERREACH, tally: tally() });
    popover.perform(GATE_ID, 'salt-more');
    popover.perform(GATE_ID, 'salt-more');
    popover.perform(GATE_ID, 'pack-more');
    popover.perform(GATE_ID, 'panacea');
    popover.perform(GATE_ID, 'overreach');

    expect(popover.perform(GATE_ID, 'loadout-save-2')).toBeTrue();

    expect(sendAction).toHaveBeenCalledWith(GameAction.SAVE_LOADOUT, {
      slot: 2,
      salt: 2,
      saltPack: 1,
      saltBearer: 'TANK',
      panacea: true,
      overreach: true,
    });
  });

  it('applies a slot by setting the Gate\'s choices, and states what it would set', () => {
    give({
      overreach: OVERREACH,
      tally: tally({
        saved: [
          { slot: 0, salt: 3, saltPack: 1, saltBearer: 'WARRIOR', panacea: true, overreach: true },
        ],
      }),
    });
    expect(action('loadout-apply-0')!.preview).toContain('Sets the Gate to: 3 Salt on the Supplies');

    expect(popover.perform(GATE_ID, 'loadout-apply-0')).toBeTrue();

    expect(launch.saltToUse()).toBe(3);
    expect(launch.packSalt()).toBe(1);
    expect(launch.packBearer()).toBe('WARRIOR');
    expect(launch.panaceaChosen()).toBeTrue();
    expect(launch.overreachChosen()).toBeTrue();
    expect(gauge('overreach')!.figure).toBe('Set');
  });

  it('clears a saved slot, and refuses an empty one in words', () => {
    give({
      tally: tally({
        saved: [{ slot: 0, salt: 0, saltPack: 0, saltBearer: null, panacea: false, overreach: false }],
      }),
    });

    expect(action('loadout-clear-1')!.usable).toBeFalse();
    expect(action('loadout-clear-1')!.reason).toBe('No Loadout is saved in slot 2.');
    expect(action('loadout-apply-1')!.usable).toBeFalse();
    expect(popover.perform(GATE_ID, 'loadout-apply-1')).toBeFalse();

    expect(popover.perform(GATE_ID, 'loadout-clear-0')).toBeTrue();
    expect(sendAction).toHaveBeenCalledWith(GameAction.CLEAR_LOADOUT, { slot: 0 });
  });

  it('holds Apply while an Expedition is out', () => {
    give({
      tally: tally({
        saved: [{ slot: 0, salt: 1, saltPack: 0, saltBearer: null, panacea: false, overreach: false }],
      }),
    });
    game.update({ expeditionActive: true });

    expect(action('loadout-apply-0')!.reason).toContain('An Expedition is already out.');
  });

  it('offers Muster Roll only when held, says why it cannot go yet, and sends MUSTER_ROLL', () => {
    const offer = {
      ready: false,
      startFloor: 0,
      partyUnits: 0,
      salt: 2,
      saltPack: 0,
      saltBearer: null,
      panacea: false,
      overreach: true,
    };
    give({ musterRoll: offer });

    expect(action('muster-roll')!.usable).toBeFalse();
    expect(action('muster-roll')!.reason).toBe('No Party has set out yet this Generation.');
    expect(popover.perform(GATE_ID, 'muster-roll')).toBeFalse();
    expect(view().lines.find((l) => l.id === 'muster')!.value).toBe(
      'No Party has set out yet this Generation.',
    );

    give({ musterRoll: { ...offer, ready: true, startFloor: 5 } });

    expect(action('muster-roll')!.usable).toBeTrue();
    expect(action('muster-roll')!.preview).toContain('Every Unit in the Reserves set out again from Floor 5.');
    expect(action('muster-roll')!.preview).toContain('Overreach.');
    expect(popover.perform(GATE_ID, 'muster-roll')).toBeTrue();
    expect(sendAction).toHaveBeenCalledWith(GameAction.MUSTER_ROLL, undefined);
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

import { TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState, PortState, VoyageView } from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { PopoverService } from '../../services/popover.service';
import { buildingView, buildingViews } from './building-registry';
import { BUILDINGS } from './buildings';
import { ledgerLines } from './records-buildings';
import { captionFor, captionText } from './village-captions';
import { buildVillage, EMPTY_VILLAGE } from './village-layout';
import { ofSubject, subjectOfVolume } from './village-subjects';
import { loadCargo, PORT, PORT_ID, resetCargo } from './voyage-building';

const voyage = (over: Partial<VoyageView> = {}): VoyageView => ({
  state: 'READY',
  relics: null,
  goods: null,
  ticksLeft: 0,
  ticksTotal: 150,
  riskTaken: null,
  seaRisk: 0.04,
  nextSeaRisk: 0.12,
  relicsPerLot: 5,
  saltPerLot: 10,
  sporesPerLot: 3,
  maxRelics: 50,
  riskReferenceRelics: 10,
  riskCap: 0.2,
  ...over,
});

describe('the Port building (the Voyage panel)', () => {
  let sendAction: jasmine.Spy;
  let game: GameStateService;

  const give = (over: Partial<GameState> = {}, port: PortState = 'BUILT') =>
    game.update({
      port: {
        state: port,
        footingExpeditions: 12,
        expeditionsCompleted: 14,
        materialsCost: 600,
        creditsCost: 300,
      },
      boat: { state: 'DOCKED', dockTicks: 600, ticksToDock: 0 },
      quarter: 'AUTUMN',
      nextQuarter: 'WINTER',
      seaRisk: 0.04,
      nextSeaRisk: 0.12,
      voyage: voyage(),
      resources: { MATERIALS: 900, RELICS: 23 },
      credit: 500,
      ...over,
    });
  const view = () => buildingView(PORT, game.registryState());
  const gauge = (id: string) => view().gauges.find((g) => g.id === id)!;
  const action = (id: string) => view().actions.find((a) => a.id === id)!;
  const line = (id: string) => view().lines.find((l) => l.id === id)!;
  const press = (id: string) =>
    TestBed.inject(PopoverService).perform(PORT_ID, id);

  beforeEach(() => {
    resetCargo();
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    give();
  });
  afterEach(() => resetCargo());

  it('is registered, and shown from the footing on, never hidden or in a Trial', () => {
    expect(BUILDINGS).toContain(PORT);
    const shown = () => buildingViews(BUILDINGS, game.registryState()).some((b) => b.id === PORT_ID);
    expect(shown()).toBeTrue();
    give({}, 'FOOTING');
    expect(shown()).toBeTrue();
    give({}, 'HIDDEN');
    expect(shown()).toBeFalse();
    give({ trial: {} as GameState['trial'] }, 'BUILT');
    expect(shown()).toBeFalse();
  });

  it('is the pier and hull in the scene: one building, with a pad to point at', () => {
    const volumes = buildVillage({ ...EMPTY_VILLAGE, port: 'BUILT', boatDocked: true });
    for (const id of ['port-pier-deck', 'port-boat-hull', 'port-hit']) {
      expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: PORT_ID });
    }
    const drawn = ofSubject(volumes, PORT_ID, 'building');
    expect(drawn.some((v) => v.id === 'port-boat-hull')).toBeTrue();
    expect(drawn.some((v) => v.id === 'port-hit')).toBeFalse();
    expect(volumes.find((v) => v.id === 'port-hit')?.alpha).toBe(0);
  });

  describe('the Boat in words', () => {
    const say = () => line('boat').value;

    it('says the Port is not finished while a footing', () => {
      give({}, 'FOOTING');
      expect(say()).toBe('The Port is not finished. A footing and a short pier.');
    });

    it('says the Boat is due, and when', () => {
      give({
        boat: { state: 'DUE', dockTicks: 600, ticksToDock: 300 },
        voyage: voyage({ state: 'UNAVAILABLE' }),
      });
      expect(say()).toBe('The Boat has not come. It is due in about 5 minutes.');
    });

    it('says the Boat lies docked', () => {
      expect(say()).toBe('The Boat lies at the pier, empty.');
    });

    it('says sailing out and then on the way home, with the time left', () => {
      give({ voyage: voyage({ state: 'SAILING', relics: 10, goods: 'SALT', ticksLeft: 120, riskTaken: 0.08 }) });
      expect(say()).toBe('The Boat is at sea with 10 Relics, for Salt. It is due back in about 2 minutes.');
      give({ voyage: voyage({ state: 'SAILING', relics: 10, goods: 'SPORES', ticksLeft: 50 }) });
      expect(say()).toBe('The Boat is on its way home with 10 Relics, for Spores. It is due in less than a minute.');
    });
  });

  describe('the sea', () => {
    it('states the danger now and next Quarter as exact figures and a gauge', () => {
      expect(gauge('sea-now').figure).toBe('4%');
      expect(gauge('sea-now').fill).toBeCloseTo(0.2, 5);
      expect(gauge('sea-next').figure).toBe('12%');
      expect(gauge('sea-next').fill).toBeCloseTo(0.6, 5);
      expect(gauge('sea-next').leaves).toBe('In Winter.');
      expect(line('quarters').value).toBe('Now, in Autumn: 4%. In Winter: 12%.');
    });

    it('says that the risk rises with the cargo, and states the cap', () => {
      expect(line('quarters').detail).toContain('scaled by the cargo');
      expect(line('quarters').detail).toContain('never above 20%');
      expect(gauge('sea-now').leaves).toContain('rises with the cargo');
    });

    it('scales the chance for this cargo by its value and caps it', () => {
      expect(gauge('cargo').leaves).toContain('Chance the sea takes this cargo: 0%');
      loadCargo(2);
      expect(gauge('cargo').leaves).toContain('Chance the sea takes this cargo: 4%');
      game.update({ voyage: voyage({ seaRisk: 0.18 }), resources: { MATERIALS: 0, RELICS: 50 } });
      loadCargo(10);
      expect(gauge('cargo').figure).toBe('50 of 50 Relics');
      expect(gauge('cargo').leaves).toContain('20%');
    });
  });

  describe('loading', () => {
    it('loads and unloads Relics in lots, and the figures follow', () => {
      expect(gauge('cargo').figure).toBe('0 of 50 Relics');
      expect(press('load-cargo')).toBeTrue();
      expect(press('load-cargo')).toBeTrue();
      expect(gauge('cargo').figure).toBe('10 of 50 Relics');
      expect(line('loaded').value).toBe('10 Relics, for Salt: 20 Salt on return.');
      expect(press('unload-cargo')).toBeTrue();
      expect(gauge('cargo').figure).toBe('5 of 50 Relics');
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('refuses to load past what the Vault holds, in words', () => {
      loadCargo(4);
      expect(action('load-cargo').usable).toBeFalse();
      expect(action('load-cargo').reason).toBe('The Vault holds 23 Relics. No more can be loaded.');
      expect(press('load-cargo')).toBeFalse();
      expect(gauge('cargo').figure).toBe('20 of 50 Relics');
    });

    it('refuses to load past the Boat', () => {
      game.update({ resources: { MATERIALS: 0, RELICS: 500 } });
      loadCargo(10);
      expect(action('load-cargo').reason).toBe('The Boat carries at most 50 Relics.');
    });

    it('refuses to unload nothing', () => {
      expect(action('unload-cargo').usable).toBeFalse();
      expect(action('unload-cargo').reason).toBe('Nothing is loaded.');
    });

    it('never shows more than the Vault holds when Relics were spent meanwhile', () => {
      loadCargo(4);
      game.update({ resources: { MATERIALS: 0, RELICS: 7 } });
      expect(gauge('cargo').figure).toBe('5 of 50 Relics');
    });

    it('chooses Salt or Spores and says what each returns', () => {
      expect(action('bring-salt').label).toBe('Bring back Salt (chosen)');
      expect(action('bring-salt').reason).toBe('Salt is already chosen.');
      expect(action('bring-spores').preview).toBe('3 Spores for each 5 Relics.');
      press('load-cargo');
      expect(press('bring-spores')).toBeTrue();
      expect(action('bring-spores').label).toBe('Bring back Spores (chosen)');
      expect(line('loaded').value).toBe('5 Relics, for Spores: 3 Spores on return.');
    });
  });

  describe('sending', () => {
    it('sends the cargo and goods, and empties the draft', () => {
      loadCargo(2, 'SPORES');
      expect(action('send-voyage').label).toBe('Send the Boat with 10 Relics, for Spores');
      expect(action('send-voyage').preview).toContain('6 Spores');
      expect(action('send-voyage').preview).toContain('4% chance');
      expect(press('send-voyage')).toBeTrue();
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.SEND_VOYAGE, {
        relics: 10,
        goods: 'SPORES',
      });
      expect(gauge('cargo').figure).toBe('0 of 50 Relics');
    });

    it('is refused, in words, with nothing loaded', () => {
      expect(action('send-voyage').usable).toBeFalse();
      expect(action('send-voyage').reason).toBe('Nothing is loaded. Load Relics first.');
      expect(press('send-voyage')).toBeFalse();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('is refused while the Boat is away, and says when it returns', () => {
      loadCargo(1);
      give({ voyage: voyage({ state: 'SAILING', relics: 10, goods: 'SALT', ticksLeft: 90 }) });
      expect(action('send-voyage').reason).toBe('The Boat is away. It returns in about 2 minutes.');
      expect(action('load-cargo').reason).toBe('The Boat is away. It returns in about 2 minutes.');
      expect(press('send-voyage')).toBeFalse();
    });

    it('is refused while the Boat has not come', () => {
      loadCargo(1);
      give({
        boat: { state: 'DUE', dockTicks: 600, ticksToDock: 61 },
        voyage: voyage({ state: 'UNAVAILABLE' }),
      });
      expect(action('send-voyage').reason).toBe('The Boat has not come. It is due in about 2 minutes.');
    });

    it('is refused while time is held, and while the Port is a footing', () => {
      loadCargo(1);
      game.setSession({ resumed: false, savedAt: null, schema: 1, notices: [], held: true });
      expect(action('send-voyage').reason).toBe('Time is held. Nothing sails. Resume first.');
      game.setSession(null);
      give({}, 'FOOTING');
      expect(buildingView(PORT, game.registryState()).actions.map((a) => a.id)).toEqual(['finish-port']);
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('is refused when the Campaign has ended', () => {
      loadCargo(1);
      give({ gameOutcome: 'EXODUS' });
      expect(action('send-voyage').reason).toBe('The Campaign has ended. Nothing sails.');
    });
  });

  describe('the sailing Voyage', () => {
    beforeEach(() =>
      give({
        voyage: voyage({ state: 'SAILING', relics: 10, goods: 'SALT', ticksLeft: 120, riskTaken: 0.08 }),
      }),
    );

    it('shows the time left as a gauge, and the cargo and its chance', () => {
      expect(gauge('time').figure).toBe('about 2 minutes');
      expect(gauge('time').fill).toBeCloseTo(0.2, 5);
      expect(view().gauges.map((g) => g.id)).not.toContain('cargo');
      expect(line('at-sea').value).toBe('10 Relics, to return 20 Salt.');
      expect(line('at-sea').detail).toContain('8%');
      expect(line('at-sea').detail).toContain('Ledger');
    });
  });

  describe('finishing the Port', () => {
    it('states the cost and sends BUILD_PORT', () => {
      give({}, 'FOOTING');
      expect(action('finish-port').label).toBe('Finish the Port, 600 Materials and 300 Credits');
      expect(press('finish-port')).toBeTrue();
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.BUILD_PORT, undefined);
    });

    it('refuses when the Vault cannot pay, with the figures', () => {
      give({ resources: { MATERIALS: 100, RELICS: 0 } }, 'FOOTING');
      expect(action('finish-port').reason).toContain('costs 600 Materials and 300 Credits');
      expect(action('finish-port').reason).toContain('holds 100 Materials and 500 Credits');
    });
  });

  describe('the hover caption', () => {
    const say = () => captionText(captionFor({ kind: 'building', id: PORT_ID }, game.registryState())!);

    it('says where the Boat is and the danger at sea', () => {
      expect(say()).toContain('A Boat lies at the pier.');
      expect(say()).toContain('Danger at sea 4%, 12% next Quarter.');
    });

    it('says the Boat is away', () => {
      give({ voyage: voyage({ state: 'SAILING', relics: 5, goods: 'SALT', ticksLeft: 100 }) });
      expect(say()).toContain('The Boat is away. It returns in about 2 minutes.');
    });
  });

  it('writes a lost cargo as one plain Ledger line, as a loss', () => {
    const text = 'The sea took the cargo of the Voyage sailed in Autumn. The Boat comes home empty.';
    game.update({ ledger: [{ text, loss: true }] });
    expect(ledgerLines(game.state())).toEqual([{ text, loss: true }]);
  });

  it('is in the list view: every control is an action of the building', () => {
    const ids = view().actions.map((a) => a.id);
    expect(ids).toEqual(['load-cargo', 'unload-cargo', 'bring-salt', 'bring-spores', 'send-voyage']);
  });
});

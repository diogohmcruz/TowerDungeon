import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import {
  LIST_VIEW_STORAGE,
  ListViewService,
  listViewWanted,
} from '../../services/list-view.service';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { PopoverService } from '../../services/popover.service';
import { ZoomService } from '../../services/zoom.service';
import { actionSpecs, buildingViews } from '../../three/village/building-registry';
import { BUILDINGS } from '../../three/village/buildings';
import { ListView, listKeyIntent } from './list-view';

describe('ListView', () => {
  let fixture: ComponentFixture<ListView>;
  let sendAction: jasmine.Spy;
  let list: ListViewService;

  const el = () => fixture.nativeElement as HTMLElement;
  const settle = () => {
    TestBed.flushEffects();
    fixture.detectChanges();
  };
  const key = (k: string, init: KeyboardEventInit = {}) =>
    document.dispatchEvent(new KeyboardEvent('keydown', { key: k, ...init }));

  beforeEach(() => {
    localStorage.removeItem(LIST_VIEW_STORAGE);
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    const game = TestBed.inject(GameStateService);
    game.update({
      village: { ...game.state().village, food: 400.25 },
      netFoodPerSecond: 1.2,
    });
    list = TestBed.inject(ListViewService);
    fixture = TestBed.createComponent(ListView);
    settle();
  });
  afterEach(() => localStorage.removeItem(LIST_VIEW_STORAGE));

  it('is hidden by default', () => {
    expect(el().querySelector('.list')).toBeNull();
  });

  it('carries the Expedition numbers (the HUD card left the card layer) while one is out', () => {
    key('l');
    settle();
    expect(el().querySelector('.expedition-hud')).toBeNull();
    TestBed.inject(GameStateService).update({
      expeditionActive: true,
      supplies: 120,
      maxSupplies: 200,
      carriedCredits: 340,
      tower: { currentFloor: 4, floors: { 4: { boss: true, enemies: [] } } } as any,
    });
    settle();
    const hud = el().querySelector('.expedition-hud');
    expect(hud?.textContent).toContain('Floor 4');
    expect(hud?.textContent).toContain('GUARDIAN');
  });

  it('opens with L and closes with L or Escape', () => {
    key('l');
    settle();
    expect(el().querySelector('.list')).not.toBeNull();
    key('l');
    settle();
    expect(el().querySelector('.list')).toBeNull();
    key('L');
    settle();
    key('Escape');
    settle();
    expect(el().querySelector('.list')).toBeNull();
  });

  it('opens from the skip button a screen reader announces', () => {
    (el().querySelector('.skip') as HTMLButtonElement).click();
    settle();
    expect(el().querySelector('.list')).not.toBeNull();
  });

  it('lists every registered building with its gauges and actions', () => {
    list.show();
    settle();
    const text = el().querySelector('.list')!.textContent!;
    expect(text).toContain('The Pantry silo');
    expect(text).toContain('Food');
    expect(text).toContain('400.25');
    expect(text).toContain('+1.20/s');
    expect(text).toContain('Sell Food');
  });

  it('keeps the Ledger and the Expedition Summary, now off the card layer', () => {
    TestBed.inject(GameStateService).update({
      tradeViews: [
        {
          trade: 'SCRIBE',
          plural: 'Scribes',
          workplace: "Scribe's Study",
          hands: 1,
          posts: 10,
          settling: 0,
          settlingSeconds: 0,
        },
      ],
      villageTierLevel: 2,
      ledger: [{ text: 'Starvation. 1 Breaker lost.', loss: true }],
    });
    key('l');
    settle();
    const text = el().textContent!;
    expect(text).toContain("The Scribe's Study");
    expect(text).toContain('- Starvation. 1 Breaker lost.');
    expect(text).toContain('The Gate');
    expect(text).toContain('No Expedition has come back yet.');
  });

  it('runs an action exactly as the popover does', () => {
    list.show();
    settle();
    const popover = TestBed.inject(PopoverService);
    const viaPopover = spyOn(popover, 'perform').and.callThrough();
    const button = el().querySelector('.building button.btn') as HTMLButtonElement;
    button.click();
    expect(viaPopover).toHaveBeenCalledOnceWith('SILO', 'sell-food');
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.SELL_FOOD, undefined);
  });

  it('says why an action is refused, and sends nothing', () => {
    const game = TestBed.inject(GameStateService);
    game.update({ village: { ...game.state().village, food: 0 } });
    list.show();
    settle();
    const button = el().querySelector('.building button.btn') as HTMLButtonElement;
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(el().querySelector('.reason')?.textContent).toContain('Nothing is sold');
    button.click();
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('takes the popover out of the way when it opens', () => {
    const popover = TestBed.inject(PopoverService);
    popover.openFor({ kind: 'building', id: 'SILO' });
    expect(popover.open()).not.toBeNull();
    list.show();
    expect(popover.open()).toBeNull();
  });

  it('remembers the choice to open at the start', () => {
    list.show();
    settle();
    const box = el().querySelector('input[type=checkbox]') as HTMLInputElement;
    box.checked = true;
    box.dispatchEvent(new Event('change'));
    expect(localStorage.getItem(LIST_VIEW_STORAGE)).toBe('on');
  });

  describe('keys', () => {
    const event = (k: string, init: KeyboardEventInit = {}, target?: HTMLElement) => {
      const e = new KeyboardEvent('keydown', { key: k, cancelable: true, ...init });
      if (target) Object.defineProperty(e, 'target', { value: target });
      return e;
    };
    it('ignores a modifier, typing and other keys', () => {
      expect(listKeyIntent(event('l'))).toBe('toggle');
      expect(listKeyIntent(event('Escape'))).toBe('close');
      expect(listKeyIntent(event('l', { ctrlKey: true }))).toBeNull();
      expect(listKeyIntent(event('l', { metaKey: true }))).toBeNull();
      expect(listKeyIntent(event('h'))).toBeNull();
      expect(listKeyIntent(event('l', {}, document.createElement('input')))).toBeNull();
      expect(listKeyIntent(event('l', {}, document.createElement('textarea')))).toBeNull();
    });
  });

  describe('opening by itself', () => {
    it('follows what the player has said, not a guess', () => {
      expect(listViewWanted('', null)).toBeFalse();
      expect(listViewWanted('', 'off')).toBeFalse();
      expect(listViewWanted('', 'on')).toBeTrue();
      expect(listViewWanted('?list=1', null)).toBeTrue();
      expect(listViewWanted('?list=0', 'on')).toBeFalse();
    });
  });

  describe('the one DOM route (the card layer is gone)', () => {
    const buttons = (section: Element) =>
      Array.from(section.querySelectorAll<HTMLButtonElement>('app-action-row button.btn'));
    const sectionOf = (title: string) =>
      Array.from(el().querySelectorAll('section.building')).find(
        (s) => s.querySelector('h3')?.textContent?.trim() === title,
      );

    it('lists every registered building, every gauge and every action, however the state stands', () => {
      // Every building made visible, so none hides behind the state: the list must still carry it.
      const popover = TestBed.inject(PopoverService);
      popover.registry = BUILDINGS.map((d) => ({ ...d, visible: undefined }));
      // The registry is a plain field: a change of state makes the service read it again.
      TestBed.inject(GameStateService).update({ credit: 1 });
      list.show();
      settle();
      const state = TestBed.inject(GameStateService).registryState();
      const views = buildingViews(popover.registry, state);
      expect(views.map((v) => v.id)).toEqual(BUILDINGS.map((d) => d.id));
      for (const def of popover.registry) {
        const view = views.find((v) => v.id === def.id)!;
        const section = sectionOf(def.title);
        expect(section).withContext(def.id).toBeTruthy();
        for (const gauge of view.gauges) {
          expect(section!.textContent).withContext(`${def.id} ${gauge.label}`).toContain(gauge.label);
        }
        const shown = actionSpecs(def, state).filter((a) => (a.visible ? a.visible(state) : true));
        expect(buttons(section!).length).withContext(`${def.id} actions`).toBe(shown.length);
      }
    });

    it('lists the buildings the state shows, and refuses nothing in silence', () => {
      list.show();
      settle();
      const popover = TestBed.inject(PopoverService);
      for (const view of popover.buildings()) {
        const section = sectionOf(view.title);
        expect(section).withContext(view.id).toBeTruthy();
        expect(buttons(section!).length).withContext(view.id).toBe(view.actions.length);
        for (const action of view.actions.filter((a) => !a.usable)) {
          expect(section!.textContent).withContext(action.label).toContain(action.reason!);
        }
      }
    });

    it('carries the bell, the lamp and the Start Floor at the Gate, as the picture has them', () => {
      const game = TestBed.inject(GameStateService);
      game.update({
        shortcuts: [
          { id: 'NARROW_STAIR', name: 'Narrow Stair', description: 'A quicker way.', trigger: 'Slay 10', floor: 10, unlocked: true },
        ],
        homeUnitCount: 3,
      });
      list.show();
      settle();
      const gate = sectionOf('The Gate: the Loadout')!;
      const labels = buttons(gate).map((b) => b.textContent!.trim());
      expect(labels).toContain('Ring the bell: set out');
      expect(labels).toContain('Ring the bell: send a Reinforcement Wave');
      expect(labels).toContain('Light the lamp: extract');
      expect(labels).toContain('Start at Floor 1, the Base (chosen)');
      expect(labels).toContain('Start at Floor 10, Narrow Stair');

      // Choosing a Start Floor and ringing the bell send what the picture sends.
      buttons(gate).find((b) => b.textContent!.includes('Narrow Stair'))!.click();
      settle();
      expect(gate.textContent).toContain('Start at Floor 10, Narrow Stair (chosen)');
      buttons(gate).find((b) => b.textContent!.includes('Ring the bell: set out'))!.click();
      expect(sendAction).toHaveBeenCalledWith(
        GameAction.INVADE,
        jasmine.objectContaining({ startFloor: 10 }),
      );
    });

    it('says why the bell, the lamp and the Wave cannot be used, and sends nothing', () => {
      list.show();
      settle();
      const gate = sectionOf('The Gate: the Loadout')!;
      expect(gate.textContent).toContain('No Expedition is out. There is no one to call home.');
      expect(gate.textContent).toContain('No Expedition is out. There is no one to follow.');
      buttons(gate).find((b) => b.textContent!.includes('extract'))!.click();
      buttons(gate).find((b) => b.textContent!.includes('Wave'))!.click();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('lights the lamp and sends a Wave from the list while an Expedition is out', () => {
      TestBed.inject(GameStateService).update({ expeditionActive: true, homeUnitCount: 2 });
      list.show();
      settle();
      const gate = sectionOf('The Gate: the Loadout')!;
      buttons(gate).find((b) => b.textContent!.includes('extract'))!.click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.EXTRACT, null);
      buttons(gate).find((b) => b.textContent!.includes('Wave'))!.click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.REINFORCE, { units: {} });
    });

    it('offers Resume for a Held Party, and only then', () => {
      list.show();
      settle();
      expect(el().textContent).not.toContain('The Party has not moved.');
      TestBed.inject(GameStateService).setSession({
        resumed: true, savedAt: null, schema: 1, notices: [], held: true,
      });
      settle();
      expect(el().textContent).toContain('The Party has not moved.');
      const resume = Array.from(el().querySelectorAll('button')).find(
        (b) => b.textContent!.trim() === 'Resume',
      )!;
      resume.click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
    });

    it('writes each ending in words, with the Toll, and never congratulates', () => {
      const game = TestBed.inject(GameStateService);
      for (const [outcome, heading] of [
        ['VICTORY', 'The Heirs'],
        ['DEFEAT', 'The Second Company'],
        ['EXODUS', 'The Flight'],
      ]) {
        game.update({ gameOutcome: outcome, pending: null });
        list.show();
        settle();
        const section = Array.from(el().querySelectorAll('section.building')).find(
          (s) => s.getAttribute('aria-label') === 'The Campaign has ended',
        )!;
        expect(section.querySelector('h3')?.textContent).toBe(heading);
        expect(section.textContent).not.toMatch(/Ignelia is free|Conquered|Prestige/);
      }
      game.update({
        gameOutcome: 'EXODUS',
        pending: {
          outcome: 'EXODUS', generation: 1, legacy: 3, pity: false, guardiansSlain: 0,
          highestFloor: 12, expeditions: 5,
          toll: { toll: 30, legacy: 3 } as never,
        },
      });
      settle();
      expect(el().textContent).toContain('Toll 30.0. Legacy 3.');
    });

    it('states what the Stock card stated: food flows, the Stockroom, the lost, the Tier and the Tally', () => {
      list.show();
      settle();
      const text = el().textContent!;
      for (const words of [
        'Fields +',
        'Upkeep (',
        'Stockroom: Knowledge',
        'Lost to Starvation:',
        'Village Tier',
        'Tally (this Campaign)',
        'enemies slain',
      ]) {
        expect(text).withContext(words).toContain(words);
      }
    });

    it('lists the Shortcuts, open or not, and the Army with its Spore Doses', () => {
      const game = TestBed.inject(GameStateService);
      game.update({
        shortcuts: [
          { id: 'NARROW_STAIR', name: 'Narrow Stair', description: 'A quicker way.', trigger: 'Slay 10', floor: 10, unlocked: true },
          { id: 'DEEP_DOOR', name: 'Deep Door', description: 'Shut.', trigger: 'Complete 3 Expeditions', floor: 20, unlocked: false },
        ],
        units: {
          ...game.state().units,
          WARRIOR: [
            {
              id: 7, name: 'Edda', currentHealth: 5, maxHealth: 20,
              stats: { type: 'WARRIOR', health: 20, damage: 4 },
              parts: [{ kind: 'LEFT_ARM', lost: false, currentHealth: 1, maxHealth: 5 }],
            } as never,
          ],
        },
        village: { ...game.state().village, spores: 2 },
      });
      list.show();
      settle();
      expect(el().querySelectorAll('app-shortcut-card').length).toBe(2);
      expect(el().textContent).toContain('✓ open · Floor 10');
      expect(el().textContent).toContain('Complete 3 Expeditions');
      const name = el().querySelector('app-army .toggle') as HTMLButtonElement;
      expect(name.textContent).toContain('Edda');
      name.click();
      settle();
      (el().querySelector('app-army .dose') as HTMLButtonElement).click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.REMEDY, { unitId: 7 });
    });

    it('is hidden under a zoomed view, and comes back open', () => {
      list.show();
      settle();
      const game = TestBed.inject(GameStateService);
      game.update({ expeditionActive: true });
      const zoom = TestBed.inject(ZoomService);
      zoom.toggleCloseup();
      settle();
      expect((el().querySelector('.list') as HTMLElement).hidden).toBeTrue();
      zoom.release();
      settle();
      expect((el().querySelector('.list') as HTMLElement).hidden).toBeFalse();
    });
  });
});

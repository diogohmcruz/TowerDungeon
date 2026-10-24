import { TestBed } from '@angular/core/testing';

import { GameAction } from '../interfaces/game-action.enum';
import { Squad } from '../interfaces/game-state';
import { GameStateService } from './game-state.service';
import { UnitViewService } from './unit-view.service';
import { GameWebSocketService } from './game-web-socket.service';
import { ListViewService } from './list-view.service';
import { MenusService } from './menus.service';
import { StageService } from './stage.service';
import { ZoomService } from './zoom.service';
import { flushEffects } from '../testing/flush-effects';

describe('ZoomService', () => {
  let sendAction: jasmine.Spy;
  let zoom: ZoomService;
  let state: GameStateService;
  let stage: StageService;
  let menus: MenusService;
  let list: ListViewService;

  const session = (held: boolean) => ({
    resumed: false,
    savedAt: null,
    schema: 1,
    notices: [],
    held,
  });

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    state = TestBed.inject(GameStateService);
    stage = TestBed.inject(StageService);
    menus = TestBed.inject(MenusService);
    list = TestBed.inject(ListViewService);
    list.show();
    zoom = TestBed.inject(ZoomService);
    state.update({ expeditionActive: true });
    state.setSession(session(false));
    flushEffects();
  });
  afterEach(() => stage.setLaunchBarHidden(false));

  it('holds and enters the closeup on a click, hiding the menus', () => {
    zoom.toggleCloseup();
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.HOLD, null);
    expect(zoom.level()).toBe('closeup');
    expect(menus.showing()).toBeFalse();
  });

  it('resumes and returns to battle on the second click, restoring the open list view', () => {
    zoom.toggleCloseup();
    state.setSession(session(true));
    zoom.toggleCloseup();
    expect(sendAction.calls.allArgs()).toEqual([
      [GameAction.HOLD, null],
      [GameAction.RESUME, null],
    ]);
    expect(zoom.level()).toBe('battle');
    expect(menus.showing()).toBeTrue();
  });

  it('leaves the menus away when the player had put them away', () => {
    menus.toggle();
    expect(menus.showing()).toBeFalse();
    zoom.toggleCloseup();
    zoom.back();
    expect(menus.showing()).toBeFalse();
    expect(list.open()).toBeFalse();
  });

  it('steps back (Escape) and does nothing at battle', () => {
    zoom.back();
    expect(sendAction).not.toHaveBeenCalled();
    zoom.toggleCloseup();
    state.setSession(session(true));
    zoom.back();
    expect(zoom.level()).toBe('battle');
    expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
  });

  it('does not enter without an Expedition', () => {
    state.update({ expeditionActive: false });
    zoom.toggleCloseup();
    expect(zoom.level()).toBe('battle');
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('leaves a Party that was already Held to the player to resume', () => {
    state.setSession(session(true));
    zoom.toggleCloseup();
    zoom.toggleCloseup();
    expect(sendAction).not.toHaveBeenCalled();
  });

  describe('the unit view', () => {
    const hero = { kind: 'unit', id: 3 } as const;
    const ghoul = { kind: 'enemy', id: 9 } as const;

    it('goes from battle straight to the unit, holding time once', () => {
      zoom.focusUnit(hero);
      expect(zoom.level()).toBe('unit');
      expect(zoom.focus()).toEqual(hero);
      expect(menus.showing()).toBeFalse();
      expect(sendAction.calls.allArgs()).toEqual([[GameAction.HOLD, null]]);
    });

    it('steps back unit, closeup, battle, resuming only at the end', () => {
      zoom.focusUnit(hero);
      state.setSession(session(true));
      zoom.back();
      expect(zoom.level()).toBe('closeup');
      expect(zoom.focus()).toBeNull();
      expect(sendAction).toHaveBeenCalledTimes(1);
      zoom.back();
      expect(zoom.level()).toBe('battle');
      expect(sendAction.calls.allArgs()).toEqual([
        [GameAction.HOLD, null],
        [GameAction.RESUME, null],
      ]);
      expect(menus.showing()).toBeTrue();
    });

    it('opens from the closeup without holding twice', () => {
      zoom.toggleCloseup();
      state.setSession(session(true));
      zoom.focusUnit(ghoul);
      expect(zoom.level()).toBe('unit');
      expect(sendAction).toHaveBeenCalledTimes(1);
    });

    it('a second choice of the same unit steps back to the closeup', () => {
      zoom.focusUnit(hero);
      zoom.focusUnit({ ...hero });
      expect(zoom.level()).toBe('closeup');
    });

    it('a different unit moves the view without leaving it', () => {
      zoom.focusUnit(hero);
      zoom.focusUnit(ghoul);
      expect(zoom.level()).toBe('unit');
      expect(zoom.focus()).toEqual(ghoul);
      expect(sendAction).toHaveBeenCalledTimes(1);
    });

    it('a click on empty space (the toggle) steps back one rung', () => {
      zoom.focusUnit(hero);
      zoom.toggleCloseup();
      expect(zoom.level()).toBe('closeup');
    });

    it('opens the view of a Party member chosen in an army list, and lets the choice go', () => {
      state.update({
        unitsOnTower: { WARRIOR: [{ id: 3 }] } as unknown as Squad,
      });
      const unitView = TestBed.inject(UnitViewService);
      unitView.choose(3);
      flushEffects();
      expect(zoom.level()).toBe('unit');
      expect(zoom.focus()).toEqual({ kind: 'unit', id: 3 });
      expect(unitView.chosen()).toBeNull();
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.HOLD, null);
    });

    it('ignores a Reserve chosen in the panel: it is not drawn as a figure', () => {
      state.update({
        unitsOnTower: { WARRIOR: [{ id: 3 }] } as unknown as Squad,
      });
      const unitView = TestBed.inject(UnitViewService);
      unitView.choose(77);
      flushEffects();
      expect(zoom.level()).toBe('battle');
      expect(unitView.chosen()).toBeNull();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('does not open without an Expedition', () => {
      state.update({ expeditionActive: false });
      zoom.focusUnit(hero);
      expect(zoom.level()).toBe('battle');
      expect(zoom.focus()).toBeNull();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('leaves a Party that was already Held to the player to resume', () => {
      state.setSession(session(true));
      zoom.focusUnit(hero);
      zoom.back();
      zoom.back();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('keeps the anchor only while a unit is shown, ignoring sub-pixel moves', () => {
      zoom.focusUnit(hero);
      const rect = { left: 10, top: 20, right: 50, bottom: 90 };
      zoom.setAnchor(rect);
      const first = zoom.anchor();
      zoom.setAnchor({ ...rect, left: 10.2 });
      expect(zoom.anchor()).toBe(first);
      zoom.setAnchor({ ...rect, left: 14 });
      expect(zoom.anchor()?.left).toBe(14);
      zoom.back();
      expect(zoom.anchor()).toBeNull();
    });

    it('lets go of the hold and the view when the Expedition ends', () => {
      zoom.focusUnit(hero);
      state.setSession(session(true));
      state.update({ expeditionActive: false });
      state.setSession(session(false));
      flushEffects();
      expect(zoom.level()).toBe('battle');
      expect(zoom.focus()).toBeNull();
    });
  });

  it('releases the hold when leaving the page', () => {
    zoom.toggleCloseup();
    state.setSession(session(true));
    window.dispatchEvent(new Event('pagehide'));
    expect(zoom.level()).toBe('battle');
    expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
  });

  it('releases without a RESUME when the Expedition ends and the server has let go', () => {
    zoom.toggleCloseup();
    state.setSession(session(true));
    state.update({ expeditionActive: false });
    state.setSession(session(false));
    flushEffects();
    expect(zoom.level()).toBe('battle');
    expect(menus.showing()).toBeTrue();
    expect(sendAction).toHaveBeenCalledTimes(1);
  });

  describe('the Village closeup (#130)', () => {
    beforeEach(() => {
      state.update({ expeditionActive: false });
      flushEffects();
    });

    it('toggles on a click, holds no time and hides no menu', () => {
      zoom.toggleCloseup();
      expect(zoom.villageCloseup()).toBeTrue();
      expect(zoom.level()).toBe('battle');
      expect(sendAction).not.toHaveBeenCalled();
      expect(menus.showing()).toBeTrue();
      zoom.toggleCloseup();
      expect(zoom.villageCloseup()).toBeFalse();
      expect(sendAction).not.toHaveBeenCalled();
    });

    it('is one step back with Escape', () => {
      zoom.toggleVillageCloseup();
      zoom.back();
      expect(zoom.villageCloseup()).toBeFalse();
    });

    it('is gone when an Expedition sets out, and the Tower ladder is as before', () => {
      zoom.toggleVillageCloseup();
      state.update({ expeditionActive: true });
      flushEffects();
      expect(zoom.villageCloseup()).toBeFalse();
      zoom.toggleVillageCloseup();
      expect(zoom.villageCloseup()).toBeFalse();
      zoom.toggleCloseup();
      expect(zoom.level()).toBe('closeup');
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.HOLD, null);
    });
  });
});

import { TestBed } from '@angular/core/testing';

import { GameAction } from '../interfaces/game-action.enum';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import { StageService } from './stage.service';
import { ZoomService } from './zoom.service';

describe('ZoomService', () => {
  let sendAction: jasmine.Spy;
  let zoom: ZoomService;
  let state: GameStateService;
  let stage: StageService;

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
    zoom = TestBed.inject(ZoomService);
    state.update({ expeditionActive: true });
    state.setSession(session(false));
    TestBed.flushEffects();
  });
  afterEach(() => stage.showMenus());

  it('holds and enters the closeup on a click, hiding the menus', () => {
    zoom.toggleCloseup();
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.HOLD, null);
    expect(zoom.level()).toBe('closeup');
    expect(stage.menusHidden()).toBeTrue();
  });

  it('resumes and returns to battle on the second click, restoring the menus', () => {
    zoom.toggleCloseup();
    state.setSession(session(true));
    zoom.toggleCloseup();
    expect(sendAction.calls.allArgs()).toEqual([
      [GameAction.HOLD, null],
      [GameAction.RESUME, null],
    ]);
    expect(zoom.level()).toBe('battle');
    expect(stage.menusHidden()).toBeFalse();
  });

  it('leaves the menus hidden when the player had hidden them', () => {
    stage.setMenusHidden(true);
    zoom.toggleCloseup();
    zoom.back();
    expect(stage.menusHidden()).toBeTrue();
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
    TestBed.flushEffects();
    expect(zoom.level()).toBe('battle');
    expect(stage.menusHidden()).toBeFalse();
    expect(sendAction).toHaveBeenCalledTimes(1);
  });
});

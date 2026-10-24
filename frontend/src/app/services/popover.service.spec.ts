import { TestBed } from '@angular/core/testing';

import { GameAction } from '../interfaces/game-action.enum';
import { defineBuilding } from '../three/village/building-registry';
import { SILO } from '../three/village/buildings';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import {
  CLICK_MAX_MS,
  CLICK_SLOP_PX,
  isClick,
  PopoverService,
} from './popover.service';
import { StageService } from './stage.service';
import { VillageSelectionService } from './village-selection.service';
import { ZoomService } from './zoom.service';

const silo = { kind: 'building', id: 'SILO' } as const;
const CELLAR = defineBuilding({
  id: 'CELLAR',
  title: 'The Spore Cellar',
  gauges: [],
  actions: [
    {
      id: 'ring',
      label: 'Ring',
      availability: () => ({ usable: true, reason: null }),
      run: (c) => c.send(GameAction.BUY_VILLAGERS),
    },
  ],
});
const cellar = { kind: 'building', id: 'CELLAR' } as const;

describe('PopoverService', () => {
  let sendAction: jasmine.Spy;
  let popover: PopoverService;
  let game: GameStateService;
  let zoom: ZoomService;

  const press = (x = 10, y = 10, at = 100) => popover.notePress({ x, y, at });
  const click = (x = 10, y = 10, at = 200) => ({
    clientX: x,
    clientY: y,
    timeStamp: at,
  });
  const frame = () => TestBed.flushEffects();

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    zoom = TestBed.inject(ZoomService);
    popover = TestBed.inject(PopoverService);
    popover.registry = [SILO, CELLAR];
    game.update({ village: { ...game.state().village, food: 50 } });
    game.setSession({ resumed: false, savedAt: null, schema: 1, notices: [], held: false });
    frame();
  });
  afterEach(() => TestBed.inject(StageService).showMenus());

  it('opens a registered building on a click and selects it for the outline', () => {
    press();
    expect(popover.sceneClick(silo, click())).toBeTrue();
    expect(popover.open()?.id).toBe('SILO');
    expect(TestBed.inject(VillageSelectionService).selected()?.id).toBe('SILO');
  });

  it('closes on a second click on the same building', () => {
    press();
    popover.sceneClick(silo, click());
    press();
    popover.sceneClick(silo, click());
    expect(popover.open()).toBeNull();
    expect(TestBed.inject(VillageSelectionService).selected()).toBeNull();
  });

  it('keeps only one open: another building takes its place', () => {
    press();
    popover.sceneClick(silo, click());
    press();
    popover.sceneClick(cellar, click());
    expect(popover.openId()).toBe('CELLAR');
    expect(TestBed.inject(VillageSelectionService).selected()?.id).toBe('CELLAR');
  });

  it('closes on a click on anything else, and on empty space', () => {
    press();
    popover.sceneClick(silo, click());
    press();
    expect(popover.sceneClick({ kind: 'annex', id: 'TILLER' }, click())).toBeFalse();
    expect(popover.open()).toBeNull();

    press();
    popover.sceneClick(silo, click());
    press();
    popover.emptyClick(click());
    expect(popover.open()).toBeNull();
  });

  it('ignores a drag and a long hold: the click limits of the closeup', () => {
    press(10, 10, 100);
    popover.sceneClick(silo, click(10 + CLICK_SLOP_PX + 1, 10, 200));
    expect(popover.open()).toBeNull();
    press(10, 10, 100);
    popover.sceneClick(silo, click(10, 10, 100 + CLICK_MAX_MS + 1));
    expect(popover.open()).toBeNull();

    press();
    popover.sceneClick(silo, click());
    press(10, 10, 100);
    popover.emptyClick(click(40, 10, 200));
    expect(popover.openId()).toBe('SILO');
    expect(isClick(null, click())).toBeFalse();
    expect(isClick({ x: 0, y: 0, at: 0 }, click(CLICK_SLOP_PX, 0, CLICK_MAX_MS))).toBeTrue();
  });

  it('does not open a subject that is not a registered building', () => {
    press();
    expect(popover.sceneClick({ kind: 'building', id: 'NOPE' }, click())).toBeFalse();
    expect(popover.open()).toBeNull();
  });

  it('closes while time is held', () => {
    popover.openFor(silo);
    game.setSession({ resumed: false, savedAt: null, schema: 1, notices: [], held: true });
    frame();
    expect(popover.open()).toBeNull();
  });

  it('closes when a zoomed view is up or an Expedition is out', () => {
    popover.openFor(silo);
    game.update({ expeditionActive: true });
    frame();
    expect(popover.open()).toBeNull();

    game.update({ expeditionActive: false });
    frame();
    popover.openFor(silo);
    frame();
    expect(popover.openId()).toBe('SILO');
    game.update({ expeditionActive: true });
    zoom.toggleCloseup();
    frame();
    expect(popover.open()).toBeNull();
  });

  it('performs an action as the panel would, and refuses one that cannot be used', () => {
    expect(popover.perform('SILO', 'sell-food')).toBeTrue();
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.SELL_FOOD, undefined);

    sendAction.calls.reset();
    game.update({ village: { ...game.state().village, food: 0.5 } });
    expect(popover.perform('SILO', 'sell-food')).toBeFalse();
    expect(popover.perform('SILO', 'nope')).toBeFalse();
    expect(popover.perform('NOPE', 'sell-food')).toBeFalse();
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('settles the anchor only on a move of half a pixel or more', () => {
    popover.openFor(silo);
    popover.setAnchor({ left: 10, top: 10, right: 20, bottom: 20 });
    const first = popover.anchor();
    popover.setAnchor({ left: 10.2, top: 10, right: 20, bottom: 20 });
    expect(popover.anchor()).toBe(first);
    popover.setAnchor({ left: 11, top: 10, right: 20, bottom: 20 });
    expect(popover.anchor()?.left).toBe(11);
  });

  it('only a press that landed on a scene volume is the scene volume\'s click', () => {
    press(10, 10, 100);
    popover.noteMeshPress(100);
    expect(popover.takeMeshPress()).toBeTrue();
    expect(popover.takeMeshPress()).toBeFalse();
    press(10, 10, 300);
    expect(popover.takeMeshPress()).toBeFalse();
  });
});

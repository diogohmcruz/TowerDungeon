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
});

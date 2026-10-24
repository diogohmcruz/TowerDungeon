import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import { BuildingPopover } from './building-popover';

describe('BuildingPopover', () => {
  let fixture: ComponentFixture<BuildingPopover>;
  let sendAction: jasmine.Spy;
  let popover: PopoverService;
  let stage: StageService;
  const silo = { kind: 'building', id: 'SILO' } as const;

  const el = () => fixture.nativeElement as HTMLElement;
  const card = () => el().querySelector('.popover') as HTMLElement | null;
  const show = () => {
    TestBed.flushEffects();
    fixture.detectChanges();
  };

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    const game = TestBed.inject(GameStateService);
    game.update({
      village: { ...game.state().village, food: 400.25 },
      netFoodPerSecond: 1.2,
    });
    stage = TestBed.inject(StageService);
    popover = TestBed.inject(PopoverService);
    fixture = TestBed.createComponent(BuildingPopover);
    show();
  });
  afterEach(() => stage.showMenus());

  it('shows nothing until a building is picked', () => {
    expect(card()).toBeNull();
  });

  it('shows the title, the exact figure, its rate and the actions', () => {
    popover.openFor(silo);
    show();
    expect(card()).not.toBeNull();
    const text = card()!.textContent!;
    expect(text).toContain('The Pantry silo');
    expect(text).toContain('Food');
    expect(text).toContain('400.25');
    expect(text).toContain('+1.20/s');
    expect(text).toContain('Sell Food');
  });

  it('acts through the same command as the panel', () => {
    popover.openFor(silo);
    show();
    (el().querySelector('button.btn') as HTMLButtonElement).click();
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.SELL_FOOD, undefined);
  });

  it('states a refusal in words and sends nothing', () => {
    const game = TestBed.inject(GameStateService);
    game.update({ village: { ...game.state().village, food: 0 } });
    popover.openFor(silo);
    show();
    const button = el().querySelector('button.btn') as HTMLButtonElement;
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(el().querySelector('.reason')?.textContent).toContain('Nothing is sold');
    button.click();
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('closes on Escape', () => {
    popover.openFor(silo);
    show();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    show();
    expect(card()).toBeNull();
  });

  it('closes on a press elsewhere, but not on the card or the canvas', () => {
    popover.openFor(silo);
    show();
    card()!.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    expect(popover.open()).not.toBeNull();

    const canvas = document.createElement('canvas');
    document.body.appendChild(canvas);
    canvas.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    expect(popover.open()).not.toBeNull();
    canvas.remove();

    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    show();
    expect(card()).toBeNull();
  });

  describe('on the screen', () => {
    const within = (w: number, h: number) => {
      const c = card()!;
      const left = parseFloat(c.style.left);
      const top = parseFloat(c.style.top);
      const width = parseFloat(c.style.width);
      const maxHeight = parseFloat(c.style.maxHeight);
      expect(left).toBeGreaterThanOrEqual(0);
      expect(top).toBeGreaterThanOrEqual(0);
      expect(left + width).toBeLessThanOrEqual(w);
      expect(top + maxHeight).toBeLessThanOrEqual(h);
    };

    it('stays on screen at 1280x720 wherever the building is', () => {
      stage.setViewport({ width: 1280, height: 720 });
      popover.openFor(silo);
      for (const rect of [
        { left: 1050, top: 380, right: 1090, bottom: 600 },
        { left: 1260, top: 700, right: 1280, bottom: 720 },
        { left: 0, top: 0, right: 30, bottom: 30 },
      ]) {
        popover.setAnchor(rect);
        show();
        within(1280, 720);
      }
    });

    it('stays on screen 440 px wide', () => {
      stage.setViewport({ width: 440, height: 800 });
      popover.openFor(silo);
      for (const rect of [
        { left: 300, top: 500, right: 340, bottom: 700 },
        { left: 420, top: 780, right: 440, bottom: 800 },
      ]) {
        popover.setAnchor(rect);
        show();
        within(440, 800);
      }
    });

    it('follows the building as it moves', () => {
      stage.setViewport({ width: 1280, height: 720 });
      popover.openFor(silo);
      popover.setAnchor({ left: 100, top: 200, right: 140, bottom: 400 });
      show();
      const first = parseFloat(card()!.style.left);
      popover.setAnchor({ left: 160, top: 240, right: 200, bottom: 440 });
      show();
      expect(parseFloat(card()!.style.left)).toBe(first + 60);
      expect(parseFloat(card()!.style.top)).toBe(240);
    });
  });
});

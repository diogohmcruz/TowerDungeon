import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import { BuildingPopover } from './building-popover';
import { flushEffects } from '../../testing/flush-effects';

describe('BuildingPopover', () => {
  let fixture: ComponentFixture<BuildingPopover>;
  let sendAction: jasmine.Spy;
  let popover: PopoverService;
  let stage: StageService;
  const silo = { kind: 'building', id: 'SILO' } as const;

  const el = () => fixture.nativeElement as HTMLElement;
  const card = () => el().querySelector('.popover') as HTMLElement | null;
  const show = () => {
    flushEffects();
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
  afterEach(() => stage.setLaunchBarHidden(false));

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
    expect(sendAction).toHaveBeenCalledOnceWith(
      GameAction.SELL_FOOD,
      undefined,
    );
  });

  it('states a refusal in words and sends nothing', () => {
    const game = TestBed.inject(GameStateService);
    game.update({ village: { ...game.state().village, food: 0 } });
    popover.openFor(silo);
    show();
    const button = el().querySelector('button.btn') as HTMLButtonElement;
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(el().querySelector('.reason')?.textContent).toContain(
      'Nothing is sold',
    );
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

    document.body.dispatchEvent(
      new PointerEvent('pointerdown', { bubbles: true }),
    );
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

    /** The card as drawn: inside the screen, and scrolling only when the screen is too short for it. */
    const drawn = (w: number, h: number, what: string) => {
      const c = card()!;
      const r = c.getBoundingClientRect();
      expect(r.left).withContext(what).toBeGreaterThanOrEqual(0);
      expect(r.top).withContext(what).toBeGreaterThanOrEqual(0);
      expect(r.right)
        .withContext(what)
        .toBeLessThanOrEqual(w + 0.5);
      expect(r.bottom)
        .withContext(what)
        .toBeLessThanOrEqual(h + 0.5);
      const scrolls = c.scrollHeight > c.clientHeight + 1;
      if (scrolls) {
        // Cut off only when it cannot fit, and then it says so.
        expect(c.classList.contains('more-below')).withContext(what).toBeTrue();
        expect(r.height)
          .withContext(what)
          .toBeGreaterThanOrEqual(h - 2 * 12 - 2);
      } else {
        expect(c.scrollHeight).withContext(what).toBeLessThanOrEqual(h);
      }
    };

    it('shows every registered building whole on screen at 1280x720, 1440x900 and 440 px wide', () => {
      const ids = popover.buildings().map((b) => b.id);
      expect(ids.length).toBeGreaterThan(5);
      for (const [w, h] of [
        [1280, 720],
        [1440, 900],
        [440, 800],
        [440, 600],
      ]) {
        stage.setViewport({ width: w, height: h });
        for (const id of ids) {
          for (const rect of [
            {
              left: w * 0.3,
              top: h * 0.45,
              right: w * 0.3 + 120,
              bottom: h * 0.45 + 160,
            },
            {
              left: w * 0.7,
              top: h * 0.2,
              right: w * 0.7 + 120,
              bottom: h * 0.2 + 160,
            },
            {
              left: w / 2 - 60,
              top: h * 0.8,
              right: w / 2 + 60,
              bottom: h - 4,
            },
          ]) {
            popover.openFor({ kind: 'building', id });
            popover.setAnchor(rect);
            show();
            show();
            drawn(w, h, `${id} at ${w}x${h}, anchor ${rect.left},${rect.top}`);
          }
        }
      }
    });

    it('reads the Ledger at the Study, newest first, and closes on its button', () => {
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
        ledger: [
          { text: 'Emergency Tilling. Pantry below 20.', loss: false },
          { text: 'Starvation. 1 Breaker lost.', loss: true },
        ],
      });
      popover.openFor({ kind: 'annex', id: 'SCRIBE' });
      show();
      const lines = Array.from(
        card()!.querySelectorAll('.written-lines li'),
      ).map((li) => li.textContent?.trim());
      expect(lines).toEqual([
        '- Starvation. 1 Breaker lost.',
        '. Emergency Tilling. Pantry below 20.',
      ]);
      expect(card()!.querySelector('.written-lines.scroll')).not.toBeNull();
      (card()!.querySelector('button.dismiss') as HTMLButtonElement).click();
      show();
      expect(card()).toBeNull();
    });

    it('shows the Expedition Summary at the Gate', () => {
      TestBed.inject(GameStateService).update({
        lastRunSummary: {
          outcome: 'WIPED',
          startFloor: 0,
          highestFloor: 7,
          floorsCleared: 6,
          enemiesDefeated: 12,
          creditsGained: 40,
          materialsGained: 20,
          relicsGained: 0,
          unitsLost: 1,
          survivors: 0,
          fallen: [{ name: 'Tomas', type: 'ARCHER', floor: 7 }],
        },
      });
      popover.openFor({ kind: 'building', id: 'GATE' });
      show();
      const text = card()!.textContent!;
      expect(text).toContain('Wipe. Floor 0 → 7');
      expect(text).toContain('Tomas, Archer, fell on Floor 7.');
      expect(text).toContain('Credits lost 40');
    });

    it('follows the building as it moves', () => {
      stage.setViewport({ width: 1280, height: 1400 });
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ZoomService } from '../../services/zoom.service';
import { StageService } from '../../services/stage.service';
import { ListViewService } from '../../services/list-view.service';
import { MenusService } from '../../services/menus.service';
import { PopoverService } from '../../services/popover.service';
import { GameComponent } from './game-component';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { stubScenes } from '../../testing/scene-stubs';
import { flushEffects } from '../../testing/flush-effects';

describe('GameComponent', () => {
  let component: GameComponent;
  let fixture: ComponentFixture<GameComponent>;
  let sendAction: jasmine.Spy;
  let stateService: GameStateService;
  const host = () => fixture.nativeElement as HTMLElement;
  const stage = () => TestBed.inject(StageService);
  const press = (key: string, init: KeyboardEventInit = {}) =>
    document.dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true, ...init }));

  beforeEach(async () => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      imports: [GameComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GameWebSocketService, useValue: { sendAction } },
      ],
    });
    stubScenes();
    await TestBed.compileComponents();

    stateService = TestBed.inject(GameStateService);
    fixture = TestBed.createComponent(GameComponent);
    component = fixture.componentInstance;
    stage().setViewport({ width: 1400, height: 800 });
    fixture.detectChanges();
  });
  afterEach(() => stage().setLaunchBarHidden(false));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('the card layer is gone', () => {
    it('keeps no card, no grid of cards and no dim behind them', () => {
      for (const selector of [
        '.card-layer',
        '.glass-card',
        '.scrim',
        'app-village-management',
        'app-shortcut-card',
        'app-army',
        '.launch-buttons',
        '.campaign-banner',
        '.expedition-start',
      ]) {
        expect(host().querySelector(selector)).withContext(selector).toBeNull();
      }
    });

    it('keeps the scene as the one fixed full-screen backdrop, and the page does not scroll over it', () => {
      document.body.appendChild(host());
      const tower = host().querySelector('.tower') as HTMLElement;
      const style = getComputedStyle(tower);
      expect(style.position).toBe('fixed');
      expect(style.zIndex).toBe('0');
      const box = tower.getBoundingClientRect();
      expect(box.top).toBe(0);
      expect(box.left).toBe(0);
      expect(box.height).toBe(window.innerHeight);
      expect(box.width).toBe(document.documentElement.clientWidth);
      // Only the top bar's buttons and the list view stand in front, and the host takes no pointer.
      expect(getComputedStyle(host()).pointerEvents).toBe('none');
      host().remove();
    });

    it('keeps the list view as the one DOM route', () => {
      expect(host().querySelector('app-list-view')).not.toBeNull();
    });
  });

  describe('the menus (H and the hide button)', () => {
    const list = () => host().querySelector('app-list-view .list') as HTMLElement | null;
    const bar = () => host().querySelector('.launch-bar') as HTMLElement;
    const button = () => host().querySelector('.menus-toggle') as HTMLButtonElement;
    const detect = () => {
      flushEffects();
      fixture.detectChanges();
    };
    /** The button reads Hide menus (pressed false) while something shows, Show menus (pressed true) otherwise. */
    const reads = (showing: boolean) => {
      expect(button().textContent).toContain(showing ? 'Hide menus' : 'Show menus');
      expect(button().getAttribute('aria-pressed')).toBe(String(!showing));
      expect(button().getAttribute('aria-label')).toBe(
        showing ? 'Hide menus (H)' : 'Show menus (H)',
      );
      expect(button().title).toBe(showing ? 'Hide menus (H)' : 'Show menus (H)');
    };

    it('reads Show menus when nothing is showing on a wide screen', () => {
      reads(false);
    });

    it('follows the list view: L opens it, L and Escape close it', () => {
      press('l');
      detect();
      reads(true);
      press('l');
      detect();
      reads(false);
      press('L');
      detect();
      reads(true);
      press('Escape');
      detect();
      expect(list()).toBeNull();
      reads(false);
    });

    it('follows a popover: opening, Escape, a click elsewhere and held time', () => {
      const popover = TestBed.inject(PopoverService);
      popover.openFor({ kind: 'building', id: 'SILO' });
      detect();
      reads(true);
      press('Escape');
      detect();
      expect(popover.openId()).toBeNull();
      reads(false);

      popover.openFor({ kind: 'building', id: 'SILO' });
      detect();
      reads(true);
      popover.notePress({ x: 10, y: 10, at: 0 });
      popover.emptyClick({ clientX: 10, clientY: 10, timeStamp: 50 });
      detect();
      reads(false);

      popover.openFor({ kind: 'building', id: 'SILO' });
      detect();
      reads(true);
      stateService.setSession({
        resumed: false,
        savedAt: null,
        schema: 1,
        notices: [],
        held: true,
      });
      detect();
      expect(popover.openId()).toBeNull();
      reads(false);
    });

    it('H closes everything that shows, and opens the list when nothing does', () => {
      const popover = TestBed.inject(PopoverService);
      const listView = TestBed.inject(ListViewService);
      listView.show();
      detect();
      reads(true);
      press('h');
      detect();
      expect(listView.open()).toBeFalse();
      reads(false);
      press('H');
      detect();
      expect(listView.open()).toBeTrue();
      expect(list()?.hidden).toBeFalse();
      reads(true);

      listView.hide();
      popover.openFor({ kind: 'building', id: 'SILO' });
      detect();
      reads(true);
      press('h');
      detect();
      expect(popover.openId()).toBeNull();
      reads(false);
    });

    it('a popover opens after H, and the button follows it', () => {
      press('h');
      press('h');
      TestBed.inject(ListViewService).hide();
      detect();
      reads(false);
      TestBed.inject(PopoverService).openFor({ kind: 'building', id: 'SILO' });
      detect();
      expect(TestBed.inject(PopoverService).openId()).toBe('SILO');
      reads(true);
    });

    it('the button does what H does, and stays reachable', () => {
      button().click();
      detect();
      expect(TestBed.inject(ListViewService).open()).toBeTrue();
      reads(true);
      expect(getComputedStyle(button()).pointerEvents).toBe('auto');
      button().click();
      detect();
      expect(TestBed.inject(ListViewService).open()).toBeFalse();
      reads(false);
    });

    it('on a narrow screen the launch bar counts, and Escape on the list leaves it showing', () => {
      stage().setViewport({ width: 440, height: 800 });
      detect();
      expect(getComputedStyle(bar()).display).toBe('flex');
      reads(true);
      press('l');
      detect();
      press('Escape');
      detect();
      reads(true);
      press('h');
      detect();
      expect(getComputedStyle(bar()).display).toBe('none');
      reads(false);
      press('h');
      detect();
      expect(getComputedStyle(bar()).display).toBe('flex');
      reads(true);
    });

    it('opening the list with L brings back the launch bar put away', () => {
      stage().setViewport({ width: 440, height: 800 });
      press('h');
      detect();
      expect(getComputedStyle(bar()).display).toBe('none');
      press('l');
      detect();
      expect(getComputedStyle(bar()).display).toBe('flex');
      expect(list()?.hidden).toBeFalse();
    });

    it('ignores H while typing and with a modifier', () => {
      press('h', { ctrlKey: true });
      press('h', { metaKey: true });
      const input = document.createElement('input');
      document.body.appendChild(input);
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'h', bubbles: true }));
      input.remove();
      detect();
      expect(TestBed.inject(ListViewService).open()).toBeFalse();
      reads(false);
    });

    describe('the closeup', () => {
      const button = () =>
        host().querySelector('.closeup-toggle') as HTMLButtonElement | null;
      const held = (value: boolean) => ({
        resumed: false,
        savedAt: null,
        schema: 1,
        notices: [],
        held: value,
      });

      it('offers the button in both scenes: the Village closeup, and during an Expedition the Floor closeup', () => {
        expect(button()?.textContent).toContain('Village closeup');
        stateService.update({ expeditionActive: true });
        fixture.detectChanges();
        expect(button()?.textContent?.trim()).toBe('Closeup');
      });

      it('takes the Village closeup with the button and C, holds no time, and Escape steps back', () => {
        button()!.click();
        fixture.detectChanges();
        expect(TestBed.inject(ZoomService).villageCloseup()).toBeTrue();
        expect(button()?.getAttribute('aria-pressed')).toBe('true');
        expect(sendAction).not.toHaveBeenCalled();
        press('Escape');
        expect(TestBed.inject(ZoomService).villageCloseup()).toBeFalse();
        press('c');
        expect(TestBed.inject(ZoomService).villageCloseup()).toBeTrue();
        press('c');
        expect(TestBed.inject(ZoomService).villageCloseup()).toBeFalse();
      });

      it('holds time and hides the menus, and Escape steps back and restores them', () => {
        TestBed.inject(ListViewService).show();
        stateService.update({ expeditionActive: true });
        fixture.detectChanges();
        button()!.click();
        fixture.detectChanges();
        expect(sendAction).toHaveBeenCalledWith(GameAction.HOLD, null);
        expect(TestBed.inject(MenusService).showing()).toBeFalse();
        expect(button()?.getAttribute('aria-pressed')).toBe('true');

        stateService.setSession(held(true));
        press('Escape');
        fixture.detectChanges();
        expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
        expect(TestBed.inject(MenusService).showing()).toBeTrue();
        expect(button()?.getAttribute('aria-pressed')).toBe('false');
      });
    });
  });

  describe('the narrow screen', () => {
    it('carries the launch controls in a bottom bar, with the same words', () => {
      stage().setViewport({ width: 440, height: 800 });
      fixture.detectChanges();
      expect(host().classList).toContain('narrow');
      const bar = host().querySelector('.launch-bar') as HTMLElement;
      const style = getComputedStyle(bar);
      expect(style.display).toBe('flex');
      expect(style.position).toBe('fixed');
      expect(bar.textContent).toContain('Ring the bell: set out');
      expect(bar.textContent).toContain('Light the lamp: extract');
      stage().setViewport({ width: 1400, height: 800 });
      fixture.detectChanges();
      expect(host().classList).not.toContain('narrow');
      expect(getComputedStyle(bar).display).toBe('none');
    });
  });

  describe('Resume for a Held Party (the top bar)', () => {
    const session = (held: boolean) => ({
      resumed: true,
      savedAt: null,
      schema: 1,
      notices: [],
      held,
    });

    it('offers Resume only while the Party is Held, from the session the socket keeps apart', () => {
      expect(host().querySelector('.held-party')).toBeNull();

      stateService.setSession(session(true));
      fixture.detectChanges();
      expect(host().querySelector('.held-party')?.textContent).toContain(
        'The Party has not moved.',
      );

      (host().querySelector('.held-party button') as HTMLButtonElement).click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);

      stateService.setSession(session(false));
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).toBeNull();
    });

    it('stays when the menus are hidden: it is the top bar, not a menu', () => {
      stateService.setSession(session(true));
      press('h');
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).not.toBeNull();
    });

    it('does not offer Resume for the hold a zoom took, nor flash it as the zoom lets go', () => {
      const zoom = TestBed.inject(ZoomService);
      stateService.update({ expeditionActive: true });
      zoom.toggleCloseup();
      stateService.setSession(session(true));
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).toBeNull();

      zoom.back();
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).toBeNull();

      stateService.setSession(session(false));
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).toBeNull();
      stateService.setSession(session(true));
      fixture.detectChanges();
      expect(host().querySelector('.held-party')).not.toBeNull();
    });
  });

  describe('a real press reaches the menus (the host takes no pointer, and that is inherited)', () => {
    /**
     * The control found at a point by the browser's own hit test, as a real mouse does,
     * then the press, the lift and the click delivered to that very element. Setting
     * `button.click()` on a node skips the hit test, which is how a menu that nothing could
     * reach shipped in 3.0.0.
     */
    const pressAt = (button: HTMLElement): Element | null => {
      const box = button.getBoundingClientRect();
      const x = box.left + box.width / 2;
      const y = box.top + box.height / 2;
      const hit = document.elementFromPoint(x, y);
      if (hit) {
        const init = { bubbles: true, clientX: x, clientY: y, button: 0 };
        hit.dispatchEvent(new PointerEvent('pointerdown', init));
        hit.dispatchEvent(new PointerEvent('pointerup', init));
        hit.dispatchEvent(new MouseEvent('click', init));
      }
      return hit;
    };
    const reaches = (button: HTMLElement) => {
      const hit = pressAt(button);
      return !!hit && (hit === button || button.contains(hit));
    };

    beforeEach(() => {
      // The card is placed within the stage's viewport: make it the window the test can hit.
      stage().setViewport({ width: window.innerWidth, height: window.innerHeight });
      document.body.appendChild(host());
      stateService.update({
        village: { ...stateService.state().village, food: 400 },
      });
    });
    afterEach(() => host().remove());

    it('sends the command of a popover action pressed with the mouse', () => {
      TestBed.inject(PopoverService).openFor({ kind: 'building', id: 'SILO' });
      flushEffects();
      fixture.detectChanges();
      const sell = host().querySelector('.popover button.btn') as HTMLButtonElement;
      expect(sell.textContent).toContain('Sell Food');
      expect(reaches(sell)).withContext('the press lands on the button').toBeTrue();
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.SELL_FOOD, undefined);
      expect(host().querySelector('.popover')).withContext('the card stays').not.toBeNull();
    });

    it('sends the command of a list view action pressed with the mouse', () => {
      TestBed.inject(ListViewService).show();
      flushEffects();
      fixture.detectChanges();
      const sell = Array.from(
        host().querySelectorAll<HTMLButtonElement>('.list button.btn'),
      ).find((b) => b.textContent?.includes('Sell Food'))!;
      expect(reaches(sell)).withContext('the press lands on the button').toBeTrue();
      expect(sendAction).toHaveBeenCalledWith(GameAction.SELL_FOOD, undefined);
    });

    it('reaches the top bar while the list stands over the scene', () => {
      TestBed.inject(ListViewService).show();
      fixture.detectChanges();
      const toggle = host().querySelector('.menus-toggle') as HTMLButtonElement;
      expect(reaches(toggle)).toBeTrue();
      expect(TestBed.inject(ListViewService).open()).toBeFalse();
    });
  });
});

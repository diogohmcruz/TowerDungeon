import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ZoomService } from '../../services/zoom.service';
import { StageService } from '../../services/stage.service';
import { ListViewService } from '../../services/list-view.service';
import { PopoverService } from '../../services/popover.service';
import { GameComponent } from './game-component';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';

describe('GameComponent', () => {
  let component: GameComponent;
  let fixture: ComponentFixture<GameComponent>;
  let sendAction: jasmine.Spy;
  let stateService: GameStateService;
  const host = () => fixture.nativeElement as HTMLElement;
  const stage = () => TestBed.inject(StageService);
  const press = (key: string, init: KeyboardEventInit = {}) =>
    document.dispatchEvent(new KeyboardEvent('keydown', { key, ...init }));

  beforeEach(async () => {
    sendAction = jasmine.createSpy('sendAction');
    await TestBed.configureTestingModule({
      imports: [GameComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GameWebSocketService, useValue: { sendAction } },
      ],
    }).compileComponents();

    stateService = TestBed.inject(GameStateService);
    fixture = TestBed.createComponent(GameComponent);
    component = fixture.componentInstance;
    stage().setViewport({ width: 1400, height: 800 });
    fixture.detectChanges();
  });
  afterEach(() => stage().showMenus());

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

    it('hides and restores the menus with the H key', () => {
      expect(stage().menusHidden()).toBeFalse();
      press('h');
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeTrue();
      press('H');
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeFalse();
    });

    it('hides and restores the menus with the button, which stays reachable', () => {
      const button = host().querySelector('.menus-toggle') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeTrue();
      expect(button.getAttribute('aria-pressed')).toBe('true');
      expect(button.textContent).toContain('Show menus');
      expect(getComputedStyle(button).pointerEvents).toBe('auto');
      button.click();
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeFalse();
      expect(button.textContent).toContain('Hide menus');
    });

    it('hides the open list view and gives it back, still open', () => {
      TestBed.inject(ListViewService).show();
      fixture.detectChanges();
      expect(list()?.hidden).toBeFalse();
      press('h');
      fixture.detectChanges();
      expect(list()?.hidden).toBeTrue();
      expect(getComputedStyle(list()!).display).toBe('none');
      press('h');
      fixture.detectChanges();
      expect(list()?.hidden).toBeFalse();
    });

    it('opening the list with L shows the menus the hide key put away', () => {
      press('h');
      fixture.detectChanges();
      press('l');
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeFalse();
      expect(list()?.hidden).toBeFalse();
    });

    it('closes a popover that is open, and opens none while hidden', () => {
      const popover = TestBed.inject(PopoverService);
      popover.openFor({ kind: 'building', id: 'SILO' });
      fixture.detectChanges();
      expect(popover.openId()).toBe('SILO');
      press('h');
      fixture.detectChanges();
      expect(popover.openId()).toBeNull();
    });

    it('hides the narrow launch bar too', () => {
      stage().setViewport({ width: 440, height: 800 });
      fixture.detectChanges();
      expect(getComputedStyle(bar()).display).toBe('flex');
      press('h');
      fixture.detectChanges();
      expect(getComputedStyle(bar()).display).toBe('none');
    });

    it('ignores H while typing and with a modifier', () => {
      press('h', { ctrlKey: true });
      press('h', { metaKey: true });
      const input = document.createElement('input');
      document.body.appendChild(input);
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'h', bubbles: true }));
      input.remove();
      fixture.detectChanges();
      expect(stage().menusHidden()).toBeFalse();
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

      it('offers the HUD button only while an Expedition is out', () => {
        expect(button()).toBeNull();
        stateService.update({ expeditionActive: true });
        fixture.detectChanges();
        expect(button()?.textContent).toContain('Closeup');
      });

      it('holds time and hides the menus, and Escape steps back', () => {
        stateService.update({ expeditionActive: true });
        fixture.detectChanges();
        button()!.click();
        fixture.detectChanges();
        expect(sendAction).toHaveBeenCalledWith(GameAction.HOLD, null);
        expect(stage().menusHidden()).toBeTrue();
        expect(button()?.getAttribute('aria-pressed')).toBe('true');

        stateService.setSession(held(true));
        press('Escape');
        fixture.detectChanges();
        expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
        expect(stage().menusHidden()).toBeFalse();
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
});

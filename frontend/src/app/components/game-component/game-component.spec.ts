import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ZoomService } from '../../services/zoom.service';
import { StageService } from '../../services/stage.service';
import { GameComponent } from './game-component';
import { cardLayerWidth } from '../../three/stage-view';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';

describe('GameComponent', () => {
  let component: GameComponent;
  let fixture: ComponentFixture<GameComponent>;
  let sendAction: jasmine.Spy;
  let stateService: GameStateService;

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
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('the full-screen stage', () => {
    const host = () => fixture.nativeElement as HTMLElement;
    const column = () => host().querySelector('.card-layer') as HTMLElement;
    const hidden = () => getComputedStyle(column()).display === 'none';
    const press = (key: string, init: KeyboardEventInit = {}) =>
      document.dispatchEvent(new KeyboardEvent('keydown', { key, ...init }));

    beforeEach(() => {
      TestBed.inject(StageService).setViewport({ width: 1400, height: 800 });
      fixture.detectChanges();
    });
    afterEach(() => TestBed.inject(StageService).showMenus());

    it('keeps the scene as a fixed full-screen backdrop and the card layer over its left', () => {
      const tower = getComputedStyle(host().querySelector('app-tower')!);
      expect(tower.position).toBe('fixed');
      expect(tower.zIndex).toBe('0');
      expect(column().getBoundingClientRect().width).toBe(
        cardLayerWidth(1400, false),
      );
      expect(column().getBoundingClientRect().width).toBeGreaterThan(440);
      expect(getComputedStyle(column()).paddingLeft).toBe('24px');
    });

    it('flows the cards in a grid of several columns, none reaching into the Tower lane', () => {
      const columns = getComputedStyle(column()).gridTemplateColumns.split(' ');
      expect(columns.length).toBeGreaterThanOrEqual(2);
      const cards = Array.from(
        host().querySelectorAll<HTMLElement>('.glass-card'),
      );
      expect(cards.length).toBeGreaterThan(3);
      const edge = column().getBoundingClientRect().left + cardLayerWidth(1400, false);
      for (const card of cards) {
        expect(card.getBoundingClientRect().right).toBeLessThanOrEqual(edge + 0.5);
      }
    });

    it('lets the pointer through the layer and the gaps between cards, but not the cards', () => {
      expect(getComputedStyle(host()).pointerEvents).toBe('none');
      expect(getComputedStyle(column()).pointerEvents).toBe('none');
      const cards = Array.from(
        host().querySelectorAll<HTMLElement>('.glass-card'),
      );
      for (const card of cards) {
        expect(getComputedStyle(card).pointerEvents).toBe('auto');
      }
      // A point in the gap between two cards of one row reaches whatever lies behind.
      const [a, b] = cards
        .map((card) => card.getBoundingClientRect())
        .filter((r, _, all) => Math.abs(r.top - all[0].top) < 1)
        .slice(0, 2);
      if (a && b && b.left > a.right) {
        const x = (a.right + b.left) / 2;
        const y = a.top + Math.min(a.height, 20) / 2;
        const hit = document.elementFromPoint(x, y);
        expect(hit?.closest('.glass-card')).toBeNull();
      }
    });

    it('paints a card as glass over a dither scrim, with no blur and nothing between cards', () => {
      const card = host().querySelector('.glass-card') as HTMLElement;
      const style = getComputedStyle(card);
      expect(style.backgroundImage).toContain('conic-gradient');
      expect(style.backdropFilter).toBe('none');
      expect(getComputedStyle(column()).backgroundImage).toBe('none');
      expect(getComputedStyle(host()).backgroundImage).toBe('none');
      expect(getComputedStyle(host().querySelector('.scrim')!).display).toBe('none');
    });

    it('hides and restores the menus with the H key', () => {
      expect(hidden()).toBeFalse();
      press('h');
      fixture.detectChanges();
      expect(hidden()).toBeTrue();
      press('H');
      fixture.detectChanges();
      expect(hidden()).toBeFalse();
    });

    it('hides and restores the menus with the button, which stays reachable', () => {
      const button = host().querySelector('.menus-toggle') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();
      expect(hidden()).toBeTrue();
      expect(button.getAttribute('aria-pressed')).toBe('true');
      expect(button.textContent).toContain('Show menus');
      button.click();
      fixture.detectChanges();
      expect(hidden()).toBeFalse();
      expect(button.textContent).toContain('Hide menus');
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
        expect(hidden()).toBeTrue();
        expect(button()?.getAttribute('aria-pressed')).toBe('true');

        stateService.setSession(held(true));
        press('Escape');
        fixture.detectChanges();
        expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
        expect(hidden()).toBeFalse();
        expect(button()?.getAttribute('aria-pressed')).toBe('false');
      });
    });

    it('ignores H while typing and with a modifier', () => {
      press('h', { ctrlKey: true });
      press('h', { metaKey: true });
      const input = document.createElement('input');
      document.body.appendChild(input);
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'h', bubbles: true }));
      input.remove();
      fixture.detectChanges();
      expect(hidden()).toBeFalse();
    });

    it('makes the cards about 88% opaque', () => {
      expect(getComputedStyle(host()).getPropertyValue('--panel-glass')).toContain(
        'calc(0.88 * 100%)',
      );
      expect(getComputedStyle(host()).getPropertyValue('--panel-opacity')).toBe('0.88');
    });

    it('takes the full width over a dimmed backdrop on a narrow screen', () => {
      const stage = TestBed.inject(StageService);
      stage.setViewport({ width: 900, height: 700 });
      fixture.detectChanges();
      expect(host().classList).toContain('narrow');
      expect(column().getBoundingClientRect().width).toBe(
        host().getBoundingClientRect().width,
      );
      const scrim = getComputedStyle(host().querySelector('.scrim')!);
      expect(scrim.display).toBe('block');
      expect(scrim.width).toBe(`${host().getBoundingClientRect().width}px`);
      expect(scrim.backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
      // The cards sit on the dim alone, and the launch controls move to the bar.
      const card = getComputedStyle(host().querySelector('.glass-card')!);
      expect(card.backgroundImage).not.toContain('conic-gradient');
      const bar = getComputedStyle(host().querySelector('.launch-bar')!);
      expect(bar.display).toBe('flex');
      expect(bar.position).toBe('fixed');
      stage.setViewport({ width: 1400, height: 800 });
      fixture.detectChanges();
      expect(host().classList).not.toContain('narrow');
    });
  });

  describe('Resume for a Held Party', () => {
    const host = () => fixture.nativeElement as HTMLElement;
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

  describe('the Toll and the endings', () => {
    const toll = {
      foodMarks: 0,
      materialsMarks: 0,
      creditsMarks: 0,
      relicsMarks: 0,
      expeditionsMarks: 0,
      ascentMarks: 0,
      guardianMarks: 0,
      marks: 30,
      toll: 30,
      newGround: 0,
      legacy: 3,
      residueNext: 3,
    };
    const forecast = {
      toll,
      pointsDivisor: 9,
      nextLegacyAt: 36,
      exodusMinimum: 3,
      canExodus: true,
    };
    const host = () => fixture.nativeElement as HTMLElement;

    it('keeps the Toll and the Milestones off the card layer: the reference pillar and the list view carry them', () => {
      stateService.update({ forecast });
      fixture.detectChanges();

      expect(host().querySelector('.toll-exodus')).toBeNull();
      expect(host().querySelector('.milestone-panel')).toBeNull();
    });

    it('names each ending and never congratulates', () => {
      const headings: [string, string][] = [
        ['VICTORY', 'The Heirs'],
        ['DEFEAT', 'The Second Company'],
        ['EXODUS', 'The Flight'],
      ];
      for (const [outcome, heading] of headings) {
        stateService.update({ gameOutcome: outcome, forecast: null });
        fixture.detectChanges();
        const banner = host().querySelector('.campaign-banner');
        expect(banner?.querySelector('h2')?.textContent).toBe(heading);
        expect(banner?.textContent).not.toMatch(/Ignelia is free|Conquered|Prestige/);
      }
    });


    it('reports the stored Toll and Legacy as plain numbers on the ending', () => {
      stateService.update({
        gameOutcome: 'EXODUS',
        forecast: null,
        pending: {
          outcome: 'EXODUS',
          generation: 1,
          toll,
          legacy: 3,
          pity: false,
          guardiansSlain: 0,
          highestFloor: 12,
          expeditions: 5,
        },
      });
      fixture.detectChanges();

      expect(host().querySelector('.campaign-toll')?.textContent).toContain(
        'Toll 30.0. Legacy 3.',
      );
    });
  });

  it('sends RESUME', () => {
    component.resume();

    expect(sendAction).toHaveBeenCalledWith(GameAction.RESUME, null);
  });

  it('leaves the Loadout to the Gate: no Loadout panel in the card layer', () => {
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-loadout')).toBeNull();
    expect(root.querySelector('.loadout-toggle')).toBeNull();
  });

  it('sets out with the defaults and no Salt on one click without opening the Loadout', () => {
    component.sendInvadeAction();

    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.INVADE, {
      units: new Map(),
      startFloor: 0,
      salt: 0,
      panacea: false,
    });
  });

  it('sends a Panacea only when the Loadout chose one and the Village can make it', () => {
    component.panaceaChosen.set(true);

    component.sendInvadeAction();
    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ panacea: true }),
    );
    expect(component.panaceaChosen()).toBeFalse();

    sendAction.calls.reset();
    stateService.update({
      loadout: {
        ...stateService.state().loadout,
        panaceaBlocked: 'A Panacea takes 4 Spores. The Stockroom holds 0.',
      },
    });
    component.panaceaChosen.set(true);
    component.sendInvadeAction();
    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ panacea: false }),
    );
  });

  it('sends a Remedy and a reattachment for a Unit at home', () => {
    component.giveRemedy(7);
    component.reattachPart({ unitId: 7, part: 'LEFT_ARM' });

    expect(sendAction).toHaveBeenCalledWith(GameAction.REMEDY, { unitId: 7 });
    expect(sendAction).toHaveBeenCalledWith(GameAction.REATTACH, {
      unitId: 7,
      part: 'LEFT_ARM',
    });
  });

  it('sends the Salt the Loadout chose, never more than is held, then forgets it', () => {
    stateService.update({
      loadout: {
        ...stateService.state().loadout,
        saltHeld: 2,
        saltUseful: 10,
        saltPansOpen: true,
        trustHeld: 0,
      },
    });
    component.saltToUse.set(5);

    component.sendInvadeAction();

    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ salt: 2 }),
    );
    expect(component.saltToUse()).toBe(0);
  });

  it('sends the Salt Pack with its bearer, from the Salt the Supplies left', () => {
    stateService.update({
      loadout: {
        ...stateService.state().loadout,
        saltHeld: 5,
        saltUseful: 10,
        saltPansOpen: true,
        bearerTypes: ['TANK'],
      },
    });
    component.saltToUse.set(2);
    component.packSalt.set(9);
    component.packBearer.set('TANK');

    component.sendInvadeAction();

    expect(sendAction).toHaveBeenCalledWith(
      GameAction.INVADE,
      jasmine.objectContaining({ salt: 2, saltPack: 3, saltBearer: 'TANK' }),
    );
    expect(component.packSalt()).toBe(0);
  });

  describe('the Succession', () => {
    const host = () => fixture.nativeElement as HTMLElement;
    const succession = {
      generation: 1,
      legacy: 4,
      spent: 0,
      courses: [{ course: 1, gate: 0, spentBelow: 0, open: true }],
      inheritances: [
        {
          id: 'SEED_GRAIN',
          name: 'Seed Grain',
          course: 1,
          kind: 'POWER',
          cost: 2,
          available: true,
          held: false,
          paid: 0,
          whyNot: null,
          refundable: false,
        },
      ],
      choices: [],
      chronicle: [],
    };
    const meta = {
      resumed: false,
      savedAt: null,
      schema: 1,
      notices: [],
      held: false,
      succession,
    };

    it('shows the screen only in OFFER', () => {
      stateService.setSession(meta);
      stateService.update({ phase: 'ACTIVE' });
      fixture.detectChanges();
      expect(host().querySelector('app-succession-screen')).toBeNull();

      stateService.update({ phase: 'OFFER', gameOutcome: 'EXODUS' });
      fixture.detectChanges();
      expect(host().querySelector('app-succession-screen')).not.toBeNull();
      expect(host().querySelector('app-village-management')).toBeNull();

      stateService.update({ phase: 'ACTIVE', gameOutcome: 'PLAYING' });
      stateService.setSession({ ...meta, succession: null });
      fixture.detectChanges();
      expect(host().querySelector('app-succession-screen')).toBeNull();
    });

    it('sends the Roll actions and Begin Generation', () => {
      stateService.setSession(meta);
      stateService.update({ phase: 'OFFER', gameOutcome: 'EXODUS' });
      fixture.detectChanges();

      (host().querySelector('.node-buy') as HTMLElement).click();
      expect(sendAction).toHaveBeenCalledWith(GameAction.ROLL_BUY, {
        id: 'SEED_GRAIN',
      });

      fixture.detectChanges();
      (host().querySelector('.begin') as HTMLElement).click();
      fixture.detectChanges();
      (host().querySelector('.begin-yes') as HTMLElement).click();
      expect(sendAction).toHaveBeenCalledWith(
        GameAction.BEGIN_GENERATION,
        null,
      );
    });
  });

  describe('Scene backdrop', () => {
    afterEach(() => {
      window.scrollTo(0, 0);
    });

    it('fills the viewport and stays put while the card layer scrolls over it', () => {
      const host: HTMLElement = fixture.nativeElement;
      document.body.appendChild(host);
      document.body.style.margin = '0';
      // Make the card layer far taller than the viewport.
      const wrapper = host.querySelector('.card-layer') as HTMLElement;
      const filler = document.createElement('div');
      filler.style.height = `${window.innerHeight * 3}px`;
      wrapper.appendChild(filler);

      const tower = host.querySelector('.tower') as HTMLElement;
      expect(getComputedStyle(tower).position).toBe('fixed');
      const before = tower.getBoundingClientRect();
      expect(before.top).toBe(0);
      expect(before.left).toBe(0);
      expect(before.height).toBe(window.innerHeight);
      expect(before.width).toBe(document.documentElement.clientWidth);

      // The page grows with the column, and the column scrolls over the scene.
      expect(host.getBoundingClientRect().height).toBeGreaterThan(
        window.innerHeight * 2,
      );
      const columnTop = wrapper.getBoundingClientRect().top;
      window.scrollTo(0, window.innerHeight);
      const after = tower.getBoundingClientRect();
      expect(after.top).toBe(0);
      expect(after.height).toBe(window.innerHeight);
      expect(wrapper.getBoundingClientRect().top).toBeLessThan(columnTop);

      host.remove();
    });
  });
});

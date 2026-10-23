import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { GameComponent } from './game-component';
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

  it('offers Resume only while the Party is Held', () => {
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('.held-party')).toBeNull();

    stateService.update({
      session: {
        resumed: true,
        savedAt: null,
        schema: 1,
        notices: [],
        held: true,
      },
    });
    fixture.detectChanges();

    expect(host.querySelector('.held-party')?.textContent).toContain(
      'The Party has not moved.',
    );
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

    it('shows the Toll bar while the Campaign is played, and sends EXODUS only after confirming', () => {
      stateService.update({ forecast });
      fixture.detectChanges();

      expect(host().querySelector('app-toll-bar')).not.toBeNull();
      (host().querySelector('.toll-exodus') as HTMLElement).click();
      fixture.detectChanges();
      expect(sendAction).not.toHaveBeenCalled();

      (host().querySelector('.toll-confirm button') as HTMLElement).click();
      expect(sendAction).toHaveBeenCalledOnceWith(GameAction.EXODUS, null);
    });

    it('has no Toll bar once the Campaign has ended', () => {
      stateService.update({ forecast: null, gameOutcome: 'EXODUS' });
      fixture.detectChanges();

      expect(host().querySelector('app-toll-bar')).toBeNull();
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

  it('keeps the Loadout collapsed, so setting out stays one click', () => {
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.loadout-toggle')).not.toBeNull();
    expect(root.querySelector('.loadout-body')).toBeNull();
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

  describe('Tower panel height', () => {
    afterEach(() => {
      window.scrollTo(0, 0);
    });

    it('is exactly one viewport tall and stays put while the controls scroll', () => {
      const host: HTMLElement = fixture.nativeElement;
      document.body.appendChild(host);
      document.body.style.margin = '0';
      // Make the controls column far taller than the viewport.
      const wrapper = host.querySelector('.flex-wrapper') as HTMLElement;
      const filler = document.createElement('div');
      filler.style.height = `${window.innerHeight * 3}px`;
      wrapper.appendChild(filler);

      const tower = host.querySelector('.tower') as HTMLElement;
      expect(getComputedStyle(tower).position).toBe('sticky');
      expect(tower.getBoundingClientRect().height).toBe(window.innerHeight);

      // The host must grow with the controls, or sticky has no room to travel.
      expect(host.getBoundingClientRect().height).toBeGreaterThan(
        window.innerHeight * 2,
      );

      window.scrollTo(0, window.innerHeight);
      const after = tower.getBoundingClientRect();
      expect(after.top).toBe(0);
      expect(after.height).toBe(window.innerHeight);

      host.remove();
    });
  });
});

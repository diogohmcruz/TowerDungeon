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
});

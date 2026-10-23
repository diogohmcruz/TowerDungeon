import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState } from '../../interfaces/game-state';
import { TradePreview, TradeView } from '../../interfaces/village';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { VillageManagement } from './village-management';

describe('VillageManagement', () => {
  let fixture: ComponentFixture<VillageManagement>;
  let sendAction: jasmine.Spy;
  let stateService: GameStateService;

  const tradeViews: TradeView[] = [
    {
      trade: 'TILLER',
      plural: 'Tillers',
      workplace: 'Fields',
      hands: 16,
      posts: null,
      settling: 0,
      settlingSeconds: 0,
    },
    {
      trade: 'BREAKER',
      plural: 'Breakers',
      workplace: 'Quarry',
      hands: 3,
      posts: 10,
      settling: 1,
      settlingSeconds: 7,
    },
    {
      trade: 'SCRIBE',
      plural: 'Scribes',
      workplace: "Scribe's Study",
      hands: 1,
      posts: 10,
      settling: 0,
      settlingSeconds: 0,
    },
  ];

  const preview = (over: Partial<TradePreview>): TradePreview => ({
    trade: 'BREAKER',
    delta: 1,
    fields: 7.5,
    upkeep: 8,
    fieldsCoverUpkeep: false,
    pantryHoldSeconds: 99,
    blocked: null,
    ...over,
  });

  const load = (over: Partial<GameState> = {}) => {
    stateService.update({
      tradeViews,
      tradePreviews: [
        preview({ trade: 'BREAKER', delta: 1 }),
        preview({ trade: 'BREAKER', delta: -1, blocked: 'Settling 7 s.' }),
        preview({ trade: 'SCRIBE', delta: 1, fieldsCoverUpkeep: true }),
        preview({ trade: 'SCRIBE', delta: -1 }),
      ],
      theCount: { villagers: 3, reserves: 2 },
      highestFloor: 12,
      yieldBonus: 0.24,
      ...over,
    });
    fixture.detectChanges();
  };

  const text = (): string => fixture.nativeElement.textContent;

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      imports: [VillageManagement],
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    stateService = TestBed.inject(GameStateService);
    fixture = TestBed.createComponent(VillageManagement);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows the Fields, each workplace with its Posts and its Settling wait', () => {
    load();

    expect(text()).toContain('Tillers 16');
    expect(text()).toContain('Breakers 3 of 10 Posts');
    expect(text()).toContain('Scribes 1 of 10 Posts');
    expect(text()).toContain('Settling 7 s.');
    expect(text()).toContain('Quarry and Study yield +24%');
  });

  it('sends the assignment when a stepper is pressed', () => {
    load();
    const plus = fixture.nativeElement.querySelector(
      '[aria-label="Assign one more to Quarry"]',
    ) as HTMLButtonElement;

    plus.click();

    expect(sendAction).toHaveBeenCalledWith(GameAction.ASSIGN_TRADE, {
      trade: 'BREAKER',
      delta: 1,
    });
  });

  it('disables a stepper whose move is blocked, and says why', () => {
    load();
    const minus = fixture.nativeElement.querySelector(
      '[aria-label="Send one Breakers to the Fields"]',
    ) as HTMLButtonElement;

    expect(minus.disabled).toBeTrue();

    minus.dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();
    expect(text()).toContain('Settling 7 s.');
  });

  it('previews the margin of the move being pointed at and warns without blocking', () => {
    load();
    const plus = fixture.nativeElement.querySelector(
      '[aria-label="Assign one more to Quarry"]',
    ) as HTMLButtonElement;

    plus.dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();

    expect(text()).toContain('Fields +7.5. Upkeep -8.');
    expect(text()).toContain('Pantry holds 99 s.');
    expect(plus.disabled).toBeFalse();
  });

  it('says the Fields cover Upkeep when they do', () => {
    load();
    const plus = fixture.nativeElement.querySelector(
      '[aria-label="Assign one more to Scribe\'s Study"]',
    ) as HTMLButtonElement;

    plus.dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();

    expect(text()).toContain('Fields cover Upkeep.');
  });

  it('carries The Count and the stores', () => {
    load({
      stockroomCap: 108,
      quarryHeapCap: 120,
      heapDeliveryPerFloor: 5,
    });

    expect(text()).toContain(
      'Lost to Starvation: 3 Villagers, 2 Reserves (this Campaign)',
    );
    expect(text()).toContain('of 108');
    expect(text()).toContain('of 120');
    expect(text()).toContain('5 per Floor cleared');
  });

  it('prints the last Ledger lines with a marker for losses', () => {
    load({
      ledger: [
        { text: 'Emergency Tilling. Pantry below 20.', loss: false },
        { text: 'Starvation. 1 Breaker lost.', loss: true },
      ],
    });

    const lines = Array.from(
      fixture.nativeElement.querySelectorAll('.ledger li'),
    ).map((li) => (li as HTMLElement).textContent?.trim());
    expect(lines).toEqual([
      '. Emergency Tilling. Pantry below 20.',
      '- Starvation. 1 Breaker lost.',
    ]);
  });
});

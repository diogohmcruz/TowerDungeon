import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameState } from '../../interfaces/game-state';
import {
  TradePreview,
  TradeView,
} from '../../interfaces/village';
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
      tally: {
        foodGrown: 1234,
        creditsBanked: 80,
        materialsBanked: 410,
        relicsBanked: 6,
        floorsCleared: 19,
        peakVillagers: 24,
      },
      villageTierLevel: 2,
      villageTierName: 'Sheltered',
      villageTierOpens: "Fields, Quarry, Scribe's Study, Salt Pans",
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

  it('no longer carries the Trades rows, the steppers or the Villager button: they are in the world', () => {
    load();

    expect(text()).not.toContain('Tillers 16');
    expect(fixture.nativeElement.querySelector('[data-row^="annex:"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.step')).toBeNull();
    expect(text()).not.toContain('Take in Villager');
  });

  it('leaves the sales to the buildings: no sell button on the card layer', () => {
    load();
    const labels = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLElement>,
    ).map((b) => b.textContent?.trim());
    expect(labels.filter((l) => l?.startsWith('Sell'))).toEqual([]);
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
    expect(text()).not.toContain('per Floor cleared');
  });

  it('shows the Village Tier and the Tally', () => {
    load();

    expect(text()).toContain('Village Tier 2, Sheltered.');
    expect(text()).toContain('Salt Pans');
    expect(text()).toContain('Tally (this Campaign)');
    expect(text()).toContain('Food grown 1,234');
    expect(text()).toContain('Floors cleared 19');
    expect(text()).toContain('peak Villagers 24');
  });

  it('states the Year and its Quarter in words, and the sea once the Port exists', () => {
    load({ year: 3, quarter: 'AUTUMN', nextQuarter: 'WINTER', seaRisk: 0.2 });
    expect(text()).toContain('Year 3, Autumn, Winter follows');
    expect(text()).not.toContain('Danger at sea');

    load({
      year: 3,
      quarter: 'WINTER',
      nextQuarter: 'SPRING',
      seaRisk: 0.2,
      port: {
        state: 'FOOTING',
        footingExpeditions: 12,
        expeditionsCompleted: 12,
        materialsCost: 1,
        creditsCost: 1,
      },
    });
    expect(text()).toContain('Danger at sea 20%');
    expect(text()).not.toMatch(/Season|Prestige/);
  });

  it('shows no Year in a Trial', () => {
    load({ year: null, quarter: null, nextQuarter: null, seaRisk: null });
    expect(text()).not.toMatch(/Year \d/);
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

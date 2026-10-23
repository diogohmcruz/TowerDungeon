import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { GameState } from '../../interfaces/game-state';
import {
  PostUpgradeView,
  TomeView,
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

  it('shows the Village Tier and the Tally', () => {
    load();

    expect(text()).toContain('Village Tier 2, Sheltered.');
    expect(text()).toContain('Salt Pans');
    expect(text()).toContain('Tally (this Campaign)');
    expect(text()).toContain('Food grown 1,234');
    expect(text()).toContain('Floors cleared 19');
    expect(text()).toContain('peak Villagers 24');
  });

  const tomeViews: TomeView[] = [
    {
      tome: 'TILLERS_RECORD',
      title: "Tillers' Record",
      effect: 'Fields output',
      bonusPercent: 25,
      bonusFlat: 0,
      level: 1,
      cost: 90,
      gated: true,
      blocked: 'Tome 2 costs 90. Stockroom holds 80. Needs Highest Floor 8.',
    },
    {
      tome: 'SEAM_CHARTS',
      title: 'Seam Charts',
      effect: 'Breaker output',
      bonusPercent: 25,
      bonusFlat: 0,
      level: 0,
      cost: 60,
      gated: false,
      blocked: null,
    },
    {
      tome: 'SURVIVORS_NOTES',
      title: "Survivors' Notes",
      effect: 'Wave Carrying Capacity',
      bonusPercent: 0,
      bonusFlat: 10,
      level: 2,
      cost: 135,
      gated: false,
      blocked: null,
    },
  ];

  const postUpgrades: PostUpgradeView[] = [
    {
      trade: 'BREAKER',
      workplace: 'Quarry',
      level: 1,
      posts: 15,
      nextPosts: 20,
      cost: 40,
      blocked: null,
    },
    {
      trade: 'SCRIBE',
      workplace: "Scribe's Study",
      level: 0,
      posts: 10,
      nextPosts: 15,
      cost: 25,
      blocked: 'Posts cost 25 Materials. The Vault holds 3.',
    },
  ];

  it('lists the Tomes, and states why a gated level cannot be bought', () => {
    load({ tomeViews });

    expect(text()).toContain("Tillers' Record");
    expect(text()).toContain('Level 1: Fields output +25%');
    expect(text()).toContain(
      'Tome 2 costs 90. Stockroom holds 80. Needs Highest Floor 8.',
    );
    const gated = fixture.nativeElement.querySelector(
      '[aria-label="Buy Tillers\' Record"]',
    ) as HTMLButtonElement;
    const open = fixture.nativeElement.querySelector(
      '[aria-label="Buy Seam Charts"]',
    ) as HTMLButtonElement;
    expect(gated.disabled).toBeTrue();
    expect(open.disabled).toBeFalse();
  });

  it("shows Survivors' Notes as a flat Carrying Capacity bonus", () => {
    load({ tomeViews });

    expect(text()).toContain('Level 2: Wave Carrying Capacity +20');
  });

  it('shows Trust and the Ring, and sends the purchase', () => {
    load({
      trustHeld: 12,
      ring: {
        level: 2,
        maxLevel: 5,
        cost: 23,
        maxed: false,
        speedBonus: 0.5,
        speedPerLevel: 0.25,
        capacityBonus: 10,
        capacityPerLevel: 5,
        blocked: 'Ring 3 costs 23. Trust 12.',
      },
    });

    expect(text()).toContain('Trust held 12');
    expect(text()).toContain('Level 2 of 5');
    expect(text()).toContain('Ring 3 costs 23. Trust 12.');
    const bind = fixture.nativeElement.querySelector(
      '[aria-label="Strengthen the Ring"]',
    ) as HTMLButtonElement;
    expect(bind.disabled).toBeTrue();

    load({
      trustHeld: 30,
      ring: { ...stateService.state().ring, blocked: null, cost: 23 },
    });
    (
      fixture.nativeElement.querySelector(
        '[aria-label="Strengthen the Ring"]',
      ) as HTMLButtonElement
    ).click();
    expect(sendAction).toHaveBeenCalledWith(GameAction.BUY_RING);
  });

  it('sends the Tome purchase', () => {
    load({ tomeViews });

    (
      fixture.nativeElement.querySelector(
        '[aria-label="Buy Seam Charts"]',
      ) as HTMLButtonElement
    ).click();

    expect(sendAction).toHaveBeenCalledWith(GameAction.BUY_TOME, {
      tome: 'SEAM_CHARTS',
    });
  });

  it('shows the Post upgrades with their cost and sends the purchase', () => {
    load({ postUpgrades });

    expect(text()).toContain('15 Posts, 20 after the next');
    expect(text()).toContain('Raise (40 Materials)');
    expect(text()).toContain('Posts cost 25 Materials. The Vault holds 3.');
    const raise = fixture.nativeElement.querySelector(
      '[aria-label="Raise Posts of Quarry"]',
    ) as HTMLButtonElement;
    raise.click();
    expect(sendAction).toHaveBeenCalledWith(GameAction.BUY_POSTS, {
      trade: 'BREAKER',
    });
    expect(
      (
        fixture.nativeElement.querySelector(
          '[aria-label="Raise Posts of Scribe\'s Study"]',
        ) as HTMLButtonElement
      ).disabled,
    ).toBeTrue();
  });

  it('keeps the avoided glossary terms out of the panel copy', () => {
    load({ tomeViews, postUpgrades });

    const avoided = [
      'idle units',
      'standby',
      'food pantry',
      'buy villagers',
      'farmers',
      'quarriers',
      'lore',
    ];
    const copy = text().toLowerCase();
    avoided.forEach((term) => expect(copy).not.toContain(term));
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

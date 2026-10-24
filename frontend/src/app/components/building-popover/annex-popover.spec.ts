import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GameAction } from '../../interfaces/game-action.enum';
import { TradePreview, TradeView } from '../../interfaces/village';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import { buildingViews } from '../../three/village/building-registry';
import { BUILDINGS, villagerPurchase } from '../../three/village/buildings';
import { captionFor, captionText } from '../../three/village/village-captions';
import { BuildingPopover } from './building-popover';

const views: TradeView[] = [
  { trade: 'TILLER', plural: 'Tillers', workplace: 'Fields', hands: 16, posts: null, settling: 0, settlingSeconds: 0 },
  { trade: 'BREAKER', plural: 'Breakers', workplace: 'Quarry', hands: 3, posts: 10, settling: 1, settlingSeconds: 7 },
  { trade: 'SALTER', plural: 'Salters', workplace: 'Salt Pans', hands: 0, posts: 10, settling: 0, settlingSeconds: 0 },
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

describe('the Annexes and the Silo as buildings', () => {
  let fixture: ComponentFixture<BuildingPopover>;
  let sendAction: jasmine.Spy;
  let popover: PopoverService;
  let game: GameStateService;

  const el = () => fixture.nativeElement as HTMLElement;
  const show = () => {
    TestBed.flushEffects();
    fixture.detectChanges();
  };
  const button = (label: string) =>
    Array.from(el().querySelectorAll('button')).find((b) =>
      b.textContent!.includes(label),
    ) as HTMLButtonElement;

  beforeEach(() => {
    sendAction = jasmine.createSpy('sendAction');
    TestBed.configureTestingModule({
      providers: [{ provide: GameWebSocketService, useValue: { sendAction } }],
    });
    game = TestBed.inject(GameStateService);
    game.update({
      tradeViews: views,
      tradePreviews: [
        preview({ trade: 'BREAKER', delta: 1 }),
        preview({ trade: 'BREAKER', delta: -1, blocked: 'Settling 7 s.' }),
      ],
      villageTierLevel: 2,
      villageTierName: 'Sheltered',
      highestFloor: 12,
      yieldBonus: 0.24,
      quarryRate: 0.75,
      credit: 60,
      foodUpkeep: 8,
      pantryHoldSeconds: 99,
      village: { ...game.state().village, villagersCount: 20, productionRate: 7.5 },
    });
    popover = TestBed.inject(PopoverService);
    fixture = TestBed.createComponent(BuildingPopover);
    show();
  });
  afterEach(() => TestBed.inject(StageService).showMenus());

  it('opens an Annex from a click on it and on its Villagers', () => {
    popover.openFor({ kind: 'annex', id: 'BREAKER' });
    show();
    expect(el().textContent).toContain('The Quarry');
    expect(el().textContent).toContain('Breakers');
    expect(el().textContent).toContain('3 of 10 Posts.');
    expect(el().textContent).toContain('Settling 7 s.');
    expect(el().textContent).toContain('+0.75/s');
    expect(el().textContent).toContain('yield +24%');

    popover.openFor({ kind: 'villagers', id: 'BREAKER' });
    expect(popover.openSubject()).toEqual({ kind: 'annex', id: 'BREAKER' });
  });

  it('assigns through the existing command', () => {
    popover.openFor({ kind: 'annex', id: 'BREAKER' });
    show();
    button('Assign one more Breakers').click();
    expect(sendAction).toHaveBeenCalledWith(GameAction.ASSIGN_TRADE, {
      trade: 'BREAKER',
      delta: 1,
    });
  });

  it('refuses a blocked removal in words and sends nothing', () => {
    popover.openFor({ kind: 'annex', id: 'BREAKER' });
    show();
    const minus = button('to the Fields');
    expect(minus.getAttribute('aria-disabled')).toBe('true');
    expect(el().textContent).toContain('Settling 7 s.');
    minus.click();
    expect(sendAction).not.toHaveBeenCalled();
  });

  it('shows the margin before the click, on hover and on focus', () => {
    popover.openFor({ kind: 'annex', id: 'BREAKER' });
    show();
    expect(el().textContent).not.toContain('After this move');
    const plus = button('Assign one more Breakers');
    plus.dispatchEvent(new Event('mouseenter'));
    show();
    expect(el().textContent).toContain(
      'After this move: Fields +7.5. Upkeep -8.0. Fields do not cover Upkeep. Pantry holds 99 s.',
    );
    plus.dispatchEvent(new Event('mouseleave'));
    show();
    expect(el().textContent).not.toContain('After this move');
    plus.dispatchEvent(new Event('focus'));
    show();
    expect(el().textContent).toContain('After this move');
  });

  it('states the Tiller minimum in words and has no steps on the Fields', () => {
    popover.openFor({ kind: 'annex', id: 'TILLER' });
    show();
    expect(el().textContent).toContain('At least 5 Tillers stay in the Fields.');
    expect(el().querySelectorAll('button:not(.dismiss)').length).toBe(0);
  });

  it('keeps a closed footing out of the buildings and says what opens it on hover', () => {
    game.update({ villageTierLevel: 1, villageTierName: 'Hamlet' });
    const ids = buildingViews(BUILDINGS, game.state()).map((b) => b.id);
    expect(ids).not.toContain('SALTER');
    expect(captionText(captionFor({ kind: 'annex', id: 'SALTER' }, game.state(), { startFloor: 0 })!)).toContain(
      'Village Tier II',
    );
  });

  it('puts the margin a hand would leave in the hover caption', () => {
    const caption = captionFor({ kind: 'annex', id: 'BREAKER' }, game.state(), { startFloor: 0 })!;
    expect(captionText(caption)).toContain('With one more here: Fields +7.5. Upkeep -8.0.');
  });

  it('buys Villagers at the Silo, with the price and what is left shown first', () => {
    // 20 held, 60 Credits: 21 costs 21, 22 costs 22, then 17 left is below the count.
    expect(villagerPurchase(game.state())).toEqual({ count: 2, spent: 43, left: 17 });
    popover.openFor({ kind: 'building', id: 'SILO' });
    show();
    expect(el().textContent).toContain('The next Villager costs 21 Credits. The Vault holds 60.');
    expect(el().textContent).toContain('Upkeep');
    expect(el().textContent).toContain('99 s');
    const buy = button('Take in Villagers');
    buy.dispatchEvent(new Event('mouseenter'));
    show();
    expect(el().textContent).toContain('Takes in 2 Villagers for 43 Credits');
    buy.click();
    expect(sendAction).toHaveBeenCalledOnceWith(GameAction.BUY_VILLAGERS, undefined);
  });

  it('refuses a purchase the Vault cannot pay, in words', () => {
    game.update({ credit: 5 });
    popover.openFor({ kind: 'building', id: 'SILO' });
    show();
    const buy = button('Take in Villagers');
    expect(buy.getAttribute('aria-disabled')).toBe('true');
    expect(el().textContent).toContain('The Vault holds 5 Credits. The next Villager costs 21.');
    buy.click();
    expect(sendAction).not.toHaveBeenCalled();
  });
});

import { Component, computed, inject, signal } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe } from '@angular/common';
import { TomeId, TradeId, TradePreview } from '../../interfaces/village';

@Component({
  selector: 'app-village-management',
  templateUrl: './village-management.html',
  imports: [DecimalPipe],
  styleUrl: './village-management.scss',
})
export class VillageManagement {
  private gameStateService = inject(GameStateService);
  private ws = inject(GameWebSocketService);

  readonly gameState = this.gameStateService.state;
  readonly food = computed(() => this.gameState().village.food);
  readonly villagersCount = computed(
    () => this.gameState().village.villagersCount,
  );
  readonly production = computed(() => this.gameState().village.productionRate);
  readonly upkeep = computed(() => this.gameState().foodUpkeep);
  readonly netFood = computed(() => this.gameState().netFoodPerSecond);
  readonly recovery = computed(() => this.gameState().recoverySpend);
  readonly pantryHoldSeconds = computed(
    () => this.gameState().pantryHoldSeconds,
  );
  readonly pantryHoldsShort = computed(() => {
    const seconds = this.pantryHoldSeconds();
    return seconds !== null && seconds < 60;
  });
  readonly homeUnits = computed(() => this.gameState().homeUnitCount);
  readonly materials = computed(() => this.gameState().resources.MATERIALS);
  readonly relics = computed(() => this.gameState().resources.RELICS);

  /** The non-Fields Trades, as the assignment rows. */
  readonly trades = computed(() =>
    this.gameState().tradeViews.filter((view) => view.trade !== 'TILLER'),
  );
  readonly tillers = computed(
    () =>
      this.gameState().tradeViews.find((view) => view.trade === 'TILLER')
        ?.hands ?? this.villagersCount(),
  );
  readonly emergencyTilling = computed(
    () => this.gameState().village.emergencyTilling,
  );
  readonly yieldPercent = computed(() =>
    Math.round(this.gameState().yieldBonus * 100),
  );
  readonly tomes = computed(() => this.gameState().tomeViews);
  readonly postUpgrades = computed(() => this.gameState().postUpgrades);
  readonly theCount = computed(() => this.gameState().theCount);
  readonly tally = computed(() => this.gameState().tally);
  readonly ledger = computed(() => this.gameState().ledger.slice(-5));

  /** The move the player is pointing at; its margin is stated under the rows. */
  private readonly pointedAt = signal<{ trade: TradeId; delta: 1 | -1 } | null>(
    null,
  );

  readonly preview = computed<TradePreview | null>(() => {
    const pointed = this.pointedAt();
    if (!pointed) {
      return null;
    }
    return (
      this.gameState().tradePreviews.find(
        (p) => p.trade === pointed.trade && p.delta === pointed.delta,
      ) ?? null
    );
  });

  point(trade: TradeId, delta: 1 | -1) {
    this.pointedAt.set({ trade, delta });
  }

  unpoint() {
    this.pointedAt.set(null);
  }

  previewFor(trade: TradeId, delta: 1 | -1): TradePreview | undefined {
    return this.gameState().tradePreviews.find(
      (p) => p.trade === trade && p.delta === delta,
    );
  }

  /** Output per second of a Trade, in its own good. */
  output(trade: TradeId): number {
    switch (trade) {
      case 'BREAKER':
        return this.gameState().quarryRate;
      case 'SALTER':
        return this.gameState().saltRate;
      default:
        return this.gameState().studyRate;
    }
  }

  onClickAssign(trade: TradeId, delta: 1 | -1) {
    this.ws.sendAction(GameAction.ASSIGN_TRADE, { trade, delta });
  }

  onClickBuyTome(tome: TomeId) {
    this.ws.sendAction(GameAction.BUY_TOME, { tome });
  }

  onClickBuyPosts(trade: TradeId) {
    this.ws.sendAction(GameAction.BUY_POSTS, { trade });
  }

  onClickBuyVillager() {
    this.ws.sendAction(GameAction.BUY_VILLAGERS);
  }

  onClickSellFood() {
    this.ws.sendAction(GameAction.SELL_FOOD);
  }

  onClickSellMaterials() {
    this.ws.sendAction(GameAction.SELL_MATERIALS);
  }

  onClickSellRelics() {
    this.ws.sendAction(GameAction.SELL_RELICS);
  }
}

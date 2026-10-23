import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { VillageSelectionService } from '../../services/village-selection.service';
import { SubjectKind } from '../../three/village/village-subjects';
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
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly selection = inject(VillageSelectionService);

  constructor() {
    // A click in the scene brings its row into view and takes focus there.
    effect(() => {
      const request = this.selection.focusRequest();
      if (!request) {
        return;
      }
      untracked(() => {
        const row = request.subject.kind === 'upgrade' ? 'upgrade' : 'annex';
        const el = this.host.nativeElement.querySelector<HTMLElement>(
          `[data-row="${row}:${request.subject.id}"]`,
        );
        if (!el) {
          return;
        }
        const still =
          typeof matchMedia === 'function' &&
          matchMedia('(prefers-reduced-motion: reduce)').matches;
        el.scrollIntoView({
          block: 'nearest',
          behavior: still ? 'auto' : 'smooth',
        });
        el.focus({ preventScroll: true });
      });
    });
  }

  /** Pointer or focus on a row: the same hover as on the scene. */
  hoverRow(kind: SubjectKind | null, id?: string) {
    this.selection.hover(kind && id ? { kind, id } : null);
  }

  /** A click on a row picks its thing, never acts. */
  pickRow(kind: SubjectKind, id: string, event?: Event) {
    if ((event?.target as HTMLElement | null)?.closest('button')) {
      return;
    }
    this.selection.select({ kind, id });
  }

  /** Enter or Space on the row itself is a click; Escape lets go. */
  onRowKey(event: KeyboardEvent, kind: SubjectKind, id: string) {
    if (event.key === 'Escape') {
      this.selection.select(null);
      return;
    }
    if (
      event.target === event.currentTarget &&
      (event.key === 'Enter' || event.key === ' ')
    ) {
      event.preventDefault();
      this.pickRow(kind, id);
    }
  }

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
  readonly ring = computed(() => this.gameState().ring);
  readonly trustHeld = computed(() => this.gameState().trustHeld);
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
      case 'SPOREWARDEN':
        return this.gameState().sporeRate;
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

  onClickBuyRing() {
    this.ws.sendAction(GameAction.BUY_RING);
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

  onClickSellSalt() {
    this.ws.sendAction(GameAction.SELL_SALT);
  }

  onClickSellRelics() {
    this.ws.sendAction(GameAction.SELL_RELICS);
  }
}

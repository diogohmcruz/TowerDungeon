import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  untracked,
} from '@angular/core';
import { VillageSelectionService } from '../../services/village-selection.service';
import { SubjectKind } from '../../three/village/village-subjects';
import { GameStateService } from '../../services/game-state.service';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe } from '@angular/common';
import { Quarter } from '../../interfaces/game-state';
import { TomeId, TradeId } from '../../interfaces/village';

const QUARTER_NAME: Record<Quarter, string> = {
  SPRING: 'Spring',
  SUMMER: 'Summer',
  AUTUMN: 'Autumn',
  WINTER: 'Winter',
};

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
  /** The Year and its Quarter in words (the picture never carries them alone); empty in a Trial. */
  readonly yearLine = computed(() => {
    const s = this.gameState();
    if (!s.quarter || s.year == null) {
      return '';
    }
    const next = s.nextQuarter ? `, ${QUARTER_NAME[s.nextQuarter]} follows` : '';
    return `Year ${s.year}, ${QUARTER_NAME[s.quarter]}${next}`;
  });
  /** The danger at sea in words, once the Port has a footing. */
  readonly seaLine = computed(() => {
    const s = this.gameState();
    if (s.seaRisk == null || !s.port || s.port.state === 'HIDDEN') {
      return '';
    }
    return `Danger at sea ${Math.round(s.seaRisk * 100)}%`;
  });
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

  readonly tomes = computed(() => this.gameState().tomeViews);
  readonly ring = computed(() => this.gameState().ring);
  readonly trustHeld = computed(() => this.gameState().trustHeld);
  readonly postUpgrades = computed(() => this.gameState().postUpgrades);
  readonly theCount = computed(() => this.gameState().theCount);
  readonly tally = computed(() => this.gameState().tally);
  readonly ledger = computed(() => this.gameState().ledger.slice(-5));

  onClickBuyTome(tome: TomeId) {
    this.ws.sendAction(GameAction.BUY_TOME, { tome });
  }

  onClickBuyRing() {
    this.ws.sendAction(GameAction.BUY_RING);
  }

  onClickBuyPosts(trade: TradeId) {
    this.ws.sendAction(GameAction.BUY_POSTS, { trade });
  }
}

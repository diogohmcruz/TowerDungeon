import { Component, computed, inject } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { DecimalPipe } from '@angular/common';
import { Quarter } from '../../interfaces/game-state';

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

  readonly theCount = computed(() => this.gameState().theCount);
  readonly tally = computed(() => this.gameState().tally);
  readonly ledger = computed(() => this.gameState().ledger.slice(-5));
}

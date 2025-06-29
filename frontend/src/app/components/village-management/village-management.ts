import { Component, computed, inject } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { GameAction } from '../../interfaces/game-action.enum';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe } from '@angular/common';

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

  onClickBuyVillager() {
    this.ws.sendAction(GameAction.BUY_VILLAGERS);
  }

  onClickSellFood() {
    this.ws.sendAction(GameAction.SELL_FOOD);
  }
}

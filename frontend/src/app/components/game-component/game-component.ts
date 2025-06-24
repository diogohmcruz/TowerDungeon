import { Component, effect, inject, signal } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { GameAction } from '../../interfaces/game-action.enum';
import { Unit } from '../../interfaces/unit.enum';
import { Army } from '../army/army';
import { TowerDisplay } from '../tower/tower-display.component';

@Component({
  selector: 'app-game-component',
  standalone: true,
  imports: [DecimalPipe, LowerCasePipe, Army, TowerDisplay],
  templateUrl: './game-component.html',
})
export class GameComponent {
  readonly gameState = inject(GameStateService).state;
  private ws = inject(GameWebSocketService);
  readonly units = signal(Object.entries(Unit));

  sendBuyAction(unitStats: Unit) {
    const payload = { unitStats, quantity: 1 };
    this.ws.sendAction(GameAction.BUY, payload);
  }

  sendInvadeAction() {
    const units = new Map();
    //units.set("unitStats", Unit.WARRIOR);
    //units.set("quantity", 1);
    const payload = { units };
    this.ws.sendAction(GameAction.INVADE, payload);
  }

  upgrade() {
    this.ws.sendAction(GameAction.UPGRADE, null);
  }

  prestige() {
    this.ws.sendAction(GameAction.PRESTIGE, null);
  }

  constructor() {
    effect(() => {
      const mana = this.gameState().mana;
      console.log('Mana atualizada:', mana);
    });
  }
}

import { Component, inject, Signal, signal } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { GameAction } from '../../interfaces/game-action.enum';
import { Unit, UnitStats } from '../../interfaces/unit.enum';
import { Army } from '../army/army';
import { TowerDisplay } from '../tower/tower-display.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { UnitStatsService } from '../../services/unit-stats.service';
import { VillageManagement } from '../village-management/village-management';

@Component({
  selector: 'app-game-component',
  standalone: true,
  imports: [
    DecimalPipe,
    LowerCasePipe,
    Army,
    TowerDisplay,
    VillageManagement,
  ],
  templateUrl: './game-component.html',
  styleUrls: ['./game-component.scss'],
})
export class GameComponent {
  readonly gameState = inject(GameStateService).state;
  private ws = inject(GameWebSocketService);
  private unitStatsService = inject(UnitStatsService);

  readonly units = signal(Object.entries(Unit));
  unitStats: Signal<Map<Unit, UnitStats>> = toSignal(
    this.unitStatsService.getUnitStats(),
    { initialValue: new Map<Unit, UnitStats>() },
  );

  sendBuyAction(unitStats: Unit) {
    const payload = { unitStats, quantity: 1 };
    this.ws.sendAction(GameAction.BUY, payload);
  }

  mapStringToUnit(unitAsString: string): UnitStats | undefined {
    return this.unitStats()?.get(unitAsString as Unit);
  }

  sendInvadeAction() {
    const units = new Map();
    //units.set("unitStats", Unit.WARRIOR);
    //units.set("quantity", 1);
    const payload = { units };
    this.ws.sendAction(GameAction.INVADE, payload);
  }

  extract() {
    this.ws.sendAction(GameAction.EXTRACT, null);
  }

  upgrade() {
    this.ws.sendAction(GameAction.UPGRADE, null);
  }

  prestige() {
    this.ws.sendAction(GameAction.PRESTIGE, null);
  }
}

import { Component, computed, inject, Signal } from '@angular/core';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe } from '@angular/common';
import { GameAction } from '../../interfaces/game-action.enum';
import { Unit, UnitStats } from '../../interfaces/unit.enum';
import { Army } from '../army/army';
import { TowerDisplay } from '../tower/tower-display.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { UnitStatsService } from '../../services/unit-stats.service';
import { VillageManagement } from '../village-management/village-management';
import { UnitCard } from '../unit-card/unit-card';
import { UpgradeCard } from '../upgrade-card/upgrade-card';
import { MilestoneCard } from '../milestone-card/milestone-card';

@Component({
  selector: 'app-game-component',
  standalone: true,
  imports: [
    DecimalPipe,
    Army,
    TowerDisplay,
    VillageManagement,
    UnitCard,
    UpgradeCard,
    MilestoneCard,
  ],
  templateUrl: './game-component.html',
  styleUrls: ['./game-component.scss'],
})
export class GameComponent {
  readonly gameState = inject(GameStateService).state;
  private ws = inject(GameWebSocketService);
  private unitStatsService = inject(UnitStatsService);

  readonly units = computed(() =>
    (this.gameState().unlockedUnitTypes ?? []).map(
      (type) => [type, type] as [string, string],
    ),
  );
  unitStats: Signal<Map<Unit, UnitStats>> = toSignal(
    this.unitStatsService.getUnitStats(),
    { initialValue: new Map<Unit, UnitStats>() },
  );

  sendBuyAction(unitStats: string) {
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

  buyUpgrade(upgradeId: string) {
    this.ws.sendAction(GameAction.UPGRADE, { upgradeId });
  }

  canAfford(cost: { MATERIALS?: number; RELICS?: number }): boolean {
    const banked = this.gameState().resources;
    return (
      (cost.MATERIALS ?? 0) <= banked.MATERIALS &&
      (cost.RELICS ?? 0) <= banked.RELICS
    );
  }

  prestige() {
    this.ws.sendAction(GameAction.PRESTIGE, null);
  }
}

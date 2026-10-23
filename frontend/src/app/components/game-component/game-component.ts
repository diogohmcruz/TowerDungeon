import { Component, computed, inject, signal, Signal } from '@angular/core';
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
import { ShortcutCard } from '../shortcut-card/shortcut-card';
import { RunSummaryCard } from '../run-summary-card/run-summary-card';
import { Reinforcement } from '../../interfaces/game-state';
import { Loadout } from '../loadout/loadout';

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
    ShortcutCard,
    RunSummaryCard,
    Loadout,
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

  /** Floor the next expedition begins on: 0 = base of the tower, otherwise an open shortcut. */
  readonly selectedStartFloor = signal(0);

  /** Salt the Loadout has chosen; 0 unless the player opened it and chose some. */
  readonly saltToUse = signal(0);

  readonly openShortcuts = computed(() =>
    (this.gameState().shortcuts ?? []).filter((s) => s.unlocked),
  );

  readonly gameWon = computed(() => this.gameState().gameOutcome === 'VICTORY');
  readonly gameLost = computed(() => this.gameState().gameOutcome === 'DEFEAT');
  readonly gameOver = computed(() => this.gameWon() || this.gameLost());
  unitStats: Signal<Map<Unit, UnitStats>> = toSignal(
    this.unitStatsService.getUnitStats(),
    { initialValue: new Map<Unit, UnitStats>() },
  );

  sendBuyAction(unitStats: string) {
    const payload = { unitStats, quantity: 1 };
    this.ws.sendAction(GameAction.BUY, payload);
  }

  reserveCount(unitAsString: string): number {
    return this.gameState().units?.[unitAsString as Unit]?.length ?? 0;
  }

  mapStringToUnit(unitAsString: string): UnitStats | undefined {
    return this.unitStats()?.get(unitAsString as Unit);
  }

  sendInvadeAction() {
    const units = new Map();
    const loadout = this.gameState().loadout;
    const salt = Math.min(
      this.saltToUse(),
      loadout?.saltHeld ?? 0,
      loadout?.saltUseful ?? 0,
    );
    const payload = { units, startFloor: this.selectedStartFloor(), salt };
    this.ws.sendAction(GameAction.INVADE, payload);
    this.saltToUse.set(0);
  }

  selectStartFloor(floor: number) {
    this.selectedStartFloor.set(floor);
  }

  resume() {
    this.ws.sendAction(GameAction.RESUME, null);
  }

  extract() {
    this.ws.sendAction(GameAction.EXTRACT, null);
  }

  sendReinforcements() {
    this.ws.sendAction(GameAction.REINFORCE, { units: {} });
  }

  waveSize(wave: Reinforcement): number {
    return Object.values(wave.units ?? {}).reduce(
      (total, members) => total + (members?.length ?? 0),
      0,
    );
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

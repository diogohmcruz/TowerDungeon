import {
  Component,
  computed,
  inject,
  OnDestroy,
  signal,
  Signal,
} from '@angular/core';
import { LaunchService } from '../../services/launch.service';
import { StageService } from '../../services/stage.service';
import { ZoomService } from '../../services/zoom.service';
import {
  CARD_GAP,
  CARD_MARGIN,
  CARD_MIN_WIDTH,
  cardLayerWidth,
  NARROW_DIM,
  PANEL_OPACITY,
  SCRIM_DITHER,
  SCRIM_TONE,
  TOP_BAR,
} from '../../three/stage-view';
import { GameStateService } from '../../services/game-state.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { DecimalPipe } from '@angular/common';
import { GameAction } from '../../interfaces/game-action.enum';
import { Unit, UnitStats } from '../../interfaces/unit.enum';
import { Army } from '../army/army';
import { ExpeditionHud } from '../expedition-hud/expedition-hud';
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
import { TollBar } from '../toll-bar/toll-bar';
import { SuccessionScreen } from '../succession-screen/succession-screen';
import { ListView } from '../list-view/list-view';

@Component({
  selector: 'app-game-component',
  standalone: true,
  imports: [
    DecimalPipe,
    Army,
    TowerDisplay,
    ExpeditionHud,
    VillageManagement,
    UnitCard,
    UpgradeCard,
    MilestoneCard,
    ShortcutCard,
    RunSummaryCard,
    TollBar,
    SuccessionScreen,
    ListView,
  ],
  templateUrl: './game-component.html',
  styleUrls: ['./game-component.scss'],
  host: {
    '[class.narrow]': 'stage.narrow()',
    '[style.--card-layer-width.px]': 'layerWidth()',
    '[style.--card-margin.px]': 'CARD_MARGIN',
    '[style.--card-gap.px]': 'CARD_GAP',
    '[style.--card-min.px]': 'CARD_MIN_WIDTH',
    '[style.--top-bar.px]': 'TOP_BAR',
    '[style.--panel-opacity]': 'PANEL_OPACITY',
    '[style.--scrim-tone]': 'SCRIM_TONE',
    '[style.--scrim-dither]': 'SCRIM_DITHER',
    '[style.--scrim-dim]': 'NARROW_DIM',
    '[style.--dither-cell.px]': 'stage.scale()',
    '(document:keydown)': 'onKey($event)',
  },
})
export class GameComponent implements OnDestroy {
  protected readonly stage = inject(StageService);
  protected readonly zoom = inject(ZoomService);
  protected readonly CARD_MARGIN = CARD_MARGIN;
  protected readonly CARD_GAP = CARD_GAP;
  protected readonly CARD_MIN_WIDTH = CARD_MIN_WIDTH;
  protected readonly TOP_BAR = TOP_BAR;
  /** How far right the cards reach: past it lies the Tower's lane, clear of cards. */
  protected readonly layerWidth = computed(() =>
    cardLayerWidth(this.stage.viewport().width, this.stage.narrow()),
  );
  protected readonly PANEL_OPACITY = PANEL_OPACITY;
  protected readonly SCRIM_TONE = SCRIM_TONE;
  protected readonly SCRIM_DITHER = SCRIM_DITHER;
  protected readonly NARROW_DIM = NARROW_DIM;

  ngOnDestroy(): void {
    // Leaving the page releases a closeup's hold.
    this.zoom.release();
  }

  /** H hides and restores the menus; Escape steps the zoom back (not while typing, and not with a modifier). */
  protected onKey(event: KeyboardEvent): void {
    const key = event.key.toLowerCase();
    if (
      (key !== 'h' && key !== 'escape') ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.defaultPrevented
    ) {
      return;
    }
    const target = event.target as HTMLElement | null;
    if (
      target &&
      (target.isContentEditable ||
        ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
    ) {
      return;
    }
    if (key === 'escape') {
      if (this.zoom.level() !== 'battle') {
        event.preventDefault();
        this.zoom.back();
      }
      return;
    }
    this.stage.toggleMenus();
  }

  private readonly stateService = inject(GameStateService);
  readonly gameState = this.stateService.state;
  /** The session facts the socket strips from the state: a Held Party, the Succession. */
  readonly session = this.stateService.session;

  /** The Campaign has ended and waits for the Succession. */
  readonly offer = computed(() => this.gameState().phase === 'OFFER');
  /** The Roll and the choices the server sent for the Succession. */
  readonly succession = computed(
    () =>
      this.stateService.session()?.succession ??
      this.gameState().session?.succession ??
      null,
  );
  private ws = inject(GameWebSocketService);
  private unitStatsService = inject(UnitStatsService);

  readonly units = computed(() =>
    (this.gameState().unlockedUnitTypes ?? []).map(
      (type) => [type, type] as [string, string],
    ),
  );

  /** The launch commands and what the Loadout has chosen; shared with the scene's bell, lamp and doorways. */
  protected readonly launch = inject(LaunchService);

  /** Floor the next expedition begins on: 0 = base of the tower, otherwise an open shortcut. */
  readonly selectedStartFloor = this.launch.startFloor;

  /** What the Loadout (chosen at the Gate) has chosen, which Set out carries. */
  readonly saltToUse = this.launch.saltToUse;
  readonly packSalt = this.launch.packSalt;
  readonly packBearer = this.launch.packBearer;
  readonly panaceaChosen = this.launch.panaceaChosen;

  /** Whole Dragon Spores in the Stockroom. */
  readonly sporesHeld = computed(() =>
    Math.floor(this.gameState().village.spores),
  );

  readonly openShortcuts = computed(() =>
    (this.gameState().shortcuts ?? []).filter((s) => s.unlocked),
  );

  readonly gameWon = computed(() => this.gameState().gameOutcome === 'VICTORY');
  readonly gameLost = computed(() => this.gameState().gameOutcome === 'DEFEAT');
  readonly gameLeft = computed(() => this.gameState().gameOutcome === 'EXODUS');
  readonly gameOver = computed(
    () => this.gameWon() || this.gameLost() || this.gameLeft(),
  );
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
    this.launch.sendInvade();
  }

  giveRemedy(unitId: number) {
    this.ws.sendAction(GameAction.REMEDY, { unitId });
  }

  reattachPart(event: { unitId: number; part: string }) {
    this.ws.sendAction(GameAction.REATTACH, event);
  }

  selectStartFloor(floor: number) {
    this.selectedStartFloor.set(floor);
  }

  exodus() {
    this.ws.sendAction(GameAction.EXODUS, null);
  }

  rollBuy(id: string) {
    this.ws.sendAction(GameAction.ROLL_BUY, { id });
  }

  rollRefund(id: string) {
    this.ws.sendAction(GameAction.ROLL_REFUND, { id });
  }

  choose(id: string) {
    this.ws.sendAction(GameAction.CHOOSE, { id });
  }

  beginGeneration() {
    this.ws.sendAction(GameAction.BEGIN_GENERATION, null);
  }

  resume() {
    this.ws.sendAction(GameAction.RESUME, null);
  }

  extract() {
    this.launch.sendExtract();
  }

  sendReinforcements() {
    this.launch.sendWave();
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
}

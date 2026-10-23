import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GameComponent } from './components/game-component/game-component';
import { Gate } from './components/gate/gate';
import { SettingsPanel } from './components/settings-panel/settings-panel';
import { RaceHud } from './components/race-hud/race-hud';
import { RaceJoin } from './components/race-join/race-join';
import { RaceTable } from './components/race-table/race-table';
import {
  CLOSE_BAD_KEY,
  CLOSE_TRIAL_REFUSED,
  GameWebSocketService,
} from './services/game-web-socket.service';
import { GameStateService } from './services/game-state.service';

type Theme = 'light' | 'dark';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, GameComponent, Gate, SettingsPanel, RaceHud, RaceJoin, RaceTable],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected title = 'Tower Dungeon';
  protected readonly theme = signal<Theme>(this.resolveInitialTheme());
  protected readonly ws = inject(GameWebSocketService);
  protected readonly gameState = inject(GameStateService).state;

  /** Which race panel is open: the join screen, or a race's finished table. */
  protected readonly panel = signal<{ kind: 'join' | 'table'; raceId?: string } | null>(null);
  /** The Trial this connection is in; the HUD and the table appear only then. */
  protected readonly trial = computed(() =>
    this.ws.trialRaceId() !== null ? (this.gameState().trial ?? null) : null,
  );
  protected readonly trialClosed = computed(() => {
    const status = this.trial()?.status;
    return status !== undefined && status !== 'RACING';
  });
  /** The finished table that opens by itself when a Trial closes has been put away. */
  protected readonly dismissedTable = signal<string>('');
  protected readonly autoTable = computed(() => {
    const t = this.trial();
    const open = t !== null && t.status !== 'RACING' && this.dismissedTable() !== `${t.raceId}#${t.attempt}`;
    return open ? t : null;
  });
  protected dismissTable(): void {
    const t = this.trial();
    this.dismissedTable.set(t ? `${t.raceId}#${t.attempt}` : '');
  }
  /** A Trial was refused at the door: nothing is connected until the player goes back. */
  protected readonly refused = computed(() => this.ws.stopReason()?.code === CLOSE_TRIAL_REFUSED);

  /** The gate shows until the player begins, and again when the server refuses the key. */
  protected readonly showGate = computed(
    () => this.ws.status() === 'idle' || this.ws.stopReason()?.code === CLOSE_BAD_KEY,
  );
  protected readonly notice = computed(() => {
    if (this.ws.status() === 'reconnecting') {
      return 'Connection lost. Reconnecting.';
    }
    return this.ws.stopReason()?.message ?? null;
  });
  protected readonly canRetry = computed(() => this.ws.stopReason()?.retryable === true);

  constructor() {
    this.applyTheme(this.theme());
  }

  protected leaveTrial(): void {
    this.panel.set(null);
    this.ws.leaveTrial();
  }

  protected retry(): void {
    this.ws.connect();
  }

  protected toggleTheme(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.applyTheme(next);
    localStorage.setItem('td-theme', next);
  }

  private resolveInitialTheme(): Theme {
    const stored = localStorage.getItem('td-theme');
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
    return 'dark';
  }

  private applyTheme(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}

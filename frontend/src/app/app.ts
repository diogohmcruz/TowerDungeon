import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GameComponent } from './components/game-component/game-component';
import { Gate } from './components/gate/gate';
import { SettingsPanel } from './components/settings-panel/settings-panel';
import { CLOSE_BAD_KEY, GameWebSocketService } from './services/game-web-socket.service';

type Theme = 'light' | 'dark';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, GameComponent, Gate, SettingsPanel],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected title = 'Tower Dungeon';
  protected readonly theme = signal<Theme>(this.resolveInitialTheme());
  protected readonly ws = inject(GameWebSocketService);

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

import { Component, computed, inject, signal } from '@angular/core';
import { CampaignKeyService, isCampaignKey } from '../../services/campaign-key.service';
import { GameWebSocketService } from '../../services/game-web-socket.service';

/** The screen before the Campaign: begin, resume with a key, or (later) import a file. */
@Component({
  selector: 'app-gate',
  standalone: true,
  templateUrl: './gate.html',
  styleUrl: './gate.scss',
})
export class Gate {
  private readonly keys = inject(CampaignKeyService);
  private readonly ws = inject(GameWebSocketService);

  /** This browser already holds a Campaign Key, so Begin returns to that Campaign. */
  protected readonly hasCampaign = this.keys.existed;
  protected readonly reason = this.ws.stopReason;
  protected readonly pasted = signal('');
  protected readonly canResume = computed(() => isCampaignKey(this.pasted().trim()));
  protected readonly pastedInvalid = computed(
    () => this.pasted().trim().length > 0 && !this.canResume(),
  );

  protected begin(): void {
    this.ws.connect();
  }

  protected resume(): void {
    if (this.keys.adopt(this.pasted())) {
      this.ws.connect();
    }
  }

  protected onInput(event: Event): void {
    this.pasted.set((event.target as HTMLInputElement).value);
  }
}

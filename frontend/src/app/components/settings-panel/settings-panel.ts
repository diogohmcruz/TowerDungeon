import { Component, inject, signal } from '@angular/core';
import { CampaignKeyService } from '../../services/campaign-key.service';

/** Settings: shows the Campaign Key so the player can copy it. */
@Component({
  selector: 'app-settings-panel',
  standalone: true,
  templateUrl: './settings-panel.html',
  styleUrl: './settings-panel.scss',
})
export class SettingsPanel {
  protected readonly key = inject(CampaignKeyService).key;
  protected readonly open = signal(false);
  protected readonly copied = signal(false);

  protected toggle(): void {
    this.open.update((v) => !v);
    this.copied.set(false);
  }

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.key());
      this.copied.set(true);
    } catch {
      this.copied.set(false);
    }
  }
}

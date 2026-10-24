import { Component, inject, signal } from '@angular/core';
import { CampaignKeyService } from '../../services/campaign-key.service';
import { StageService } from '../../services/stage.service';
import { parseQuality, PIXEL_SCALES } from '../../three/ink/pixel-scale';

/** Settings: the Campaign Key to copy, and the picture quality (the pixel size). */
@Component({
  selector: 'app-settings-panel',
  standalone: true,
  templateUrl: './settings-panel.html',
  styleUrl: './settings-panel.scss',
})
export class SettingsPanel {
  protected readonly key = inject(CampaignKeyService).key;
  protected readonly stage = inject(StageService);
  protected readonly options = PIXEL_SCALES;
  protected readonly open = signal(false);
  protected readonly copied = signal(false);

  protected chooseQuality(value: string): void {
    this.stage.setQuality(parseQuality(value));
  }

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

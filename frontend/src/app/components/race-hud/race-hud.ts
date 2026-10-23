import { Component, computed, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TrialCost, TrialRun } from '../../interfaces/race';
import { clock, statusText } from '../../interfaces/race-copy';

/**
 * The Race HUD: the clock, the Finish Floor and the Cost so far, shown only inside a Trial. The
 * Cost is set beside the clock and never summed into it. Plain numbers, no praise.
 */
@Component({
  selector: 'app-race-hud',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './race-hud.html',
  styleUrl: './race-hud.scss',
})
export class RaceHud {
  /** The Trial the Campaign is; the HUD draws nothing without one. */
  trial = input<TrialRun | null | undefined>(null);
  cost = input<TrialCost | null | undefined>(null);
  highestFloor = input<number>(0);

  leave = output<void>();

  protected readonly elapsed = computed(() => clock(this.trial()?.ticksSinceStart ?? 0));
  protected readonly limit = computed(() => clock(this.trial()?.limitTicks ?? 0));
  protected readonly status = computed(() => {
    const trial = this.trial();
    return trial ? statusText(trial.status) : '';
  });
  protected readonly racing = computed(() => this.trial()?.status === 'RACING');
}

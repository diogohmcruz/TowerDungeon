import { Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TollForecast } from '../../interfaces/game-state';

interface TollLine {
  label: string;
  marks: number;
}

/**
 * The Toll bar: what leaving the Tower now would pay, the Marks each counter
 * has earned, and Exodus, gated by the Toll's own rule and confirmed once.
 * It reports plain numbers and never congratulates.
 */
@Component({
  selector: 'app-toll-bar',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './toll-bar.html',
  styleUrl: './toll-bar.scss',
})
export class TollBar {
  forecast = input.required<TollForecast>();
  /** Raised once the player has confirmed Exodus. */
  leave = output<void>();

  readonly confirming = signal(false);

  /** Share of the way from the last whole point of Legacy to the next, 0 to 100. */
  readonly fill = computed(() => {
    const { toll, pointsDivisor, nextLegacyAt } = this.forecast();
    if (pointsDivisor <= 0) {
      return 0;
    }
    const share = (toll.toll - (nextLegacyAt - pointsDivisor)) / pointsDivisor;
    return Math.round(Math.min(1, Math.max(0, share)) * 100);
  });

  readonly lines = computed<TollLine[]>(() => {
    const { toll } = this.forecast();
    return [
      { label: 'Food grown', marks: toll.foodMarks },
      { label: 'Materials banked', marks: toll.materialsMarks },
      { label: 'Credits banked', marks: toll.creditsMarks },
      { label: 'Relics banked', marks: toll.relicsMarks },
      { label: 'Expeditions completed', marks: toll.expeditionsMarks },
      { label: 'Ascent', marks: toll.ascentMarks },
      { label: 'Guardians slain', marks: toll.guardianMarks },
    ];
  });

  /** Why Exodus is closed, in words; empty while it is open. */
  readonly closedReason = computed(() => {
    const f = this.forecast();
    if (f.canExodus) {
      return '';
    }
    return f.toll.legacy < f.exodusMinimum
      ? `Exodus opens at ${f.exodusMinimum} Legacy.`
      : 'Extract first. Exodus cannot leave a Party on the Tower.';
  });

  ask() {
    if (this.forecast().canExodus) {
      this.confirming.set(true);
    }
  }

  stay() {
    this.confirming.set(false);
  }

  confirm() {
    this.confirming.set(false);
    if (this.forecast().canExodus) {
      this.leave.emit();
    }
  }
}

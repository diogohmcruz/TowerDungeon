import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RunSummary } from '../../interfaces/game-state';
import { Card } from '../card/card';

@Component({
  selector: 'app-run-summary-card',
  standalone: true,
  templateUrl: './run-summary-card.html',
  styleUrl: './run-summary-card.scss',
  imports: [Card, DecimalPipe],
})
export class RunSummaryCard {
  summary = input.required<RunSummary>();

  wiped = computed(() => this.summary().outcome === 'WIPED');
  victory = computed(() => this.summary().outcome === 'VICTORY');
  titleText = computed(() => {
    switch (this.summary().outcome) {
      case 'WIPED':
        return 'Party Wiped';
      case 'VICTORY':
        return 'Tower Conquered';
      default:
        return 'Extraction Complete';
    }
  });
}

import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-supplies',
  standalone: true,
  templateUrl: './supplies.html',
  styleUrl: './supplies.scss',
  imports: [DecimalPipe],
})
export class Supplies {
  supplies = input<number>(0);
  maxSupplies = input<number>(0);
  percent = computed(() => {
    const max = this.maxSupplies();
    return max > 0 ? Math.min(100, (this.supplies() / max) * 100) : 0;
  });
}

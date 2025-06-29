import { Component, computed, inject, input } from '@angular/core';
import { Squad } from '../../interfaces/game-state';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-army',
  standalone: true,
  templateUrl: './army.html',
  styleUrl: './army.scss',
  imports: [DecimalPipe],
})
export class Army {
  army = input<Squad>();
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
}

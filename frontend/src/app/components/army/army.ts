import { Component, computed, input } from '@angular/core';
import { Squad } from '../../interfaces/game-state';

@Component({
  selector: 'app-army',
  standalone: true,
  templateUrl: './army.html',
  styleUrl: './army.css',
})
export class Army {
  army = input<Squad>();
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
}

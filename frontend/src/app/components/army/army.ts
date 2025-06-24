import {Component, computed, input, Input, signal} from '@angular/core';
import {Squad} from '../../interfaces/game-state';
import {KeyValuePipe} from '@angular/common';

@Component({
  selector: 'app-army',
  standalone: true,
  imports: [
    KeyValuePipe
  ],
  templateUrl: './army.html',
  styleUrl: './army.css'
})
export class Army {
  army = input<Squad>();
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
}

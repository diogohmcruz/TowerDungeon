import { Component, computed, input } from '@angular/core';
import { Squad } from '../../interfaces/game-state';
import { LowerCasePipe } from '@angular/common';
import { CharacterCard } from '../character-card/character-card';

@Component({
  selector: 'app-army',
  standalone: true,
  templateUrl: './army.html',
  styleUrl: './army.scss',
  imports: [LowerCasePipe, CharacterCard],
})
export class Army {
  army = input<Squad>();
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
  totalMembers = computed(() =>
    this.armyEntries().reduce((sum, entry) => sum + entry[1].length, 0),
  );
}

import { Component, computed, input, output } from '@angular/core';
import { Squad } from '../../interfaces/game-state';
import { LowerCasePipe } from '@angular/common';
import { CharacterCard } from '../character-card/character-card';
import { BodyPartKind } from '../../interfaces/member';

@Component({
  selector: 'app-army',
  standalone: true,
  templateUrl: './army.html',
  styleUrl: './army.scss',
  imports: [LowerCasePipe, CharacterCard],
})
export class Army {
  army = input<Squad>();
  /** Set for the Reserves at home, where Spore Doses can reach them. */
  atHome = input(false);
  sporesHeld = input(0);
  remedy = output<number>();
  reattach = output<{ unitId: number; part: BodyPartKind }>();
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
  totalMembers = computed(() =>
    this.armyEntries().reduce((sum, entry) => sum + entry[1].length, 0),
  );
}

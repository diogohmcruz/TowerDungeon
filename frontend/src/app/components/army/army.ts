import { Component, computed, inject, input, output } from '@angular/core';
import { Squad } from '../../interfaces/game-state';
import { LowerCasePipe } from '@angular/common';
import { CharacterCard } from '../character-card/character-card';
import { BodyPartKind } from '../../interfaces/member';
import { UnitViewService } from '../../services/unit-view.service';

@Component({
  selector: 'app-army',
  standalone: true,
  templateUrl: './army.html',
  styleUrl: './army.scss',
  imports: [LowerCasePipe, CharacterCard],
})
export class Army {
  private readonly unitView = inject(UnitViewService);
  army = input<Squad>();
  /** Set for the Reserves at home, where Spore Doses can reach them. */
  atHome = input(false);
  sporesHeld = input(0);
  remedy = output<number>();
  reattach = output<{ unitId: number; part: BodyPartKind }>();
  /** Choosing a unit (the name button, so by keyboard too) hands it to the unit view. */
  chooseUnit(unitId: number): void {
    this.unitView.choose(unitId);
  }
  armyEntries = computed(() => Object.entries(this.army() ?? {}));
  totalMembers = computed(() =>
    this.armyEntries().reduce((sum, entry) => sum + entry[1].length, 0),
  );
}

import { Component, computed, inject, input, isDevMode } from '@angular/core';
import { Tower } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import { TowerScene } from '../tower-scene/tower-scene.component';
import { VillageScene } from '../village-scene/village-scene.component';
import { TransitionService } from '../../services/transition.service';
import { TransitionVeil } from '../transition-veil/transition-veil.component';
import { UnitFocusCard } from '../unit-focus/unit-focus';
import { readFixtureParams } from '../../three/ink/ink-fixture';

@Component({
  selector: 'app-tower',
  imports: [TowerScene, VillageScene, TransitionVeil, UnitFocusCard],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.scss',
})
export class TowerDisplay {
  private readonly transition = inject(TransitionService);
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);
  /** Dev only: `?inkFixture=<floors>` shows the Tower scene without an Expedition (capture tooling). */
  private readonly fixtureOn =
    isDevMode() && readFixtureParams(location.search) !== null;
  /**
   * Dev only: `&transitionFixture=1` leaves the scene choice to the Set out transition, which
   * `window.__transition.begin(toTower, wiped)` then plays over the fixture Tower (capture tooling).
   */
  private readonly transitionFixture =
    this.fixtureOn && new URLSearchParams(location.search).has('transitionFixture');
  /** The Tower scene shows while the Expedition is out, and through the dissolve at either end of it. */
  sceneShown = computed(
    () =>
      (this.fixtureOn && !this.transitionFixture) || this.transition.towerShown(),
  );

  constructor() {
    if (this.transitionFixture) {
      (window as unknown as Record<string, unknown>)['__transition'] = {
        begin: (toTower: boolean, wiped = false) =>
          this.transition.begin(toTower, wiped),
      };
    }
  }
}

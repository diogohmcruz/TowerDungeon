import { Component, computed, input, isDevMode } from '@angular/core';
import { Tower } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import { TowerScene } from '../tower-scene/tower-scene.component';
import { VillageScene } from '../village-scene/village-scene.component';
import { readFixtureParams } from '../../three/ink/ink-fixture';

@Component({
  selector: 'app-tower',
  imports: [TowerScene, VillageScene],
  templateUrl: './tower-display.component.html',
  styleUrl: './tower-display.component.scss',
})
export class TowerDisplay {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);
  /** Dev only: `?inkFixture=<floors>` shows the Tower scene without an Expedition (capture tooling). */
  private readonly fixtureOn =
    isDevMode() && readFixtureParams(location.search) !== null;
  sceneShown = computed(() => this.fixtureOn || this.active());
}

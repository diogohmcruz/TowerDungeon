import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Reinforcement, Squad } from '../interfaces/game-state';
import { Tower } from '../interfaces/tower';
import { TowerDisplay } from '../components/tower/tower-display.component';
import { TowerScene } from '../components/tower-scene/tower-scene.component';
import { VillageScene } from '../components/village-scene/village-scene.component';

/**
 * A plain canvas in place of the WebGL scene. A spec that renders the whole game page and tears it
 * down at once would destroy an `ngt-canvas` that has not set up its root yet, and angular-three
 * then logs "Unexpected error while destroying Canvas Root" 500 ms later, in whatever spec runs.
 */
@Component({
  selector: 'app-tower-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<canvas></canvas>',
})
class StubTowerScene {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);
}

@Component({
  selector: 'app-village-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<canvas></canvas>',
})
class StubVillageScene {}

/** Call before the component is created: the Tower display then draws no WebGL canvas. */
export function stubScenes(): void {
  TestBed.overrideComponent(TowerDisplay, {
    remove: { imports: [TowerScene, VillageScene] },
    add: { imports: [StubTowerScene, StubVillageScene] },
  });
}

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgtCanvas } from 'angular-three/dom';
import type { NgtCameraParameters, NgtDpr } from 'angular-three';

import { Tower } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import { TowerSceneGraph } from './tower-scene-graph.component';

@Component({
  selector: 'app-tower-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgtCanvas, TowerSceneGraph],
  template: `
    <ngt-canvas [camera]="camera" [dpr]="dpr">
      <app-tower-scene-graph
        *canvasContent
        [tower]="tower()"
        [party]="party()"
        [active]="active()"
        [reinforcements]="reinforcements()"
      />
    </ngt-canvas>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
  `,
})
export class TowerScene {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);

  protected readonly camera: NgtCameraParameters = {
    position: [0, 4.8, 12.5],
    fov: 64,
    near: 0.1,
    far: 200,
  };
  protected readonly dpr: NgtDpr = [1, 2];
}

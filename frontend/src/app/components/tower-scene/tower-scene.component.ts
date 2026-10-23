import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  isDevMode,
} from '@angular/core';
import { NgtCanvas } from 'angular-three/dom';
import type { NgtCameraParameters, NgtDpr } from 'angular-three';

import { Tower } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import {
  buildFixture,
  InkFixture,
  readFixtureParams,
} from '../../three/ink/ink-fixture';
import { INK_DPR } from '../../three/ink/pixel-scale';
import { TowerSceneGraph } from './tower-scene-graph.component';

@Component({
  selector: 'app-tower-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgtCanvas, TowerSceneGraph],
  template: `
    <ngt-canvas
      [camera]="camera"
      [dpr]="dpr"
      [flat]="true"
      [gl]="{ antialias: false }"
    >
      <app-tower-scene-graph
        *canvasContent
        [tower]="shownTower()"
        [party]="shownParty()"
        [active]="fixture ? true : active()"
        [reinforcements]="fixture?.reinforcements ?? reinforcements()"
      />
    </ngt-canvas>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
    /* The canvas draws at 1 / PIXEL_SCALE of its CSS size; keep the pixels hard. */
    :host ::ng-deep canvas {
      image-rendering: pixelated;
    }
  `,
})
export class TowerScene {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);

  /** Spike #15: `?inkFixture=<floors>` swaps in a synthetic tower (dev mode only). */
  protected readonly fixture: InkFixture | null = (() => {
    const p = isDevMode() ? readFixtureParams(location.search) : null;
    return p ? buildFixture(p) : null;
  })();
  protected readonly shownTower = computed(
    () => this.fixture?.tower ?? this.tower(),
  );
  protected readonly shownParty = computed(
    () => this.fixture?.party ?? this.party(),
  );

  protected readonly camera: NgtCameraParameters = {
    position: [0, 4.8, 12.5],
    fov: 64,
    near: 0.1,
    far: 200,
  };
  protected readonly dpr: NgtDpr = INK_DPR;
}

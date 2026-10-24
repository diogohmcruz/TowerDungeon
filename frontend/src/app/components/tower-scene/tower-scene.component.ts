import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  isDevMode,
} from '@angular/core';
import { NgtCanvas } from 'angular-three/dom';
import type { NgtCameraParameters } from 'angular-three';

import { Tower } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import {
  buildFixture,
  CarriedFigures,
  InkFixture,
  readFixtureParams,
} from '../../three/ink/ink-fixture';
import { StageService } from '../../services/stage.service';
import { GameStateService } from '../../services/game-state.service';
import { LaunchService } from '../../services/launch.service';
import { VillageSelectionService } from '../../services/village-selection.service';
import { captionFor, captionText } from '../../three/village/village-captions';
import { towerRegion } from '../../three/stage-view';
import { SceneLines } from '../scene-lines/scene-lines';
import { TowerSceneGraph } from './tower-scene-graph.component';

@Component({
  selector: 'app-tower-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgtCanvas, TowerSceneGraph, SceneLines],
  template: `
    <ngt-canvas
      [camera]="camera"
      [dpr]="stage.dpr()"
      [flat]="true"
      [gl]="{ antialias: false }"
    >
      <app-tower-scene-graph
        *canvasContent
        [tower]="shownTower()"
        [party]="shownParty()"
        [active]="fixture ? true : active()"
        [reinforcements]="fixture?.reinforcements ?? reinforcements()"
        [launchOverride]="launchOverride"
        [carried]="carried()"
      />
    </ngt-canvas>
    <app-scene-lines
      [caption]="caption()"
      [locked]="locked()"
      [refusal]="refusal()"
      [left]="region().left"
      [width]="region().width"
    />
  `,
  styles: `
    :host {
      display: block;
      position: relative;
      width: 100%;
      height: 100%;
    }
    /* The canvas draws at 1 / pixel scale of its CSS size; keep the pixels hard. */
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

  /** What the Expedition carries, for the Supplies post and the crates (a fixture's, or the game's). */
  protected readonly carried = computed<CarriedFigures>(() => {
    if (this.fixture) return this.fixture.carried;
    const s = this.game();
    return {
      supplies: s.supplies ?? 0,
      maxSupplies: s.maxSupplies ?? 0,
      credits: s.carriedCredits ?? 0,
      materials: s.carriedLoot?.MATERIALS ?? 0,
      relics: s.carriedLoot?.RELICS ?? 0,
    };
  });

  protected readonly camera: NgtCameraParameters = {
    position: [0, 4.8, 12.5],
    fov: 64,
    near: 0.1,
    far: 200,
  };
  protected readonly stage = inject(StageService);

  /** Dev only: `?launch=lit` or `?launch=dark` draws the bell and the lamp so, for capture. */
  protected readonly launchOverride: { bell: boolean; recall: boolean } | null =
    (() => {
      const v = isDevMode()
        ? new URLSearchParams(location.search).get('launch')
        : null;
      return v === 'lit' || v === 'dark'
        ? { bell: v === 'lit', recall: v === 'lit' }
        : null;
    })();

  private readonly game = inject(GameStateService).state;
  private readonly selection = inject(VillageSelectionService);
  private readonly launch = inject(LaunchService);

  /** Where the words sit: inside the Tower's region, clear of the menus. */
  protected readonly region = computed(() =>
    towerRegion(this.stage.viewport().width, this.stage.narrow()),
  );
  private readonly captionOf = computed(() =>
    captionFor(this.selection.hovered(), this.game(), {
      startFloor: this.launch.startFloor(),
    }),
  );
  protected readonly caption = computed(() => {
    const c = this.captionOf();
    return c ? captionText(c) : null;
  });
  protected readonly locked = computed(() => !!this.captionOf()?.lock);
  protected readonly refusal = computed(
    () => this.launch.refusal()?.text ?? null,
  );
}

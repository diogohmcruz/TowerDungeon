import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgtCanvas } from 'angular-three/dom';
import type { NgtCameraParameters, NgtDpr } from 'angular-three';

import { GameStateService } from '../../services/game-state.service';
import { INK_DPR } from '../../three/ink/pixel-scale';
import { VillageInput } from '../../three/village/village-layout';
import { VILLAGE_FOV } from '../../three/village/village-camera';
import { VillageSceneGraph } from './village-scene-graph.component';

/** Villagers take one step per tick: four a second. */
const STEP_MS = 250;

/**
 * The Village Elevation at the foot of the Tower: a fixed vantage over the Fort,
 * the stair, the Fields, the silo, the Annexes and the villagers in their lanes.
 * Shown while no Expedition is under way.
 */
@Component({
  selector: 'app-village-scene',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgtCanvas, VillageSceneGraph],
  template: `
    <ngt-canvas
      [camera]="camera"
      [dpr]="dpr"
      [flat]="true"
      [gl]="{ antialias: false }"
      frameloop="demand"
    >
      <app-village-scene-graph *canvasContent [village]="shown()" />
    </ngt-canvas>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
    :host ::ng-deep canvas {
      image-rendering: pixelated;
    }
  `,
})
export class VillageScene {
  private readonly state = inject(GameStateService).state;
  private readonly tick = signal(0);
  /** Dev tooling: draws this state instead of the game's (see `/village-fixture`). */
  readonly fixture = input<VillageInput | null>(null);
  /**
   * Hook for the shared selection service (#78): bind the hovered Shortcut id
   * here so its mark thickens. Not wired yet because that service has not landed.
   */
  readonly highlightedShortcut = input<string | null>(null);
  private readonly still =
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches;

  protected readonly camera: NgtCameraParameters = {
    position: [0, 1, 30],
    fov: VILLAGE_FOV,
    near: 0.5,
    far: 200,
  };
  protected readonly dpr: NgtDpr = INK_DPR;

  /** The state the Village draws; the renderer wakes only when this changes. */
  protected readonly shown = computed<VillageInput>(() => {
    const fixed = this.fixture();
    if (fixed) {
      const t = this.tick();
      return {
        ...fixed,
        highlightedShortcut:
          this.highlightedShortcut() ?? fixed.highlightedShortcut,
        tick: t,
        flicker: (t * STEP_MS) / 1000,
      };
    }
    const s = this.state();
    const hands = {
      TILLER: 0,
      BREAKER: 0,
      SCRIBE: 0,
      SALTER: 0,
      SPOREWARDEN: 0,
    };
    const posts: VillageInput['posts'] = {};
    for (const view of s.tradeViews ?? []) {
      hands[view.trade] = view.hands;
      posts[view.trade] = view.posts;
    }
    const upgrades: Record<string, number> = {};
    for (const u of s.availableUpgrades ?? []) {
      upgrades[u.id] = u.level;
    }
    const tick = this.tick();
    return {
      food: s.village.food,
      reserves: s.homeUnitCount,
      hands,
      posts,
      quarryHeap: s.village.quarryHeap,
      quarryHeapCap: s.quarryHeapCap,
      materials: s.resources.MATERIALS,
      relics: s.resources.RELICS,
      villageTierLevel: s.villageTierLevel,
      upgrades,
      saltPansOpen: s.loadout?.saltPansOpen ?? false,
      salt: s.village.salt,
      shortcuts: (s.shortcuts ?? [])
        .filter((sc) => sc.unlocked)
        .map((sc) => ({ id: sc.id, floor: sc.floor })),
      highlightedShortcut: this.highlightedShortcut(),
      tick,
      flicker: (tick * STEP_MS) / 1000,
      still: this.still,
    };
  });

  constructor() {
    if (!this.still) {
      const timer = setInterval(() => this.tick.update((t) => t + 1), STEP_MS);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }
}

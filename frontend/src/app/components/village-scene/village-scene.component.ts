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
import type { NgtCameraParameters } from 'angular-three';

import { LaunchService } from '../../services/launch.service';
import { PopoverService } from '../../services/popover.service';
import { VillageSelectionService } from '../../services/village-selection.service';
import { captionFor, captionText } from '../../three/village/village-captions';
import { GameStateService } from '../../services/game-state.service';
import { StageService } from '../../services/stage.service';
import { woundedTotal } from '../../three/village/fort-building';
import { pillarInput } from '../../three/village/reference-pillar-building';
import { VillageInput } from '../../three/village/village-layout';
import { towerRegion } from '../../three/stage-view';
import { VILLAGE_FOV } from '../../three/village/village-camera';
import { SceneLines } from '../scene-lines/scene-lines';
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
  imports: [NgtCanvas, VillageSceneGraph, SceneLines],
  template: `
    <ngt-canvas
      [camera]="camera"
      [dpr]="stage.dpr()"
      [flat]="true"
      [gl]="{ antialias: false }"
      frameloop="demand"
    >
      <app-village-scene-graph *canvasContent [village]="shown()" />
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
    :host ::ng-deep canvas {
      image-rendering: pixelated;
    }
  `,
})
export class VillageScene {
  private readonly state = inject(GameStateService).registryState;
  private readonly selection = inject(VillageSelectionService);
  private readonly launch = inject(LaunchService);
  private readonly popover = inject(PopoverService);
  private readonly tick = signal(0);
  /** Dev tooling: draws this state instead of the game's (see `/village-fixture`). */
  readonly fixture = input<VillageInput | null>(null);
  private readonly still =
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches;

  protected readonly camera: NgtCameraParameters = {
    position: [0, 1, 30],
    fov: VILLAGE_FOV,
    near: 0.5,
    far: 200,
  };
  protected readonly stage = inject(StageService);
  /** Where the Village is framed: the caption sits inside it, clear of the menus. */
  protected readonly region = computed(() => {
    const { width } = this.stage.viewport();
    return towerRegion(width, this.stage.narrow());
  });

  /** The caption of what is hovered, or else what is selected. */
  private readonly subject = computed(
    () => this.selection.hovered() ?? this.selection.selected(),
  );
  private readonly captionOf = computed(() => {
    const subject = this.subject();
    // A building with its popover open says it there, so the caption steps aside.
    if (subject && this.popover.openId() === subject.id) return null;
    return captionFor(subject, this.state(), {
      startFloor: this.startFloor(),
      held: this.launch.held(),
    });
  });
  protected readonly caption = computed(() => {
    const c = this.captionOf();
    return c ? captionText(c) : null;
  });
  protected readonly locked = computed(() => !!this.captionOf()?.lock);
  /** The one line a refused launch leaves, beside the caption. */
  protected readonly refusal = computed(() => this.launch.refusal()?.text ?? null);
  /** The Start Floor chosen: the fixture's own, or the one the panel's radios show. */
  private readonly startFloor = computed(
    () => this.fixture()?.startFloor ?? this.launch.startFloor(),
  );

  /** The state the Village draws; the renderer wakes only when this changes. */
  protected readonly shown = computed<VillageInput>(() => {
    const fixed = this.fixture();
    if (fixed) {
      const t = this.tick();
      return {
        ...fixed,
        tick: t,
        flicker: (t * STEP_MS) / 1000,
        startFloor: this.startFloor(),
        hovered: this.selection.hovered()?.id ?? null,
        selected: this.selection.selected()?.id ?? null,
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
      wounded: woundedTotal(s),
      hands,
      posts,
      quarryHeap: s.village.quarryHeap,
      quarryHeapCap: s.quarryHeapCap,
      materials: s.resources.MATERIALS,
      relics: s.resources.RELICS,
      credits: s.credit,
      sporeCellarOpen: s.villageTierLevel >= 3,
      spores: s.village.spores,
      villageTierLevel: s.villageTierLevel,
      pillar: pillarInput(s),
      fallenCount: (s.wallOfNames ?? []).length,
      upgrades,
      ringLevel: s.ring?.level ?? 0,
      ringMax: s.ring?.maxLevel ?? 0,
      saltPansOpen: (s.loadout?.saltPansOpen ?? false) || s.villageTierLevel >= 2,
      salt: s.village.salt,
      shortcuts: (s.shortcuts ?? [])
        .filter((sc) => sc.unlocked)
        .map((sc) => ({ id: sc.id, floor: sc.floor })),
      port: s.port?.state ?? 'HIDDEN',
      boatDocked: s.boat?.state === 'DOCKED',
      quarter: s.quarter ?? null,
      seaRisk: s.seaRisk ?? null,
      tick,
      flicker: (tick * STEP_MS) / 1000,
      still: this.still,
      startFloor: this.startFloor(),
      bellUsable: this.launch.setOut().usable,
      offer: s.phase === 'OFFER',
      hovered: this.selection.hovered()?.id ?? null,
      selected: this.selection.selected()?.id ?? null,
    };
  });

  constructor() {
    if (!this.still) {
      const timer = setInterval(() => this.tick.update((t) => t + 1), STEP_MS);
      inject(DestroyRef).onDestroy(() => clearInterval(timer));
    }
  }
}

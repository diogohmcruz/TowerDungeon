import {
  ChangeDetectionStrategy,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  isDevMode,
  signal,
  untracked,
  viewChild,
  ElementRef,
} from '@angular/core';
import {
  injectBeforeRender,
  injectObjectEvents,
  injectStore,
  NgtArgs,
} from 'angular-three';
import * as THREE from 'three';

import { Tower, TowerFloor } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import {
  createFigureAtlas,
  FIGURE_COLS,
  FIGURE_H,
  FIGURE_ROWS,
  FIGURE_W,
  TEXEL_STEP,
} from '../../three/figures/figure-atlas';
import {
  FigureInstance,
  FigureMember,
  MARK_CAPACITY,
  planFigures,
  SPECK_CAPACITY,
  SURFACE_LIFT,
} from '../../three/figures/figure-layout';
import {
  FIGURE_FRAGMENT,
  FIGURE_VERTEX,
} from '../../three/figures/figure.glsl';
import {
  createInkUniforms,
  setInkTheme,
  TONE,
} from '../../three/ink/ink-material';
import { INK_FRAGMENT, INK_VERTEX } from '../../three/ink/ink.glsl';
import { InkTheme, RAMPS } from '../../three/ink/palette';
import {
  ATLAS_COLS,
  ATLAS_ROWS,
  CELL_H,
  CELL_W,
  createAtlasTexture,
  SEAL_CENTER,
} from '../../three/floor-strip/architecture-atlas';
import {
  createDigitTexture,
  DIGIT_COUNT,
  DIGIT_H,
  DIGIT_W,
} from '../../three/floor-strip/digit-atlas';
import {
  NUMERAL_FRAGMENT,
  NUMERAL_VERTEX,
  STRIP_FRAGMENT,
  STRIP_VERTEX,
} from '../../three/floor-strip/floor-strip.glsl';
import {
  FLOOR_SURFACE_OFFSET,
  floorToY,
  NUMERAL_CAPACITY,
  NUMERAL_SIZE,
  NUMERAL_Z,
  PILLAR_COUNT,
  PILLAR_SPACING,
  planFloorStrips,
  STRIP_CAPACITY,
  STRIP_H,
  STRIP_W,
  stripY,
} from '../../three/floor-strip/floor-strip-plan';
import '../../three/catalogue';
import { figureBoost } from '../../three/ink/pixel-scale';
import { applyStageView, towerRegion } from '../../three/stage-view';
import { StageService } from '../../services/stage.service';
import { TransitionService } from '../../services/transition.service';
import { ZoomService } from '../../services/zoom.service';
import {
  dampFactor,
  prefersReducedMotion,
  SHOTS,
  ShotBlend,
} from '../../three/camera-shots';
import { GameStateService } from '../../services/game-state.service';
import { LaunchService } from '../../services/launch.service';
import { VillageSelectionService } from '../../services/village-selection.service';
import {
  bellAvailability,
  extractAvailability,
} from '../../three/launch/launch-controls';
import { towerLaunchVolumes } from '../../three/launch/launch-objects';
import { subjectOfVolume } from '../../three/village/village-subjects';
import type { Volume } from '../../three/village/village-layout';
import type { Quarter } from '../../interfaces/game-state';
import { LandscapeGraph } from '../landscape/landscape-graph.component';

/** Instances of the box mesh: the foundation and the backdrop wall. */
const BOX_CAPACITY = 4;
/** Boxes the launch objects and their outlines can use (about 80 are drawn). */
const LAUNCH_CAPACITY = 128;
/** Fog starts to eat the ink beyond the focal distance of the battle shot. */
const FOG_NEAR = 13;

/** World y of the ground the foundation stands on (its underside), where the landscape's plain meets it. */
const GROUND_TOP = floorToY(0) + 0.13 - 0.85;
/** Fog of the landscape in this scene (the camera is closer than the Village's). */
const LANDSCAPE_FOG = 0.012;
/** The plain reaches past the camera (z 12.5), so no void shows under it. */
const LANDSCAPE_NEAR = 20;

/** Specks and figures are tinted by ink density only; this is the tone of a figure over the cap. */
const SPECK_TONE = 0.85;

/**
 * A press that barely moves and soon lifts, and did not land on a launch control
 * (the bell or the lamp act on their own), is a click on empty space.
 */
export function isEmptySpaceClick(
  press: { x: number; y: number; at: number } | null,
  click: { clientX: number; clientY: number; timeStamp: number },
  onLaunchControl: boolean,
): boolean {
  return (
    !!press &&
    !onLaunchControl &&
    Math.hypot(click.clientX - press.x, click.clientY - press.y) <=
      CLICK_SLOP_PX &&
    click.timeStamp - press.at <= CLICK_MAX_MS
  );
}

/** A press that moves farther than this, or lasts longer, is a drag or a hold, not a click. */
const CLICK_SLOP_PX = 5;
const CLICK_MAX_MS = 500;

/** World Y of the walkable surface of floor `n` (figures stand here, before the lift). */
function floorSurfaceY(n: number): number {
  return floorToY(n) + FLOOR_SURFACE_OFFSET;
}

@Component({
  selector: 'app-tower-scene-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [NgtArgs, LandscapeGraph],
  template: `
    <!-- Drawn, not lit (ADR 0003): clear colour = ramp slot 0, no lights, no scene fog. -->
    <ngt-color *args="[clearHex()]" attach="background" />

    <!-- The ambient landscape: the plain, ridges and ruins the Tower stands in, one draw call. -->
    <app-landscape-graph
      [quarter]="quarter()"
      [theme]="theme()"
      [groundY]="GROUND_TOP"
      [fogDensity]="LANDSCAPE_FOG"
      [nearZ]="LANDSCAPE_NEAR"
    />

    <!-- Floor strips: three layers per floor and the pencil above the Highest Floor, one draw call. -->
    <ngt-instanced-mesh
      #strips
      *args="[undefined, undefined, STRIP_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[STRIP_W, STRIP_H]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aCell"
          *args="[stripCell, 4]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aState"
          *args="[stripState, 4]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="stripVertex"
        [fragmentShader]="stripFragment"
        [uniforms]="stripUniforms"
      />
    </ngt-instanced-mesh>

    <!-- Floor numerals stencilled on the reference pillars: one draw call. -->
    <ngt-instanced-mesh
      #numerals
      *args="[undefined, undefined, NUMERAL_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aDigit"
          *args="[numeralDigit, 2]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="numeralVertex"
        [fragmentShader]="numeralFragment"
        [uniforms]="numeralUniforms"
      />
    </ngt-instanced-mesh>

    <!-- The twelve: tall dark columns behind the stack, one draw call. -->
    <ngt-instanced-mesh
      #pillars
      *args="[undefined, undefined, PILLAR_COUNT]"
      [frustumCulled]="false"
    >
      <ngt-box-geometry *args="[1, 1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aTone"
          *args="[pillarTone, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aAlpha"
          *args="[pillarAlpha, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aRole"
          *args="[pillarRole, 1]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="inkVertex"
        [fragmentShader]="inkFragment"
        [uniforms]="inkUniforms"
      />
    </ngt-instanced-mesh>

    <!-- The foundation and the backdrop wall behind the fight: one draw call. -->
    <ngt-instanced-mesh
      #bodies
      *args="[undefined, undefined, BOX_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-box-geometry *args="[1, 1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aTone"
          *args="[bodyTone, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aAlpha"
          *args="[bodyAlpha, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aRole"
          *args="[bodyRole, 1]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="inkVertex"
        [fragmentShader]="inkFragment"
        [uniforms]="inkUniforms"
      />
    </ngt-instanced-mesh>

    <!-- The bell and the recall lamp beside the Party's Floor: launching from the picture. -->
    <ngt-instanced-mesh
      #launchMesh
      *args="[undefined, undefined, LAUNCH_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-box-geometry *args="[1, 1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aTone"
          *args="[launchTone, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aAlpha"
          *args="[launchAlpha, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aRole"
          *args="[launchRole, 1]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="inkVertex"
        [fragmentShader]="inkFragment"
        [uniforms]="inkUniforms"
      />
    </ngt-instanced-mesh>

    @if (pickProbe) {
      <!-- Spike #15: pointer events on ngt-primitive (see docs/research/ink-spike.md). -->
      <ngt-primitive #probe *args="[pickProbe]" />
    }

    <!-- Figures, Wave columns and their numerals: light silhouettes from one atlas, one draw call. -->
    <ngt-instanced-mesh
      #marks
      *args="[undefined, undefined, MARK_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aCell"
          *args="[markCell, 4]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="figureVertex"
        [fragmentShader]="figureFragment"
        [uniforms]="figureUniforms"
      />
    </ngt-instanced-mesh>

    <!-- Specks: the figures over the cap, one more draw call. -->
    <ngt-instanced-mesh
      #specks
      *args="[undefined, undefined, SPECK_CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aCell"
          *args="[speckCell, 4]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="figureVertex"
        [fragmentShader]="figureFragment"
        [uniforms]="figureUniforms"
      />
    </ngt-instanced-mesh>
  `,
})
export class TowerSceneGraph {
  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);
  /** Dev tooling: draws the bell and the lamp lit or dark whatever the game says. */
  launchOverride = input<{ bell: boolean; recall: boolean } | null>(null);

  private readonly dark = signal(this.readTheme());
  private readonly gameState = inject(GameStateService).state;

  /** The Quarter's weather for the landscape (`?quarter=` picks one in dev fixtures). */
  protected readonly quarter = computed<Quarter | null>(() => {
    const forced = isDevMode()
      ? new URLSearchParams(location.search).get('quarter')?.toUpperCase()
      : undefined;
    if (
      forced === 'SPRING' ||
      forced === 'SUMMER' ||
      forced === 'AUTUMN' ||
      forced === 'WINTER'
    ) {
      return forced;
    }
    return this.gameState().quarter ?? null;
  });
  protected readonly GROUND_TOP = GROUND_TOP;
  protected readonly LANDSCAPE_FOG = LANDSCAPE_FOG;
  protected readonly LANDSCAPE_NEAR = LANDSCAPE_NEAR;

  /** Canvas width/height ratio; drives how much the formations shrink to fit. */
  private readonly viewAspect = signal(0.6);

  protected readonly theme = computed<InkTheme>(() =>
    this.dark() ? 'night' : 'day',
  );
  /** Ramp slot 0: the clear colour, which is also what fog resolves into. */
  protected readonly clearHex = computed(() => RAMPS[this.theme()][0]);

  protected readonly BOX_CAPACITY = BOX_CAPACITY;
  protected readonly STRIP_CAPACITY = STRIP_CAPACITY;
  protected readonly NUMERAL_CAPACITY = NUMERAL_CAPACITY;
  protected readonly PILLAR_COUNT = PILLAR_COUNT;
  protected readonly MARK_CAPACITY = MARK_CAPACITY;
  protected readonly SPECK_CAPACITY = SPECK_CAPACITY;
  protected readonly STRIP_W = STRIP_W;
  protected readonly STRIP_H = STRIP_H;
  protected readonly inkVertex = INK_VERTEX;
  protected readonly inkFragment = INK_FRAGMENT;
  protected readonly stripVertex = STRIP_VERTEX;
  protected readonly stripFragment = STRIP_FRAGMENT;
  protected readonly numeralVertex = NUMERAL_VERTEX;
  protected readonly numeralFragment = NUMERAL_FRAGMENT;
  protected readonly figureVertex = FIGURE_VERTEX;
  protected readonly figureFragment = FIGURE_FRAGMENT;
  protected readonly inkUniforms = createInkUniforms(this.theme());

  // Textures owned here, disposed on destroy.
  private readonly atlas = createAtlasTexture();
  private readonly digits = createDigitTexture();
  private readonly figures = createFigureAtlas();

  // The strip and numeral materials share the ink ramp, fog and dither uniforms.
  protected readonly stripUniforms = {
    ...this.inkUniforms,
    uAtlas: { value: this.atlas },
    uGrid: { value: new THREE.Vector2(ATLAS_COLS, ATLAS_ROWS) },
    uCellPx: { value: new THREE.Vector2(CELL_W, CELL_H) },
    uSeal: { value: new THREE.Vector2(...SEAL_CENTER) },
    uFogNear: { value: FOG_NEAR },
  };
  protected readonly figureUniforms = {
    ...this.inkUniforms,
    uFigures: { value: this.figures.texture },
    uGrid: { value: new THREE.Vector2(FIGURE_COLS, FIGURE_ROWS) },
    uCellPx: { value: new THREE.Vector2(FIGURE_W, FIGURE_H) },
    uTexelStep: { value: TEXEL_STEP },
    uFogNear: { value: FOG_NEAR },
  };
  protected readonly numeralUniforms = {
    ...this.inkUniforms,
    uDigits: { value: this.digits },
    uGlyphPx: { value: new THREE.Vector2(DIGIT_W, DIGIT_H) },
    uDigitCount: { value: DIGIT_COUNT },
    uFogNear: { value: FOG_NEAR },
  };

  // Per-instance attributes: strips (cell, state), numerals (digit, struck),
  // and the ink boxes (tone, dithered alpha, crimson role flag).
  protected readonly stripCell = new Float32Array(STRIP_CAPACITY * 4);
  protected readonly stripState = new Float32Array(STRIP_CAPACITY * 4);
  protected readonly numeralDigit = new Float32Array(NUMERAL_CAPACITY * 2);
  protected readonly markCell = new Float32Array(MARK_CAPACITY * 4);
  protected readonly speckCell = new Float32Array(SPECK_CAPACITY * 4);
  protected readonly bodyTone = new Float32Array(BOX_CAPACITY);
  protected readonly bodyAlpha = new Float32Array(BOX_CAPACITY);
  protected readonly bodyRole = new Float32Array(BOX_CAPACITY);
  protected readonly LAUNCH_CAPACITY = LAUNCH_CAPACITY;
  protected readonly launchTone = new Float32Array(LAUNCH_CAPACITY);
  protected readonly launchAlpha = new Float32Array(LAUNCH_CAPACITY);
  protected readonly launchRole = new Float32Array(LAUNCH_CAPACITY);
  protected readonly pillarTone = new Float32Array(PILLAR_COUNT);
  protected readonly pillarAlpha = new Float32Array(PILLAR_COUNT);
  protected readonly pillarRole = new Float32Array(PILLAR_COUNT);

  private readonly stripsRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('strips');
  private readonly numeralsRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('numerals');
  private readonly pillarsRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('pillars');
  private readonly launchRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('launchMesh');
  private readonly bodiesRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('bodies');
  private readonly marksRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('marks');
  private readonly specksRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('specks');

  /**
   * The strip plan is rebuilt only when the tower's `layoutVersion` changes
   * (floor added or a Guardian first slain), not on the 100 ms state pushes or
   * when a floor is repopulated with new Enemies.
   */
  private readonly layoutKey = computed(() => {
    const t = this.tower();
    return t ? `v${t.layoutVersion}` : 'none';
  });
  protected readonly plan = computed(() => {
    this.layoutKey();
    return untracked(() => planFloorStrips(this.tower()));
  });

  private readonly floors = computed<[number, TowerFloor][]>(() => {
    const map = this.tower()?.floors ?? {};
    return Object.entries(map)
      .map(([k, v]) => [Number(k), v] as [number, TowerFloor])
      .sort((a, b) => a[0] - b[0]);
  });

  private readonly currentFloor = computed(
    () => this.tower()?.currentFloor ?? 1,
  );

  /**
   * The floor the battle is *drawn* on. The party stands on top of a slab, so
   * anchoring to `currentFloor` made them read as one floor too high relative
   * to the floor labels; drawing one floor lower lines them up with the floor
   * they're actually on. The slab above (the real currentFloor) becomes the
   * faded ceiling over the fight.
   *
   * On floor 1 the party stands on the tower's base foundation (floor 0), so
   * the battle lines up exactly the same way as on every floor above it.
   */
  private readonly anchorFloor = computed(() =>
    Math.max(0, this.currentFloor() - 1),
  );

  /**
   * A dim panel directly behind the battle, filling the room of the current
   * Floor so the figures read against it. Shown only once the tower's floor
   * data has arrived, so units never render without a floor under them.
   */
  protected readonly backdrop = computed(() => ({
    show: this.active() && this.floors().length > 0,
    y: stripY(this.currentFloor()),
    height: STRIP_H,
    boss: this.tower()?.floors?.[this.currentFloor()]?.boss === true,
  }));

  /** The fight and the Waves as plain data (see `planFigures`). */
  protected readonly figurePlan = computed(() => {
    // Hold off until the tower's floor data has loaded, so the party never
    // renders for a frame without the floor deck it should be standing on.
    if (!this.active() || this.floors().length === 0) {
      return planFigures({
        party: [],
        enemies: [],
        waves: [],
        surfaceY: 0,
        floorSurfaceY: floorSurfaceY,
        viewAspect: this.viewAspect(),
        anchorFloor: 0,
      });
    }
    const anchor = this.anchorFloor();
    const floor = this.tower()?.floors?.[this.currentFloor()];
    return planFigures({
      party: this.flattenSquad(this.party()).map((m) =>
        this.figureMember(m, 'unit'),
      ),
      enemies: (floor?.enemies ?? []).map((m) => this.figureMember(m, 'enemy')),
      waves: this.reinforcements().map((w) => ({
        size: this.flattenSquad(w.units).length,
        floor: w.currentFloor ?? 1,
      })),
      surfaceY: floorSurfaceY(anchor) + SURFACE_LIFT,
      floorSurfaceY,
      viewAspect: this.viewAspect(),
      figureBoost: figureBoost(this.stage.scale()),
      anchorFloor: anchor,
    });
  });

  private figureMember(member: Member, kind: 'unit' | 'enemy'): FigureMember {
    return {
      id: member.id,
      typeKey: member.stats?.type ?? member.name ?? kind,
      guardian: kind === 'enemy' && member.boss === true,
    };
  }

  private flattenSquad(squad: Squad | undefined): Member[] {
    if (!squad) {
      return [];
    }
    return Object.values(squad).flat().filter(Boolean) as Member[];
  }

  private readonly camTarget = new THREE.Vector3(0, floorToY(0) + 1.3, 0);
  private readonly camPos = new THREE.Vector3(0, floorToY(0) + 7, 12.5);
  private readonly lookAtSmooth = this.camTarget.clone();

  /** Vertical world offset from scroll-to-pan; decays back to 0 when idle. */
  private panY = 0;
  private lastPanMs = 0;
  /** Timestamp of the pointer press that landed on a launch control; such a click never toggles the closeup. */
  private launchPressAt = -1;
  private pointerDown = false;
  private lastPointerY = 0;

  private readonly store = injectStore();
  private readonly stage = inject(StageService);
  private readonly zoom = inject(ZoomService);
  private readonly transition = inject(TransitionService);
  private readonly shot = new ShotBlend();
  private readonly reducedMotion = prefersReducedMotion();
  private readonly game = inject(GameStateService).state;
  private readonly launch = inject(LaunchService);
  private readonly selection = inject(VillageSelectionService);

  /** What the bell and the lamp can do now: the game's rule, unless a fixture overrides it. */
  private readonly usable = computed(() => {
    const o = this.launchOverride();
    if (o) return { bell: o.bell, recall: o.recall };
    const state = this.game();
    return {
      bell: bellAvailability(state).usable,
      recall: extractAvailability(state).usable,
    };
  });

  /** The launch objects as boxes, beside the Floor the Party fights on. */
  private readonly launchVolumes = computed<Volume[]>(() => {
    if (!this.active()) return [];
    const u = this.usable();
    return towerLaunchVolumes({
      surfaceY: floorSurfaceY(this.anchorFloor()) + SURFACE_LIFT,
      bellUsable: u.bell,
      recallUsable: u.recall,
      hovered: this.selection.hovered()?.id ?? null,
    });
  });

  constructor() {
    const host = document.documentElement;
    const observer = new MutationObserver(() =>
      this.dark.set(this.readTheme()),
    );
    observer.observe(host, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
    inject(DestroyRef).onDestroy(() => observer.disconnect());

    this.bindPanControls();
    inject(DestroyRef).onDestroy(() => {
      this.atlas.dispose();
      this.digits.dispose();
      this.figures.dispose();
    });
    this.figures.loadArt();

    // The template type of `ngt-primitive` only allows lifecycle outputs, so
    // pointer events on a primitive go through injectObjectEvents.
    const count = (key: string) => () => {
      const w = window as unknown as Record<string, number>;
      w[key] = (w[key] ?? 0) + 1;
    };
    injectObjectEvents(() => this.probeRef()?.nativeElement, {
      click: count('__towerPicks'),
      pointerover: count('__towerHovers'),
    });

    effect(() => setInkTheme(this.inkUniforms, this.theme()));
    effect(() => this.writeInstances());
    effect(() => this.writeLaunch());

    // The bell and the lamp act (architecture doc 5.5) through the LaunchService,
    // the same route as the panel's buttons.
    injectObjectEvents(() => this.launchRef()?.nativeElement, {
      pointermove: (event) => {
        event.stopPropagation();
        const subject = this.launchSubjectAt(event.instanceId);
        this.selection.hover(subject);
        this.store.snapshot.gl.domElement.style.cursor = subject ? 'pointer' : '';
      },
      pointerout: () => {
        this.selection.hover(null);
        this.store.snapshot.gl.domElement.style.cursor = '';
      },
      // A press on a launch control is not a click on empty space: remember which
      // press it was, so the closeup toggle on the canvas lets it pass.
      pointerdown: (event) => {
        event.stopPropagation();
        if (this.launchSubjectAt(event.instanceId)) {
          this.launchPressAt = (event.nativeEvent as Event).timeStamp;
        }
      },
      click: (event) => {
        event.stopPropagation();
        const subject = this.launchSubjectAt(event.instanceId);
        if (subject) this.launch.activate(subject);
      },
    });

    if (isDevMode()) {
      // Spike #15 measurement hook: renderer.info of the last frame.
      (window as unknown as Record<string, unknown>)['__tower'] = {
        info: () => {
          const gl = this.store.gl();
          const i = gl.info;
          return {
            calls: i.render.calls,
            triangles: i.render.triangles,
            programs: i.programs?.length ?? 0,
            geometries: i.memory.geometries,
            textures: i.memory.textures,
            // What the scene holds before frustum culling (draw-call upper bound).
            ...this.countScene(),
            dpr: gl.getPixelRatio(),
            size: [gl.domElement.width, gl.domElement.height],
          };
        },
      };
    }

    injectBeforeRender(({ camera, delta }) => {
      const focusY = floorToY(this.anchorFloor());
      // Reduced motion cuts instead of easing; the damped approach never overshoots.
      const damp = this.reducedMotion ? 1 : dampFactor(delta);
      this.shot.step(SHOTS[this.zoom.level()], damp);
      const pose = this.shot.current;
      const zoomed = this.zoom.level() !== 'battle';

      // Keep the panned view within the discovered tower (floor 1 → top floor).
      const floorsArr = this.floors();
      const topFloor = floorsArr.length
        ? floorsArr[floorsArr.length - 1][0]
        : 1;
      const minView = floorToY(0) - 0.5;
      const maxView = floorToY(topFloor) + 0.5;
      this.panY = THREE.MathUtils.clamp(
        this.panY,
        minView - focusY,
        maxView - focusY,
      );

      // Recenter on the current floor once the player stops interacting.
      if (zoomed) {
        // The closeup follows the Party and nothing else.
        this.panY = 0;
      } else if (
        performance.now() - this.lastPanMs > 2500 &&
        !this.pointerDown
      ) {
        this.panY = THREE.MathUtils.lerp(this.panY, 0, damp * 0.6);
      }

      // The Set out rise and the drop back: the camera is lower while the Set out transition plays.
      // It is driven by its own eased curve (no overshoot), so the damped approach is bypassed.
      const dropY = this.transition.drop;
      const viewY = focusY + this.panY - dropY;
      this.camTarget.set(0, viewY + pose.lookUp, 0);
      this.camPos.set(0, viewY + pose.camUp, pose.distance);

      // Both the position and the look-at target ease, so the view never snaps.
      const follow = dropY > 0 || this.transition.running ? 1 : damp;
      camera.position.lerp(this.camPos, follow);
      this.lookAtSmooth.lerp(this.camTarget, follow);
      camera.lookAt(this.lookAtSmooth);
      const cam = camera as THREE.PerspectiveCamera;

      // The canvas fills the screen; the Tower is framed where the old panel was.
      const el = this.store.snapshot.gl.domElement;
      applyStageView(
        camera as THREE.PerspectiveCamera,
        el.clientWidth,
        el.clientHeight,
        this.stage.narrow(),
        pose.spread,
      );
      if (pose.fov !== cam.fov) {
        cam.fov = pose.fov;
        cam.updateProjectionMatrix();
      }
    });
  }

  /**
   * Spike #15: a probe mesh for the "pointer events on ngt-primitive" claim,
   * built only with `?inkPick=1`. It covers the current floor's front face.
   */
  protected readonly pickProbe: THREE.Mesh | null =
    isDevMode() && new URLSearchParams(location.search).has('inkPick')
      ? new THREE.Mesh(
          new THREE.PlaneGeometry(4, 2),
          new THREE.MeshBasicMaterial({ colorWrite: false, depthWrite: false }),
        )
      : null;

  private readonly probeRef = viewChild<ElementRef<THREE.Mesh>>('probe');

  /** Rewrites the strip, numeral, pillar and box meshes from the plan and the current floor. */
  private writeInstances(): void {
    const strips = this.stripsRef()?.nativeElement;
    const numerals = this.numeralsRef()?.nativeElement;
    const pillars = this.pillarsRef()?.nativeElement;
    const bodies = this.bodiesRef()?.nativeElement;
    const plan = this.plan();
    const current = this.currentFloor();
    const backdrop = this.backdrop();
    const hasFloors = plan.top > 0;
    this.pickProbe?.position.set(0, floorToY(this.anchorFloor()) + 1.9, 1.7);
    if (
      !strips?.isInstancedMesh ||
      !numerals?.isInstancedMesh ||
      !pillars?.isInstancedMesh ||
      !bodies?.isInstancedMesh
    ) {
      return;
    }

    const m = new THREE.Matrix4();
    const p = new THREE.Vector3();
    const sc = new THREE.Vector3(1, 1, 1);

    this.writeFigures();

    plan.strips.forEach((s, i) => {
      strips.setMatrixAt(
        i,
        m.compose(p.set(0, s.y, s.z), IDENTITY_QUATERNION, sc),
      );
      this.stripCell.set([s.col, s.row, s.layer, s.alpha], i * 4);
      this.stripState.set(
        [s.floor === current ? 1 : 0, s.seal, s.tone, 0],
        i * 4,
      );
    });
    strips.count = plan.strips.length;
    strips.instanceMatrix.needsUpdate = true;
    markAttributes(strips, ['aCell', 'aState']);

    const size = new THREE.Vector3(NUMERAL_SIZE[0], NUMERAL_SIZE[1], 1);
    plan.numerals.forEach((n, i) => {
      numerals.setMatrixAt(
        i,
        m.compose(p.set(n.x, n.y, NUMERAL_Z), IDENTITY_QUATERNION, size),
      );
      this.numeralDigit.set([n.digit, n.struck ? 1 : 0], i * 2);
    });
    numerals.count = plan.numerals.length;
    numerals.instanceMatrix.needsUpdate = true;
    markAttributes(numerals, ['aDigit']);

    // The twelve: two fogged rings behind the stack, tall enough for the stack
    // and the pencil above it.
    const bottom = floorToY(0) - 1;
    const topY = stripY(plan.top + 10);
    const pillar = new InstanceWriter(pillars);
    if (hasFloors) {
      for (let i = 0; i < PILLAR_COUNT; i++) {
        pillar.add(
          [
            (i - (PILLAR_COUNT - 1) / 2) * PILLAR_SPACING,
            (bottom + topY) / 2,
            i % 2 ? -3 : -6,
          ],
          [0.55, topY - bottom, 0.55],
          i % 2 ? 1 : 0.8,
          1,
          false,
        );
      }
    }
    pillar.finish();

    const body = new InstanceWriter(bodies);
    if (hasFloors) {
      // The foundation the party stands on while fighting floor 1.
      body.add(
        [0, floorToY(0) + 0.13, 0],
        [4.4, 1.7, 2.8],
        TONE.base,
        1,
        false,
      );
    }
    if (backdrop.show) {
      body.add(
        [0, backdrop.y, -1.25],
        [4.2, backdrop.height, 0.12],
        TONE.backdrop,
        0.55,
        backdrop.boss,
      );
    }
    body.finish();
  }

  private launchSubjectAt(instanceId: number | undefined) {
    const volume =
      instanceId === undefined ? undefined : this.launchVolumes()[instanceId];
    return volume ? subjectOfVolume(volume.id) : null;
  }

  /** Rewrites the launch mesh from its boxes. */
  private writeLaunch(): void {
    const mesh = this.launchRef()?.nativeElement;
    if (!mesh?.isInstancedMesh) return;
    const writer = new InstanceWriter(mesh);
    for (const v of this.launchVolumes()) {
      writer.add([v.x, v.y, v.z], [v.w, v.h, v.d], v.tone, v.alpha, false);
    }
    writer.finish();
  }

  /** Rewrites the figure and speck meshes from the figure plan. */
  private writeFigures(): void {
    const plan = this.figurePlan();
    const write = (
      mesh: THREE.InstancedMesh | undefined,
      list: FigureInstance[],
      cells: Float32Array,
      tone: number,
    ) => {
      if (!mesh?.isInstancedMesh) {
        return;
      }
      const m = new THREE.Matrix4();
      const p = new THREE.Vector3();
      const sc = new THREE.Vector3();
      const n = Math.min(list.length, cells.length / 4);
      for (let i = 0; i < n; i++) {
        const f = list[i];
        mesh.setMatrixAt(
          i,
          m.compose(
            p.set(f.x, f.y, f.z),
            IDENTITY_QUATERNION,
            sc.set(f.w, f.h, 1),
          ),
        );
        const col = f.cell % FIGURE_COLS;
        const row = Math.floor(f.cell / FIGURE_COLS);
        cells.set([col, row, f.role, tone], i * 4);
      }
      mesh.count = n;
      mesh.instanceMatrix.needsUpdate = true;
      markAttributes(mesh, ['aCell']);
    };
    write(this.marksRef()?.nativeElement, plan.marks, this.markCell, 1);
    write(
      this.specksRef()?.nativeElement,
      plan.specks,
      this.speckCell,
      SPECK_TONE,
    );
  }

  /** Spike #15: renderable objects and triangles in the scene, ignoring culling. */
  private countScene(): { objects: number; sceneTriangles: number } {
    let objects = 0;
    let sceneTriangles = 0;
    this.store.scene().traverse((o) => {
      const m = o as THREE.Mesh;
      if (!(m.isMesh || (o as THREE.Sprite).isSprite) || !o.visible) return;
      objects++;
      const g = m.geometry;
      const tris = (g.index?.count ?? g.attributes['position'].count) / 3;
      sceneTriangles += tris * ((o as THREE.InstancedMesh).count ?? 1);
    });
    return { objects, sceneTriangles };
  }

  /** Mouse-wheel and drag on the canvas pan the camera vertically. */
  private bindPanControls(): void {
    const nudge = (deltaWorldY: number) => {
      if (this.zoom.level() !== 'battle') return;
      this.panY += deltaWorldY;
      this.lastPanMs = performance.now();
    };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      nudge(-e.deltaY * 0.01);
    };
    // A press that barely moves and soon lifts is a click on empty space: the closeup toggle.
    let press: { x: number; y: number; at: number } | null = null;
    const onPointerDown = (e: PointerEvent) => {
      this.pointerDown = true;
      this.lastPointerY = e.clientY;
      press = e.button === 0 ? { x: e.clientX, y: e.clientY, at: e.timeStamp } : null;
    };
    const onClick = (e: MouseEvent) => {
      const p = press;
      press = null;
      const onLaunch = !!p && p.at === this.launchPressAt;
      this.launchPressAt = -1;
      if (isEmptySpaceClick(p, e, onLaunch)) {
        this.zoom.toggleCloseup();
      }
    };
    const onPointerMove = (e: PointerEvent) => {
      if (!this.pointerDown) return;
      nudge((e.clientY - this.lastPointerY) * 0.02);
      this.lastPointerY = e.clientY;
    };
    const onPointerUp = () => {
      this.pointerDown = false;
      this.lastPanMs = performance.now();
    };

    let bound: HTMLElement | undefined;
    let resizeObs: ResizeObserver | undefined;
    const updateAspect = (el: HTMLElement) => {
      const w = el.clientWidth || 1;
      const h = el.clientHeight || 1;
      // Formations fit the Tower's region, not the whole screen.
      this.viewAspect.set(towerRegion(w, this.stage.narrow()).width / h);
    };
    const stop = effect(() => {
      const el = this.store.gl()?.domElement as HTMLElement | undefined;
      if (!el || el === bound) return;
      bound = el;
      el.addEventListener('wheel', onWheel, { passive: false });
      el.addEventListener('pointerdown', onPointerDown);
      el.addEventListener('click', onClick);
      window.addEventListener('pointermove', onPointerMove);
      window.addEventListener('pointerup', onPointerUp);
      updateAspect(el);
      resizeObs = new ResizeObserver(() => updateAspect(el));
      resizeObs.observe(el);
    });

    inject(DestroyRef).onDestroy(() => {
      stop.destroy();
      resizeObs?.disconnect();
      bound?.removeEventListener('wheel', onWheel);
      bound?.removeEventListener('pointerdown', onPointerDown);
      bound?.removeEventListener('click', onClick);
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
    });
  }

  private readTheme(): boolean {
    return document.documentElement.dataset['theme'] === 'dark';
  }
}

/** Fills an instanced ink mesh (matrices plus the three ink attributes) in order. */
class InstanceWriter {
  private n = 0;
  private readonly matrix = new THREE.Matrix4();
  private readonly position = new THREE.Vector3();
  private readonly scale = new THREE.Vector3();
  private readonly tone: THREE.BufferAttribute;
  private readonly alpha: THREE.BufferAttribute;
  private readonly role: THREE.BufferAttribute;

  private readonly capacity: number;

  constructor(private readonly mesh: THREE.InstancedMesh) {
    this.capacity = mesh.instanceMatrix.count;
    const a = mesh.geometry.attributes;
    this.tone = a['aTone'] as THREE.BufferAttribute;
    this.alpha = a['aAlpha'] as THREE.BufferAttribute;
    this.role = a['aRole'] as THREE.BufferAttribute;
  }

  add(
    position: [number, number, number],
    scale: [number, number, number],
    tone: number,
    alpha: number,
    crimson: boolean,
  ): void {
    if (this.n >= this.capacity) {
      return;
    }
    this.matrix.compose(
      this.position.set(...position),
      IDENTITY_QUATERNION,
      this.scale.set(...scale),
    );
    this.mesh.setMatrixAt(this.n, this.matrix);
    this.tone.setX(this.n, tone);
    this.alpha.setX(this.n, alpha);
    this.role.setX(this.n, crimson ? 1 : 0);
    this.n++;
  }

  finish(): void {
    this.mesh.count = this.n;
    this.mesh.instanceMatrix.needsUpdate = true;
    this.tone.needsUpdate = true;
    this.alpha.needsUpdate = true;
    this.role.needsUpdate = true;
  }
}

const IDENTITY_QUATERNION = new THREE.Quaternion();

/** Flags per-instance attributes for upload after their backing arrays were rewritten. */
function markAttributes(mesh: THREE.InstancedMesh, names: string[]): void {
  for (const name of names) {
    const a = mesh.geometry.attributes[name];
    if (a) a.needsUpdate = true;
  }
}

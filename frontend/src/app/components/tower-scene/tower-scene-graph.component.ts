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
  FIGURE_Z,
  planFigures,
  SPECK_CAPACITY,
} from '../../three/figures/figure-layout';
import {
  DECK_LINE_RISE,
  DeckView,
  deckViewOf,
  sameDeckView,
  standCentreY,
  standFeetY,
} from '../../three/figures/figure-anchor';
import { ROLE_PARTY, ROLE_RAMP } from '../../three/figures/figure-tone';
import {
  FIGURE_FRAGMENT,
  FIGURE_VERTEX,
  HOVER_OUTLINE_FRAGMENT,
  OUTLINE_FRAGMENT,
  OUTLINE_PAD,
} from '../../three/figures/figure.glsl';
import {
  figureId,
  figureRect,
  parseFigureId,
  pickSpriteSteady,
  UnitFocus,
} from '../../three/figures/figure-pick';
import { hoverCaption } from '../../three/figures/unit-hover';
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
  MARK_DIGIT,
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
  planFinishMarks,
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
import { UnitHoverService } from '../../services/unit-hover.service';
import {
  dampFactor,
  fitDistance,
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
import {
  TOWER_LAUNCH_Z,
  TOWER_RECALL_X,
  towerLaunchVolumes,
} from '../../three/launch/launch-objects';
import { InstanceWriter } from '../../three/ink/instance-writer';
import { LAUNCH_CAPACITY, rimVolumes } from '../../three/launch/object-rim';
import { subjectOfVolume } from '../../three/village/village-subjects';
import { resolveHover, type HoverRegion } from '../../three/hover-resolver';
import { volumeRegions } from '../../three/village/volume-regions';
import type { VillageSubject } from '../../three/village/village-subjects';
import {
  CRATE_CEILING_RISE,
  CRATE_Z,
  expeditionVolumes,
  finishSubject,
  GROUP_SLACK_PX,
  groupKey,
  groupSubject,
  isGroupSubject,
  PARTY,
  unionRects,
} from '../../three/expedition/expedition-objects';
import type { CarriedFigures } from '../../three/ink/ink-fixture';
import type { Volume } from '../../three/village/village-layout';
import type { Quarter } from '../../interfaces/game-state';
import { LandscapeGraph } from '../landscape/landscape-graph.component';

/** Instances of the box mesh: the foundation and the backdrop wall. */
const BOX_CAPACITY = 4;
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

/**
 * Half the width the closeup keeps in view: the recall lamp (and the bell opposite),
 * its pick box and a margin. A screen too narrow for it makes the closeup stand back.
 */
const CLOSEUP_REACH = TOWER_RECALL_X + 0.6;

/** A press that moves farther than this, or lasts longer, is a drag or a hold, not a click on empty space. */
const CLICK_SLOP_PX = 5;
const CLICK_MAX_MS = 500;
/** A press that began on a unit is allowed more: a finger rolls, and a player aiming at a small figure takes a moment. */
const UNIT_CLICK_SLOP_PX = 12;
const UNIT_CLICK_MAX_MS = 1500;

/** A press that began on a unit and lifted close to where it began, within the longer limit. */
export function isUnitClick(
  press: { x: number; y: number; at: number; unit: UnitFocus | null } | null,
  click: { clientX: number; clientY: number; timeStamp: number },
  onLaunchControl: boolean,
): boolean {
  return (
    !!press?.unit &&
    !onLaunchControl &&
    Math.hypot(click.clientX - press.x, click.clientY - press.y) <=
      UNIT_CLICK_SLOP_PX &&
    click.timeStamp - press.at <= UNIT_CLICK_MAX_MS
  );
}

/** Whether the click is the end of the press: it barely moved and soon lifted. */
function isClickOf(
  press: { x: number; y: number; at: number },
  click: { clientX: number; clientY: number; timeStamp: number },
): boolean {
  return (
    Math.hypot(click.clientX - press.x, click.clientY - press.y) <= CLICK_SLOP_PX &&
    click.timeStamp - press.at <= CLICK_MAX_MS
  );
}

/** A rectangle grown by `by` on every side. */
function growRect(
  r: { left: number; top: number; right: number; bottom: number },
  by: number,
) {
  return { left: r.left - by, top: r.top - by, right: r.right + by, bottom: r.bottom + by };
}

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

    <!-- The bone outline of the figure a unit view is on: one instance, a few texels larger than its sprite. -->
    <ngt-instanced-mesh
      #outline
      *args="[undefined, undefined, 1]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aCell"
          *args="[outlineCell, 4]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="figureVertex"
        [fragmentShader]="outlineFragment"
        [uniforms]="figureUniforms"
      />
    </ngt-instanced-mesh>

    <!-- The dashed outline of the figure under the pointer: one instance, the same ring, thinner. -->
    <ngt-instanced-mesh
      #hoverOutline
      *args="[undefined, undefined, 1]"
      [frustumCulled]="false"
    >
      <ngt-plane-geometry *args="[1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aCell"
          *args="[hoverOutlineCell, 4]"
        />
      </ngt-plane-geometry>
      <ngt-shader-material
        [vertexShader]="figureVertex"
        [fragmentShader]="hoverOutlineFragment"
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
  /** Supplies and the loads carried, drawn as the Supplies post and the crates. */
  carried = input<CarriedFigures | null>(null);
  /** Finish Floors of Races the player has run, from the frame; only listed Floors wear the mark (#123). */
  pillarMarks = input<number[]>([]);

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

  /**
   * Where the camera is, as the figures' anchor needs it (#136): the feet are placed on the deck line
   * as this camera sees it, so they stand on it in every Shot and while the camera moves. The first
   * frame replaces it with the real camera's.
   */
  private readonly deckView = signal<DeckView>(
    (() => {
      const { camUp, lookUp, distance } = SHOTS.battle;
      const pitch = Math.atan2(camUp - lookUp, distance);
      return {
        y: floorToY(0) + camUp,
        z: distance,
        upY: Math.cos(pitch),
        upZ: -Math.sin(pitch),
      };
    })(),
    { equal: sameDeckView },
  );

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
  protected readonly outlineFragment = OUTLINE_FRAGMENT;
  protected readonly hoverOutlineFragment = HOVER_OUTLINE_FRAGMENT;
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
  protected readonly outlineCell = new Float32Array(4);
  protected readonly hoverOutlineCell = new Float32Array(4);
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
  private readonly outlineRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('outline');
  private readonly hoverOutlineRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('hoverOutline');

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

  /** The Finish Floor marks to draw: listed by the server and discovered. */
  protected readonly finishMarks = computed(() =>
    planFinishMarks(this.tower(), this.pillarMarks()),
  );

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
    const view = this.deckView();
    const focus = this.zoom.focus();
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
      surfaceY: floorSurfaceY(anchor),
      standY: (surfaceY, z, h) => standCentreY(view, surfaceY, z, h),
      // A unit view puts the figure it is on on the line; the rest stand on the same flat deck.
      standOn: focus ? figureId(focus) : undefined,
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
  /** Timestamp of the click the launch mesh acted on, so the canvas's own click does not act twice. */
  private launchClickedAt = -1;
  /** The unit the frame's hover is on: the press takes it, so the hover and the click never disagree. */
  private hoverUnit: UnitFocus | null = null;
  private pointerDown = false;
  private lastPointerY = 0;

  private readonly store = injectStore();
  private readonly stage = inject(StageService);
  private readonly zoom = inject(ZoomService);
  private readonly unitHover = inject(UnitHoverService);
  /** Where the pointer last was on the canvas (CSS px), when it is a mouse or a pen; null off the canvas and for touch. */
  private pointer: { x: number; y: number } | null = null;
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
      bell: bellAvailability(state, this.launch.held()).usable,
      recall: extractAvailability(state).usable,
    };
  });

  /** Where the Party's formation stands, so the crates lie at its feet. */
  private readonly partyX = computed(() => {
    const plan = this.figurePlan();
    const xs = [...plan.marks.slice(0, plan.figureCount), ...plan.specks]
      .filter((f) => f.id.startsWith('unit-'))
      .map((f) => f.x);
    return xs.length ? (Math.min(...xs) + Math.max(...xs)) / 2 : -1.9;
  });

  /** The launch objects and the Expedition objects as boxes, beside the Floor the Party fights on. */
  private readonly launchVolumes = computed<Volume[]>(() => {
    if (!this.active()) return [];
    const u = this.usable();
    // The bell, the lamp and the post stand on the deck line, where they are in front of it; the
    // crates' shelf rises from the height of the figures' feet (#136).
    const view = this.deckView();
    const surface = floorSurfaceY(this.anchorFloor());
    const surfaceY = standFeetY(view, surface, TOWER_LAUNCH_Z);
    const crateSurfaceY = standFeetY(view, surface, FIGURE_Z);
    // The rods of the shelf end in the Floor's ceiling slab, at the crates' depth (#142).
    const crateCeilingY = standFeetY(view, surface + CRATE_CEILING_RISE - DECK_LINE_RISE, CRATE_Z);
    const hovered = this.selection.hovered()?.id ?? null;
    const c = this.carried();
    const volumes = [
      ...towerLaunchVolumes({
        surfaceY,
        bellUsable: u.bell,
        recallUsable: u.recall,
        hovered,
      }),
      ...expeditionVolumes({
        surfaceY,
        crateSurfaceY,
        crateCeilingY,
        supplies: c?.supplies ?? 0,
        maxSupplies: c?.maxSupplies ?? 0,
        credits: c?.credits ?? 0,
        materials: c?.materials ?? 0,
        relics: c?.relics ?? 0,
        partyX: this.partyX(),
        hovered,
      }),
    ];
    // A rim of clear ground behind each part, so the objects read against the landscape (#122).
    // Appended last: the indices of the parts and their marks do not move.
    return [...volumes, ...rimVolumes(volumes)];
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
    effect(() => this.writeFigures());
    effect(() => this.writeLaunch());

    // The bell and the lamp act (architecture doc 5.5) through the LaunchService,
    // the same route as the panel's buttons.
    injectObjectEvents(() => this.launchRef()?.nativeElement, {
      // No hover from the ray: the bell, the lamp, the post and the crates are resolved once per
      // frame from the pointer's position with the groups (`updateSubjectHover`), steady across
      // the gaps between their boxes.
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
        if (subject) {
          this.launchClickedAt = (event.nativeEvent as Event).timeStamp;
          this.launch.activate(subject);
        }
      },
    });

    if (isDevMode()) {
      // Spike #15 measurement hook: renderer.info of the last frame.
      (window as unknown as Record<string, unknown>)['__tower'] = {
        // The hover sweep (#126): the subject hovered now, and the regions the frame resolves it from (CSS px on the page).
        hovered: () => this.selection.hovered(),
        regions: () => {
          const el = this.store.snapshot.gl.domElement;
          const box = el.getBoundingClientRect();
          const shift = (r: { left: number; top: number; right: number; bottom: number }) => ({
            left: r.left + box.left,
            top: r.top + box.top,
            right: r.right + box.left,
            bottom: r.bottom + box.top,
          });
          return [
            ...volumeRegions(this.store.snapshot.camera, this.launchVolumes(), el.clientWidth, el.clientHeight),
            ...this.groupRects().map((g) => ({ subject: g.subject, direct: false, rects: [g.rect] })),
          ].map((r) => ({ subject: r.subject, direct: r.direct, rects: r.rects.map(shift) }));
        },
        // Capture tooling (#95): where each member sprite is on the canvas, in CSS px.
        figures: () => {
          const el = this.store.snapshot.gl.domElement;
          const box = el.getBoundingClientRect();
          const plan = this.figurePlan();
          return plan.marks.slice(0, plan.figureCount).map((f) => {
            const r = figureRect(
              this.store.snapshot.camera,
              f,
              el.clientWidth,
              el.clientHeight,
            );
            return {
              id: f.id,
              x: box.left + (r.left + r.right) / 2,
              y: box.top + (r.top + r.bottom) / 2,
            };
          });
        },
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
      const unitView = this.zoom.level() === 'unit';
      const subject = unitView ? this.focusSubject() : null;

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
      if (unitView) {
        // The unit Shot frames the figure itself; without it (gone from the plan) the Party's deck.
        const at = subject ?? {
          x: 0,
          y: standCentreY(
            this.deckView(),
            floorSurfaceY(this.anchorFloor()),
            FIGURE_Z,
            0.6,
          ),
          z: FIGURE_Z,
        };
        this.camTarget.set(at.x, at.y - dropY + pose.lookUp, at.z);
        this.camPos.set(at.x, at.y - dropY + pose.camUp, at.z + pose.distance);
      } else {
        // The closeup stands back on a narrow screen, so the lamp is still in the picture.
        let distance = pose.distance;
        if (this.zoom.level() === 'closeup') {
          const el = this.store.snapshot.gl.domElement;
          const region = towerRegion(
            el.clientWidth,
            this.stage.narrow(),
            pose.spread,
          );
          distance = Math.max(
            distance,
            fitDistance(
              pose.fov,
              region.width / (el.clientHeight || 1),
              CLOSEUP_REACH,
              TOWER_LAUNCH_Z,
            ),
          );
        }
        this.camTarget.set(0, viewY + pose.lookUp, 0);
        this.camPos.set(0, viewY + pose.camUp, distance);
      }

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

      // The figures and the objects stand on the deck line as this camera sees it (#136): when the
      // camera has moved, place them again now, so no frame draws them for the camera before.
      const before = this.deckView();
      this.deckView.set(deckViewOf(camera));
      if (this.deckView() !== before) {
        this.writeFigures();
        this.writeLaunch();
      }

      // The card follows the figure: tell it where the figure is on the canvas.
      const placed = unitView ? this.focusSubject() : null;
      this.zoom.setAnchor(
        placed && el.clientWidth > 0
          ? figureRect(camera, placed, el.clientWidth, el.clientHeight)
          : null,
      );

      this.updateSubjectHover();
      this.updateUnitHover();
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
    // The Finish Floor marks follow the numerals in the same draw call.
    const marks = this.finishMarks();
    marks.forEach((k, j) => {
      const i = plan.numerals.length + j;
      numerals.setMatrixAt(
        i,
        m.compose(p.set(k.x, k.y, NUMERAL_Z), IDENTITY_QUATERNION, size),
      );
      this.numeralDigit.set([MARK_DIGIT, 0], i * 2);
    });
    numerals.count = plan.numerals.length + marks.length;
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
    this.writeOutline(plan.marks.slice(0, plan.figureCount));
  }

  /** The sprite the unit view is on, if it is one (a speck has no sprite to outline). */
  private writeOutline(sprites: FigureInstance[]): void {
    const focus = this.zoom.focus();
    this.placeOutline(
      this.outlineRef()?.nativeElement,
      this.outlineCell,
      (focus && sprites.find((s) => s.id === figureId(focus))) || null,
    );
  }

  /** Puts a one-instance outline mesh on a sprite, or hides it. */
  private placeOutline(
    mesh: THREE.InstancedMesh | undefined,
    cell: Float32Array,
    f: FigureInstance | null,
  ): void {
    if (!mesh?.isInstancedMesh) {
      return;
    }
    if (!f) {
      mesh.count = 0;
      return;
    }
    mesh.setMatrixAt(
      0,
      new THREE.Matrix4().compose(
        new THREE.Vector3(f.x, f.y, f.z),
        IDENTITY_QUATERNION,
        new THREE.Vector3(
          (f.w * (FIGURE_W + 2 * OUTLINE_PAD)) / FIGURE_W,
          (f.h * (FIGURE_H + 2 * OUTLINE_PAD)) / FIGURE_H,
          1,
        ),
      ),
    );
    cell.set(
      [
        f.cell % FIGURE_COLS,
        Math.floor(f.cell / FIGURE_COLS),
        // The ring is drawn one texel out and over the Party's rim: it is no Party quad itself.
        f.role === ROLE_PARTY ? ROLE_RAMP : f.role,
        1,
      ],
      0,
    );
    mesh.count = 1;
    mesh.instanceMatrix.needsUpdate = true;
    markAttributes(mesh, ['aCell']);
  }

  /**
   * The sprite under the pointer, by the very hit-test a click uses (`figureAt`), drawn with the
   * dashed outline and named beside the pointer. Run every frame, so it follows a figure that
   * moves and a camera that eases. Off for the chosen unit, for touch, while a button is down
   * (a drag pans), and under a launch or Expedition object, which wins as the group hover does.
   */
  private updateUnitHover(): void {
    const hit = this.hoveredSprite();
    this.hoverUnit = hit ? parseFigureId(hit.id) : null;
    this.placeOutline(this.hoverOutlineRef()?.nativeElement, this.hoverOutlineCell, hit);
    const at = this.pointer;
    this.unitHover.set(
      hit && at
        ? { id: hit.id, caption: this.captionOfFigure(hit.id), x: at.x, y: at.y }
        : null,
    );
  }

  private hoveredSprite(): FigureInstance | null {
    const at = this.pointer;
    const over = this.selection.hovered();
    if (
      !at ||
      !this.active() ||
      this.pointerDown ||
      (over && !isGroupSubject(over))
    ) {
      return null;
    }
    const hit = this.spriteAt(
      at.x,
      at.y,
      this.hoverUnit ? figureId(this.hoverUnit) : null,
    );
    const focus = this.zoom.focus();
    if (!hit || (focus && hit.id === figureId(focus))) {
      return null;
    }
    return hit;
  }

  private captionOfFigure(id: string): string {
    const focus = parseFigureId(id);
    if (!focus) {
      return '';
    }
    const member =
      focus.kind === 'unit'
        ? this.flattenSquad(this.party()).find((m) => m.id === focus.id)
        : this.tower()?.floors?.[this.currentFloor()]?.enemies?.find(
            (m) => m.id === focus.id,
          );
    return member ? hoverCaption(member, focus.kind) : '';
  }

  /** The figure (sprite or speck) the unit view is on, as the plan places it now. */
  private focusSubject(): FigureInstance | null {
    const focus = this.zoom.focus();
    if (!focus) {
      return null;
    }
    const plan = this.figurePlan();
    const id = figureId(focus);
    return (
      plan.marks.slice(0, plan.figureCount).find((f) => f.id === id) ??
      plan.specks.find((f) => f.id === id) ??
      null
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
    // The unit under the pointer is taken at the press: that is what the player aimed at, and the
    // camera or the figures may have moved a little by the time the button lifts.
    let press: {
      x: number;
      y: number;
      at: number;
      unit: UnitFocus | null;
      /** The bell, the lamp, the post or a crate the hover is on at the press. */
      control: VillageSubject | null;
    } | null = null;
    const onPointerDown = (e: PointerEvent) => {
      this.pointerDown = true;
      this.lastPointerY = e.clientY;
      press =
        e.button === 0
          ? {
              x: e.clientX,
              y: e.clientY,
              at: e.timeStamp,
              unit: this.hoverUnit ?? this.figureAt(e),
              control: this.hoveredControl(),
            }
          : null;
    };
    const onClick = (e: MouseEvent) => {
      const p = press;
      press = null;
      // The mesh claims a press its ray landed on; a press inside a control's region that the ray
      // missed (a gap, the hold margin) is that control's too.
      const meshClaimed = !!p && p.at === this.launchPressAt;
      const onLaunch = meshClaimed || !!p?.control;
      this.launchPressAt = -1;
      if (
        p?.control &&
        !meshClaimed &&
        isClickOf(p, e) &&
        this.launchClickedAt !== e.timeStamp
      ) {
        this.launch.activate(p.control);
      }
      // A sprite opens its unit view; anything else, specks included, is empty space.
      if (isUnitClick(p, e, onLaunch)) {
        this.zoom.focusUnit(p!.unit!);
      } else if (isEmptySpaceClick(p, e, onLaunch)) {
        const hit = this.figureAt(e);
        if (hit) {
          this.zoom.focusUnit(hit);
        } else {
          this.zoom.toggleCloseup();
        }
      }
    };
    const onHoverMove = (e: PointerEvent) => {
      // A finger has no hover: only a mouse or a pen names what it is over.
      this.pointer =
        e.pointerType === 'touch' ? null : this.pointerAt(e);
    };
    const onHoverLeave = () => {
      this.pointer = null;
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
      el.addEventListener('pointermove', onHoverMove);
      el.addEventListener('pointerleave', onHoverLeave);
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
      bound?.removeEventListener('pointermove', onHoverMove);
      bound?.removeEventListener('pointerleave', onHoverLeave);
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
    });
  }

  /** Kinds this scene resolves the hover of: the launch controls, the Expedition objects and the groups. */
  private ownsHover(subject: VillageSubject | null): boolean {
    return (
      !!subject &&
      (subject.kind === 'bell' ||
        subject.kind === 'recall' ||
        subject.kind === 'expedition')
    );
  }

  /** The bell, the lamp, the post or a crate under the pointer now (a group is not a control). */
  private hoveredControl(): VillageSubject | null {
    const over = this.selection.hovered();
    return this.ownsHover(over) && !isGroupSubject(over) ? over : null;
  }

  /**
   * What the pointer is over in the scene, decided once per frame from its position alone, to one
   * subject (#126): a thing it is directly over first (the bell, the lamp, the post, a crate), then
   * the Party's formation or an enemy group, the smaller first, held until the pointer has left it
   * by a small margin. It reads the boxes and the figures as drawn this frame, so a figure that moves
   * or a camera that eases is followed, and moving one pixel inside a region never changes it.
   * A finger has no hover (no pointer here).
   */
  private updateSubjectHover(): void {
    const current = this.selection.hovered();
    // What something else (a panel row) hovers is not this scene's to clear or replace.
    if (current && !this.ownsHover(current)) return;
    // A button held down is a drag that pans: the hover stays as it was until it lifts.
    if (this.pointerDown) return;
    const at = this.pointer;
    const el = this.store.snapshot.gl.domElement;
    let next: VillageSubject | null = null;
    if (
      at &&
      this.active() &&
      this.zoom.level() === 'battle'
    ) {
      const regions: HoverRegion[] = [
        ...volumeRegions(
          this.store.snapshot.camera,
          this.launchVolumes(),
          el.clientWidth,
          el.clientHeight,
        ),
        ...this.groupRects().map((g) => ({
          subject: g.subject,
          direct: false,
          rects: [growRect(g.rect, GROUP_SLACK_PX)],
        })),
      ];
      next = resolveHover(regions, at.x, at.y, current)?.subject ?? null;
    }
    if (next === current || (next && current && next.id === current.id)) return;
    this.selection.hover(next);
    el.style.cursor = next ? (isGroupSubject(next) ? 'help' : 'pointer') : '';
  }

  private pointerAt(e: MouseEvent): { x: number; y: number } {
    const [x, y] = this.canvasPoint(e);
    return { x, y };
  }

  private canvasPoint(e: MouseEvent): [number, number] {
    const box = this.store.snapshot.gl.domElement.getBoundingClientRect();
    return [e.clientX - box.left, e.clientY - box.top];
  }

  /** The group a figure belongs to: the Party for a unit, its enemy group for an enemy. */
  private subjectOfFigure(id: string): VillageSubject | null {
    if (id.startsWith('unit-')) {
      return PARTY;
    }
    const focus = parseFigureId(id);
    if (focus?.kind !== 'enemy') {
      return null;
    }
    const enemy = this.tower()
      ?.floors?.[this.currentFloor()]?.enemies?.find((e) => e.id === focus.id);
    return enemy ? groupSubject(groupKey(enemy)) : null;
  }

  /** Where the Party and each enemy group are on the canvas now, specks included. */
  private groupRects() {
    const el = this.store.snapshot.gl.domElement;
    const plan = this.figurePlan();
    const figures = [...plan.marks.slice(0, plan.figureCount), ...plan.specks]
      .map((f) => ({
        subject: this.subjectOfFigure(f.id),
        rect: figureRect(
          this.store.snapshot.camera,
          f,
          el.clientWidth,
          el.clientHeight,
        ),
      }))
      .filter((f): f is { subject: VillageSubject; rect: typeof f.rect } => !!f.subject);
    const marks = this.finishMarks().map((k) => ({
      subject: finishSubject(k.floor),
      rect: figureRect(
        this.store.snapshot.camera,
        { x: k.x, y: k.y, z: NUMERAL_Z, w: NUMERAL_SIZE[0], h: NUMERAL_SIZE[1] },
        el.clientWidth,
        el.clientHeight,
      ),
    }));
    return [...unionRects(figures), ...marks];
  }

  /** The sprite at a point of the canvas (CSS px): one hit-test for the click and the hover. */
  private spriteAt(
    x: number,
    y: number,
    holdId: string | null = null,
  ): FigureInstance | null {
    const el = this.store.snapshot.gl.domElement;
    const plan = this.figurePlan();
    return pickSpriteSteady(
      this.store.snapshot.camera,
      plan.marks.slice(0, plan.figureCount),
      x,
      y,
      el.clientWidth,
      el.clientHeight,
      this.stage.scale(),
      holdId,
    );
  }

  /** The member whose sprite is under a click, if any. */
  private figureAt(e: MouseEvent): UnitFocus | null {
    const [x, y] = this.canvasPoint(e);
    const hit = this.spriteAt(x, y);
    return hit ? parseFigureId(hit.id) : null;
  }

  private readTheme(): boolean {
    return document.documentElement.dataset['theme'] === 'dark';
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

import {
  ChangeDetectionStrategy,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { injectBeforeRender, injectStore, NgtArgs } from 'angular-three';
import * as THREE from 'three';

import { Tower, TowerFloor } from '../../interfaces/tower';
import { Squad, Reinforcement } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { SpriteTextureService, SpriteKind } from '../../three/sprite-texture.service';
import '../../three/catalogue';

const FLOOR_HEIGHT = 2.2;
const GHOST_FLOORS_AHEAD = 8;
const SPRITE_Z = 1.7;
// World Y of the walkable deck surface above a floor's base (matches the thin
// platform mesh at slab.y + 0.9, half its 0.16 thickness on top). Sprites are
// anchored by their feet here so they stay planted as the formation scales.
const FLOOR_SURFACE_OFFSET = 0.98;

/**
 * Formation rectangle used to lay out a group of billboards. The grid keeps
 * this columns:rows aspect ratio as the group grows, so with the default 5:3
 * a group of 15 becomes 5×3 and a group of 8 becomes ~4×2. The wide ratio
 * spreads units sideways rather than deep. Change these two numbers to
 * reconfigure the ratio.
 */
const FORMATION_COLUMNS = 5;
const FORMATION_ROWS = 3;
const FORMATION_RATIO = FORMATION_COLUMNS / FORMATION_ROWS;

const GROUP_OFFSET_X = 1.9;
const COL_STEP = 0.52;
const ROW_STEP_Z = 0.6;
// Ranks are NOT lifted vertically: every unit's feet rest on the flat deck.
// Back ranks read as "further in" purely through depth (Z) + the sideways
// diagonal shift, exactly like figures standing on a real floor.
const ROW_STEP_Y = 0;
// Each rank shifts sideways so the group reads as a loose diagonal wedge
// instead of a rigid grid, and so back ranks peek out beside the front rank.
const ROW_STEP_X = 0.34;
const SPRITE_SCALE = 0.72;

interface FloorSlab {
  n: number;
  y: number;
  boss: boolean;
  current: boolean;
  ceiling: boolean;
  opacity: number;
  color: string;
}

interface SpriteDesc {
  id: string;
  texture: THREE.Texture;
  tint: string;
  position: [number, number, number];
  scale: [number, number, number];
}

/** floor number -> world Y (floor 1 sits at the base, y = 0). */
function floorToY(n: number): number {
  return (n - 1) * FLOOR_HEIGHT;
}

/** Golden-angle hue per wave index, matching the old 2D markers. */
function waveColor(index: number): string {
  return `hsl(${(index * 137.5) % 360}, 72%, 55%)`;
}

/** Columns a group of `n` members occupies, preserving FORMATION_RATIO. */
function gridCols(n: number): number {
  return Math.max(1, Math.round(Math.sqrt(n * FORMATION_RATIO)));
}

@Component({
  selector: 'app-tower-scene-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [NgtArgs],
  template: `
    <ngt-color *args="[bgColor()]" attach="background" />
    <ngt-fog-exp2 *args="[bgColor(), fogDensity()]" attach="fog" />

    <ngt-ambient-light [intensity]="ambient()" />
    <ngt-directional-light [position]="[6, 12, 8]" [intensity]="1.2" />
    <ngt-directional-light
      [position]="[-5, 4, -6]"
      [intensity]="0.35"
      [color]="'#8fb7ff'"
    />

    <!-- Solid, discovered floors -->
    @for (slab of slabs(); track slab.n) {
      <ngt-mesh [position]="[0, slab.y, 0]">
        <ngt-box-geometry *args="[4.4, 1.7, 2.8]" />
        <ngt-mesh-standard-material
          [color]="slab.color"
          [roughness]="0.85"
          [metalness]="0.1"
          [transparent]="slab.ceiling"
          [opacity]="slab.opacity"
        />
      </ngt-mesh>

      <ngt-mesh [position]="[0, slab.y + 0.9, 0.2]">
        <ngt-box-geometry *args="[4.7, 0.16, 3.1]" />
        <ngt-mesh-standard-material
          [color]="slab.current ? '#6a5326' : '#2b3346'"
          [roughness]="0.9"
          [emissive]="slab.current ? '#8a5a1e' : '#000000'"
          [emissiveIntensity]="slab.current ? 0.85 : 0"
          [transparent]="slab.ceiling"
          [opacity]="slab.opacity"
        />
      </ngt-mesh>
    }

    <!-- Glowing backdrop wall standing behind the current battle -->
    @if (backdrop().show) {
      <ngt-mesh [position]="[0, backdrop().y, -1.25]">
        <ngt-box-geometry *args="[4.2, backdrop().height, 0.12]" />
        <ngt-mesh-standard-material
          [color]="backdrop().boss ? '#6a2030' : '#7b6a45'"
          [roughness]="0.85"
          [metalness]="0.1"
          [transparent]="true"
          [opacity]="0.55"
          [emissive]="backdrop().boss ? '#8a1e33' : '#8a5a1e'"
          [emissiveIntensity]="0.9"
        />
      </ngt-mesh>
    }

    <!-- Faint ghost floors rising into the fog above the explored top -->
    @for (g of ghostSlabs(); track g.n) {
      <ngt-mesh [position]="[0, g.y, -0.3]">
        <ngt-box-geometry *args="[4.2, 1.7, 2.6]" />
        <ngt-mesh-standard-material
          [color]="'#3a4256'"
          [transparent]="true"
          [opacity]="g.opacity"
          [roughness]="1"
        />
      </ngt-mesh>
    }

    <!-- Billboarded characters -->
    @for (s of sprites(); track s.id) {
      <ngt-sprite [position]="s.position" [scale]="s.scale">
        <ngt-sprite-material
          [map]="s.texture"
          [color]="s.tint"
          [transparent]="true"
          [depthWrite]="false"
        />
      </ngt-sprite>
    }

    <!-- Floor-number plaques -->
    @for (l of labels(); track l.id) {
      <ngt-sprite [position]="l.position" [scale]="l.scale">
        <ngt-sprite-material
          [map]="l.texture"
          [transparent]="true"
          [depthWrite]="false"
        />
      </ngt-sprite>
    }
  `,
})
export class TowerSceneGraph {
  private readonly textures = inject(SpriteTextureService);

  tower = input<Tower | undefined>();
  party = input<Squad>();
  active = input<boolean>(false);
  reinforcements = input<Reinforcement[]>([]);

  private readonly dark = signal(this.readTheme());

  /** Canvas width/height ratio; drives how much the formations shrink to fit. */
  private readonly viewAspect = signal(0.6);

  protected readonly bgColor = computed(() => (this.dark() ? '#0e1424' : '#9fc0e8'));
  protected readonly fogDensity = computed(() => (this.dark() ? 0.05 : 0.028));
  protected readonly ambient = computed(() => (this.dark() ? 0.85 : 1.1));

  private readonly floors = computed<[number, TowerFloor][]>(() => {
    const map = this.tower()?.floors ?? {};
    return Object.entries(map)
      .map(([k, v]) => [Number(k), v] as [number, TowerFloor])
      .sort((a, b) => a[0] - b[0]);
  });

  private readonly currentFloor = computed(() => this.tower()?.currentFloor ?? 1);

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
  private readonly anchorFloor = computed(() => Math.max(0, this.currentFloor() - 1));

  protected readonly slabs = computed<FloorSlab[]>(() => {
    const anchor = this.anchorFloor();
    const discovered = this.floors();
    // Nothing to show until the first expedition has actually started building
    // the tower.
    if (discovered.length === 0) {
      return [];
    }
    // The tower's foundation: a base platform below floor 1 that the party
    // stands on while fighting floor 1, mirroring how they stand on floor N-1
    // while fighting floor N. Without it, floor 1 has nothing to stand on.
    const base: FloorSlab = {
      n: 0,
      y: floorToY(0),
      boss: false,
      current: anchor === 0,
      ceiling: false,
      opacity: 1,
      color: '#4c5464',
    };
    const rest = discovered.map(([n, floor]) => {
      const ceiling = n === anchor + 1;
      // The floor the units actually stand on gets the warm highlight; the glow
      // above it is a separate backdrop wall (see `backdrop`).
      const current = n === anchor;
      return {
        n,
        y: floorToY(n),
        boss: floor.boss === true,
        current,
        ceiling,
        // The slab above the fight doubles as the ceiling; keep it translucent
        // so it never covers the units below.
        opacity: ceiling ? 0.22 : 1,
        color: floor.boss ? '#7a2230' : '#5b6472',
      } satisfies FloorSlab;
    });
    return [base, ...rest];
  });

  /**
   * A glowing panel that stands directly behind the battle. Its base rests on
   * the anchor floor's deck and it rises up *within* that floor's room, so it
   * reads as the background wall above the floor the units stand on — not as a
   * separate floor slab one level higher.
   */
  protected readonly backdrop = computed(() => {
    const anchor = this.anchorFloor();
    const base = floorToY(anchor) + FLOOR_SURFACE_OFFSET;
    const height = 1.9;
    return {
      // Wait until the tower's floor data has arrived: otherwise the backdrop
      // (and units) would render for a frame or two with no floor deck beneath
      // them, which looks like the battle is floating one floor too low.
      show: this.active() && this.floors().length > 0,
      y: base + height / 2,
      height,
      boss: this.tower()?.floors?.[this.currentFloor()]?.boss === true,
    };
  });

  protected readonly ghostSlabs = computed(() => {
    const floors = this.floors();
    const maxDiscovered = floors.length ? floors[floors.length - 1][0] : 0;
    const maxFloor = this.tower()?.maxFloor ?? 0;
    const top = Math.min(maxFloor, maxDiscovered + GHOST_FLOORS_AHEAD);
    const out: { n: number; y: number; opacity: number }[] = [];
    for (let n = maxDiscovered + 1; n <= top; n++) {
      const dist = n - maxDiscovered;
      // The first ghost floor is the immediate ceiling over the current
      // battle, so keep it very faint; higher ghosts fade into the fog.
      const opacity = dist === 1 ? 0.12 : Math.max(0.05, 0.5 - dist * 0.06);
      out.push({ n, y: floorToY(n), opacity });
    }
    return out;
  });

  protected readonly sprites = computed<SpriteDesc[]>(() => {
    const out: SpriteDesc[] = [];
    // Hold off until the tower's floor data has loaded, so the party never
    // renders for a frame without the floor deck it should be standing on.
    if (!this.active() || this.floors().length === 0) {
      return out;
    }

    const current = this.currentFloor();
    const anchor = this.anchorFloor();
    const surfaceY = floorToY(anchor) + FLOOR_SURFACE_OFFSET;

    const partyMembers = this.flattenSquad(this.party());
    const floor = this.tower()?.floors?.[current];
    const enemies = floor?.enemies ?? [];

    // Shrink both formations when there isn't room to show them at full size:
    // the visible width scales with the (portrait) canvas aspect, so the more
    // units per side and the narrower the column, the smaller the sprites.
    const partyCols = gridCols(partyMembers.length || 1);
    const enemyCols = gridCols(enemies.length || 1);
    const maxCols = Math.max(partyCols, enemyCols, 1);
    const maxRows = Math.max(
      Math.ceil((partyMembers.length || 1) / partyCols),
      Math.ceil((enemies.length || 1) / enemyCols),
      1,
    );
    const diagHalf = ((maxRows - 1) / 2) * ROW_STEP_X;
    const naturalHalf =
      GROUP_OFFSET_X + ((maxCols - 1) * COL_STEP) / 2 + diagHalf + SPRITE_SCALE / 2;
    const visibleHalf = Math.max(1.05, 2.5 * this.viewAspect());
    const fit = Math.min(1, Math.max(0.24, (visibleHalf * 0.92) / naturalHalf));

    // Party formation on the left, enemies on the right of the current floor.
    // The two wedges lean opposite ways so they mirror across the centre.
    this.layoutGroup('unit', partyMembers, -GROUP_OFFSET_X * fit, surfaceY, fit, 1, out);
    this.layoutGroup('enemy', enemies, GROUP_OFFSET_X * fit, surfaceY, fit, -1, out);

    // Reinforcement waves climbing up the front of the tower toward the party.
    const waveList = this.reinforcements();
    waveList.forEach((wave, index) => {
      const raw = wave.currentFloor ?? 1;
      const waveFloor = Math.min(anchor, Math.max(1, raw));
      const wy = floorToY(waveFloor) + FLOOR_SURFACE_OFFSET + 0.35;
      const count = waveList.length;
      const step = count > 1 ? 2.4 / (count - 1) : 0;
      const wx = count > 1 ? -1.2 + index * step : 0;
      out.push({
        id: `wave-${index}`,
        texture: this.textures.get('unit', 'PORTER'),
        tint: waveColor(index),
        position: [wx, wy, SPRITE_Z + 0.4],
        scale: [1.25, 1.25, 1],
      });
    });

    return out;
  });

  /**
   * Arranges a group of members into a rectangle that preserves
   * `FORMATION_RATIO`: columns run left→right, rows recede into the tower
   * (stepping back in Z and up in Y so back ranks stay visible). `surfaceY` is
   * the deck the front rank stands on; every sprite is anchored by its feet so
   * the group stays planted as it scales. `diagSign` (±1) leans the wedge.
   */
  private layoutGroup(
    kind: SpriteKind,
    members: Member[],
    centerX: number,
    surfaceY: number,
    scale: number,
    diagSign: number,
    out: SpriteDesc[],
  ): void {
    const n = members.length;
    if (!n) {
      return;
    }
    const cols = gridCols(n);
    const rows = Math.ceil(n / cols);
    const colStep = COL_STEP * scale;
    const rowStepZ = ROW_STEP_Z * scale;
    const rowStepY = ROW_STEP_Y * scale;
    const rowStepX = ROW_STEP_X * scale;
    const spriteScale = SPRITE_SCALE * scale;
    // Feet sit on the deck: raise the (centre-anchored) sprite by half its
    // height plus the per-rank rise that keeps back rows visible.
    const feetY = surfaceY + spriteScale / 2;

    members.forEach((member, i) => {
      const c = i % cols;
      const r = Math.floor(i / cols);
      const itemsInRow = r === rows - 1 ? n - r * cols : cols;
      const rowWidth = (itemsInRow - 1) * colStep;
      // Centre the diagonal so the wedge stays balanced around centerX.
      const diag = diagSign * (r - (rows - 1) / 2) * rowStepX;
      const x = centerX + c * colStep - rowWidth / 2 + diag;
      const z = SPRITE_Z - r * rowStepZ;
      const y = feetY + r * rowStepY;
      out.push({
        id: `${kind}-${member.id}`,
        texture: this.textures.get(kind, this.typeKey(kind, member)),
        tint: '#ffffff',
        position: [x, y, z],
        scale: [spriteScale, spriteScale, 1],
      });
    });
  }

  /** Floor-number plaques (and a GUARDIAN tag on boss floors) on each slab. */
  protected readonly labels = computed<SpriteDesc[]>(() => {
    const out: SpriteDesc[] = [];
    const current = this.anchorFloor();

    for (const [n, floor] of this.floors()) {
      const y = floorToY(n);
      const boss = floor.boss === true;
      const numColor = boss ? '#f0c866' : n === current ? '#ffe6a3' : '#e7dcc4';
      const label = this.textures.text(`FLOOR ${n}`, { color: numColor });
      const h = 0.42;
      out.push({
        id: `label-${n}`,
        texture: label.texture,
        tint: '#ffffff',
        position: [0, y - 0.5, 1.5],
        scale: [h * label.aspect, h, 1],
      });
      if (boss) {
        const tag = this.textures.text('GUARDIAN', { color: '#ffd873' });
        const gh = 0.34;
        out.push({
          id: `boss-${n}`,
          texture: tag.texture,
          tint: '#ffffff',
          position: [0, y + 0.2, 1.5],
          scale: [gh * tag.aspect, gh, 1],
        });
      }
    }
    return out;
  });

  private typeKey(kind: SpriteKind, member: Member): string {
    if (kind === 'enemy') {
      return member.stats?.type ?? member.name ?? 'enemy';
    }
    return member.stats?.type ?? member.name ?? 'unit';
  }

  private flattenSquad(squad: Squad | undefined): Member[] {
    if (!squad) {
      return [];
    }
    return Object.values(squad).flat().filter(Boolean) as Member[];
  }

  private readonly camTarget = new THREE.Vector3(0, floorToY(0) + 1.3, 0);
  private readonly camPos = new THREE.Vector3(0, floorToY(0) + 7, 12.5);

  /** Vertical world offset from scroll-to-pan; decays back to 0 when idle. */
  private panY = 0;
  private lastPanMs = 0;
  private pointerDown = false;
  private lastPointerY = 0;

  private readonly store = injectStore();

  constructor() {
    const host = document.documentElement;
    const observer = new MutationObserver(() => this.dark.set(this.readTheme()));
    observer.observe(host, { attributes: true, attributeFilter: ['data-theme'] });
    inject(DestroyRef).onDestroy(() => observer.disconnect());

    this.bindPanControls();

    injectBeforeRender(({ camera, delta }) => {
      const focusY = floorToY(this.anchorFloor());
      const damp = 1 - Math.pow(0.001, delta);

      // Keep the panned view within the discovered tower (floor 1 → top floor).
      const floorsArr = this.floors();
      const topFloor = floorsArr.length ? floorsArr[floorsArr.length - 1][0] : 1;
      const minView = floorToY(0) - 0.5;
      const maxView = floorToY(topFloor) + 0.5;
      this.panY = THREE.MathUtils.clamp(
        this.panY,
        minView - focusY,
        maxView - focusY,
      );

      // Recenter on the current floor once the player stops interacting.
      if (performance.now() - this.lastPanMs > 2500 && !this.pointerDown) {
        this.panY = THREE.MathUtils.lerp(this.panY, 0, damp * 0.6);
      }

      const viewY = focusY + this.panY;
      this.camTarget.set(0, viewY + 1.3, 0);
      this.camPos.set(0, viewY + 7, 12.5);

      camera.position.lerp(this.camPos, damp);
      camera.lookAt(this.camTarget);
    });
  }

  /** Mouse-wheel and drag on the canvas pan the camera vertically. */
  private bindPanControls(): void {
    const nudge = (deltaWorldY: number) => {
      this.panY += deltaWorldY;
      this.lastPanMs = performance.now();
    };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      nudge(-e.deltaY * 0.01);
    };
    const onPointerDown = (e: PointerEvent) => {
      this.pointerDown = true;
      this.lastPointerY = e.clientY;
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
      this.viewAspect.set(w / h);
    };
    const stop = effect(() => {
      const el = this.store.gl()?.domElement as HTMLElement | undefined;
      if (!el || el === bound) return;
      bound = el;
      el.addEventListener('wheel', onWheel, { passive: false });
      el.addEventListener('pointerdown', onPointerDown);
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
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
    });
  }

  private readTheme(): boolean {
    return document.documentElement.dataset['theme'] === 'dark';
  }
}

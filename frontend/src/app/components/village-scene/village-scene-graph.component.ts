import {
  ChangeDetectionStrategy,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  isDevMode,
  signal,
  viewChild,
} from '@angular/core';
import {
  injectBeforeRender,
  injectObjectEvents,
  injectStore,
  NgtArgs,
} from 'angular-three';
import * as THREE from 'three';

import { LaunchService } from '../../services/launch.service';
import { PopoverService } from '../../services/popover.service';
import { VillageSelectionService } from '../../services/village-selection.service';
import { createInkUniforms, setInkTheme } from '../../three/ink/ink-material';
import { INK_FRAGMENT, INK_VERTEX } from '../../three/ink/ink.glsl';
import { InkTheme, RAMPS } from '../../three/ink/palette';
import {
  buildVillage,
  VillageInput,
  Volume,
} from '../../three/village/village-layout';
import { subjectRect } from '../../three/village/subject-rect';
import { resolveHover } from '../../three/hover-resolver';
import { volumeRegions } from '../../three/village/volume-regions';
import { VILLAGE_OVERLAY_CAPACITY } from '../../three/village/cube-outline';
import {
  OUTLINE,
  subjectOfVolume,
  type VillageSubject,
} from '../../three/village/village-subjects';
import { applyStageView } from '../../three/stage-view';
import { StageService } from '../../services/stage.service';
import { ZoomService } from '../../services/zoom.service';
import { dampFactor, prefersReducedMotion } from '../../three/camera-shots';
import {
  closeupFrame,
  fitVillageCamera,
  focusLineScale,
} from '../../three/village/village-camera';
import { LandscapeGraph } from '../landscape/landscape-graph.component';
import '../../three/catalogue';

/** Instances the Village mesh can hold (about 250 are used at the busiest). */
const VILLAGE_CAPACITY = 512;
/**
 * Ink density of a focus line. Above 1 on purpose: the shader clamps it, so every face of a thin
 * edge (top, front, side) reaches the line slot of the ramp, and the line reads as one line.
 */
const OVERLAY_TONE = 3;

/** The Village's own fog: its depth is about 30 units, the Tower fades by alpha. */
const VILLAGE_FOG_DENSITY = 0.01;
/** World y of the Village ground's top face (its box is 0.2 thick, centred on -0.2). */
const VILLAGE_GROUND_TOP = -0.1;

/** A focus line (a hover, a choice, a Start mark): drawn in the cube's own pass, above the buildings. */
export function isFocusLine(v: Volume): boolean {
  return v.id.startsWith(OUTLINE);
}

/**
 * The Village Elevation: one instanced ink mesh, no lights, a fixed vantage.
 * Every volume is a box (architecture doc section 5.2).
 */
@Component({
  selector: 'app-village-scene-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [NgtArgs, LandscapeGraph],
  template: `
    <ngt-color *args="[clearHex()]" attach="background" />
    <!-- The ambient landscape behind and to the left of the Village: one more draw call. -->
    <app-landscape-graph
      [quarter]="village().quarter ?? null"
      [theme]="theme()"
      [groundY]="GROUND_TOP"
      [fogDensity]="FOG_DENSITY"
      [time]="village().flicker"
    />
    <ngt-instanced-mesh
      #village
      *args="[undefined, undefined, CAPACITY]"
      [frustumCulled]="false"
    >
      <ngt-box-geometry *args="[1, 1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aTone"
          *args="[tone, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aAlpha"
          *args="[alpha, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aRole"
          *args="[role, 1]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="inkVertex"
        [fragmentShader]="inkFragment"
        [uniforms]="inkUniforms"
      />
    </ngt-instanced-mesh>
    <!-- The focus cube (#133): the hovered and the chosen outline, drawn above the buildings so no face hides an edge. -->
    <ngt-instanced-mesh
      #cube
      *args="[undefined, undefined, OVERLAY_CAPACITY]"
      [frustumCulled]="false"
      [renderOrder]="10"
    >
      <ngt-box-geometry *args="[1, 1, 1]">
        <ngt-instanced-buffer-attribute
          attach="attributes.aTone"
          *args="[overlayTone, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aAlpha"
          *args="[overlayAlpha, 1]"
        />
        <ngt-instanced-buffer-attribute
          attach="attributes.aRole"
          *args="[overlayRole, 1]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="inkVertex"
        [fragmentShader]="inkFragment"
        [uniforms]="inkUniforms"
        [depthTest]="false"
        [depthWrite]="false"
      />
    </ngt-instanced-mesh>
  `,
})
export class VillageSceneGraph {
  /** What the Village shows; the layout is rebuilt from it. */
  village = input.required<VillageInput>();

  private readonly store = injectStore();
  private readonly selection = inject(VillageSelectionService);
  private readonly launch = inject(LaunchService);
  private readonly popover = inject(PopoverService);
  private readonly stage = inject(StageService);
  private readonly zoom = inject(ZoomService);
  /** 0 is the far view, 1 the closeup (#130); it eases between them, or cuts under reduced motion. */
  private closeness = 0;
  /** The thickness the focus lines are written at, as a share of the far view's (`focusLineScale`). */
  private lineScale = 1;
  /** What the closeup frames: the built Village of this Tier. */
  private readonly frame = computed(() => closeupFrame(this.volumes()));
  private readonly dark = signal(this.readTheme());

  protected readonly theme = computed<InkTheme>(() =>
    this.dark() ? 'night' : 'day',
  );
  protected readonly clearHex = computed(() => RAMPS[this.theme()][0]);

  protected readonly CAPACITY = VILLAGE_CAPACITY;
  protected readonly GROUND_TOP = VILLAGE_GROUND_TOP;
  protected readonly FOG_DENSITY = VILLAGE_FOG_DENSITY;
  protected readonly inkVertex = INK_VERTEX;
  protected readonly inkFragment = INK_FRAGMENT;
  protected readonly inkUniforms = createInkUniforms(this.theme());
  protected readonly tone = new Float32Array(VILLAGE_CAPACITY);
  protected readonly alpha = new Float32Array(VILLAGE_CAPACITY);
  protected readonly role = new Float32Array(VILLAGE_CAPACITY);
  protected readonly OVERLAY_CAPACITY = VILLAGE_OVERLAY_CAPACITY;
  protected readonly overlayTone = new Float32Array(VILLAGE_OVERLAY_CAPACITY);
  protected readonly overlayAlpha = new Float32Array(VILLAGE_OVERLAY_CAPACITY);
  protected readonly overlayRole = new Float32Array(VILLAGE_OVERLAY_CAPACITY);

  private readonly meshRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('village');
  private readonly cubeRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('cube');
  /** Every volume of the scene: the buildings first, the focus lines (`outline-`) last. */
  private readonly volumes = computed<Volume[]>(() =>
    buildVillage(this.village()),
  );
  /** What the main mesh draws, in instance order: the focus lines are not in it. */
  private readonly solids = computed(() =>
    this.volumes().filter((v) => !isFocusLine(v)),
  );
  /** The subject the last press resolved to: the click that follows is on it. */
  private pressSubject: VillageSubject | null = null;
  private readonly pickCamera = new THREE.PerspectiveCamera();
  private readonly matrix = new THREE.Matrix4();
  private readonly position = new THREE.Vector3();
  private readonly scale = new THREE.Vector3();
  private readonly identity = new THREE.Quaternion();

  constructor() {
    const observer = new MutationObserver(() =>
      this.dark.set(this.readTheme()),
    );
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    });
    inject(DestroyRef).onDestroy(() => observer.disconnect());

    effect(() => {
      setInkTheme(this.inkUniforms, this.theme());
      this.inkUniforms.uFogDensity.value = VILLAGE_FOG_DENSITY;
      this.store.snapshot.invalidate();
    });
    // The closeup toggled: wake the sleeping renderer so the camera sets off.
    effect(() => {
      this.zoom.villageCloseup();
      this.store.snapshot.invalidate();
    });
    effect(() => {
      this.write(this.solids(), this.meshRef()?.nativeElement, false);
      this.writeFocus();
      this.store.snapshot.invalidate();
    });

    // The scene points and picks; only the launch controls act (architecture doc 5.5),
    // and they do it through the LaunchService, the panel's own route. `ngt-primitive` and the mesh
    // take no pointer outputs in a template, so events go through injectObjectEvents.
    // Hover is not read from here: a building is many volumes, and the ray lands on a different one
    // from pixel to pixel. It is resolved once per pointer move from the position (`bindCanvasPointer`).
    injectObjectEvents(() => this.meshRef()?.nativeElement, {
      // A press on a scene volume is that volume's click: remember which press it was,
      // so the canvas's own click (empty space: the sky) lets it pass.
      pointerdown: (event) => {
        event.stopPropagation();
        this.popover.noteMeshPress((event.nativeEvent as Event).timeStamp);
      },
      click: (event) => {
        event.stopPropagation();
        // The thing resolved at the press (the one the caption named), else the volume the ray hit.
        const subject = this.pressSubject ?? this.subjectAt(event.instanceId);
        this.pressSubject = null;
        this.clickSubject(subject, event.nativeEvent as MouseEvent);
      },
    });
    this.bindCanvasPointer();

    if (isDevMode()) {
      // Capture tooling (#99): where a subject is on the page, in CSS px.
      (window as unknown as Record<string, unknown>)['__village'] = {
        // The subject hovered now, and every subject the scene draws (the hover sweep, #126).
        hovered: () => this.selection.hovered(),
        regions: () => {
          const el = this.store.snapshot.gl.domElement;
          return volumeRegions(this.framedCamera(el.clientWidth, el.clientHeight), this.volumes(), el.clientWidth, el.clientHeight);
        },
        volumes: () => this.volumes(),
        registered: () => this.popover.buildings().map((b) => b.id),
        zoomed: () => this.closeness,
        subjects: () => {
          const seen = new Map<string, { kind: string; id: string }>();
          for (const v of this.volumes()) {
            const s = subjectOfVolume(v.id);
            if (s) seen.set(`${s.kind}:${s.id}`, s);
          }
          return [...seen.values()];
        },
        rect: (kind: string, id: string) => {
          const el = this.store.snapshot.gl.domElement;
          const box = el.getBoundingClientRect();
          const r = subjectRect(
            this.framedCamera(el.clientWidth, el.clientHeight),
            this.volumes(),
            { kind: kind as 'building', id },
            el.clientWidth,
            el.clientHeight,
          );
          return r && { left: box.left + r.left, top: box.top + r.top, right: box.left + r.right, bottom: box.top + r.bottom };
        },
      };
    }

    // The vantage follows the canvas and, in the closeup, the blend toward it: the far view is a
    // fixed pose; the closeup widens the stage to the whole screen and frames the built Village.
    // The canvas fills the screen; the Village is framed in the Tower's region of it.
    injectBeforeRender(({ camera, delta }) => {
      const cam = camera as THREE.PerspectiveCamera;
      const el = this.store.snapshot.gl.domElement;
      const w = el.clientWidth;
      const h = el.clientHeight;
      this.easeCloseness(delta);
      this.frameCamera(cam, w, h);
      // The focus lines keep their thickness in pixels as the camera comes in.
      const lineScale = focusLineScale(cam.aspect, this.closeness, this.frame());
      if (Math.abs(lineScale - this.lineScale) > 1e-3) {
        this.lineScale = lineScale;
        this.writeFocus();
      }
      // The open building's popover follows it: report where it is on the canvas.
      const open = this.popover.open();
      this.popover.setAnchor(
        open
          ? subjectRect(cam, this.volumes(), this.popover.openSubject() ?? { kind: 'building', id: open.id }, w, h)
          : null,
      );
      // The renderer sleeps between changes: keep it awake until the camera has arrived.
      if (this.closeness !== (this.zoom.villageCloseup() ? 1 : 0)) {
        this.store.snapshot.invalidate();
      }
    });
  }

  /** Moves the blend toward the rung the zoom is on: damped, never past it; a cut under reduced motion. */
  private easeCloseness(delta: number): void {
    const target = this.zoom.villageCloseup() ? 1 : 0;
    if (this.closeness === target) return;
    // A frame after a long sleep is not a long step: the ease is read over frames, not idle time.
    const damp = prefersReducedMotion() ? 1 : dampFactor(Math.min(delta || 1 / 60, 1 / 20));
    const gap = target - this.closeness;
    this.closeness =
      damp >= 1 || Math.abs(gap) < 1e-3 ? target : this.closeness + gap * damp;
  }

  /** Sets a camera to the stage view and the Village vantage at the blend now. */
  private frameCamera(cam: THREE.PerspectiveCamera, w: number, h: number): void {
    applyStageView(cam, w, h, this.stage.narrow(), this.closeness);
    fitVillageCamera(cam, this.closeness, this.frame());
  }

  /**
   * The canvas's own pointer. A mouse move resolves the hover (one subject, by priority, held
   * with a margin: `resolveHover`); the caption and the outline follow in the same frame. The press
   * gives the click its limits and the subject it landed on; a click whose press did not land on a
   * scene volume is on empty space and closes the popover, unless the press was inside a building's
   * region (the gap between its volumes), where it is that building's click. (The scene's pointer
   * events run on the canvas's wrapper, so the order of the two click handlers is not relied on:
   * the press is.)
   */
  private bindCanvasPointer(): void {
    const onMove = (e: PointerEvent) => {
      // A finger has no hover.
      if (e.pointerType === 'touch') return;
      const subject = this.resolveAt(e, this.selection.hovered());
      this.selection.hover(subject);
      this.store.snapshot.gl.domElement.style.cursor = subject ? 'pointer' : '';
    };
    const onLeave = () => {
      this.selection.hover(null);
      this.store.snapshot.gl.domElement.style.cursor = '';
    };
    const onDown = (e: PointerEvent) => {
      this.popover.notePress(
        e.button === 0 ? { x: e.clientX, y: e.clientY, at: e.timeStamp } : null,
      );
      this.pressSubject =
        e.button === 0 ? this.resolveAt(e, this.selection.hovered()) : null;
    };
    const onClick = (e: MouseEvent) => {
      if (this.popover.takeMeshPress()) return;
      const subject = this.pressSubject;
      this.pressSubject = null;
      if (subject) this.clickSubject(subject, e);
      else this.emptyGround(e);
    };
    let bound: HTMLElement | undefined;
    const stop = effect(() => {
      const el = this.store.gl()?.domElement as HTMLElement | undefined;
      if (!el || el === bound) return;
      bound = el;
      el.addEventListener('pointermove', onMove);
      el.addEventListener('pointerleave', onLeave);
      el.addEventListener('pointerdown', onDown);
      el.addEventListener('click', onClick);
    });
    inject(DestroyRef).onDestroy(() => {
      stop.destroy();
      bound?.removeEventListener('pointermove', onMove);
      bound?.removeEventListener('pointerleave', onLeave);
      bound?.removeEventListener('pointerdown', onDown);
      bound?.removeEventListener('click', onClick);
    });
  }

  /**
   * The camera as the frame draws with it, for hit-testing between frames. The scene's own camera
   * is not that between frames: the canvas library resets its aspect to the whole canvas's when
   * its size signal fires, and the frame sets the stage view again only when it renders. The
   * Village's vantage is a pure function of the canvas size, so it is rebuilt here, and what the
   * pointer hit-tests is what is on screen.
   */
  private framedCamera(width: number, height: number): THREE.PerspectiveCamera {
    this.frameCamera(this.pickCamera, width, height);
    return this.pickCamera;
  }

  /** The subject under a pointer event, resolved from its position alone (see `resolveHover`). */
  private resolveAt(
    e: MouseEvent,
    current: VillageSubject | null,
  ): VillageSubject | null {
    const el = this.store.snapshot.gl.domElement;
    const box = el.getBoundingClientRect();
    const regions = volumeRegions(
      this.framedCamera(el.clientWidth, el.clientHeight),
      this.volumes(),
      el.clientWidth,
      el.clientHeight,
    );
    return (
      resolveHover(regions, e.clientX - box.left, e.clientY - box.top, current)
        ?.subject ?? null
    );
  }

  /**
   * A click on a subject. A registered building opens its popover (within the click limits); the
   * scene carries on with the launch controls and the selection for everything else.
   */
  private clickSubject(subject: VillageSubject | null, event: MouseEvent): void {
    if (!subject) {
      this.emptyGround(event);
      return;
    }
    if (this.popover.sceneClick(subject, event)) return;
    if (subject && !this.launch.activate(subject)) {
      this.selection.selectFromScene(subject);
    }
  }

  /**
   * A click on nothing (the ground, the Tower, the sky). An open card closes first; with none
   * open it is the Village closeup's toggle, as a click on empty space is the Tower's.
   */
  private emptyGround(event: MouseEvent): void {
    if (!this.popover.wasClick(event)) return;
    if (this.popover.openId() !== null) {
      this.popover.close();
      return;
    }
    this.zoom.toggleVillageCloseup();
  }

  private subjectAt(instanceId: number | undefined) {
    const volume =
      instanceId === undefined ? undefined : this.solids()[instanceId];
    return volume ? subjectOfVolume(volume.id) : null;
  }

  /** Writes the focus lines into the cube mesh at the thickness for the camera now. */
  private writeFocus(): void {
    this.write(
      this.volumes().filter(isFocusLine),
      this.cubeRef()?.nativeElement,
      true,
    );
  }

  /** Writes volumes into an instanced mesh: the main one, or the focus cube's (`overlay`). */
  private write(
    volumes: Volume[],
    mesh: THREE.InstancedMesh | undefined,
    overlay: boolean,
  ): void {
    if (!mesh?.isInstancedMesh) {
      return;
    }
    const tone = overlay ? this.overlayTone : this.tone;
    const alpha = overlay ? this.overlayAlpha : this.alpha;
    const role = overlay ? this.overlayRole : this.role;
    const n = Math.min(
      volumes.length,
      overlay ? VILLAGE_OVERLAY_CAPACITY : VILLAGE_CAPACITY,
    );
    for (let i = 0; i < n; i++) {
      const v = volumes[i];
      this.matrix.compose(
        this.position.set(v.x, v.y, v.z),
        this.identity,
        overlay ? this.thin(v) : this.scale.set(v.w, v.h, v.d),
      );
      mesh.setMatrixAt(i, this.matrix);
      tone[i] = overlay ? OVERLAY_TONE : v.tone;
      alpha[i] = v.alpha;
      role[i] = 0;
    }
    mesh.count = n;
    mesh.instanceMatrix.needsUpdate = true;
    const attrs = mesh.geometry.attributes;
    for (const name of ['aTone', 'aAlpha', 'aRole']) {
      (attrs[name] as THREE.BufferAttribute).needsUpdate = true;
    }
  }

  /** The scale of a focus line: its two thinnest sides follow the camera's line scale (`lineScale`). */
  private thin(v: Volume): THREE.Vector3 {
    const sides = [v.w, v.h, v.d];
    const longest = sides.indexOf(Math.max(...sides));
    const [w, h, d] = sides.map((s, i) => (i === longest ? s : s * this.lineScale));
    return this.scale.set(w, h, d);
  }

  private readTheme(): boolean {
    return document.documentElement.dataset['theme'] === 'dark';
  }
}

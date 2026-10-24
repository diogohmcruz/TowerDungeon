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
import {
  subjectOfVolume,
  type VillageSubject,
} from '../../three/village/village-subjects';
import { applyStageView } from '../../three/stage-view';
import { StageService } from '../../services/stage.service';
import { fitVillageCamera } from '../../three/village/village-camera';
import { LandscapeGraph } from '../landscape/landscape-graph.component';
import '../../three/catalogue';

/** Instances the Village mesh can hold (about 250 are used at the busiest). */
const VILLAGE_CAPACITY = 512;

/** The Village's own fog: its depth is about 30 units, the Tower fades by alpha. */
const VILLAGE_FOG_DENSITY = 0.01;
/** World y of the Village ground's top face (its box is 0.2 thick, centred on -0.2). */
const VILLAGE_GROUND_TOP = -0.1;

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

  private readonly meshRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('village');
  private readonly volumes = computed<Volume[]>(() =>
    buildVillage(this.village()),
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
    effect(() => {
      this.write(this.volumes());
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

    // A fixed vantage: only the framing follows the canvas aspect.
    // The canvas fills the screen; the Village is framed in the Tower's region of it.
    injectBeforeRender(({ camera }) => {
      const cam = camera as THREE.PerspectiveCamera;
      const el = this.store.snapshot.gl.domElement;
      const w = el.clientWidth;
      const h = el.clientHeight;
      if (applyStageView(cam, w, h, this.stage.narrow())) {
        fitVillageCamera(cam);
      }
      // The open building's popover follows it: report where it is on the canvas.
      const open = this.popover.open();
      this.popover.setAnchor(
        open
          ? subjectRect(cam, this.volumes(), this.popover.openSubject() ?? { kind: 'building', id: open.id }, w, h)
          : null,
      );
    });
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
      else this.popover.emptyClick(e);
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
    const cam = this.pickCamera;
    if (applyStageView(cam, width, height, this.stage.narrow())) {
      fitVillageCamera(cam);
    }
    return cam;
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
    if (this.popover.sceneClick(subject, event)) return;
    if (subject && !this.launch.activate(subject)) {
      this.selection.selectFromScene(subject);
    }
  }

  private subjectAt(instanceId: number | undefined) {
    const volume =
      instanceId === undefined ? undefined : this.volumes()[instanceId];
    return volume ? subjectOfVolume(volume.id) : null;
  }

  private write(volumes: Volume[]): void {
    const mesh = this.meshRef()?.nativeElement;
    if (!mesh?.isInstancedMesh) {
      return;
    }
    const n = Math.min(volumes.length, VILLAGE_CAPACITY);
    for (let i = 0; i < n; i++) {
      const v = volumes[i];
      this.matrix.compose(
        this.position.set(v.x, v.y, v.z),
        this.identity,
        this.scale.set(v.w, v.h, v.d),
      );
      mesh.setMatrixAt(i, this.matrix);
      this.tone[i] = v.tone;
      this.alpha[i] = v.alpha;
      this.role[i] = 0;
    }
    mesh.count = n;
    mesh.instanceMatrix.needsUpdate = true;
    const attrs = mesh.geometry.attributes;
    for (const name of ['aTone', 'aAlpha', 'aRole']) {
      (attrs[name] as THREE.BufferAttribute).needsUpdate = true;
    }
  }

  private readTheme(): boolean {
    return document.documentElement.dataset['theme'] === 'dark';
  }
}

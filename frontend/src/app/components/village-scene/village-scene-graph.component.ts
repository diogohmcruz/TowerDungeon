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
import { VillageSelectionService } from '../../services/village-selection.service';
import { createInkUniforms, setInkTheme } from '../../three/ink/ink-material';
import { INK_FRAGMENT, INK_VERTEX } from '../../three/ink/ink.glsl';
import { InkTheme, RAMPS } from '../../three/ink/palette';
import {
  buildVillage,
  VillageInput,
  Volume,
} from '../../three/village/village-layout';
import { subjectOfVolume } from '../../three/village/village-subjects';
import { applyStageView } from '../../three/stage-view';
import { StageService } from '../../services/stage.service';
import { fitVillageCamera } from '../../three/village/village-camera';
import '../../three/catalogue';

/** Instances the Village mesh can hold (about 250 are used at the busiest). */
const VILLAGE_CAPACITY = 512;

/** The Village's own fog: its depth is about 30 units, the Tower fades by alpha. */
const VILLAGE_FOG_DENSITY = 0.01;

/**
 * The Village Elevation: one instanced ink mesh, no lights, a fixed vantage.
 * Every volume is a box (architecture doc section 5.2).
 */
@Component({
  selector: 'app-village-scene-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [NgtArgs],
  template: `
    <ngt-color *args="[clearHex()]" attach="background" />
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
  private readonly stage = inject(StageService);
  private readonly dark = signal(this.readTheme());

  protected readonly theme = computed<InkTheme>(() =>
    this.dark() ? 'night' : 'day',
  );
  protected readonly clearHex = computed(() => RAMPS[this.theme()][0]);

  protected readonly CAPACITY = VILLAGE_CAPACITY;
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
    injectObjectEvents(() => this.meshRef()?.nativeElement, {
      pointermove: (event) => {
        event.stopPropagation();
        const subject = this.subjectAt(event.instanceId);
        this.selection.hover(subject);
        this.store.snapshot.gl.domElement.style.cursor = subject
          ? 'pointer'
          : '';
      },
      pointerout: () => {
        this.selection.hover(null);
        this.store.snapshot.gl.domElement.style.cursor = '';
      },
      click: (event) => {
        event.stopPropagation();
        const subject = this.subjectAt(event.instanceId);
        if (subject && !this.launch.activate(subject)) {
          this.selection.selectFromScene(subject);
        }
      },
    });

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
    });
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

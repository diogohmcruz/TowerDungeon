import {
  ChangeDetectionStrategy,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  computed,
  effect,
  input,
  viewChild,
  ElementRef,
} from '@angular/core';
import { injectBeforeRender, injectStore, NgtArgs } from 'angular-three';
import * as THREE from 'three';

import type { Quarter } from '../../interfaces/game-state';
import {
  createInkUniforms,
  InkUniforms,
  setInkTheme,
} from '../../three/ink/ink-material';
import { InkTheme } from '../../three/ink/palette';
import {
  LANDSCAPE_CAPACITY,
  LandscapeVolume,
  planLandscape,
  PLAIN_NEAR,
} from '../../three/landscape/landscape-plan';
import {
  LANDSCAPE_FRAGMENT,
  LANDSCAPE_VERTEX,
} from '../../three/landscape/landscape.glsl';
import '../../three/catalogue';

/** True when the player asked the system for less motion: the landscape holds still. */
function prefersStill(): boolean {
  return (
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * The ambient landscape to the left of the Tower (architecture doc 5.5): one
 * instanced ink mesh, one draw call, no lights. It is drawn behind the menus
 * and shows nothing but the Quarter's weather. Both scenes put it in.
 */
@Component({
  selector: 'app-landscape-graph',
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [NgtArgs],
  template: `
    <ngt-instanced-mesh
      #landscape
      *args="[undefined, undefined, CAPACITY]"
      [position]="[0, groundY(), 0]"
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
        <ngt-instanced-buffer-attribute
          attach="attributes.aMove"
          *args="[move, 3]"
        />
      </ngt-box-geometry>
      <ngt-shader-material
        [vertexShader]="vertex"
        [fragmentShader]="fragment"
        [uniforms]="uniforms"
      />
    </ngt-instanced-mesh>
  `,
})
export class LandscapeGraph {
  /** The Quarter's weather; null in a Trial (no weather marks). */
  quarter = input<Quarter | null>(null);
  theme = input<InkTheme>('night');
  /** World y of the ground top the landscape joins. */
  groundY = input(0);
  /** Exponential-squared fog density of the scene it sits in. */
  fogDensity = input(0.01);
  /** How far toward the camera the plain reaches (the Tower's camera is closer than the Village's). */
  nearZ = input(PLAIN_NEAR);
  /** Whether the Tower seen from afar ends in its crown here (the Village view; the Tower scene draws the Tower itself). */
  crown = input(true);
  /**
   * Seconds to draw the drift at, when the host owns the clock (the Village's
   * 4 Hz tick). Null: the landscape advances its own clock, unless motion is reduced.
   */
  time = input<number | null>(null);

  private readonly store = injectStore();
  private readonly still = prefersStill();
  private clock = 0;

  protected readonly CAPACITY = LANDSCAPE_CAPACITY;
  protected readonly vertex = LANDSCAPE_VERTEX;
  protected readonly fragment = LANDSCAPE_FRAGMENT;
  protected readonly uniforms: InkUniforms & { uTime: THREE.IUniform<number> } =
    {
      ...createInkUniforms('night'),
      uTime: { value: 0 },
    };
  protected readonly tone = new Float32Array(LANDSCAPE_CAPACITY);
  protected readonly alpha = new Float32Array(LANDSCAPE_CAPACITY);
  protected readonly role = new Float32Array(LANDSCAPE_CAPACITY);
  protected readonly move = new Float32Array(LANDSCAPE_CAPACITY * 3);

  private readonly meshRef =
    viewChild<ElementRef<THREE.InstancedMesh>>('landscape');
  private readonly volumes = computed<LandscapeVolume[]>(() =>
    planLandscape(this.quarter(), this.nearZ(), this.crown()),
  );
  private readonly matrix = new THREE.Matrix4();
  private readonly position = new THREE.Vector3();
  private readonly scale = new THREE.Vector3();
  private readonly identity = new THREE.Quaternion();

  constructor() {
    effect(() => {
      setInkTheme(this.uniforms, this.theme());
      this.uniforms.uFogDensity.value = this.fogDensity();
      this.store.snapshot.invalidate();
    });
    effect(() => {
      this.write(this.volumes());
      this.store.snapshot.invalidate();
    });
    effect(() => {
      const t = this.time();
      if (t !== null) {
        this.uniforms.uTime.value = t;
        this.store.snapshot.invalidate();
      }
    });

    // Without a host clock the drift follows the frames, and holds still when motion is reduced.
    injectBeforeRender(({ delta }) => {
      if (this.time() !== null || this.still) {
        return;
      }
      this.clock += delta;
      this.uniforms.uTime.value = this.clock;
    });
  }

  private write(volumes: LandscapeVolume[]): void {
    const mesh = this.meshRef()?.nativeElement;
    if (!mesh?.isInstancedMesh) {
      return;
    }
    const n = Math.min(volumes.length, LANDSCAPE_CAPACITY);
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
      this.move[i * 3] = v.sway;
      this.move[i * 3 + 1] = v.fall;
      this.move[i * 3 + 2] = v.phase;
    }
    mesh.count = n;
    mesh.instanceMatrix.needsUpdate = true;
    const attrs = mesh.geometry.attributes;
    for (const name of ['aTone', 'aAlpha', 'aRole', 'aMove']) {
      (attrs[name] as THREE.BufferAttribute).needsUpdate = true;
    }
  }
}

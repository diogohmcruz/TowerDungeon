import * as THREE from 'three';

import { INK_FRAGMENT, INK_VERTEX } from './ink.glsl';
import { GUARDIAN_CRIMSON, inkColor, InkTheme, RAMPS } from './palette';

export interface InkUniforms {
  [uniform: string]: THREE.IUniform;
  uRamp: { value: THREE.Color[] };
  uCrimson: { value: THREE.Color };
  uCell: { value: number };
  uAmount: { value: number };
  uFogDensity: { value: number };
}

/** Exponential-squared fog density per theme (the values the lit scene used). */
const FOG_DENSITY: Record<InkTheme, number> = { night: 0.05, day: 0.028 };

/** 0 flat bands to 1 full dither; `prefers-contrast: more` drops the dither. */
export function inkAmount(): number {
  const more =
    typeof matchMedia === 'function' &&
    matchMedia('(prefers-contrast: more)').matches;
  return more ? 0 : 1;
}

/**
 * One uniform set shared by every ink material, so a theme change is a single
 * update. `uCell` is the Bayer cell in drawing-buffer pixels: the buffer is
 * already at virtual resolution (dpr = 1 / PIXEL_SCALE), so it is 1.
 */
export function createInkUniforms(theme: InkTheme): InkUniforms {
  const uniforms: InkUniforms = {
    uRamp: { value: RAMPS[theme].map(inkColor) },
    uCrimson: { value: inkColor(GUARDIAN_CRIMSON) },
    uCell: { value: 1 },
    uAmount: { value: inkAmount() },
    uFogDensity: { value: FOG_DENSITY[theme] },
  };
  return uniforms;
}

/** Swaps the ramp and fog for a theme in place (no new material, no recompile). */
export function setInkTheme(uniforms: InkUniforms, theme: InkTheme): void {
  RAMPS[theme].forEach((hex, i) => uniforms.uRamp.value[i].set(hex));
  uniforms.uFogDensity.value = FOG_DENSITY[theme];
}

/**
 * The ink material for instanced meshes. Declarative scenes can use the same
 * program through `<ngt-shader-material>` with `INK_VERTEX`, `INK_FRAGMENT` and
 * the shared uniforms.
 */
export function createInkMaterial(uniforms: InkUniforms): THREE.ShaderMaterial {
  return new THREE.ShaderMaterial({
    vertexShader: INK_VERTEX,
    fragmentShader: INK_FRAGMENT,
    uniforms,
  });
}

/** Per-instance tone presets for the slab boxes (ink density 0..1). */
export const TONE = {
  slab: 0.62,
  base: 0.7,
  deck: 0.85,
  deckCurrent: 1,
  ghost: 0.5,
  backdrop: 0.9,
} as const;

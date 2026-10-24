import { INK_FRAGMENT } from '../ink/ink.glsl';

/**
 * The landscape's vertex shader: the ink vertex shader plus the moving parts.
 * `aMove` is (sway, fall, phase) per instance. Sway slides a box sideways on a
 * slow sine (haze); fall carries it down a span and wraps it (snowfall). With
 * `uTime` held still, nothing moves: that is how `prefers-reduced-motion` freezes it.
 */
export const LANDSCAPE_VERTEX = /* glsl */ `
attribute float aTone;
attribute float aAlpha;
attribute float aRole;
attribute vec3 aMove;

uniform float uTime;

varying float vTone;
varying float vAlpha;
varying float vRole;
varying float vFace;
varying float vDepth;

float faceShade(vec3 n) {
  if (n.y > 0.5) return 1.0;
  if (n.z > 0.5) return 0.72;
  if (abs(n.x) > 0.5) return 0.5;
  return 0.4;
}

void main() {
  mat4 m = modelMatrix * instanceMatrix;
  vec3 n = normalize(mat3(m) * normal);
  float sway = aMove.x;
  float fall = aMove.y;
  float phase = aMove.z;
  vec3 drift = vec3(0.0);
  drift.x = sway * sin(uTime * 0.12 + phase * 6.2831853);
  if (fall > 0.0) {
    drift.y = fall - mod(uTime * 0.35 + phase * fall, fall);
    drift.x += sway * sin(uTime * 0.7 + phase * 20.0);
  }
  vec4 viewPos = viewMatrix * (m * vec4(position, 1.0) + vec4(drift, 0.0));
  vTone = aTone;
  vAlpha = aAlpha;
  vRole = aRole;
  vFace = faceShade(n);
  vDepth = -viewPos.z;
  gl_Position = projectionMatrix * viewPos;
}
`;

/** The same ink fragment as everything else: ramp, dither and fog, no new colour. */
export const LANDSCAPE_FRAGMENT = INK_FRAGMENT;

import { INK_DITHER } from '../ink/ink.glsl';

/**
 * Figure shader: instanced camera-facing quads that sample a ramp-slot texel
 * from the silhouette atlas, then resolve it through the shared fog and Bayer
 * dither, so figures are ink like everything else (ADR 0003, no lights).
 * The instance matrix carries the centre and the width and height.
 *   aCell = (atlas column, atlas row, role 0 ramp | 1 Guardian crimson, tone 0..1)
 */
export const FIGURE_VERTEX = /* glsl */ `
attribute vec4 aCell;

varying vec2 vUv;
varying vec4 vCell;
varying float vDepth;

void main() {
  vUv = uv;
  vCell = aCell;
  mat4 m = modelMatrix * instanceMatrix;
  vec2 size = vec2(length(m[0].xyz), length(m[1].xyz));
  vec4 viewPos = viewMatrix * vec4(m[3].xyz, 1.0);
  viewPos.xy += position.xy * size;
  vDepth = -viewPos.z;
  gl_Position = projectionMatrix * viewPos;
}
`;

export const FIGURE_FRAGMENT = /* glsl */ `
uniform sampler2D uFigures;
uniform vec2 uGrid;
uniform vec2 uCellPx;
uniform float uTexelStep;
uniform vec3 uRamp[4];
uniform vec3 uCrimson;
uniform float uCell;
uniform float uAmount;
uniform float uFogDensity;
uniform float uFogNear;

varying vec2 vUv;
varying vec4 vCell;
varying float vDepth;

${INK_DITHER}
void main() {
  vec2 px = min(floor(vec2(vUv.x, 1.0 - vUv.y) * uCellPx), uCellPx - 1.0);
  vec2 uv = (vCell.xy * uCellPx + px + 0.5) / (uGrid * uCellPx);
  float v = floor(texture2D(uFigures, uv).r * 255.0 / uTexelStep + 0.5);
  if (v < 0.5) discard;

  float threshold = mix(0.5, bayer4(gl_FragCoord.xy / uCell), uAmount);
  // Value 4 is the plate: the clear colour, so a numeral sits on a patch of void.
  float level = v > 3.5 ? 0.0 : clamp(v * vCell.w * inkFog(max(vDepth - uFogNear, 0.0)), 0.0, 3.0);
  float base = floor(level);
  int idx = int(base + step(threshold, level - base));

  vec3 color = uRamp[idx];
  // A Guardian's lines and fill are crimson; it is also larger, never colour alone.
  if (vCell.z > 0.5 && idx >= 1) color = uCrimson;
  gl_FragColor = vec4(color, 1.0);
  #include <colorspace_fragment>
}
`;

/** Texels the outline quad extends beyond the sprite on each side. */
export const OUTLINE_PAD = 1;

/**
 * The outline of the figure a unit view is on: a quad `OUTLINE_PAD` (1) texel larger
 * on each side than the sprite, which draws the ramp's line colour (bone) on
 * every empty texel beside the figure, diagonals included. The sprite itself draws over the rest.
 * Same vertex shader and uniforms as the figures.
 */
export const OUTLINE_FRAGMENT = /* glsl */ `
#define OUTLINE_PAD 1.0
uniform sampler2D uFigures;
uniform vec2 uGrid;
uniform vec2 uCellPx;
uniform float uTexelStep;
uniform vec3 uRamp[4];

varying vec2 vUv;
varying vec4 vCell;

float inked(vec2 p) {
  if (p.x < 0.0 || p.y < 0.0 || p.x >= uCellPx.x || p.y >= uCellPx.y) return 0.0;
  vec2 uv = (vCell.xy * uCellPx + p + 0.5) / (uGrid * uCellPx);
  return floor(texture2D(uFigures, uv).r * 255.0 / uTexelStep + 0.5) > 0.5 ? 1.0 : 0.0;
}

void main() {
  vec2 px = floor(vec2(vUv.x, 1.0 - vUv.y) * (uCellPx + 2.0 * OUTLINE_PAD)) - OUTLINE_PAD;
  if (inked(px) > 0.5) discard;
  float near = 0.0;
  for (int dy = -1; dy <= 1; dy++) {
    for (int dx = -1; dx <= 1; dx++) {
      near += inked(px + vec2(float(dx), float(dy)));
    }
  }
  if (near < 0.5) discard;
  gl_FragColor = vec4(uRamp[3], 1.0);
  #include <colorspace_fragment>
}
`;

/**
 * The outline of the figure under the pointer: the same ring as the chosen unit's, but
 * dashed, so a hover reads as "this one" and not as "chosen". No animation, no glow.
 */
export const HOVER_OUTLINE_FRAGMENT = /* glsl */ `
#define OUTLINE_PAD 1.0
uniform sampler2D uFigures;
uniform vec2 uGrid;
uniform vec2 uCellPx;
uniform float uTexelStep;
uniform vec3 uRamp[4];

varying vec2 vUv;
varying vec4 vCell;

float inked(vec2 p) {
  if (p.x < 0.0 || p.y < 0.0 || p.x >= uCellPx.x || p.y >= uCellPx.y) return 0.0;
  vec2 uv = (vCell.xy * uCellPx + p + 0.5) / (uGrid * uCellPx);
  return floor(texture2D(uFigures, uv).r * 255.0 / uTexelStep + 0.5) > 0.5 ? 1.0 : 0.0;
}

void main() {
  vec2 px = floor(vec2(vUv.x, 1.0 - vUv.y) * (uCellPx + 2.0 * OUTLINE_PAD)) - OUTLINE_PAD;
  if (inked(px) > 0.5) discard;
  float near = 0.0;
  for (int dy = -1; dy <= 1; dy++) {
    for (int dx = -1; dx <= 1; dx++) {
      near += inked(px + vec2(float(dx), float(dy)));
    }
  }
  if (near < 0.5) discard;
  // Dashed: two texels drawn, two left out, along the diagonal of the grid. The chosen unit's
  // outline is solid, so the two differ in line, not only in colour.
  if (mod(floor((px.x + px.y) / 2.0), 2.0) > 0.5) discard;
  gl_FragColor = vec4(uRamp[3], 1.0);
  #include <colorspace_fragment>
}
`;

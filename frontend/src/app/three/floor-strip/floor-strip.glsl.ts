import { INK_DITHER } from '../ink/ink.glsl';

/**
 * Strip shader: samples a ramp-slot texel from the atlas cell named by the
 * instance, then resolves it through the same fog and Bayer dither as the other
 * ink materials. Layers use `discard` (no sorting). Per-instance attributes:
 *   aCell  = (atlas column, atlas row, layer 0..2, dithered alpha 0..1)
 *   aState = (selected 0|1, seal 0 none | 1 Guardian | 2 slain, tone 0..1, unused)
 * The seal ring and the thick outline of the current Floor are drawn here, on
 * the lip layer, so they cost no atlas cell and no extra draw call.
 */
export const STRIP_VERTEX = /* glsl */ `
attribute vec4 aCell;
attribute vec4 aState;

varying vec2 vUv;
varying vec4 vCell;
varying vec4 vState;
varying float vDepth;

void main() {
  vUv = uv;
  vCell = aCell;
  vState = aState;
  vec4 viewPos = viewMatrix * (modelMatrix * instanceMatrix * vec4(position, 1.0));
  vDepth = -viewPos.z;
  gl_Position = projectionMatrix * viewPos;
}
`;

export const STRIP_FRAGMENT = /* glsl */ `
uniform sampler2D uAtlas;
uniform vec2 uGrid;
uniform vec2 uCellPx;
uniform vec2 uSeal;
uniform vec3 uRamp[4];
uniform vec3 uCrimson;
uniform float uCell;
uniform float uAmount;
uniform float uFogDensity;
uniform float uFogNear;

varying vec2 vUv;
varying vec4 vCell;
varying vec4 vState;
varying float vDepth;

${INK_DITHER}
void main() {
  vec2 px = min(floor(vec2(vUv.x, 1.0 - vUv.y) * uCellPx), uCellPx - 1.0);
  vec2 uv = (vCell.xy * uCellPx + px + 0.5) / (uGrid * uCellPx);
  float v = floor(texture2D(uAtlas, uv).r * 3.0 + 0.5);
  float layer = vCell.z;
  float selected = vState.x;
  float seal = vState.y;

  bool ring = false;
  bool outline = false;
  if (layer > 1.5) {
    if (seal > 0.5) {
      float d = length(px + 0.5 - uSeal);
      ring = abs(d - 10.0) < 0.9;
    }
    if (selected > 0.5) {
      outline = px.x < 2.0 || px.x > uCellPx.x - 3.0 || px.y < 2.0 || px.y > uCellPx.y - 3.0;
    }
  }
  if (v < 0.5 && !ring && !outline) discard;

  // uAmount 0 gives flat bands (threshold 0.5), 1 full dither.
  float threshold = mix(0.5, bayer4(gl_FragCoord.xy / uCell), uAmount);
  if (vCell.w < threshold) discard;

  if (outline) v = 3.0;
  // Fog counts from the focal distance, so the near layers keep their full ink.
  float level = clamp(v * vState.z * inkFog(max(vDepth - uFogNear, 0.0)), 0.0, 3.0);
  float base = floor(level);
  int idx = int(base + step(threshold, level - base));

  vec3 color = uRamp[idx];
  // A Guardian floor draws its lines and detail in crimson; the ring is crimson
  // whatever the fog does to it.
  if (seal > 0.5 && layer > 0.5 && idx >= 2 && !outline) color = uCrimson;
  if (ring) color = uCrimson;
  gl_FragColor = vec4(color, 1.0);
  #include <colorspace_fragment>
}
`;

/**
 * Numeral shader: one quad per digit from a 10-cell strip of 7 x 9 texels
 * (0 clear, 1 plate, 3 glyph). A struck numeral crosses the glyph in crimson.
 *   aDigit = (digit 0..9, struck 0|1)
 */
export const NUMERAL_VERTEX = /* glsl */ `
attribute vec2 aDigit;

varying vec2 vUv;
varying vec2 vDigit;
varying float vDepth;

void main() {
  vUv = uv;
  vDigit = aDigit;
  vec4 viewPos = viewMatrix * (modelMatrix * instanceMatrix * vec4(position, 1.0));
  vDepth = -viewPos.z;
  gl_Position = projectionMatrix * viewPos;
}
`;

export const NUMERAL_FRAGMENT = /* glsl */ `
uniform sampler2D uDigits;
uniform vec2 uGlyphPx;
uniform float uDigitCount;
uniform vec3 uRamp[4];
uniform vec3 uCrimson;
uniform float uCell;
uniform float uAmount;
uniform float uFogDensity;
uniform float uFogNear;

varying vec2 vUv;
varying vec2 vDigit;
varying float vDepth;

${INK_DITHER}
void main() {
  vec2 px = min(floor(vec2(vUv.x, 1.0 - vUv.y) * uGlyphPx), uGlyphPx - 1.0);
  vec2 uv = vec2((vDigit.x * uGlyphPx.x + px.x + 0.5) / (uDigitCount * uGlyphPx.x),
                 (px.y + 0.5) / uGlyphPx.y);
  float v = floor(texture2D(uDigits, uv).r * 3.0 + 0.5);
  bool strike = vDigit.y > 0.5 && px.y == 4.0;
  if (strike) v = 3.0;
  if (v < 0.5) discard;

  float threshold = mix(0.5, bayer4(gl_FragCoord.xy / uCell), uAmount);
  // The plate is the clear colour, so the numeral sits on a patch of void.
  float level = v < 1.5 ? 0.0 : clamp(v * inkFog(max(vDepth - uFogNear, 0.0)), 0.0, 3.0);
  float base = floor(level);
  int idx = int(base + step(threshold, level - base));

  vec3 color = uRamp[idx];
  if (strike) color = uCrimson;
  gl_FragColor = vec4(color, 1.0);
  #include <colorspace_fragment>
}
`;

/**
 * Ink shader chunks (ADR 0003: the scene is drawn, not lit). No lights: shade
 * comes from a per-instance tone, a fixed face shade and distance fog, then an
 * ordered 4x4 Bayer dither picks one of four ramp slots per pixel.
 *
 * Per-instance attributes (instanced meshes only): `aTone` ink density 0..1,
 * `aAlpha` dithered translucency 0..1, `aRole` 0 ramp or 1 Guardian crimson.
 */
export const INK_VERTEX = /* glsl */ `
attribute float aTone;
attribute float aAlpha;
attribute float aRole;

varying float vTone;
varying float vAlpha;
varying float vRole;
varying float vFace;
varying float vDepth;

// Fixed face shade: tops catch the most ink, fronts less, sides least.
float faceShade(vec3 n) {
  if (n.y > 0.5) return 1.0;
  if (n.z > 0.5) return 0.72;
  if (abs(n.x) > 0.5) return 0.5;
  return 0.4;
}

void main() {
  mat4 m = modelMatrix * instanceMatrix;
  vec3 n = normalize(mat3(m) * normal);
  vec4 viewPos = viewMatrix * (m * vec4(position, 1.0));
  vTone = aTone;
  vAlpha = aAlpha;
  vRole = aRole;
  vFace = faceShade(n);
  vDepth = -viewPos.z;
  gl_Position = projectionMatrix * viewPos;
}
`;

export const INK_FRAGMENT = /* glsl */ `
uniform vec3 uRamp[4];
uniform vec3 uCrimson;
uniform float uCell;
uniform float uAmount;
uniform float uFogDensity;

varying float vTone;
varying float vAlpha;
varying float vRole;
varying float vFace;
varying float vDepth;

const float BAYER[16] = float[16](
   0.0,  8.0,  2.0, 10.0,
  12.0,  4.0, 14.0,  6.0,
   3.0, 11.0,  1.0,  9.0,
  15.0,  7.0, 13.0,  5.0
);

// Ordered 4x4 Bayer threshold in (0,1) for a virtual-pixel coordinate.
float bayer4(vec2 p) {
  ivec2 i = ivec2(mod(floor(p), 4.0));
  return (BAYER[i.y * 4 + i.x] + 0.5) / 16.0;
}

// Fog as lost ink: the level falls to 0 (ramp slot 0) with distance.
float inkFog(float depth) {
  float d = depth * uFogDensity;
  return exp(-d * d);
}

void main() {
  // uAmount 0 gives flat bands (threshold 0.5), 1 full dither.
  float threshold = mix(0.5, bayer4(gl_FragCoord.xy / uCell), uAmount);
  if (vAlpha < threshold) discard;

  float level = clamp(vTone * vFace * inkFog(vDepth), 0.0, 1.0) * 3.0;
  float base = floor(level);
  int idx = int(base + step(threshold, level - base));

  vec3 color = uRamp[idx];
  if (vRole > 0.5) {
    color = idx < 1 ? uRamp[0] : (idx < 2 ? uRamp[1] : uCrimson);
  }
  gl_FragColor = vec4(color, 1.0);
  #include <colorspace_fragment>
}
`;

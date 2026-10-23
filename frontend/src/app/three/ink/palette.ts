import * as THREE from 'three';

export type InkTheme = 'night' | 'day';

/** Four ramp slots as sRGB hex: 0 clear/fog/background, 1 shadow, 2 detail, 3 line. */
export type InkRamp = readonly [string, string, string, string];

/**
 * Today's scene background, kept only so a unit test can pin that colour
 * management round-trips it. Generation 1's slot 0 is darker (ADR 0004).
 */
export const LEGACY_INK = '#0e1424';

/** Guardian crimson: a role colour (fill and line only), never a ramp slot. */
export const GUARDIAN_CRIMSON = '#7a2230';

/**
 * Generation 1 ("Cold Stone") ramps from `docs/brainstorm/palette-per-generation.md`
 * section 2. Slot 0 equals fog, clear colour and scene background. By day slot 0
 * is the overcast ground and slot 3 the ink (the night slot 0).
 */
export const RAMPS: Record<InkTheme, InkRamp> = {
  night: ['#080923', '#232449', '#667082', '#b5bfcf'],
  day: ['#b9bcc0', '#9499a0', '#686e78', '#080923'],
};

/**
 * `Color` for a hex string. Three's colour management converts the sRGB hex to
 * linear working space, which is what shader uniforms expect; the fragment
 * shader converts back through `colorspace_fragment`.
 */
export function inkColor(hex: string): THREE.Color {
  return new THREE.Color(hex);
}

/** The sRGB hex (`#rrggbb`) of a working-space colour: the inverse of `inkColor`. */
export function toSrgbHex(color: THREE.Color): string {
  return `#${color.getHexString()}`;
}

/**
 * The clear and fog colour for a theme: ramp slot 0, so fog resolves into the
 * background exactly.
 */
export function clearColor(theme: InkTheme): THREE.Color {
  return inkColor(RAMPS[theme][0]);
}

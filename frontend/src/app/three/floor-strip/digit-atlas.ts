import * as THREE from 'three';

/**
 * 5 x 7 pixel digits for the Floor numerals. Each cell is 7 x 9: the glyph with
 * a one-texel plate around it, so the numeral reads as stencilled on the pillar
 * whatever line work lies behind. Texel values: 0 clear, 1 plate, 3 glyph.
 */
export const DIGIT_W = 7;
export const DIGIT_H = 9;
/** Row of the glyph a struck numeral is crossed through. */
export const STRIKE_ROW = 4;

const FONT: readonly (readonly string[])[] = [
  ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
  ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
  ['01110', '10001', '00001', '00010', '00100', '01000', '11111'],
  ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
  ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
  ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
  ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
  ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
  ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
  ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
];

/** The strip of ten cells, ramp slots (row 0 first). */
export function buildDigitSlots(): Uint8Array {
  const w = DIGIT_W * FONT.length;
  const data = new Uint8Array(w * DIGIT_H).fill(1);
  FONT.forEach((glyph, d) => {
    glyph.forEach((row, y) => {
      for (let x = 0; x < 5; x++) {
        if (row[x] === '1') data[(y + 1) * w + d * DIGIT_W + x + 1] = 3;
      }
    });
  });
  return data;
}

export function createDigitTexture(): THREE.DataTexture {
  const slots = buildDigitSlots();
  const bytes = new Uint8Array(slots.length);
  for (let i = 0; i < slots.length; i++) bytes[i] = slots[i] * 85;
  const tex = new THREE.DataTexture(
    bytes,
    DIGIT_W * FONT.length,
    DIGIT_H,
    THREE.RedFormat,
    THREE.UnsignedByteType,
  );
  tex.minFilter = THREE.NearestFilter;
  tex.magFilter = THREE.NearestFilter;
  tex.generateMipmaps = false;
  tex.colorSpace = THREE.NoColorSpace;
  tex.unpackAlignment = 1;
  tex.needsUpdate = true;
  return tex;
}

export const DIGIT_COUNT = FONT.length;

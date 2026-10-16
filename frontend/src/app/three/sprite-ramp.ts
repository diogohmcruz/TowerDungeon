export type Rgb = readonly [number, number, number];
/** Ramp slots 1 to 3 (line, detail, fill) as RGB. */
export type Ramp = readonly [Rgb, Rgb, Rgb];

/**
 * Today's palette for index PNGs (public/sprites/README.md). Units are stone
 * with one amber pixel at the head (slot 3); enemies are bone (slot 3) with a
 * dim rim (slot 1). Per-Generation ramps arrive with #15 and #18.
 */
export const RAMPS: Record<'unit' | 'enemy', Ramp> = {
  unit: [
    [90, 98, 117],
    [164, 170, 184],
    [232, 169, 60],
  ],
  enemy: [
    [125, 114, 96],
    [58, 51, 40],
    [230, 220, 195],
  ],
};

/** Recolours RGBA index-PNG pixels in place: grey 85 / 170 / 255 become ramp slots 1 to 3. */
export function mapRamp(data: Uint8ClampedArray, ramp: Ramp): void {
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] === 0) {
      continue;
    }
    const grey = data[i];
    const [r, g, b] = grey < 128 ? ramp[0] : grey < 213 ? ramp[1] : ramp[2];
    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
  }
}

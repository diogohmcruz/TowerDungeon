import * as THREE from 'three';

import { createInkUniforms, setInkTheme } from './ink-material';
import {
  clearColor,
  GUARDIAN_CRIMSON,
  inkColor,
  LEGACY_INK,
  RAMPS,
  toSrgbHex,
} from './palette';

describe('ink palette', () => {
  it('round-trips LEGACY_INK and Guardian crimson through colour management', () => {
    expect(toSrgbHex(inkColor(LEGACY_INK))).toBe('#0e1424');
    expect(toSrgbHex(inkColor(GUARDIAN_CRIMSON))).toBe('#7a2230');
  });

  it('really converts to linear: a colour is not its sRGB bytes', () => {
    expect(THREE.ColorManagement.enabled).toBeTrue();
    const linear = inkColor(LEGACY_INK);
    expect(linear.r).toBeLessThan(0x0e / 255 / 4);
    expect(linear.b).toBeLessThan(0x24 / 255);
  });

  it('pins the Generation 1 ramps (ADR 0004, palette-per-generation section 2)', () => {
    expect(RAMPS.night).toEqual(['#080923', '#232449', '#667082', '#b5bfcf']);
    expect(RAMPS.day).toEqual(['#b9bcc0', '#9499a0', '#686e78', '#080923']);
  });

  it('round-trips every ramp slot in both themes', () => {
    for (const ramp of Object.values(RAMPS)) {
      for (const hex of ramp) {
        expect(toSrgbHex(inkColor(hex))).toBe(hex);
      }
    }
  });

  it('keeps the day ink equal to the night void, so slot 3 by day is slot 0 by night', () => {
    expect(RAMPS.day[3]).toBe(RAMPS.night[0]);
  });

  it('makes the clear colour equal ramp slot 0', () => {
    expect(toSrgbHex(clearColor('night'))).toBe(RAMPS.night[0]);
    expect(toSrgbHex(clearColor('day'))).toBe(RAMPS.day[0]);
  });

  it('is darker at night than the legacy ink (the Generation 1 decision)', () => {
    const luma = (hex: string) => inkColor(hex).getHSL({} as THREE.HSL).l;
    expect(luma(RAMPS.night[0])).toBeLessThan(luma(LEGACY_INK));
  });
});

describe('ink uniforms', () => {
  it('holds the theme ramp as linear colours that convert back to the hex', () => {
    const u = createInkUniforms('night');
    expect(u.uRamp.value.map(toSrgbHex)).toEqual([...RAMPS.night]);
    expect(toSrgbHex(u.uCrimson.value)).toBe(GUARDIAN_CRIMSON);
    expect(u.uCell.value).toBe(1);
  });

  it('swaps theme in place without replacing the colour objects', () => {
    const u = createInkUniforms('night');
    const slot0 = u.uRamp.value[0];
    const nightFog = u.uFogDensity.value;
    setInkTheme(u, 'day');
    expect(u.uRamp.value[0]).toBe(slot0);
    expect(u.uRamp.value.map(toSrgbHex)).toEqual([...RAMPS.day]);
    expect(u.uFogDensity.value).not.toBe(nightFog);
  });
});

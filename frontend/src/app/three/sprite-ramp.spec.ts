import { mapRamp, RAMPS } from './sprite-ramp';

function px(grey: number, alpha = 255): Uint8ClampedArray {
  return new Uint8ClampedArray([grey, grey, grey, alpha]);
}

describe('mapRamp', () => {
  it('maps the three index greys to the kind ramp slots', () => {
    for (const kind of ['unit', 'enemy'] as const) {
      const ramp = RAMPS[kind];
      [85, 170, 255].forEach((grey, i) => {
        const data = px(grey);
        mapRamp(data, ramp);
        expect(Array.from(data)).toEqual([...ramp[i], 255]);
      });
    }
  });

  it('leaves transparent pixels transparent', () => {
    const data = px(0, 0);
    mapRamp(data, RAMPS.unit);
    expect(data[3]).toBe(0);
  });

  it('gives units an amber head slot and enemies a bone fill', () => {
    const [, , unitFill] = RAMPS.unit;
    const [, , enemyFill] = RAMPS.enemy;
    expect(unitFill[0]).toBeGreaterThan(unitFill[2]); // amber: red over blue
    expect(enemyFill[0]).toBeGreaterThan(200); // bone: light
  });
});

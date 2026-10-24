import { dampFactor, SHOTS, ShotBlend } from './camera-shots';

describe('camera shots', () => {
  it('starts on the battle shot and has a closeup that fills the screen', () => {
    expect(new ShotBlend().current).toEqual(SHOTS.battle);
    expect(SHOTS.battle.spread).toBe(0);
    expect(SHOTS.closeup.spread).toBe(1);
  });

  it('has a unit shot that is closer than the closeup and fills the screen', () => {
    expect(SHOTS.unit.fov).toBeLessThan(SHOTS.closeup.fov);
    expect(SHOTS.unit.distance).toBeLessThan(SHOTS.closeup.distance);
    expect(SHOTS.unit.spread).toBe(1);
  });

  it('eases toward the shot without ever passing it', () => {
    const blend = new ShotBlend();
    const frames: number[] = [];
    for (let i = 0; i < 400; i++) {
      blend.step(SHOTS.closeup, dampFactor(1 / 60));
      frames.push(blend.current.fov);
    }
    // fov falls from 64 to 42: monotone, never below the target.
    for (let i = 1; i < frames.length; i++) {
      expect(frames[i]).toBeLessThanOrEqual(frames[i - 1]);
      expect(frames[i]).toBeGreaterThanOrEqual(SHOTS.closeup.fov);
    }
    expect(blend.current).toEqual(SHOTS.closeup);
  });

  it('does not overshoot on a long frame either', () => {
    const blend = new ShotBlend();
    blend.step(SHOTS.closeup, dampFactor(30));
    expect(blend.current.distance).toBeGreaterThanOrEqual(
      SHOTS.closeup.distance,
    );
  });

  it('cuts instantly with a damp of 1 (reduced motion)', () => {
    const blend = new ShotBlend();
    expect(blend.step(SHOTS.closeup, 1)).toBeTrue();
    expect(blend.current).toEqual(SHOTS.closeup);
    expect(blend.step(SHOTS.battle, 1)).toBeTrue();
    expect(blend.current).toEqual(SHOTS.battle);
    expect(blend.step(SHOTS.battle, 1)).toBeFalse();
  });
});

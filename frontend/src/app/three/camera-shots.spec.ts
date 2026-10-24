import { dampFactor, fitDistance, SHOTS, ShotBlend } from './camera-shots';
import { TOWER_LAUNCH_Z, TOWER_RECALL_X } from './launch/launch-objects';

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

  describe('fitDistance', () => {
    const reach = TOWER_RECALL_X + 0.6;
    const { fov, distance } = SHOTS.closeup;
    /** The half width the closeup shows at the lamp's depth. */
    const shown = (d: number, aspect: number) =>
      Math.tan((fov * Math.PI) / 360) * (d - TOWER_LAUNCH_Z) * aspect;

    it('asks for no more than the closeup on a wide screen', () => {
      expect(fitDistance(fov, 1280 / 720, reach, TOWER_LAUNCH_Z)).toBeLessThan(
        distance,
      );
      expect(fitDistance(fov, 1920 / 1080, reach, TOWER_LAUNCH_Z)).toBeLessThan(
        distance,
      );
    });

    it('stands the closeup back until the lamp is in the picture on a narrow screen', () => {
      for (const [w, h] of [
        [440, 900],
        [800, 700],
        [360, 640],
        [1000, 700],
      ]) {
        const d = Math.max(
          distance,
          fitDistance(fov, w / h, reach, TOWER_LAUNCH_Z),
        );
        expect(shown(d, w / h)).toBeGreaterThanOrEqual(TOWER_RECALL_X + 0.25);
      }
      // The old closeup showed less than the lamp's own distance from the centre at 440 x 900.
      expect(shown(distance, 440 / 900)).toBeLessThan(TOWER_RECALL_X);
    });

    it('gives no distance for a screen with no size', () => {
      expect(fitDistance(fov, 0, reach, TOWER_LAUNCH_Z)).toBe(0);
    });
  });
});

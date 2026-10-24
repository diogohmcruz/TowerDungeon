import {
  closeupDistance,
  dampFactor,
  fitDistance,
  SHOTS,
  ShotBlend,
} from './camera-shots';
import { FIGURE_Z } from './figures/figure-layout';
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

  describe('closeupDistance (#143)', () => {
    const fight = { reach: 0.97, depth: FIGURE_Z };
    const lamp = { reach: TOWER_RECALL_X + 0.6, depth: TOWER_LAUNCH_Z };
    const old = 5.2;
    /** How much larger a figure is than in the old closeup (a figure's size goes with 1 / its distance from the camera). */
    const magnification = (d: number) => (old - FIGURE_Z) / (d - FIGURE_Z);

    it('frames the fight at least twice as large as the old closeup on a wide screen', () => {
      for (const aspect of [1440 / 900, 1920 / 1080, 2560 / 1440]) {
        const d = closeupDistance(SHOTS.closeup, aspect, fight, null);
        expect(magnification(d)).toBeGreaterThanOrEqual(2);
      }
    });

    it('keeps the whole fight in the width, whatever the screen', () => {
      for (const aspect of [1, 1.2, 1440 / 900, 1920 / 1080, 21 / 9]) {
        const d = closeupDistance(SHOTS.closeup, aspect, fight, null);
        const half =
          Math.tan((SHOTS.closeup.fov * Math.PI) / 360) *
          (d - FIGURE_Z) *
          aspect;
        // Within the Shot's own minimum distance the fight may stand in the picture with room to spare.
        expect(half).toBeGreaterThanOrEqual(fight.reach - 1e-9);
      }
    });

    it('lets the bell and the lamp leave the picture on a wide screen', () => {
      const wide = closeupDistance(SHOTS.closeup, 1440 / 900, fight, null);
      expect(
        fitDistance(SHOTS.closeup.fov, 1440 / 900, lamp.reach, lamp.depth),
      ).toBeGreaterThan(wide);
    });

    it('stands back for the lamp on a narrow screen, as before', () => {
      const aspect = 440 / 900;
      const d = closeupDistance(SHOTS.closeup, aspect, fight, lamp);
      expect(d).toBeGreaterThanOrEqual(
        fitDistance(SHOTS.closeup.fov, aspect, lamp.reach, lamp.depth),
      );
      expect(d).toBeGreaterThan(old * 0.9);
    });

    it('stays outside the unit view', () => {
      expect(closeupDistance(SHOTS.closeup, 3, fight, null)).toBeGreaterThan(
        SHOTS.unit.distance,
      );
    });
  });
});

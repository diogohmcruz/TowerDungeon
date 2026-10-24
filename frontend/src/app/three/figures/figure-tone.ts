import type { InkTheme } from '../ink/palette';

/**
 * Issue #144: how a figure's ink is chosen, in ramp levels (0 to 3, what the ink
 * shader works out before it dithers). A Party member is drawn from the detail
 * slot (2), a garrison member from the fill slot (3) with a slot-1 rim baked into
 * its art. By day slot 0 is the pale ground and slot 3 the ink, so the Party's
 * mid-grey sat on the mid-grey of the pillars and the Floor strip and read far
 * worse than the garrison.
 *
 * The Party therefore (a) is drawn one step darker in the day theme, through
 * `PARTY_DAY_SLOTS`, and (b) in both themes carries a one-texel rim of slot 0
 * (the ground colour), so a part of the ground that matches the body still
 * parts from it. No colour is added: it is the same ramp. The garrison keeps
 * its own shape, its slot-1 rim, and the Guardian its crimson and size.
 */

/** Figure role carried in the instance data: 0 ramp, 1 Guardian crimson, 2 Party. */
export type FigureRole = 0 | 1 | 2;
export const ROLE_RAMP = 0;
export const ROLE_GUARDIAN = 1;
export const ROLE_PARTY = 2;

/** Texels the Party's rim extends beyond its sprite on each side. */
export const PARTY_RIM_PAD = 1;

/**
 * By day a Party texel value (index = the atlas slot 0 to 3) is drawn as this slot:
 * the body (2) in ink, the accents (3) and lines (1) one step lighter than it.
 */
export const PARTY_DAY_SLOTS: readonly number[] = [0, 2, 3, 2];

/** The ramp slot of a figure's rim texels: 0 for the Party's, 1 for the garrison's baked rim. */
export const PARTY_RIM_SLOT = 0;
export const GARRISON_RIM_SLOT = 1;

export interface FigureTone {
  /** The level of the body: the slot most of the figure is drawn in. */
  body: number;
  /** The level of the rim, which a ground matching the body does not also match. */
  rim: number;
}

/** What a role draws, by theme. A figure's tone is 1 and no fog reaches it (it stands inside the fog's near distance). */
export function figureTone(
  role: FigureRole,
  theme: InkTheme,
  { partyRim = true, partyLift = true } = {},
): FigureTone {
  if (role === ROLE_PARTY) {
    const body = theme === 'day' && partyLift ? PARTY_DAY_SLOTS[2] : 2;
    return { body, rim: partyRim ? PARTY_RIM_SLOT : body };
  }
  return { body: 3, rim: GARRISON_RIM_SLOT };
}

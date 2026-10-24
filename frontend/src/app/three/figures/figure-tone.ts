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
 * `PARTY_DAY_SLOTS`, and (b) has a lit edge: the texels of its silhouette whose
 * left neighbour is empty are drawn one step off the body (lighter by
 * night, lighter than the ink by day), so a part of the ground that matches the
 * body does not match the lit edge as well. The edge is inside the silhouette,
 * on one side only; there is no border outside it (issue #146 removed the
 * one-texel ground-coloured rim of #144, which read as a dark border by night).
 * No colour is added: it is the same ramp. The garrison keeps its own shape, its
 * slot-1 rim, and the Guardian its crimson and size.
 */

/** Figure role carried in the instance data: 0 ramp, 1 Guardian crimson, 2 Party. */
export type FigureRole = 0 | 1 | 2;
export const ROLE_RAMP = 0;
export const ROLE_GUARDIAN = 1;
export const ROLE_PARTY = 2;

/**
 * By day a Party texel value (index = the atlas slot 0 to 3) is drawn as this slot:
 * the body (2) in ink, the accents (3) and lines (1) one step lighter than it.
 */
export const PARTY_DAY_SLOTS: readonly number[] = [0, 2, 3, 2];

/** The ramp slot of the Party's lit edge, by theme: one step off the body in the lighter direction. */
export const PARTY_EDGE_SLOT_NIGHT = 3;
export const PARTY_EDGE_SLOT_DAY = 2;
/** The ramp slot of the garrison's baked rim. */
export const GARRISON_RIM_SLOT = 1;

export interface FigureTone {
  /** The level of the body: the slot most of the figure is drawn in. */
  body: number;
  /** The level of the lit edge (the Party's) or baked rim (the garrison's), which a ground matching the body does not also match. */
  edge: number;
}

/** What a role draws, by theme. A figure's tone is 1 and no fog reaches it (it stands inside the fog's near distance). */
export function figureTone(
  role: FigureRole,
  theme: InkTheme,
  { partyEdge = true, partyLift = true } = {},
): FigureTone {
  if (role === ROLE_PARTY) {
    const body = theme === 'day' && partyLift ? PARTY_DAY_SLOTS[2] : 2;
    const edge = theme === 'day' ? PARTY_EDGE_SLOT_DAY : PARTY_EDGE_SLOT_NIGHT;
    return { body, edge: partyEdge ? edge : body };
  }
  return { body: 3, edge: GARRISON_RIM_SLOT };
}

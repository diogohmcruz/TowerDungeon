/**
 * What setting out would take and leave, from the frame's Loadout preview and the
 * choices made so far (Salt, Salt Pack and bearer, Panacea). One rule for the
 * Loadout panel, the Gate's popover and its caption, so the three never disagree.
 * Pure: no Angular, no three.js.
 */
import type { LoadoutPreview } from '../../interfaces/village';

/** What the player has chosen; none of it is kept by the server until setting out. */
export interface LoadoutChoice {
  salt: number;
  pack: number;
  bearer: string;
  panacea: boolean;
  overreach: boolean;
}

export interface LoadoutForecast {
  saltMax: number;
  saltChosen: number;
  packMax: number;
  packChosen: number;
  bearerChosen: string;
  panaceaAvailable: boolean;
  panaceaChosen: boolean;
  /** The Roll holds Overreach. */
  overreachAvailable: boolean;
  /** Chosen, and the Roll holds it. */
  overreachChosen: boolean;
  /** Carrying Capacity left once the Panacea, if chosen, takes its share. */
  capacity: number;
  /** Supplies taken from the Pantry: no more than the capacity left. */
  taken: number;
  /** Food the Pantry holds after setting out with these choices. */
  pantryAfter: number;
  /** Whole seconds the Supplies last plain, with the chosen Salt, and fully salted; null with no one to feed. */
  lastsPlain: number | null;
  lastsChosen: number | null;
  lastsFullySalted: number | null;
  /** The runway under Overreach with the chosen Salt, whether or not it is chosen; null with no one to feed or no Overreach held. */
  lastsOverreach: number | null;
  /** The Pantry would hold less than a minute after leaving. */
  pantryHoldsShort: boolean;
}

export function loadoutForecast(
  preview: LoadoutPreview,
  choice: LoadoutChoice,
): LoadoutForecast {
  const saltMax = Math.max(0, Math.min(preview.saltHeld, preview.saltUseful));
  const saltChosen = Math.min(Math.max(0, choice.salt), saltMax);
  const bearerChosen = preview.bearerTypes.includes(choice.bearer)
    ? choice.bearer
    : (preview.bearerTypes[0] ?? '');
  const packMax = Math.max(0, preview.saltHeld - saltChosen);
  const packChosen =
    bearerChosen === '' ? 0 : Math.min(Math.max(0, choice.pack), packMax);
  const panaceaAvailable = preview.panaceaBlocked === null;
  const panaceaChosen = choice.panacea && panaceaAvailable;
  const capacity = Math.max(
    0,
    preview.carryingCapacity - (panaceaChosen ? preview.panaceaCapacity : 0),
  );
  const taken = Math.min(preview.suppliesTaken, capacity);
  const share =
    taken > 0
      ? Math.min(1, (saltChosen * preview.suppliesPerSalt) / taken)
      : 0;
  const overreachAvailable = !!preview.overreach;
  const overreachChosen = choice.overreach && overreachAvailable;
  const lasts = (s: number, overreach = overreachChosen): number | null => {
    const perUnit =
      (preview.drainPerUnit -
        s * (preview.drainPerUnit - preview.saltedDrainPerUnit)) *
      (overreach && preview.overreach
        ? preview.overreach.supplyDrainMultiplier
        : 1);
    const drain = preview.partySize * perUnit;
    return drain > 0 ? Math.floor(taken / drain) : null;
  };
  return {
    saltMax,
    saltChosen,
    packMax,
    packChosen,
    bearerChosen,
    panaceaAvailable,
    panaceaChosen,
    overreachAvailable,
    overreachChosen,
    capacity,
    taken,
    pantryAfter: Math.max(
      0,
      preview.pantryAfter + (preview.suppliesTaken - taken),
    ),
    lastsPlain: lasts(0),
    lastsChosen: lasts(share),
    lastsFullySalted: lasts(1),
    lastsOverreach: overreachAvailable ? lasts(share, true) : null,
    pantryHoldsShort:
      preview.pantryHoldSeconds !== null && preview.pantryHoldSeconds < 60,
  };
}

/** What a Loadout holds, in words, for a saved slot, a Muster Roll and a preview. */
export interface LoadoutWords {
  salt: number;
  saltPack: number;
  saltBearer: string | null;
  panacea: boolean;
  overreach: boolean;
}

export function describeLoadout(l: LoadoutWords): string {
  const parts: string[] = [];
  parts.push(l.salt > 0 ? `${l.salt} Salt on the Supplies` : 'no Salt on the Supplies');
  parts.push(
    l.saltPack > 0
      ? `a Salt Pack of ${l.saltPack}${l.saltBearer ? ` carried by ${l.saltBearer}` : ''}`
      : 'no Salt Pack',
  );
  parts.push(l.panacea ? 'a Panacea' : 'no Panacea');
  parts.push(l.overreach ? 'Overreach' : 'no Overreach');
  const text = parts.join(', ');
  return `${text.charAt(0).toUpperCase()}${text.slice(1)}.`;
}

/** What the Gate's present choices hold, as a Loadout to save. */
export function chosenLoadout(f: LoadoutForecast): LoadoutWords {
  return {
    salt: f.saltChosen,
    saltPack: f.packChosen,
    saltBearer: f.packChosen > 0 ? f.bearerChosen : null,
    panacea: f.panaceaChosen,
    overreach: f.overreachChosen,
  };
}

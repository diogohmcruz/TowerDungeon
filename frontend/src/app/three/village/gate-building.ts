/**
 * The Gate, beside the bell (architecture doc 5.6): where the Loadout is chosen.
 * Its gauges state what setting out takes and leaves (Carrying Capacity, Supplies
 * and how long they last, the Pantry after, Salt, the Salt Pack, the Panacea) and
 * its actions are the Loadout panel's choices: the steppers, the Salt-bearer and
 * the Panacea. The figures come from `loadoutForecast`, the panel's own rule. The
 * bell still sets out with whatever was chosen here. Overreach and saved Loadouts
 * are not in the game yet; they join this building when they are.
 */
import type { GameState } from '../../interfaces/game-state';
import { setOutAvailability } from '../launch/launch-controls';
import { loadoutChoices } from '../launch/loadout-choices';
import { loadoutForecast, type LoadoutForecast } from '../launch/loadout-forecast';
import { defineBuilding, type ActionSpec } from './building-registry';
import { hasReturned, RUN_SUMMARY_SECTION } from './records-buildings';

export const GATE_ID = 'GATE';

function forecast(state: GameState): LoadoutForecast {
  return loadoutForecast(state.loadout, loadoutChoices().choice());
}

/** Why the Loadout cannot change now (an Expedition is out, or the Campaign has ended); null when it can. */
function frozen(state: GameState): string | null {
  const can = setOutAvailability(state);
  return can.usable
    ? null
    : `${can.reason} The Loadout cannot change.`;
}

const USABLE = { usable: true, reason: null } as const;
const refused = (reason: string) => ({ usable: false, reason });

const PANS_CLOSED = 'The Salt Pans are not open yet.';

function seconds(s: number | null): string {
  return s === null ? 'no one to feed' : `${s} s`;
}

function stepper(
  id: string,
  label: string,
  field: 'salt' | 'pack',
  direction: 1 | -1,
): ActionSpec {
  return {
    id,
    label,
    availability: (s) => {
      const locked = frozen(s);
      if (locked) return refused(locked);
      if (!s.loadout.saltPansOpen) return refused(PANS_CLOSED);
      const f = forecast(s);
      if (field === 'pack' && f.bearerChosen === '') {
        return refused('No Unit in the Reserves can carry a Salt Pack.');
      }
      const now = field === 'salt' ? f.saltChosen : f.packChosen;
      const max = field === 'salt' ? f.saltMax : f.packMax;
      if (direction > 0 && now >= max) {
        return refused(
          field === 'salt'
            ? 'No more Salt would salt anything, or none is held.'
            : 'No Salt is left to pack.',
        );
      }
      if (direction < 0 && now <= 0) {
        return refused(
          field === 'salt' ? 'No Salt is chosen for the Supplies.' : 'No Salt is packed.',
        );
      }
      return USABLE;
    },
    run: (_commands, s) => {
      const f = forecast(s);
      const now = field === 'salt' ? f.saltChosen : f.packChosen;
      loadoutChoices().set({ [field]: Math.max(0, now + direction) });
    },
  };
}

function panaceaAction(s: GameState): ActionSpec {
  return {
    id: 'panacea',
    label: forecast(s).panaceaChosen ? 'Leave the Panacea' : 'Carry a Panacea',
    availability: (state) => {
      const locked = frozen(state);
      if (locked) return refused(locked);
      const f = forecast(state);
      if (!f.panaceaAvailable && !f.panaceaChosen) {
        return refused(state.loadout.panaceaBlocked ?? 'A Panacea cannot be made now.');
      }
      return USABLE;
    },
    run: (_commands, state) =>
      loadoutChoices().set({ panacea: !forecast(state).panaceaChosen }),
  };
}

export const GATE = defineBuilding({
  id: GATE_ID,
  title: 'The Gate: the Loadout',
  visible: (s: GameState) => !!s.loadout,
  // The last Expedition is written up here, and opens by itself when one comes back.
  sections: [RUN_SUMMARY_SECTION],
  opensOnReturn: hasReturned,
  gauges: [
    {
      id: 'capacity',
      label: 'Carrying Capacity',
      value: (s) => forecast(s).capacity,
      fill: (s) => forecast(s).capacity / Math.max(1, s.loadout.carryingCapacity),
      leaves: (s) =>
        forecast(s).panaceaChosen
          ? `The Panacea takes ${s.loadout.panaceaCapacity} of the ${s.loadout.carryingCapacity}.`
          : null,
    },
    {
      id: 'supplies',
      label: 'Supplies taken',
      value: (s) => forecast(s).taken,
      fill: (s) => forecast(s).taken / Math.max(1, forecast(s).capacity),
      leaves: (s) => {
        const f = forecast(s);
        if (f.lastsChosen === null) return 'No Party to feed.';
        const plain =
          f.lastsPlain !== f.lastsFullySalted
            ? ` ${seconds(f.lastsPlain)} plain, ${seconds(f.lastsFullySalted)} if fully salted.`
            : '';
        return `Supplies last about ${seconds(f.lastsChosen)} at Party size ${s.loadout.partySize}.${plain}`;
      },
    },
    {
      id: 'pantry',
      label: 'Pantry after setting out',
      value: (s) => forecast(s).pantryAfter,
      fill: (s) => {
        const f = forecast(s);
        return f.pantryAfter / Math.max(1, f.pantryAfter + f.taken);
      },
      leaves: (s) => {
        const f = forecast(s);
        const hold =
          s.loadout.pantryHoldSeconds === null
            ? 'Fields cover Upkeep.'
            : `Pantry would hold ${s.loadout.pantryHoldSeconds} s.`;
        return `Setting out takes ${Math.floor(f.taken)} Food. ${hold}`;
      },
    },
    {
      id: 'salt',
      label: 'Salt for the Supplies',
      value: (s) => forecast(s).saltChosen,
      fill: (s) => forecast(s).saltChosen / Math.max(1, s.loadout.saltHeld),
      leaves: (s) => {
        const l = s.loadout;
        if (!l.saltPansOpen) return PANS_CLOSED;
        return (
          `${forecast(s).saltChosen} of ${l.saltHeld} Salt held. 1 Salt salts ${l.suppliesPerSalt} Supplies. ` +
          `Drain ${l.saltedDrainPerUnit} per Unit per second instead of ${l.drainPerUnit}.`
        );
      },
    },
    {
      id: 'pack',
      label: 'Salt Pack',
      value: (s) => forecast(s).packChosen,
      fill: (s) => forecast(s).packChosen / Math.max(1, s.loadout.saltHeld),
      leaves: (s) => {
        const l = s.loadout;
        if (!l.saltPansOpen) return null;
        const f = forecast(s);
        const bearer =
          f.bearerChosen === ''
            ? 'No Unit to carry it.'
            : `Marked: ${f.bearerChosen}. Chymus Insects aim at it.`;
        return (
          `One Salt per Chymus Insect: +50% damage on each. Slimes are not affected. ` +
          `Takes ${l.packCapacity} Carrying Capacity. ${bearer}`
        );
      },
    },
    {
      id: 'panacea',
      label: 'Panacea',
      value: (s) => (forecast(s).panaceaChosen ? 1 : 0),
      format: (v) => (v > 0 ? 'Carried' : 'Not carried'),
      fill: (s) => (forecast(s).panaceaChosen ? 1 : 0),
      leaves: (s) => {
        const l = s.loadout;
        return l.panaceaBlocked === null
          ? `${l.panaceaSpores} Spores and ${l.panaceaMaterials} Materials, ${l.panaceaCapacity} Carrying Capacity. One per Party. The first time a head or torso would reach zero, it is left at 10% and the Panacea is spent.`
          : l.panaceaBlocked;
      },
    },
    {
      id: 'trust',
      label: 'Trust held',
      value: (s) => s.loadout.trustHeld,
      fill: () => 0,
    },
    {
      id: 'start',
      label: 'Start Floor',
      value: () => Math.max(1, loadoutChoices().startFloor()),
      format: (v) => `Floor ${v}`,
      fill: () => 0,
    },
  ],
  actions: [
    stepper('salt-more', 'Use one more Salt on the Supplies', 'salt', 1),
    stepper('salt-less', 'Use one less Salt on the Supplies', 'salt', -1),
    stepper('pack-more', 'Pack one more Salt', 'pack', 1),
    stepper('pack-less', 'Pack one less Salt', 'pack', -1),
  ],
  moreActions: (s: GameState) => {
    if (!s.loadout) return [];
    if (!s.loadout.saltPansOpen) return [panaceaAction(s)];
    // One choice per Unit type in the Reserves, as the panel's select lists them.
    const bearers = s.loadout.bearerTypes.map(
      (type): ActionSpec => ({
        id: `bearer-${type}`,
        label: `Salt-bearer: ${type}`,
        availability: (state) => {
          const locked = frozen(state);
          if (locked) return refused(locked);
          return forecast(state).bearerChosen === type
            ? refused(`${type} already carries the Salt Pack.`)
            : USABLE;
        },
        run: () => loadoutChoices().set({ bearer: type }),
      }),
    );
    return [panaceaAction(s), ...bearers];
  },
});

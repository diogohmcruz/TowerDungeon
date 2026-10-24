/**
 * The Gate, beside the bell (architecture doc 5.6): where the Loadout is chosen.
 * Its gauges state what setting out takes and leaves (Carrying Capacity, Supplies
 * and how long they last, the Pantry after, Salt, the Salt Pack, the Panacea) and
 * its actions are the Loadout panel's choices: the steppers, the Salt-bearer and
 * the Panacea. The figures come from `loadoutForecast`, the panel's own rule. The
 * bell still sets out with whatever was chosen here. Overreach shows only while the
 * Roll holds it (with its three effects in words and the runway under it); the saved
 * Loadouts (three slots) only while the Quartermaster's Tally is held; Muster Roll,
 * which sends the last Party and Loadout again, only while the Roll holds it.
 */
import { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import {
  availabilityOf,
  setOutAvailability,
  type LaunchCommand,
} from '../launch/launch-controls';
import { loadoutChoices } from '../launch/loadout-choices';
import {
  chosenLoadout,
  describeLoadout,
  loadoutForecast,
  type LoadoutForecast,
  type LoadoutWords,
} from '../launch/loadout-forecast';
import { defineBuilding, type ActionSpec, type LineSpec } from './building-registry';
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

/** The bell, the lamp and the Wave as actions: the same commands and refusals the objects in the picture give. */
function launchAction(command: LaunchCommand, label: string, preview?: string): ActionSpec {
  return {
    id: command,
    label,
    availability: (s) => availabilityOf(command, s),
    ...(preview ? { preview: () => preview } : {}),
    run: (commands) => commands.launch(command),
  };
}

/** One Start Floor: the Base, or an open Shortcut, as the radios were. */
function startFloorAction(floor: number, label: string): ActionSpec {
  return {
    id: `start-floor-${floor}`,
    label: () => (loadoutChoices().startFloor() === floor ? `${label} (chosen)` : label),
    availability: (s) => {
      const locked = frozen(s);
      if (locked) return refused(locked);
      return loadoutChoices().startFloor() === floor
        ? refused('Already the Start Floor.')
        : USABLE;
    },
    run: () => {
      loadoutChoices().setStartFloor(floor);
    },
  };
}

function startFloorActions(s: GameState): ActionSpec[] {
  const open = (s.shortcuts ?? []).filter((sc) => sc.unlocked);
  return [
    startFloorAction(0, 'Start at Floor 1, the Base'),
    ...open.map((sc) => startFloorAction(sc.floor, `Start at Floor ${sc.floor}, ${sc.name}`)),
  ];
}

const NO_PARTY = 'No Party has set out yet this Generation.';

function overreachWords(o: {
  supplyDrainMultiplier: number;
  starvationDamageMultiplier: number;
  lootMultiplier: number;
}): string {
  return (
    `The Supplies drain ${o.supplyDrainMultiplier} times as fast. ` +
    `Starvation does ${o.starvationDamageMultiplier} times the damage. ` +
    `All Loot found is ${o.lootMultiplier} times as much.`
  );
}

function overreachAction(): ActionSpec {
  return {
    id: 'overreach',
    label: (s) =>
      forecast(s).overreachChosen ? 'Set out without Overreach' : 'Set out under Overreach',
    visible: (s) => !!s.loadout?.overreach,
    availability: (state) => {
      const locked = frozen(state);
      return locked ? refused(locked) : USABLE;
    },
    preview: (s) => {
      const o = s.loadout?.overreach;
      return o ? overreachWords(o) : null;
    },
    run: (_commands, state) =>
      loadoutChoices().set({ overreach: !forecast(state).overreachChosen }),
  };
}

const slotName = (i: number) => `slot ${i + 1}`;

/** One Save, Apply and Clear for each slot the Tally holds. */
function tallyActions(s: GameState): ActionSpec[] {
  const tally = s.loadout?.tally;
  if (!tally) return [];
  const saved = (state: GameState, i: number) =>
    state.loadout?.tally?.saved.find((l) => l.slot === i) ?? null;
  const out: ActionSpec[] = [];
  for (let i = 0; i < tally.slots; i++) {
    out.push(
      {
        id: `loadout-save-${i}`,
        label: `Save these choices in ${slotName(i)}`,
        availability: () => USABLE,
        preview: (state) => {
          const now = describeLoadout(chosenLoadout(forecast(state)));
          return saved(state, i)
            ? `Replaces what ${slotName(i)} holds. It would hold: ${now}`
            : `${slotName(i)} would hold: ${now}`;
        },
        run: (commands, state) => {
          const c = chosenLoadout(forecast(state));
          commands.send(GameAction.SAVE_LOADOUT, {
            slot: i,
            salt: c.salt,
            saltPack: c.saltPack,
            ...(c.saltBearer ? { saltBearer: c.saltBearer } : {}),
            panacea: c.panacea,
            overreach: c.overreach,
          });
        },
      },
      {
        id: `loadout-apply-${i}`,
        label: `Apply ${slotName(i)}`,
        availability: (state) => {
          const locked = frozen(state);
          if (locked) return refused(locked);
          return saved(state, i) ? USABLE : refused(`No Loadout is saved in ${slotName(i)}.`);
        },
        preview: (state) => {
          const held = saved(state, i);
          return held ? `Sets the Gate to: ${describeLoadout(held)}` : null;
        },
        run: (_commands, state) => {
          const held = saved(state, i);
          if (!held) return;
          loadoutChoices().set({
            salt: held.salt,
            pack: held.saltPack,
            bearer: held.saltBearer ?? '',
            panacea: held.panacea,
            overreach: held.overreach,
          });
        },
      },
      {
        id: `loadout-clear-${i}`,
        label: `Clear ${slotName(i)}`,
        availability: (state) =>
          saved(state, i) ? USABLE : refused(`No Loadout is saved in ${slotName(i)}.`),
        run: (commands) => commands.send(GameAction.CLEAR_LOADOUT, { slot: i }),
      },
    );
  }
  return out;
}

function musterWords(m: LoadoutWords & { partyUnits: number; startFloor: number }): string {
  const who = m.partyUnits > 0 ? `${m.partyUnits} Units` : 'Every Unit in the Reserves';
  const from = m.startFloor > 0 ? `Floor ${m.startFloor}` : 'the Base';
  return `${who} set out again from ${from}. ${describeLoadout(m)}`;
}

function musterAction(): ActionSpec {
  return {
    id: 'muster-roll',
    label: 'Muster Roll: send the last Party out again',
    visible: (s) => !!s.loadout?.musterRoll,
    availability: (state) => {
      const locked = frozen(state);
      if (locked) return refused(locked);
      return state.loadout?.musterRoll?.ready ? USABLE : refused(NO_PARTY);
    },
    preview: (state) => {
      const m = state.loadout?.musterRoll;
      return m?.ready ? musterWords(m) : null;
    },
    run: (commands) => commands.send(GameAction.MUSTER_ROLL),
  };
}

function gateLines(s: GameState): LineSpec[] {
  const out: LineSpec[] = [];
  const tally = s.loadout?.tally;
  if (tally) {
    for (let i = 0; i < tally.slots; i++) {
      const held = tally.saved.find((l) => l.slot === i);
      out.push({
        id: `slot-${i}`,
        group: "Quartermaster's Tally",
        label: `Saved Loadout, ${slotName(i)}`,
        value: held ? describeLoadout(held) : 'Nothing is saved here.',
      });
    }
  }
  const muster = s.loadout?.musterRoll;
  if (muster) {
    out.push({
      id: 'muster',
      group: 'Muster Roll',
      label: 'The last Party',
      value: muster.ready ? musterWords(muster) : NO_PARTY,
    });
  }
  return out;
}

export const GATE = defineBuilding({
  id: GATE_ID,
  title: 'The Gate: the Loadout',
  visible: (s: GameState) => !!s.loadout,
  lines: gateLines,
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
        const over =
          f.overreachAvailable && !f.overreachChosen
            ? ` Under Overreach, about ${seconds(f.lastsOverreach)}.`
            : f.overreachChosen
              ? ' This is the runway under Overreach.'
              : '';
        return `Supplies last about ${seconds(f.lastsChosen)} at Party size ${s.loadout.partySize}.${plain}${over}`;
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
      id: 'overreach',
      label: 'Overreach',
      visible: (s) => !!s.loadout?.overreach,
      value: (s) => (forecast(s).overreachChosen ? 1 : 0),
      format: (v) => (v > 0 ? 'Set' : 'Not set'),
      fill: (s) => (forecast(s).overreachChosen ? 1 : 0),
      leaves: (s) => {
        const o = s.loadout.overreach;
        if (!o) return null;
        const f = forecast(s);
        const runway =
          f.lastsOverreach === null
            ? 'No Party to feed.'
            : `Under it the Supplies last about ${seconds(f.lastsOverreach)}.`;
        return `${overreachWords(o)} ${runway}`;
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
    launchAction('set-out', 'Ring the bell: set out'),
    launchAction(
      'wave',
      'Ring the bell: send a Reinforcement Wave',
      'Sends every Reserve up after the Party. They carry supplies from the Pantry and climb from the Base. If their supplies run out before they catch up, climbers are lost.',
    ),
    launchAction('extract', 'Light the lamp: extract'),
    stepper('salt-more', 'Use one more Salt on the Supplies', 'salt', 1),
    stepper('salt-less', 'Use one less Salt on the Supplies', 'salt', -1),
    stepper('pack-more', 'Pack one more Salt', 'pack', 1),
    stepper('pack-less', 'Pack one less Salt', 'pack', -1),
  ],
  moreActions: (s: GameState) => {
    if (!s.loadout) return [];
    const extras = [
      ...startFloorActions(s),
      overreachAction(),
      ...tallyActions(s),
      musterAction(),
    ];
    if (!s.loadout.saltPansOpen) return [panaceaAction(s), ...extras];
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
    return [panaceaAction(s), ...bearers, ...extras];
  },
});

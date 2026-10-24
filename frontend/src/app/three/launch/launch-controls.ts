/**
 * What the launch controls can do now (architecture doc 5.5). One rule for the
 * panel's buttons and for the objects in the picture, so the two never disagree:
 * a control is usable exactly when its panel button is enabled, and when it is not
 * the reason is said in words. Pure, no three.js and no Angular.
 */
import type { GameState } from '../../interfaces/game-state';

export type LaunchCommand = 'set-out' | 'wave' | 'extract';

export interface Availability {
  usable: boolean;
  /** Why the control cannot be used; null when it can. */
  reason: string | null;
}

const OK: Availability = { usable: true, reason: null };

function refused(reason: string): Availability {
  return { usable: false, reason };
}

function over(state: GameState): boolean {
  return (
    state.gameOutcome === 'VICTORY' ||
    state.gameOutcome === 'DEFEAT' ||
    state.gameOutcome === 'EXODUS'
  );
}

function held(state: GameState): boolean {
  return state.session?.held === true;
}

/** Set out: refused when an Expedition is out or the Campaign has ended. */
export function setOutAvailability(state: GameState): Availability {
  if (over(state)) return refused('The Campaign has ended. No one sets out.');
  if (state.expeditionActive) {
    return refused('An Expedition is already out.');
  }
  return OK;
}

/** Send Reinforcement Wave: needs an Expedition, Reserves, and a Party that is not Held. */
export function waveAvailability(state: GameState): Availability {
  if (!state.expeditionActive) {
    return refused('No Expedition is out. There is no one to follow.');
  }
  if (held(state)) {
    return refused('The Party has not moved. Resume first.');
  }
  if (state.homeUnitCount === 0) {
    return refused('No Reserves wait in the Fort.');
  }
  return OK;
}

/** Extract: needs an Expedition; a Held Party may still be called home. */
export function extractAvailability(state: GameState): Availability {
  if (!state.expeditionActive) {
    return refused('No Expedition is out. There is no one to call home.');
  }
  return OK;
}

/** The bell sets out while no Expedition is out, and sends a Wave while one is. */
export function bellCommand(state: GameState): 'set-out' | 'wave' {
  return state.expeditionActive ? 'wave' : 'set-out';
}

export function bellAvailability(state: GameState): Availability {
  return bellCommand(state) === 'wave'
    ? waveAvailability(state)
    : setOutAvailability(state);
}

export function availabilityOf(
  command: LaunchCommand,
  state: GameState,
): Availability {
  switch (command) {
    case 'set-out':
      return setOutAvailability(state);
    case 'wave':
      return waveAvailability(state);
    case 'extract':
      return extractAvailability(state);
  }
}

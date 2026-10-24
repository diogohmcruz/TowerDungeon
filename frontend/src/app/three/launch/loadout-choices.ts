/**
 * Where the Gate building reads and changes what the Loadout has chosen. The
 * building registry's figures and actions are functions of the game state, but the
 * Loadout's choices live in the client (the `LaunchService` signals) until setting
 * out sends them. The service binds itself here; reads made inside a computed or an
 * effect track its signals, so the popover and the caption stay current. Pure: no
 * Angular.
 */
import type { LoadoutChoice } from './loadout-forecast';

export interface LoadoutChoiceSource {
  choice(): LoadoutChoice;
  /** The Floor the next Expedition begins on; 0 is the Base. */
  startFloor(): number;
  set(patch: Partial<LoadoutChoice>): void;
}

const NONE: LoadoutChoiceSource = {
  choice: () => ({ salt: 0, pack: 0, bearer: '', panacea: false, overreach: false }),
  startFloor: () => 0,
  set: () => undefined,
};

let source: LoadoutChoiceSource = NONE;

/** The launch service says where the choices are kept. */
export function bindLoadoutChoices(next: LoadoutChoiceSource): void {
  source = next;
}

export function loadoutChoices(): LoadoutChoiceSource {
  return source;
}

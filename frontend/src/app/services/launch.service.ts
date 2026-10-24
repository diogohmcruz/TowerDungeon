import { computed, inject, Injectable, signal } from '@angular/core';

import { GameAction } from '../interfaces/game-action.enum';
import type { LedgerLine } from '../interfaces/village';
import {
  Availability,
  availabilityOf,
  bellCommand,
  LaunchCommand,
} from '../three/launch/launch-controls';
import { bindLoadoutChoices } from '../three/launch/loadout-choices';
import type { VillageSubject } from '../three/village/village-subjects';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';

/** A refusal stays on screen this long, in milliseconds. */
export const REFUSAL_MS = 6000;

/** The one line a refused launch leaves beside its control. */
export interface Refusal {
  command: LaunchCommand | 'start-floor';
  text: string;
}

/**
 * Launching: Set out, Send Reinforcement Wave, Extract and the Start Floor choice.
 * The panel's buttons and radios and the objects in the picture (the gate bell, the
 * recall lamp and the doorways, architecture doc 5.5) all go through here, so there
 * is one command and one set of refusal rules. Holds what the Loadout has chosen
 * because Set out needs it.
 */
@Injectable({ providedIn: 'root' })
export class LaunchService {
  private readonly state = inject(GameStateService).state;
  private readonly ws = inject(GameWebSocketService);

  /** Floor the next expedition begins on: 0 = base of the tower, otherwise an open shortcut. */
  readonly startFloor = signal(0);
  /** Salt the Loadout has chosen; 0 unless the player opened it and chose some. */
  readonly saltToUse = signal(0);
  /** Salt the Loadout has chosen for the Salt Pack, and the Unit type that carries it. */
  readonly packSalt = signal(0);
  readonly packBearer = signal('');
  /** Whether the Loadout has chosen to carry a Panacea. */
  readonly panaceaChosen = signal(false);

  private readonly _refusal = signal<Refusal | null>(null);
  /** The latest refusal, as one line; clears by itself. */
  readonly refusal = this._refusal.asReadonly();
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // The Gate building (the Loadout in the picture) reads and sets the choices here.
    bindLoadoutChoices({
      choice: () => ({
        salt: this.saltToUse(),
        pack: this.packSalt(),
        bearer: this.packBearer(),
        panacea: this.panaceaChosen(),
      }),
      startFloor: () => this.startFloor(),
      set: (patch) => {
        if (patch.salt !== undefined) this.saltToUse.set(patch.salt);
        if (patch.pack !== undefined) this.packSalt.set(patch.pack);
        if (patch.bearer !== undefined) this.packBearer.set(patch.bearer);
        if (patch.panacea !== undefined) this.panaceaChosen.set(patch.panacea);
      },
    });
  }

  /** What the Ledger shows of the latest refusal, until something is launched. */
  readonly ledgerLine = computed<LedgerLine | null>(() => {
    const r = this._refusal();
    return r ? { text: r.text, loss: false } : null;
  });

  readonly setOut = computed(() => availabilityOf('set-out', this.state()));
  readonly wave = computed(() => availabilityOf('wave', this.state()));
  readonly extractControl = computed(() =>
    availabilityOf('extract', this.state()),
  );
  /** The bell's command and whether it can be rung now. */
  readonly bell = computed<Availability & { command: 'set-out' | 'wave' }>(() => {
    const command = bellCommand(this.state());
    const view = command === 'wave' ? this.wave() : this.setOut();
    return { ...view, command };
  });

  /** Set out with the chosen Start Floor, Loadout and Salt options. */
  sendInvade(): void {
    const state = this.state();
    const loadout = state.loadout;
    const salt = Math.min(
      this.saltToUse(),
      loadout?.saltHeld ?? 0,
      loadout?.saltUseful ?? 0,
    );
    const bearers = loadout?.bearerTypes ?? [];
    const bearer = bearers.includes(this.packBearer())
      ? this.packBearer()
      : (bearers[0] ?? '');
    const pack = bearer
      ? Math.min(this.packSalt(), Math.max(0, (loadout?.saltHeld ?? 0) - salt))
      : 0;
    const panacea = this.panaceaChosen() && loadout?.panaceaBlocked === null;
    const payload = {
      units: new Map(),
      startFloor: this.startFloor(),
      salt,
      ...(pack > 0 ? { saltPack: pack, saltBearer: bearer } : {}),
      panacea,
    };
    this.ws.sendAction(GameAction.INVADE, payload);
    this.saltToUse.set(0);
    this.packSalt.set(0);
    this.panaceaChosen.set(false);
  }

  sendExtract(): void {
    this.ws.sendAction(GameAction.EXTRACT, null);
  }

  sendWave(): void {
    this.ws.sendAction(GameAction.REINFORCE, { units: {} });
  }

  /** Ring the bell: Set out, or a Reinforcement Wave while an Expedition is out. */
  ring(): void {
    const bell = this.bell();
    if (!bell.usable) {
      this.refuse(bell.command, bell.reason);
      return;
    }
    this.clearRefusal();
    if (bell.command === 'wave') this.sendWave();
    else this.sendInvade();
  }

  /** Light the recall lamp: Extract. */
  light(): void {
    const lamp = this.extractControl();
    if (!lamp.usable) {
      this.refuse('extract', lamp.reason);
      return;
    }
    this.clearRefusal();
    this.sendExtract();
  }

  /**
   * Choose where the next Expedition starts. Refused while one is out, as the
   * panel's radios are disabled then. A floor that is not open is not chosen.
   */
  chooseStartFloor(floor: number): boolean {
    if (this.state().expeditionActive) {
      this.refuse('start-floor', 'An Expedition is out. The Start Floor cannot change.');
      return false;
    }
    this.startFloor.set(floor);
    this.clearRefusal();
    return true;
  }

  /**
   * A click in the picture. Returns whether it was a launch control, so the scene
   * knows whether to carry on with its own selection. A Shortcut doorway chooses
   * its Floor as the Start Floor, and is still a Shortcut for the panel's card.
   */
  activate(subject: VillageSubject): boolean {
    switch (subject.kind) {
      case 'bell':
        this.ring();
        return true;
      case 'recall':
        this.light();
        return true;
      case 'base-door':
        this.chooseStartFloor(0);
        return true;
      case 'shortcut': {
        const sc = (this.state().shortcuts ?? []).find(
          (s) => s.id === subject.id && s.unlocked,
        );
        if (sc) this.chooseStartFloor(sc.floor);
        return false;
      }
      default:
        return false;
    }
  }

  private refuse(command: Refusal['command'], text: string | null): void {
    if (!text) return;
    this._refusal.set({ command, text });
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.clearRefusal(), REFUSAL_MS);
  }

  private clearRefusal(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this._refusal.set(null);
  }
}

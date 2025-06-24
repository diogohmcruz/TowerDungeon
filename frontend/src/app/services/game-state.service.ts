import { Injectable, Signal, signal } from '@angular/core';
import { GameState } from '../interfaces/game-state';
import { Unit } from '../interfaces/unit.enum';

@Injectable({ providedIn: 'root' })
export class GameStateService {
  private _state = signal<GameState>({
    mana: 0,
    manaPerSecond: 1,
    credit: 0,
    units: {
      [Unit.WARRIOR]: [],
      [Unit.ARCHER]: [],
      [Unit.MAGE]: [],
      [Unit.HEALER]: [],
      [Unit.TANK]: [],
      [Unit.ROGUE]: [],
      [Unit.NECROMANCER]: [],
      [Unit.DRACO_METAMORPH]: [],
    },
    unitsOnTower: {
      [Unit.WARRIOR]: [],
      [Unit.ARCHER]: [],
      [Unit.MAGE]: [],
      [Unit.HEALER]: [],
      [Unit.TANK]: [],
      [Unit.ROGUE]: [],
      [Unit.NECROMANCER]: [],
      [Unit.DRACO_METAMORPH]: [],
    },
    upgrades: [],
    prestigePoints: 0,
  });
  readonly state: Signal<GameState> = this._state.asReadonly();

  setState(newState: GameState) {
    this._state.set(newState);
  }

  update(partial: Partial<GameState>) {
    this._state.update((s) => ({ ...s, ...partial }));
  }

  addMana(amount: number) {
    this._state.update((s) => ({ ...s, mana: s.mana + amount }));
  }
}

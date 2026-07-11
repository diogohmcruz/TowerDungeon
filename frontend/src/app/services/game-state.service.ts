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
      [Unit.PORTER]: [],
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
      [Unit.PORTER]: [],
    },
    village: {
      food: 0,
      villagersCount: 0,
      productionRate: 0,
    },
    upgrades: [],
    prestigePoints: 0,
    resources: { MATERIALS: 0, RELICS: 0 },
    carriedLoot: { MATERIALS: 0, RELICS: 0 },
    carriedCredits: 0,
    supplies: 0,
    maxSupplies: 100,
    lastFoodReturned: 0,
    homePartyWounds: 0,
    foodHealRate: 0,
    homeUnitCount: 0,
    foodUpkeep: 0,
    netFoodPerSecond: 0,
    unlockedUnitTypes: [Unit.WARRIOR, Unit.ARCHER, Unit.PORTER],
    availableUpgrades: [],
    damageMultiplier: 1,
    supplyCapacityBonus: 0,
    deepestFloor: 0,
    expeditionsCompleted: 0,
    milestones: [],
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

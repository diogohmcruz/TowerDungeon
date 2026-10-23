import { Injectable, Signal, signal } from '@angular/core';
import { GameState } from '../interfaces/game-state';
import { Unit } from '../interfaces/unit.enum';
import { SessionMeta } from '../interfaces/game-frame';

@Injectable({ providedIn: 'root' })
export class GameStateService {
  private _state = signal<GameState>({
    credit: 0,
    units: {
      [Unit.WARRIOR]: [],
      [Unit.ARCHER]: [],
      [Unit.MAGE]: [],
      [Unit.HEALER]: [],
      [Unit.TANK]: [],
      [Unit.CAPTAIN]: [],
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
      [Unit.CAPTAIN]: [],
      [Unit.ROGUE]: [],
      [Unit.NECROMANCER]: [],
      [Unit.DRACO_METAMORPH]: [],
      [Unit.PORTER]: [],
    },
    village: {
      food: 0,
      villagersCount: 0,
      productionRate: 0,
      breakers: 0,
      scribes: 0,
      knowledge: 0,
      quarryHeap: 0,
      emergencyTilling: false,
    },
    expeditionActive: false,
    gameOutcome: 'PLAYING',
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
    recoverySpend: 0,
    pantryHoldSeconds: null,
    unlockedUnitTypes: [Unit.WARRIOR, Unit.ARCHER, Unit.PORTER],
    availableUpgrades: [],
    damageMultiplier: 1,
    supplyCapacityBonus: 0,
    highestFloor: 0,
    expeditionsCompleted: 0,
    enemiesDefeated: 0,
    milestones: [],
    shortcuts: [],
    lastRunSummary: null,
    reinforcements: [],
    tradeViews: [],
    tradePreviews: [],
    quarryRate: 0,
    studyRate: 0,
    yieldBonus: 0,
    stockroomCap: 60,
    quarryHeapCap: 0,
    heapDeliveryPerFloor: 5,
    theCount: { villagers: 0, reserves: 0 },
    tally: {
      foodGrown: 0,
      creditsBanked: 0,
      materialsBanked: 0,
      relicsBanked: 0,
      floorsCleared: 0,
      peakVillagers: 0,
    },
    villageTierLevel: 1,
    villageTierName: 'Huddled',
    villageTierOpens: "Fields, Quarry, Scribe's Study",
    ledger: [],
  });
  readonly state: Signal<GameState> = this._state.asReadonly();

  private _session = signal<SessionMeta | null>(null);
  /** The latest frame's session facts (resumed, notices, held), or null before the first frame. */
  readonly session: Signal<SessionMeta | null> = this._session.asReadonly();

  setState(newState: GameState) {
    this._state.set(newState);
  }

  setSession(session: SessionMeta | null) {
    this._session.set(session);
  }

  update(partial: Partial<GameState>) {
    this._state.update((s) => ({ ...s, ...partial }));
  }
}

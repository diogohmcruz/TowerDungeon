import { Unit } from './unit.enum';
import { Tower } from './tower';
import { Member } from './member';
import {
  LedgerLine,
  Tally,
  TheCount,
  TradePreview,
  TradeView,
  Village,
} from './village';

export type Squad = {
  [unit in Unit]: Member[];
};

export type ResourceWallet = {
  MATERIALS: number;
  RELICS: number;
};

export interface UpgradeOffer {
  id: string;
  name: string;
  description: string;
  level: number;
  maxed: boolean;
  repeatable: boolean;
  unlockUnit: string | null;
  nextCost: Partial<ResourceWallet>;
}

export interface MilestoneOffer {
  id: string;
  name: string;
  description: string;
  trigger: string;
  achieved: boolean;
  companion: boolean;
}

export interface ShortcutOffer {
  id: string;
  name: string;
  description: string;
  trigger: string;
  floor: number;
  unlocked: boolean;
}

export interface RunSummary {
  outcome: string;
  startFloor: number;
  highestFloor: number;
  floorsCleared: number;
  enemiesDefeated: number;
  creditsGained: number;
  materialsGained: number;
  relicsGained: number;
  unitsLost: number;
  survivors: number;
}

export interface Reinforcement {
  units: Squad;
  currentFloor: number;
  supplies: number;
  maxSupplies: number;
}

export interface GameState {
  credit: number;
  units: Squad;
  unitsOnTower: Squad;
  village: Village;
  tower?: Tower;
  expeditionActive: boolean;
  gameOutcome: string;
  resources: ResourceWallet;
  carriedLoot: ResourceWallet;
  carriedCredits: number;
  supplies: number;
  maxSupplies: number;
  lastFoodReturned: number;
  homePartyWounds: number;
  foodHealRate: number;
  homeUnitCount: number;
  foodUpkeep: number;
  netFoodPerSecond: number;
  /** Food Recovery spent on the home party in the last tick. */
  recoverySpend: number;
  /** Seconds until Starvation begins at current rates; null while the Fields cover Upkeep. */
  pantryHoldSeconds: number | null;
  unlockedUnitTypes: string[];
  availableUpgrades: UpgradeOffer[];
  damageMultiplier: number;
  supplyCapacityBonus: number;
  highestFloor: number;
  expeditionsCompleted: number;
  enemiesDefeated: number;
  milestones: MilestoneOffer[];
  shortcuts: ShortcutOffer[];
  lastRunSummary: RunSummary | null;
  reinforcements: Reinforcement[];
  tradeViews: TradeView[];
  tradePreviews: TradePreview[];
  quarryRate: number;
  studyRate: number;
  /** Share the Quarry and the Study yield above their base, from Highest Floor. */
  yieldBonus: number;
  stockroomCap: number;
  quarryHeapCap: number;
  heapDeliveryPerFloor: number;
  theCount: TheCount;
  tally: Tally;
  villageTierLevel: number;
  villageTierName: string;
  /** What the Village Tier has opened so far, comma-separated. */
  villageTierOpens: string;
  ledger: LedgerLine[];
}

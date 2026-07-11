import { Unit } from './unit.enum';
import { Tower } from './tower';
import { Member } from './member';
import { Village } from './village';

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
}

export interface GameState {
  mana: number;
  manaPerSecond: number;
  credit: number;
  units: Squad;
  unitsOnTower: Squad;
  village: Village;
  tower?: Tower;
  upgrades: string[];
  prestigePoints: number;
  expeditionActive: boolean;
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
  unlockedUnitTypes: string[];
  availableUpgrades: UpgradeOffer[];
  damageMultiplier: number;
  supplyCapacityBonus: number;
  deepestFloor: number;
  expeditionsCompleted: number;
  milestones: MilestoneOffer[];
}

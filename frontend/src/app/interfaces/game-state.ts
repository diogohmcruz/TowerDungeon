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
  resources: ResourceWallet;
  carriedLoot: ResourceWallet;
  supplies: number;
  maxSupplies: number;
  lastFoodReturned: number;
  homePartyWounds: number;
  foodHealRate: number;
  homeUnitCount: number;
  foodUpkeep: number;
  netFoodPerSecond: number;
}

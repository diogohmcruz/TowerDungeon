import { Unit } from "./unit.enum";

export interface Member {
  id: number;
  name: string;
  stats: string;
  currentHealth: number;
}

export type Squad = {
  [unit in Unit]: Member[];
};

export interface Tower {
  currentFloor: number;
  maxFloor: number;
  enemies: Member[];
}

export interface GameState {
  mana: number;
  manaPerSecond: number;
  credit: number;
  units: Squad;
  unitsOnTower: Squad;
  tower?: Tower;
  upgrades: string[];
  prestigePoints: number;
}

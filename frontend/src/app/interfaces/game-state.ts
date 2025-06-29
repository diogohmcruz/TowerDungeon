import { Unit } from './unit.enum';
import { Tower } from './tower';
import { Member } from './member';
import { Village } from './village';

export type Squad = {
  [unit in Unit]: Member[];
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
}

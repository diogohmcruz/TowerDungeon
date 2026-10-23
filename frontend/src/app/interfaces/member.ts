import { UnitStats } from './unit.enum';

export interface Member {
  id: number;
  name: string;
  stats: UnitStats;
  currentHealth: number;
  maxHealth?: number;
  /** This unit's own rolled damage per strike (type damage x its damage factor). */
  damage?: number;
  maxMana?: number;
  currentMana?: number;
  boss?: boolean;
}

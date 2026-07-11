import { UnitStats } from './unit.enum';

export interface Member {
  id: number;
  name: string;
  stats: UnitStats;
  currentHealth: number;
  maxHealth?: number;
  maxMana?: number;
  currentMana?: number;
  boss?: boolean;
}

import { UnitStats } from './unit.enum';

export interface Member {
  id: number;
  name: string;
  stats: UnitStats;
  currentHealth: number;
}

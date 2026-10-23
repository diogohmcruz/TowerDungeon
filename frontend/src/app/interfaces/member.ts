import { UnitStats } from './unit.enum';

export type BodyPartKind = 'TORSO' | 'HEAD' | 'LEFT_ARM' | 'RIGHT_ARM' | 'LEFT_LEG' | 'RIGHT_LEG';

/** One body part of a unit; a lost arm or leg stays listed but no longer counts. */
export interface BodyPart {
  kind: BodyPartKind;
  currentHealth: number;
  maxHealth: number;
  lost: boolean;
}

export interface Member {
  id: number;
  name: string;
  stats: UnitStats;
  currentHealth: number;
  maxHealth?: number;
  /** Per-part health; currentHealth and maxHealth are the sums over the parts not lost. */
  parts?: BodyPart[];
  /** This unit's own rolled damage per strike (type damage x its damage factor). */
  damage?: number;
  maxMana?: number;
  currentMana?: number;
  boss?: boolean;
}

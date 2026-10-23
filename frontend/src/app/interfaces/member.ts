import { UnitStats } from './unit.enum';

export type BodyPartKind =
  | 'TORSO'
  | 'HEAD'
  | 'LEFT_ARM'
  | 'RIGHT_ARM'
  | 'LEFT_LEG'
  | 'RIGHT_LEG';

/** One body part of a unit; a lost arm or leg stays listed but no longer counts. */
export interface BodyPart {
  kind: BodyPartKind;
  currentHealth: number;
  maxHealth: number;
  lost: boolean;
}

export type SlotName =
  | 'HEAD'
  | 'TORSO'
  | 'LEFT_ARM'
  | 'RIGHT_ARM'
  | 'LEFT_LEG'
  | 'RIGHT_LEG'
  | 'TRINKET_1'
  | 'TRINKET_2';

/** A worn piece of gear; stats missing from the map count as zero. */
export interface Item {
  id: string;
  name: string;
  tier: string;
  category: string;
  stats: Partial<Record<string, number>>;
  effectId?: string | null;
}

/** A fear of one enemy type; the type is null until the unit first meets it. */
export interface Phobia {
  enemyType: string | null;
  known: boolean;
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
  /** Current and maximum sanity. */
  sanity?: number;
  maxSanity?: number;
  phobias?: Phobia[];
  /** What the unit wears, by slot; an empty slot is absent. */
  gear?: { slots?: Partial<Record<SlotName, Item>> };
  maxMana?: number;
  currentMana?: number;
  boss?: boolean;
}

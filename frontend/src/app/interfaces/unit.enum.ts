export interface AttackType {
  MELEE: 'MELEE';
  RANGED: 'RANGED';
  MAGIC: 'MAGIC';
  HEAL: 'HEAL';
}

export type Weakness = {
  [unit in Unit]: number;
};

export interface UnitStats {
  type?: string;
  cost?: number;
  health: number;
  damage: number;
  attackType: AttackType;
  weight?: number;
  weaknesses?: Weakness[];
  supplyBonus?: number;
}

export enum Unit {
  WARRIOR = 'WARRIOR',
  ARCHER = 'ARCHER',
  MAGE = 'MAGE',
  HEALER = 'HEALER',
  TANK = 'TANK',
  ROGUE = 'ROGUE',
  NECROMANCER = 'NECROMANCER',
  DRACO_METAMORPH = 'DRACO_METAMORPH',
  PORTER = 'PORTER',
}

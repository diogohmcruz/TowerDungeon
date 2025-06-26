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
  cost?: number;
  health: number;
  damage: number;
  attackType: AttackType;
  weight?: number;
  weaknesses?: Weakness[];
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
}

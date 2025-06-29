import { Member } from './member';

export interface Tower {
  currentFloor: number;
  maxFloor: number;
  floors: Record<number, TowerFloor>;
}

export interface TowerFloor {
  id: number;
  difficulty: number;
  enemies: Member[];
}

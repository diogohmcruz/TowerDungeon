import { Member } from './member';

export type ArchitectureId =
  | 'COLONNADE'
  | 'NAVE'
  | 'PILLAR_MAZE'
  | 'STAIR_SHAFT'
  | 'SPAN'
  | 'WATERWAY'
  | 'LOOP_ROOMS'
  | 'STATUARY'
  | 'WARREN'
  | 'GATE'
  | 'WELL'
  | 'EXPANSE';

export interface Tower {
  currentFloor: number;
  maxFloor: number;
  floors: Record<number, TowerFloor>;
  /** Bumps when a floor is added or a guardian is first slain, not when enemies are re-populated. */
  layoutVersion: number;
  /** Floors of the guardians slain this campaign, lowest first. */
  slainGuardians: number[];
}

export interface TowerFloor {
  id: number;
  difficulty: number;
  enemies: Member[];
  boss?: boolean;
  architecture: ArchitectureId;
  variant: 0 | 1 | 2 | 3;
}

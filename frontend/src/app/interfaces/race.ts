export type ContenderStatus = 'RACING' | 'FINISHED' | 'TIMED_OUT' | 'DEFEATED';

/** The Trial a Campaign is, as the server sends it in `GameState.trial`. */
export interface TrialRun {
  raceId: string;
  attempt: number;
  finishFloor: number;
  /** The clock's limit, in lifecycle ticks (one per second). */
  limitTicks: number;
  /** Connected, unheld ticks so far (one per second). */
  ticksSinceStart: number;
  status: ContenderStatus;
  expeditions: number;
  wipes: number;
  /** The tick at which each Floor was first reached. */
  arrivals: Record<string, number>;
}

/** The Cost column: what the climb has spent so far. Never added to the Order of Arrival. */
export interface TrialCost {
  expeditions: number;
  wipes: number;
  unitsLost: number;
  lootBanked: number;
}

/** What a Key may read of a race: the Terms without the seed, and the Key's own standing. */
export interface RaceTerms {
  raceId: string;
  kind: 'SEASON' | 'SPRINT';
  finishFloor: number;
  opensAt: string | null;
  closesAt: string | null;
  limitSeconds: number;
  maxAttempts: number;
  maxConcurrent: number;
  attemptsUsed: number;
  attemptsLeft: number;
  /** The current attempt is still being run, so entering resumes it. */
  attemptOpen: boolean;
  /** A TrialDesk refusal name, or null when entry is open. */
  refusal: string | null;
}

export interface Arrival {
  floor: number;
  seconds: number;
}

export interface RaceRow {
  place: number;
  tied: boolean;
  alias: string;
  finished: boolean;
  status: ContenderStatus;
  seconds: number;
  highestFloor: number;
  arrivalSeconds: number;
  attempt: number;
  cost: TrialCost;
  timeline: Arrival[];
  you: boolean;
}

/** A race's finished table: hands and agents in separate tables. */
export interface RaceTableView {
  raceId: string;
  hands: RaceRow[];
  agents: RaceRow[];
}

/** One line of the Race record section of the Chronicle screen. */
export interface RaceRecord {
  raceId: string;
  attempt: number;
  finishFloor: number;
  floor: number;
  place: number;
  tied: boolean;
  of: number;
  seconds: number;
  fallen: number;
  at: string;
  line: string;
}

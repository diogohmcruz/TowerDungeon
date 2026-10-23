export interface Village {
  food: number;
  villagersCount: number;
  productionRate: number;
  breakers: number;
  scribes: number;
  knowledge: number;
  quarryHeap: number;
  emergencyTilling: boolean;
}

export type TradeId = 'TILLER' | 'BREAKER' | 'SCRIBE';

export interface TradeView {
  trade: TradeId;
  plural: string;
  workplace: string;
  hands: number;
  /** Null for the Fields, which has no cap. */
  posts: number | null;
  settling: number;
  settlingSeconds: number;
}

/** The margin after one more (+1) or one fewer (-1) hand in a Trade. */
export interface TradePreview {
  trade: TradeId;
  delta: 1 | -1;
  fields: number;
  upkeep: number;
  fieldsCoverUpkeep: boolean;
  pantryHoldSeconds: number | null;
  /** Why the move cannot be made; null when it can. */
  blocked: string | null;
}

export type TomeId = 'TILLERS_RECORD' | 'SEAM_CHARTS';

/** A Tome bought with Knowledge. */
export interface TomeView {
  tome: TomeId;
  title: string;
  /** What each level raises, e.g. "Fields output". */
  effect: string;
  /** Percent of the base each level adds. */
  bonusPercent: number;
  level: number;
  /** Knowledge the next level costs. */
  cost: number;
  /** The Stockroom cannot hold the cost of the next level. */
  gated: boolean;
  /** Why the next level cannot be bought now; null when it can. */
  blocked: string | null;
}

/** The next Post upgrade of the Quarry or the Scribe's Study. */
export interface PostUpgradeView {
  trade: TradeId;
  workplace: string;
  level: number;
  posts: number;
  nextPosts: number;
  /** Materials the upgrade costs. */
  cost: number;
  blocked: string | null;
}

export interface LedgerLine {
  text: string;
  loss: boolean;
}

/** The never-spent counters of the Campaign. */
export interface Tally {
  foodGrown: number;
  creditsBanked: number;
  materialsBanked: number;
  relicsBanked: number;
  floorsCleared: number;
  peakVillagers: number;
}

export interface TheCount {
  villagers: number;
  reserves: number;
}

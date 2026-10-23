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

export interface LedgerLine {
  text: string;
  loss: boolean;
}

export interface TheCount {
  villagers: number;
  reserves: number;
}

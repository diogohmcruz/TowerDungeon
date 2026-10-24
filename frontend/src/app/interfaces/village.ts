export interface Village {
  food: number;
  villagersCount: number;
  productionRate: number;
  breakers: number;
  scribes: number;
  salters: number;
  sporewardens: number;
  knowledge: number;
  salt: number;
  /** Dragon Spores in the Stockroom. */
  spores: number;
  quarryHeap: number;
  emergencyTilling: boolean;
}

export type TradeId =
  | 'TILLER'
  | 'BREAKER'
  | 'SCRIBE'
  | 'SALTER'
  | 'SPOREWARDEN';

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

export type TomeId = 'TILLERS_RECORD' | 'SEAM_CHARTS' | 'SURVIVORS_NOTES';

/** A Tome bought with Knowledge. */
export interface TomeView {
  tome: TomeId;
  title: string;
  /** What each level raises, e.g. "Fields output". */
  effect: string;
  /** Percent of the base each level adds. */
  bonusPercent: number;
  /** Flat amount each level adds, when it is not a percent (Survivors' Notes). */
  bonusFlat: number;
  level: number;
  /** Knowledge the next level costs. */
  cost: number;
  /** The Stockroom cannot hold the cost of the next level. */
  gated: boolean;
  /** Why the next level cannot be bought now; null when it can. */
  blocked: string | null;
}

/** The Thought-Binding Ring, bought with Trust. */
export interface RingView {
  level: number;
  maxLevel: number;
  /** Trust the next level costs. */
  cost: number;
  maxed: boolean;
  /** Floors per second the Ring adds to a Wave's climb now, and per level. */
  speedBonus: number;
  speedPerLevel: number;
  /** Carrying Capacity the Ring adds to every Wave now, and per level. */
  capacityBonus: number;
  capacityPerLevel: number;
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

/**
 * What setting out with the default party (every Reserve) would do. One field group per Loadout
 * line; Panacea and Trust join as more fields.
 */
export interface LoadoutPreview {
  carryingCapacity: number;
  suppliesTaken: number;
  pantryAfter: number;
  partySize: number;
  reservesStaying: number;
  /** Seconds the Pantry holds after setting out; null when the Fields cover Upkeep. */
  pantryHoldSeconds: number | null;
  drainPerUnit: number;
  saltedDrainPerUnit: number;
  suppliesPerSalt: number;
  /** Salt that would still salt something. */
  saltUseful: number;
  saltHeld: number;
  saltPansOpen: boolean;
  /** Trust held, whole. */
  trustHeld: number;
  /** Carrying Capacity a Salt Pack takes. */
  packCapacity: number;
  /** Unit types in the Reserves, any of which can be the Salt-bearer. */
  bearerTypes: string[];
  /** Dragon Spores in the Stockroom, whole. */
  sporesHeld: number;
  panaceaSpores: number;
  panaceaMaterials: number;
  /** Materials in the Vault, which a Panacea draws on. */
  vaultMaterials: number;
  /** Carrying Capacity a Panacea takes up. */
  panaceaCapacity: number;
  /** Why a Panacea cannot be made now; null when it can. */
  panaceaBlocked: string | null;
  /** Overreach, present only while the Roll holds it. */
  overreach?: OverreachSwitch | null;
  /** The Quartermaster's Tally, present only while the Roll holds it. */
  tally?: QuartermastersTally | null;
  /** Muster Roll, present only while the Roll holds it. */
  musterRoll?: MusterRollOffer | null;
}

/** What Overreach multiplies while an Expedition is under it. */
export interface OverreachSwitch {
  supplyDrainMultiplier: number;
  starvationDamageMultiplier: number;
  lootMultiplier: number;
}

/** A Loadout saved in a slot of the Tally (not the Party). */
export interface SavedLoadout {
  slot: number;
  salt: number;
  saltPack: number;
  saltBearer: string | null;
  panacea: boolean;
  overreach: boolean;
}

export interface QuartermastersTally {
  /** How many Loadouts may be saved. */
  slots: number;
  saved: SavedLoadout[];
  runwayTicks: number | null;
  saltedRunwayTicks: number | null;
  overreachRunwayTicks: number | null;
}

/** What Muster Roll would send again; `ready` is false before a Party has set out this Generation. */
export interface MusterRollOffer {
  ready: boolean;
  startFloor: number;
  /** Units sent; 0 is every Unit in the Reserves. */
  partyUnits: number;
  salt: number;
  saltPack: number;
  saltBearer: string | null;
  panacea: boolean;
  overreach: boolean;
}

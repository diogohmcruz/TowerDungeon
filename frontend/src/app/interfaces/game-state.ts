import { Unit } from './unit.enum';
import { SuccessionView } from './succession';
import { Tower } from './tower';
import { RaceRecord, TrialCost, TrialRun } from './race';
import { Member } from './member';
import {
  LedgerLine,
  PostUpgradeView,
  Tally,
  LoadoutPreview,
  RingView,
  TheCount,
  TomeView,
  TradePreview,
  TradeView,
  Village,
} from './village';

export type Squad = {
  [unit in Unit]: Member[];
};

export type ResourceWallet = {
  MATERIALS: number;
  RELICS: number;
};

export interface UpgradeOffer {
  id: string;
  name: string;
  description: string;
  level: number;
  maxed: boolean;
  repeatable: boolean;
  unlockUnit: string | null;
  nextCost: Partial<ResourceWallet>;
}

export interface MilestoneOffer {
  id: string;
  name: string;
  description: string;
  trigger: string;
  achieved: boolean;
  companion: boolean;
}

export interface ShortcutOffer {
  id: string;
  name: string;
  description: string;
  trigger: string;
  floor: number;
  unlocked: boolean;
}

/** Where the Port stands: out of sight, a footing to build on, or finished. */
export type PortState = 'HIDDEN' | 'FOOTING' | 'BUILT';

/** The Port as the frame reports it (backend `PortView`). */
export interface PortView {
  state: PortState;
  /** Completed Expeditions that open the footing. */
  footingExpeditions: number;
  expeditionsCompleted: number;
  materialsCost: number;
  creditsCost: number;
}

export interface Fallen {
  name: string;
  type: string;
  floor: number;
}

export interface RunSummary {
  outcome: string;
  startFloor: number;
  highestFloor: number;
  floorsCleared: number;
  enemiesDefeated: number;
  creditsGained: number;
  materialsGained: number;
  relicsGained: number;
  unitsLost: number;
  survivors: number;
  fallen: Fallen[];
  /** Dragon Spores brought home (or lost, on a Wipe). */
  sporesGained?: number;
}

/** The Toll broken down by counter, in Marks. */
export interface Toll {
  foodMarks: number;
  materialsMarks: number;
  creditsMarks: number;
  relicsMarks: number;
  expeditionsMarks: number;
  ascentMarks: number;
  guardianMarks: number;
  marks: number;
  toll: number;
  newGround: number;
  legacy: number;
  residueNext: number;
}

/** What ending the Campaign now would pay; absent once the Campaign has ended. */
export interface TollForecast {
  toll: Toll;
  /** The Toll that pays one point of Legacy. */
  pointsDivisor: number;
  nextLegacyAt: number;
  /** Legacy the forecast must reach before Exodus is offered. */
  exodusMinimum: number;
  /** Exodus is open: playing, no Expedition active or Held, forecast at the minimum. */
  canExodus: boolean;
}

/** What an ended Campaign was worth, stored when it ended. */
export interface SuccessionReport {
  outcome: string;
  generation: number;
  toll: Toll;
  legacy: number;
  pity: boolean;
  guardiansSlain: number;
  highestFloor: number;
  expeditions: number;
}

export interface Reinforcement {
  units: Squad;
  currentFloor: number;
  supplies: number;
  maxSupplies: number;
}

/** The session's own facts, sent with every frame. */
export interface SessionMeta {
  resumed: boolean;
  savedAt: string | null;
  schema: number;
  notices: { code: string; message: string }[];
  /** A Held Party is waiting for the player to resume. */
  held: boolean;
  succession?: SuccessionView | null;
}

export interface GameState {
  credit: number;
  units: Squad;
  unitsOnTower: Squad;
  village: Village;
  tower?: Tower;
  expeditionActive: boolean;
  gameOutcome: string;
  /** ACTIVE while the Campaign is played, OFFER once it has ended. */
  phase?: string;
  /** The Succession report computed when the Campaign ended. */
  pending?: SuccessionReport | null;
  /** The Toll forecast; absent once the Campaign has ended. */
  forecast?: TollForecast | null;
  resources: ResourceWallet;
  carriedLoot: ResourceWallet;
  carriedCredits: number;
  supplies: number;
  maxSupplies: number;
  lastFoodReturned: number;
  homePartyWounds: number;
  foodHealRate: number;
  homeUnitCount: number;
  foodUpkeep: number;
  netFoodPerSecond: number;
  /** Food Recovery spent on the home party in the last tick. */
  recoverySpend: number;
  /** Seconds until Starvation begins at current rates; null while the Fields cover Upkeep. */
  pantryHoldSeconds: number | null;
  unlockedUnitTypes: string[];
  availableUpgrades: UpgradeOffer[];
  damageMultiplier: number;
  supplyCapacityBonus: number;
  highestFloor: number;
  expeditionsCompleted: number;
  enemiesDefeated: number;
  milestones: MilestoneOffer[];
  shortcuts: ShortcutOffer[];
  lastRunSummary: RunSummary | null;
  reinforcements: Reinforcement[];
  tradeViews: TradeView[];
  tradePreviews: TradePreview[];
  quarryRate: number;
  /** Salt the Salt Pans make per second. */
  saltRate: number;
  /** Dragon Spores the Spore Cellar makes per second. */
  sporeRate: number;
  /** What setting out with every Reserve would do, for the Loadout. */
  loadout: LoadoutPreview;
  /** Supplies carried that are salted. */
  saltedSupplies: number;
  /** Dragon Spores found on this Expedition, banked at Extraction. */
  carriedSpores: number;
  /** Whether the Party out on the Tower still carries an unspent Panacea. */
  panaceaCarried: boolean;
  studyRate: number;
  /** Share the Quarry and the Study yield above their base, from Highest Floor. */
  yieldBonus: number;
  stockroomCap: number;
  quarryHeapCap: number;
  heapDeliveryPerFloor: number;
  theCount: TheCount;
  tally: Tally;
  villageTierLevel: number;
  villageTierName: string;
  /** The Port; absent from older frames, which means hidden. */
  port?: PortView;
  /**
   * True while the Boat lies docked at the pier (false on a Voyage or before it
   * has arrived). The Boat is #83: the backend does not send this field yet, so
   * the Village reads it as absent, no hull. Rename here when #83 names it.
   */
  boatDocked?: boolean;
  /** What the Village Tier has opened so far, comma-separated. */
  villageTierOpens: string;
  tomeViews: TomeView[];
  /** Trust held, whole; earned on Extraction. */
  trustHeld: number;
  ring: RingView;
  postUpgrades: PostUpgradeView[];
  ledger: LedgerLine[];
  session?: SessionMeta;
  /** The Trial this Campaign is; absent for a real Campaign. */
  trial?: TrialRun | null;
  /** What the Trial has cost so far; absent outside a Trial. */
  trialCost?: TrialCost | null;
  /** The player's Race records, for the Chronicle screen; empty in a Trial. */
  raceRecords?: RaceRecord[];
  /** Finish Floors to mark on a pillar: only Floors the player has discovered. */
  pillarMarks?: number[];
}

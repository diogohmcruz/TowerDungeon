/** One Course of the Roll: its gate and whether the Legacy spent below has met it. */
export interface SuccessionCourse {
  course: number;
  gate: number;
  spentBelow: number;
  open: boolean;
}

/** One Inheritance on the Roll, held or not, with the server's reason it cannot be bought. */
export interface SuccessionNode {
  id: string;
  name: string;
  course: number;
  kind: string;
  cost: number;
  available: boolean;
  held: boolean;
  paid: number;
  /** A RollRefusal name; null when it can be bought, or when it is held. */
  whyNot: string | null;
  refundable: boolean;
}

/** One answer to a choice question (DOWRY or HEIR). */
export interface SuccessionOption {
  id: string;
  group: string;
  selected: boolean;
  isDefault: boolean;
}

export interface FallenName {
  name: string;
  type: string;
}

/** One ended Campaign in the Chronicle. */
export interface ChronicleEntry {
  generation: number;
  outcome: string;
  toll: number;
  legacy: number;
  highestFloor: number;
  guardiansSlain: number;
  expeditions: number;
  fallen: FallenName[];
}

/** What the Succession screen needs, sent in `session.succession` while the Campaign waits in OFFER. */
export interface SuccessionView {
  generation: number;
  legacy: number;
  spent: number;
  courses: SuccessionCourse[];
  inheritances: SuccessionNode[];
  choices: SuccessionOption[];
  chronicle: ChronicleEntry[];
}

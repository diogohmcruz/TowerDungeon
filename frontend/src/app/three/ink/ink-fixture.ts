import { Reinforcement, Squad } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { ArchitectureId, Tower, TowerFloor } from '../../interfaces/tower';
import { Unit } from '../../interfaces/unit.enum';

/**
 * Spike tooling for #15: a synthetic tower so the scene can be captured and
 * profiled without the backend. Active only when the page URL carries
 * `?inkFixture=<floors>` (optionally `&floor=<current>`, `&garrison=<n>`,
 * `&party=<n>`, `&waves=<n>`), and only in dev mode (see TowerScene).
 */
export interface InkFixture {
  tower: Tower;
  party: Squad;
  reinforcements: Reinforcement[];
  /** What the Expedition carries, for the Supplies post and the crates. */
  carried: CarriedFigures;
}

/** Supplies and the three loads the Party carries (`&supplies=`, `&maxSupplies=`, `&credits=`, `&materials=`, `&relics=`). */
export interface CarriedFigures {
  supplies: number;
  maxSupplies: number;
  credits: number;
  materials: number;
  relics: number;
}

/** The pinned Floors of the plan (architecture brainstorm 4.2), so captures show them. */
const PINS: Record<number, ArchitectureId> = {
  1: 'COLONNADE',
  3: 'STAIR_SHAFT',
  5: 'COLONNADE',
  10: 'GATE',
  14: 'LOOP_ROOMS',
  15: 'WATERWAY',
  20: 'WELL',
  25: 'PILLAR_MAZE',
  40: 'GATE',
  50: 'GATE',
  59: 'STATUARY',
  60: 'GATE',
  80: 'EXPANSE',
  100: 'GATE',
};
const FILLER: ArchitectureId[] = [
  'COLONNADE',
  'NAVE',
  'STAIR_SHAFT',
  'COLONNADE',
];

const ENEMY_TYPES = [
  'SLIME',
  'DEAD_SOLDIERS',
  'TOWER_BEAST',
  'CHIMERA',
  'CHYMUS_INSECT',
  'STONE_GOLEM',
  'NECROMANCER',
  'DEAD_DRAGON',
  'DRAGON',
];
const UNIT_TYPES: Unit[] = [
  Unit.WARRIOR,
  Unit.ARCHER,
  Unit.MAGE,
  Unit.HEALER,
  Unit.TANK,
];

function member(id: number, type: string): Member {
  return {
    id,
    name: type,
    currentHealth: 10,
    maxHealth: 10,
    stats: {
      type,
      health: 10,
      damage: 1,
      attackType: 'MELEE' as unknown as never,
    },
  };
}

export function readFixtureParams(search: string): {
  floors: number;
  current: number;
  garrison: number;
  party: number;
  waves: number;
  carried: CarriedFigures;
} | null {
  const q = new URLSearchParams(search);
  const floors = Number(q.get('inkFixture'));
  if (!q.has('inkFixture') || !Number.isFinite(floors) || floors < 1) {
    return null;
  }
  return {
    floors,
    current: Math.min(floors, Number(q.get('floor') ?? floors)),
    garrison: Number(q.get('garrison') ?? 5),
    party: Number(q.get('party') ?? 8),
    waves: Number(q.get('waves') ?? 0),
    carried: {
      supplies: Number(q.get('supplies') ?? 120),
      maxSupplies: Number(q.get('maxSupplies') ?? 200),
      credits: Number(q.get('credits') ?? 340),
      materials: Number(q.get('materials') ?? 45),
      relics: Number(q.get('relics') ?? 1),
    },
  };
}

export function buildFixture(p: {
  floors: number;
  current: number;
  garrison: number;
  party: number;
  waves?: number;
  carried?: CarriedFigures;
}): InkFixture {
  const floors: Record<number, TowerFloor> = {};
  for (let n = 1; n <= p.floors; n++) {
    const boss = n % 10 === 0;
    const count = n === p.current ? p.garrison : boss ? 1 : 2;
    floors[n] = {
      id: n,
      difficulty: 1,
      boss,
      architecture: PINS[n] ?? FILLER[n % FILLER.length],
      variant: (n % 4) as 0 | 1 | 2 | 3,
      enemies: Array.from({ length: count }, (_, i) => ({
        ...member(n * 1000 + i, ENEMY_TYPES[(n + i) % ENEMY_TYPES.length]),
        ...(boss ? { boss: true } : {}),
      })),
    };
  }
  const party = Object.fromEntries(
    Object.values(Unit).map((u) => [u, [] as Member[]]),
  ) as unknown as Squad;
  for (let i = 0; i < p.party; i++) {
    const t = UNIT_TYPES[i % UNIT_TYPES.length];
    party[t].push(member(i + 1, t));
  }
  return {
    carried: p.carried ?? {
      supplies: 120,
      maxSupplies: 200,
      credits: 340,
      materials: 45,
      relics: 1,
    },
    tower: {
      currentFloor: p.current,
      maxFloor: 100,
      floors,
      layoutVersion: 1,
      slainGuardians: Array.from(
        { length: Math.floor(p.floors / 10) },
        (_, i) => (i + 1) * 10,
      ).filter((n) => n < p.current),
    },
    party,
    reinforcements: Array.from({ length: p.waves ?? 0 }, (_, i) => {
      const units = Object.fromEntries(
        Object.values(Unit).map((u) => [u, [] as Member[]]),
      ) as unknown as Squad;
      for (let k = 0; k < 2 + i * 3; k++) {
        const t = UNIT_TYPES[k % UNIT_TYPES.length];
        units[t].push(member(5000 + i * 100 + k, t));
      }
      return {
        units,
        currentFloor: Math.max(1, p.current - 1 - i * 2),
        supplies: 10,
        maxSupplies: 10,
      };
    }),
  };
}

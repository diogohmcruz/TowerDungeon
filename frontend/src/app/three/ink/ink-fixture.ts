import { Reinforcement, Squad } from '../../interfaces/game-state';
import { Member } from '../../interfaces/member';
import { Tower, TowerFloor } from '../../interfaces/tower';
import { Unit } from '../../interfaces/unit.enum';

/**
 * Spike tooling for #15: a synthetic tower so the scene can be captured and
 * profiled without the backend. Active only when the page URL carries
 * `?inkFixture=<floors>` (optionally `&floor=<current>`, `&garrison=<n>`,
 * `&party=<n>`), and only in dev mode (see TowerScene).
 */
export interface InkFixture {
  tower: Tower;
  party: Squad;
  reinforcements: Reinforcement[];
}

const ENEMY_TYPES = ['SLIME', 'DEAD_SOLDIERS', 'TOWER_BEAST', 'CHIMERA'];
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
  };
}

export function buildFixture(p: {
  floors: number;
  current: number;
  garrison: number;
  party: number;
}): InkFixture {
  const floors: Record<number, TowerFloor> = {};
  for (let n = 1; n <= p.floors; n++) {
    const boss = n % 10 === 0;
    const count = n === p.current ? p.garrison : boss ? 1 : 2;
    floors[n] = {
      id: n,
      difficulty: 1,
      boss,
      architecture: 'COLONNADE',
      variant: 0,
      enemies: Array.from({ length: count }, (_, i) =>
        member(n * 1000 + i, ENEMY_TYPES[(n + i) % ENEMY_TYPES.length]),
      ),
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
    tower: {
      currentFloor: p.current,
      maxFloor: 100,
      floors,
      layoutVersion: 1,
      slainGuardians: [],
    },
    party,
    reinforcements: [],
  };
}

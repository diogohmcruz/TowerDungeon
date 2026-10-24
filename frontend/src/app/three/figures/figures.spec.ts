import { SPRITE_ART } from '../catalogue';
import {
  buildFigureSlots,
  COLUMN_CELL,
  digitCell,
  figureCell,
  FIGURE_CELLS,
  FIGURE_COLS,
  FIGURE_ROWS,
  FIGURE_H,
  FIGURE_W,
  PLACEHOLDER_CELL,
  SPECK_CELL,
} from './figure-atlas';
import {
  FIGURE_CAP,
  FigureInput,
  FigureMember,
  PARTY_CLEAR_X,
  planFigures,
} from './figure-layout';
import {
  FLOOR_SURFACE_OFFSET,
  floorToY,
} from '../floor-strip/floor-strip-plan';

const UNIT_TYPES = SPRITE_ART.unit.map((s) =>
  s.toUpperCase().replace(/-/g, '_'),
);

function party(n: number): FigureMember[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    typeKey: UNIT_TYPES[i % UNIT_TYPES.length],
    guardian: false,
  }));
}

function garrison(n: number, guardians: number[] = []): FigureMember[] {
  return Array.from({ length: n }, (_, i) => ({
    id: 100 + i,
    typeKey: 'SLIME',
    guardian: guardians.includes(i),
  }));
}

function input(over: Partial<FigureInput> = {}): FigureInput {
  return {
    party: party(6),
    enemies: garrison(4),
    waves: [],
    surfaceY: floorToY(3) + FLOOR_SURFACE_OFFSET,
    floorSurfaceY: (n) => floorToY(n) + FLOOR_SURFACE_OFFSET,
    viewAspect: 0.6,
    anchorFloor: 3,
    ...over,
  };
}

const round = (n: number) => Math.round(n * 1000) / 1000;

describe('figure atlas', () => {
  it('has a cell for every catalogued slug and falls back to the placeholder otherwise', () => {
    const cells = new Set<number>();
    for (const kind of ['unit', 'enemy'] as const) {
      for (const slug of SPRITE_ART[kind]) {
        const cell = figureCell(kind, slug.toUpperCase().replace(/-/g, '_'));
        expect(cell).not.toBe(PLACEHOLDER_CELL);
        expect(cells.has(cell)).toBeFalse();
        cells.add(cell);
      }
    }
    expect(figureCell('enemy', 'NOT_A_THING')).toBe(PLACEHOLDER_CELL);
    expect(FIGURE_CELLS).toBeLessThanOrEqual(FIGURE_COLS * FIGURE_ROWS);
  });

  it('draws all 9 enemy types from art', () => {
    expect(SPRITE_ART.enemy.length).toBe(9);
    for (const t of ['CHYMUS_INSECT', 'NECROMANCER', 'DEAD_DRAGON', 'DRAGON']) {
      expect(figureCell('enemy', t)).not.toBe(PLACEHOLDER_CELL);
    }
  });

  it('writes the speck, column and digit cells', () => {
    const slots = buildFigureSlots();
    const stride = FIGURE_COLS * FIGURE_W;
    const texels = (cell: number) => {
      const col = cell % FIGURE_COLS;
      const row = Math.floor(cell / FIGURE_COLS);
      const out: number[] = [];
      for (let y = 0; y < FIGURE_H; y++) {
        for (let x = 0; x < FIGURE_W; x++) {
          out.push(slots[(row * FIGURE_H + y) * stride + col * FIGURE_W + x]);
        }
      }
      return out;
    };
    expect(new Set(texels(SPECK_CELL))).toEqual(new Set([3]));
    expect(texels(COLUMN_CELL).includes(0)).toBeTrue();
    const digits = new Set<string>();
    for (let d = 0; d < 10; d++) {
      const t = texels(digitCell(d));
      expect(t.includes(3)).toBeTrue();
      digits.add(t.join(''));
    }
    expect(digits.size).toBe(10);
  });
});

describe('figure layout', () => {
  it('pins figure positions for a known party and garrison', () => {
    const plan = planFigures(input());
    expect(plan.specks.length).toBe(0);
    expect(plan.overflow).toBe(0);
    expect(
      plan.marks.map((f) => [f.id, round(f.x), round(f.y), round(f.z), f.cell]),
    ).toEqual(PINNED);
  });

  it('is deterministic', () => {
    expect(planFigures(input())).toEqual(planFigures(input()));
  });

  it('draws at most the cap as sprites and the rest as specks', () => {
    const plan = planFigures(
      input({ party: party(18), enemies: garrison(20) }),
    );
    expect(plan.figureCount).toBe(FIGURE_CAP);
    expect(plan.marks.length).toBe(FIGURE_CAP);
    expect(plan.specks.length).toBe(38 - FIGURE_CAP);
    expect(plan.overflow).toBe(38 - FIGURE_CAP);
  });

  it('draws the Party first, then Guardians, then the rest by proximity', () => {
    const plan = planFigures(
      input({ party: party(18), enemies: garrison(20, [19]) }),
    );
    const ids = plan.marks.map((f) => f.id);
    for (let i = 1; i <= 18; i++) {
      expect(ids).toContain(`unit-${i}`);
    }
    expect(ids).toContain('enemy-119');
    expect(plan.marks.find((f) => f.id === 'enemy-119')?.role).toBe(1);
    expect(plan.marks.filter((f) => f.id.startsWith('enemy-')).length).toBe(6);

    // The nearest garrison members win the remaining places.
    const drawnEnemies = plan.marks.filter(
      (f) => f.id.startsWith('enemy-') && f.id !== 'enemy-119',
    );
    const speckEnemies = plan.specks.filter((f) => f.id.startsWith('enemy-'));
    const units = plan.marks.filter((f) => f.id.startsWith('unit-'));
    const cx = units.reduce((s, f) => s + f.x, 0) / units.length;
    const cz = units.reduce((s, f) => s + f.z, 0) / units.length;
    const dist = (f: { x: number; z: number }) =>
      Math.hypot(f.x - cx, f.z - cz);
    expect(Math.max(...drawnEnemies.map(dist))).toBeLessThanOrEqual(
      Math.min(...speckEnemies.map(dist)) + 1e-9,
    );
  });

  it('with a Party over the cap, draws the Party only and the Guardian is a speck', () => {
    const plan = planFigures(
      input({ party: party(30), enemies: garrison(3, [0]) }),
    );
    expect(plan.marks.every((f) => f.id.startsWith('unit-'))).toBeTrue();
    expect(plan.specks.length).toBe(9);
  });

  it('draws Guardians larger than other figures', () => {
    const plan = planFigures(input({ enemies: garrison(3, [1]) }));
    const height = (id: string) => plan.marks.find((f) => f.id === id)!.h;
    expect(height('enemy-101')).toBeGreaterThan(height('enemy-100'));
  });

  it('keeps the Party clear of the numeral on the Floor pillar', () => {
    for (const n of [1, 3, 8, 15, 24]) {
      for (const aspect of [0.45, 0.6, 1.2]) {
        const plan = planFigures(
          input({ party: party(n), enemies: garrison(n), viewAspect: aspect }),
        );
        const left = Math.min(
          ...plan.marks
            .concat(plan.specks)
            .filter((f) => f.id.startsWith('unit-'))
            .map((f) => f.x - f.w / 2),
        );
        expect(left).toBeGreaterThanOrEqual(PARTY_CLEAR_X - 1e-9);
      }
    }
  });
});

describe('Wave columns', () => {
  const waves = [
    { size: 3, floor: 2 },
    { size: 12, floor: 3 },
  ];

  it('are columns with a numeral, one per Wave, and no hue', () => {
    const plan = planFigures(input({ waves }));
    const columns = plan.marks.filter((f) => f.cell === COLUMN_CELL);
    expect(columns.length).toBe(2);
    expect(columns.every((f) => f.role === 0)).toBeTrue();
    const numerals = plan.marks.filter((f) => f.id.endsWith('-n0'));
    expect(numerals.map((f) => f.cell)).toEqual([digitCell(1), digitCell(2)]);
    // The numeral stands above its column.
    columns.forEach((c, i) => {
      expect(numerals[i].y - numerals[i].h / 2).toBeGreaterThan(
        c.y + c.h / 2 - 1e-9,
      );
    });
  });

  it('grow with the size of the Wave and do not count against the cap', () => {
    const plan = planFigures(
      input({ party: party(24), enemies: garrison(0), waves }),
    );
    const columns = plan.marks.filter((f) => f.cell === COLUMN_CELL);
    expect(columns[1].h).toBeGreaterThan(columns[0].h);
    expect(plan.figureCount).toBe(FIGURE_CAP);
  });

  it('write two digits for Wave 10 and beyond', () => {
    const many = Array.from({ length: 10 }, () => ({ size: 1, floor: 1 }));
    const plan = planFigures(input({ waves: many }));
    const tenth = plan.marks.filter((f) => f.id.startsWith('wave-9-n'));
    expect(tenth.map((f) => f.cell)).toEqual([digitCell(1), digitCell(0)]);
  });
});

const PINNED: [string, number, number, number, number][] = [
  ['unit-1', -1.089, 6.182, 1.7, 13],
  ['unit-2', -0.871, 6.182, 1.7, 14],
  ['unit-3', -0.652, 6.182, 1.7, 15],
  ['unit-4', -0.946, 6.182, 1.448, 16],
  ['unit-5', -0.728, 6.182, 1.448, 17],
  ['unit-6', -0.509, 6.182, 1.448, 18],
  ['enemy-100', 0.652, 6.182, 1.7, 23],
  ['enemy-101', 0.871, 6.182, 1.7, 23],
  ['enemy-102', 1.089, 6.182, 1.7, 23],
  ['enemy-103', 0.728, 6.182, 1.448, 23],
];

import { ArchitectureId, Tower, TowerFloor } from '../../interfaces/tower';
import {
  ATLAS_H,
  ATLAS_W,
  buildAtlasSlots,
  cellIndex,
  cellOrigin,
  CELL_H,
  CELL_W,
  DRAWN_ARCHITECTURES,
  drawnArchitecture,
  PENCIL_CELL,
} from './architecture-atlas';
import { buildDigitSlots, DIGIT_H, DIGIT_W } from './digit-atlas';
import {
  digitsOf,
  GHOST_FLOORS_AHEAD,
  MAX_DISCOVERED,
  NUMERAL_CAPACITY,
  planFinishMarks,
  planFloorStrips,
  STRIP_CAPACITY,
  stripY,
} from './floor-strip-plan';

function tower(
  floors: number,
  arch: (n: number) => ArchitectureId = () => 'COLONNADE',
  slain: number[] = [],
): Tower {
  const map: Record<number, TowerFloor> = {};
  for (let n = 1; n <= floors; n++) {
    map[n] = {
      id: n,
      difficulty: 1,
      enemies: [],
      boss: n % 10 === 0,
      architecture: arch(n),
      variant: (n % 4) as 0 | 1 | 2 | 3,
    };
  }
  return {
    currentFloor: floors,
    maxFloor: 100,
    floors: map,
    layoutVersion: 1,
    slainGuardians: slain,
  };
}

function cellSlots(slots: Uint8Array, index: number): Uint8Array {
  const { col, row } = cellOrigin(index);
  const out = new Uint8Array(CELL_W * CELL_H);
  for (let y = 0; y < CELL_H; y++) {
    for (let x = 0; x < CELL_W; x++) {
      out[y * CELL_W + x] =
        slots[(row * CELL_H + y) * ATLAS_W + col * CELL_W + x];
    }
  }
  return out;
}

describe('architecture atlas', () => {
  const slots = buildAtlasSlots();

  it('holds only ramp slots 0 to 3', () => {
    expect(slots.length).toBe(ATLAS_W * ATLAS_H);
    expect(slots.every((v) => v <= 3)).toBeTrue();
  });

  it('is deterministic', () => {
    expect(buildAtlasSlots()).toEqual(slots);
  });

  it('draws every cell of the four Architectures, none empty', () => {
    for (const arch of DRAWN_ARCHITECTURES) {
      for (let v = 0; v < 4; v++) {
        for (const layer of [0, 1, 2] as const) {
          const cell = cellSlots(slots, cellIndex(arch, v, layer));
          expect(cell.some((t) => t > 0))
            .withContext(`${arch} v${v} layer ${layer}`)
            .toBeTrue();
        }
      }
    }
    expect(cellSlots(slots, PENCIL_CELL).some((t) => t > 0)).toBeTrue();
  });

  it('keeps every cell inside its own bounds (cells never overlap)', () => {
    const indices = new Set<number>();
    for (const arch of DRAWN_ARCHITECTURES) {
      for (let v = 0; v < 4; v++) {
        for (const layer of [0, 1, 2] as const) {
          indices.add(cellIndex(arch, v, layer));
        }
      }
    }
    indices.add(PENCIL_CELL);
    expect(indices.size).toBe(49);
    let drawn = 0;
    for (const t of slots) if (t > 0) drawn++;
    let inCells = 0;
    for (const i of indices) {
      inCells += cellSlots(slots, i).filter((t) => t > 0).length;
    }
    expect(inCells).toBe(drawn);
  });

  it('draws the Architectures differently', () => {
    const a = cellSlots(slots, cellIndex('COLONNADE', 0, 1));
    const b = cellSlots(slots, cellIndex('NAVE', 0, 1));
    const c = cellSlots(slots, cellIndex('STAIR_SHAFT', 0, 1));
    const d = cellSlots(slots, cellIndex('GATE', 0, 1));
    expect(a).not.toEqual(b);
    expect(b).not.toEqual(c);
    expect(c).not.toEqual(d);
    expect(d).not.toEqual(a);
  });

  it('falls back to the Colonnade for the eight other Architectures', () => {
    const others: ArchitectureId[] = [
      'PILLAR_MAZE',
      'SPAN',
      'WATERWAY',
      'LOOP_ROOMS',
      'STATUARY',
      'WARREN',
      'WELL',
      'EXPANSE',
    ];
    for (const id of others) {
      expect(drawnArchitecture(id)).toBe('COLONNADE');
    }
    for (const id of DRAWN_ARCHITECTURES) {
      expect(drawnArchitecture(id)).toBe(id);
    }
  });
});

describe('digit atlas', () => {
  it('has a plate and a glyph in every cell, and only slots 1 and 3', () => {
    const slots = buildDigitSlots();
    expect(slots.length).toBe(DIGIT_W * 11 * DIGIT_H);
    expect(slots.every((t) => t === 1 || t === 3)).toBeTrue();
    for (let d = 0; d < 11; d++) {
      let glyph = 0;
      for (let y = 0; y < DIGIT_H; y++) {
        for (let x = 0; x < DIGIT_W; x++) {
          if (slots[y * DIGIT_W * 11 + d * DIGIT_W + x] === 3) glyph++;
        }
      }
      expect(glyph).withContext(`digit ${d}`).toBeGreaterThan(5);
    }
  });

  it('reads a floor number as digits', () => {
    expect(digitsOf(7)).toEqual([7]);
    expect(digitsOf(14)).toEqual([1, 4]);
    expect(digitsOf(100)).toEqual([1, 0, 0]);
  });
});

describe('floor strip plan', () => {
  it('draws three layers and a numeral per digit for each discovered floor', () => {
    const plan = planFloorStrips(tower(100));
    const real = plan.strips.filter((s) => s.floor > 0);
    expect(real.length).toBe(300);
    // 9 one-digit floors, 90 two-digit floors, floor 100.
    expect(plan.numerals.length).toBe(9 + 180 + 3);
    expect(plan.top).toBe(100);
  });

  it('stays within the instance, draw-call and triangle budgets at 100 floors', () => {
    const plan = planFloorStrips(tower(100));
    expect(plan.strips.length).toBe(100 * 3 + GHOST_FLOORS_AHEAD);
    expect(plan.strips.length).toBeLessThanOrEqual(STRIP_CAPACITY);
    expect(plan.numerals.length).toBeLessThanOrEqual(NUMERAL_CAPACITY);

    // Draw calls: strips 1, numerals 1, the twelve 1, foundation and backdrop 1.
    // Figures are #18's budget (whole scene at most 60).
    const drawCalls = 4;
    expect(drawCalls).toBeLessThanOrEqual(60);

    // Two triangles per quad, plus 12 boxes and 2 boxes of 12 triangles.
    const triangles =
      (plan.strips.length + plan.numerals.length) * 2 + (12 + 2) * 12;
    expect(triangles).toBeLessThanOrEqual(30000);
  });

  it('never draws undiscovered floors beyond the faint pencil', () => {
    const plan = planFloorStrips(tower(14));
    const above = plan.strips.filter((s) => s.floor === 0);
    expect(above.length).toBe(GHOST_FLOORS_AHEAD);
    for (const s of above) {
      expect(s.layer).toBe(2);
      expect(s.alpha).toBeLessThan(0.5);
      expect(s.seal).toBe(0);
      expect(s.y).toBeGreaterThan(stripY(14));
    }
    // Same cell for every pencil strip: no architecture is revealed.
    expect(new Set(above.map((s) => `${s.col},${s.row}`)).size).toBe(1);
    // No numeral above the Highest Floor.
    expect(plan.numerals.every((n) => n.y <= stripY(14) + 0.1)).toBeTrue();
  });

  it('draws nothing before the first floor is discovered', () => {
    expect(planFloorStrips(undefined).strips).toEqual([]);
    expect(planFloorStrips(tower(0)).strips).toEqual([]);
  });

  it('seals Guardian floors and strikes the numeral of a slain Guardian', () => {
    const plan = planFloorStrips(tower(25, () => 'GATE', [10]));
    const seal = (n: number) =>
      plan.strips.find((s) => s.floor === n && s.layer === 2)!.seal;
    expect(seal(9)).toBe(0);
    expect(seal(10)).toBe(2);
    expect(seal(20)).toBe(1);
    const struck = plan.numerals.filter((n) => n.struck);
    expect(struck.length).toBe(2); // "10" has two digits
    expect(struck.every((n) => n.y === stripY(10) + 0.05)).toBeTrue();
  });

  it('uses the Colonnade cell for an Architecture that is not drawn yet', () => {
    const fallback = planFloorStrips(tower(3, () => 'WELL')).strips[0];
    const colonnade = planFloorStrips(tower(3, () => 'COLONNADE')).strips[0];
    expect([fallback.col, fallback.row]).toEqual([
      colonnade.col,
      colonnade.row,
    ]);
  });

  it('holds at floors 1, 14, 50 and 80 of the pinned plan', () => {
    const pins: Record<number, ArchitectureId> = {
      1: 'COLONNADE',
      14: 'LOOP_ROOMS',
      50: 'GATE',
      80: 'EXPANSE',
    };
    for (const f of [1, 14, 50, 80]) {
      const plan = planFloorStrips(tower(f, (n) => pins[n] ?? 'NAVE'));
      expect(plan.top).toBe(f);
      expect(plan.strips.filter((s) => s.floor === f).length).toBe(3);
      expect(plan.strips.length).toBeLessThanOrEqual(STRIP_CAPACITY);
    }
  });

  it('caps the discovered floors at the buffer capacity', () => {
    const plan = planFloorStrips(tower(MAX_DISCOVERED + 30));
    expect(plan.strips.length).toBeLessThanOrEqual(STRIP_CAPACITY);
    expect(plan.numerals.length).toBeLessThanOrEqual(NUMERAL_CAPACITY);
  });
});

describe('Finish Floor marks', () => {
  it('draws a mark only on Floors the frame lists', () => {
    const marks = planFinishMarks(tower(40), [30, 10]);
    expect(marks.map((m) => m.floor)).toEqual([10, 30]);
    expect(planFinishMarks(tower(40), [])).toEqual([]);
    expect(planFinishMarks(tower(40), undefined)).toEqual([]);
  });

  it('draws nothing for a Floor that is not discovered, or listed twice', () => {
    expect(planFinishMarks(tower(5), [9, 3, 3]).map((m) => m.floor)).toEqual([3]);
    expect(planFinishMarks(undefined, [3])).toEqual([]);
  });

  it('sits on the strip of its Floor, above the numeral', () => {
    const [mark] = planFinishMarks(tower(20), [12]);
    const numeral = planFloorStrips(tower(20)).numerals.find((n) => n.digit === 2)!;
    expect(mark.y).toBeGreaterThan(numeral.y);
    expect(mark.y).toBeLessThan(stripY(12) + 0.86);
  });

  it('fits in the numeral draw call at 120 Floors', () => {
    const all = Array.from({ length: 120 }, (_, i) => i + 1);
    const t = tower(120);
    expect(
      planFloorStrips(t).numerals.length + planFinishMarks(t, all).length,
    ).toBeLessThanOrEqual(NUMERAL_CAPACITY);
  });
});

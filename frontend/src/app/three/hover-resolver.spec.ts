import {
  HOVER_DIRECT_SLOP_PX,
  HOVER_HOLD_PX,
  resolveHover,
  type HoverRegion,
} from './hover-resolver';
import type { VillageSubject } from './village/village-subjects';

const building = (id: string, ...rect: [number, number, number, number]): HoverRegion => ({
  subject: { kind: 'building', id },
  direct: false,
  rects: [{ left: rect[0], top: rect[1], right: rect[2], bottom: rect[3] }],
});
const direct = (kind: 'bell' | 'expedition', id: string, ...rect: [number, number, number, number]): HoverRegion => ({
  subject: { kind, id },
  direct: true,
  rects: [{ left: rect[0], top: rect[1], right: rect[2], bottom: rect[3] }],
});
const at = (regions: HoverRegion[], x: number, y: number, current: VillageSubject | null = null) =>
  resolveHover(regions, x, y, current)?.subject.id ?? null;

describe('resolveHover', () => {
  const gate = building('GATE', 0, 0, 40, 60);
  const bell = direct('bell', 'BELL', 10, 10, 30, 50);

  it('is null over nothing, and reads the first pixel of a region as in it', () => {
    expect(at([gate], -1, 30)).toBeNull();
    expect(at([gate], 0, 30)).toBe('GATE');
    expect(at([gate], 40, 30)).toBe('GATE');
    expect(at([gate], 41, 30)).toBeNull();
  });

  it('puts a thing the pointer is directly over before the building behind it', () => {
    expect(at([gate, bell], 5, 30)).toBe('GATE');
    expect(at([gate, bell], 20, 30)).toBe('BELL');
    expect(at([bell, gate], 20, 30)).toBe('BELL');
  });

  it('gives a direct thing a pixel of slop, and no more', () => {
    expect(at([bell], 10 - HOVER_DIRECT_SLOP_PX, 30)).toBe('BELL');
    expect(at([bell], 10 - HOVER_DIRECT_SLOP_PX - 1, 30)).toBeNull();
  });

  it('takes the smaller of two nested buildings', () => {
    const fort = building('FORT', 0, 0, 100, 60);
    const stair = building('STAIR', 20, 10, 40, 60);
    expect(at([fort, stair], 30, 30)).toBe('STAIR');
    expect(at([fort, stair], 70, 30)).toBe('FORT');
  });

  it('keeps the current subject until the pointer has left it by the hold margin', () => {
    const now = gate.subject;
    expect(at([gate], 40 + HOVER_HOLD_PX, 30, now)).toBe('GATE');
    expect(at([gate], 40 + HOVER_HOLD_PX + 1, 30, now)).toBeNull();
    // Without it held, the same pixel is not in.
    expect(at([gate], 41, 30)).toBeNull();
  });

  it('holds the bell over its margin and then lets the building behind it come back', () => {
    const now = bell.subject;
    expect(at([gate, bell], 30 + HOVER_HOLD_PX, 30, now)).toBe('BELL');
    expect(at([gate, bell], 30 + HOVER_HOLD_PX + 1, 30, now)).toBe('GATE');
  });

  it('keeps a building where a neighbour overlaps it, until the pointer is only in the neighbour', () => {
    const a = building('A', 0, 0, 50, 50);
    const b = building('B', 40, 0, 60, 50);
    // In both, the smaller (B) wins when nothing is held; the held one stays.
    expect(at([a, b], 45, 25)).toBe('B');
    expect(at([a, b], 41, 25, a.subject)).toBe('A');
    expect(at([a, b], 55, 25, a.subject)).toBe('B');
  });

  it('lets a building inside the held one take over once the pointer is well inside it, and holds it on the way out', () => {
    const gateRegion = building('GATE', 0, 0, 100, 60);
    const ring = building('RING', 40, 10, 70, 40);
    const regions = [gateRegion, ring];
    expect(at(regions, 41, 25, gateRegion.subject)).toBe('GATE');
    expect(at(regions, 40 + HOVER_HOLD_PX, 25, gateRegion.subject)).toBe('RING');
    // Leaving the Ring into the Gate: held a moment, then the Gate.
    expect(at(regions, 70 + HOVER_HOLD_PX, 25, ring.subject)).toBe('RING');
    expect(at(regions, 70 + HOVER_HOLD_PX + 1, 25, ring.subject)).toBe('GATE');
  });

  it('is steady: a sweep across a building made of many parts never changes subject inside it', () => {
    // One region for the whole building, as `volumeRegions` makes it: no holes between its parts.
    const regions = [building('SILO', 100, 0, 140, 80), building('FIELDS', 150, 0, 260, 30)];
    let current: VillageSubject | null = null;
    const seen: Array<string | null> = [];
    for (let x = 90; x <= 150; x++) {
      current = resolveHover(regions, x, 40, current)?.subject ?? null;
      seen.push(current?.id ?? null);
    }
    const inside = seen.slice(100 - 90, 140 - 90 + 1);
    expect(new Set(inside)).toEqual(new Set(['SILO']));
  });

  it('answers with an Expedition crate directly, over a group behind it', () => {
    const crate = direct('expedition', 'CREDITS', 50, 50, 60, 60);
    const party = building('PARTY', 0, 0, 100, 100);
    expect(at([party, crate], 55, 55)).toBe('CREDITS');
    expect(at([party, crate], 20, 20)).toBe('PARTY');
  });
});

import {
  buildVillage,
  EMPTY_VILLAGE,
  villageBounds,
  VillageInput,
} from './village-layout';
import { subjectOfVolume } from './village-subjects';

const busy: VillageInput = {
  ...EMPTY_VILLAGE,
  hands: { TILLER: 6, BREAKER: 4, SCRIBE: 2, SALTER: 0, SPOREWARDEN: 0 },
  saltPansOpen: false,
};

describe('village subjects', () => {
  it('maps Annex and Villager volumes to their Trade', () => {
    expect(subjectOfVolume('field-1')).toEqual({ kind: 'annex', id: 'TILLER' });
    expect(subjectOfVolume('gantry-beam')).toEqual({
      kind: 'annex',
      id: 'BREAKER',
    });
    expect(subjectOfVolume('study-cell')?.id).toBe('SCRIBE');
    expect(subjectOfVolume('footing-n')?.id).toBe('SALTER');
    expect(subjectOfVolume('villager-BREAKER-2-load')).toEqual({
      kind: 'villagers',
      id: 'BREAKER',
    });
  });

  it('gives the ground and the Tower no subject', () => {
    for (const id of ['ground', 'tower-3', 'base-slab', 'lamp-0']) {
      expect(subjectOfVolume(id)).toBeNull();
    }
  });

  it('draws no outline when nothing is hovered or selected', () => {
    const ids = buildVillage(busy).map((v) => v.id);

    // Only the chosen Start Floor, the Base door, wears a line.
    expect(
      ids.filter((id) => id.startsWith('outline-') && !id.includes(':start:')),
    ).toEqual([]);
  });

  it('outlines the Annex and the lane of a hovered Trade, and nothing scales', () => {
    const plain = buildVillage(busy);
    const hovered = buildVillage({ ...busy, hovered: 'BREAKER' });
    const outline = hovered.filter(
      (v) => v.id.startsWith('outline-') && !v.id.includes(':start:'),
    );

    // A dashed cube each for the Annex and for the lane: twelve edges, cut into dashes.
    expect(new Set(outline.map((v) => v.id.split(':')[2].split('-')[0]))).toEqual(
      new Set(['annex', 'villagers']),
    );
    expect(outline.length).toBeGreaterThanOrEqual(24);
    expect(hovered.length).toBe(plain.length + outline.length);
    // The picture itself is unchanged: same volumes, same size, same tone.
    expect(hovered.slice(0, plain.length)).toEqual(plain);
    // Every outline stands for the thing it frames.
    expect(
      outline.every((v) => subjectOfVolume(v.id)?.id === 'BREAKER'),
    ).toBeTrue();
  });

  it('makes the selected line heavier than the hovered one', () => {
    const thickness = (input: VillageInput) =>
      Math.min(
        ...buildVillage(input)
          .filter((v) => v.id.startsWith('outline-') && !v.id.includes(':start:'))
          .map((v) => Math.min(v.w, v.h, v.d)),
      );

    const hair = thickness({ ...busy, hovered: 'SCRIBE' });
    const heavy = thickness({ ...busy, selected: 'SCRIBE' });

    expect(heavy).toBeCloseTo(hair * 2, 6);
  });

  it('outlines a locked footing, so it can be pointed at', () => {
    const outline = buildVillage({ ...busy, hovered: 'SALTER' }).filter((v) =>
      v.id.startsWith('outline-SALTER'),
    );

    expect(outline.length).toBeGreaterThanOrEqual(12);
  });

  it('leaves the Village bounds alone', () => {
    const plain = villageBounds(buildVillage(busy));
    const marked = villageBounds(
      buildVillage({ ...busy, hovered: 'TILLER', selected: 'BREAKER' }),
    );

    expect(marked).toEqual(plain);
  });

  it('makes the footing, pier and hull one Port subject; the sea is no subject', () => {
    for (const id of ['port-footing-n', 'port-pier-deck', 'port-boat-hull']) {
      expect(subjectOfVolume(id)).toEqual({ kind: 'building', id: 'PORT' });
    }
    expect(subjectOfVolume('sea-band-1')).toBeNull();
    expect(subjectOfVolume('sea-body')).toBeNull();
  });

  it('outlines the Port footing and pier when hovered, within the frame', () => {
    const input = { ...busy, port: 'FOOTING' as const, hovered: 'PORT' };
    const v = buildVillage(input);
    const outline = v.filter((x) => x.id.startsWith('outline-PORT:hover:building'));

    expect(outline.length).toBeGreaterThanOrEqual(12);
    expect(subjectOfVolume(outline[0].id)).toEqual({ kind: 'building', id: 'PORT' });
    for (const x of outline) expect(x.x + x.w / 2).toBeLessThanOrEqual(5.5);
  });

  it('makes every part of a doorway one Shortcut subject, and an outline of it too', () => {
    const v = buildVillage({
      ...busy,
      shortcuts: [{ id: 'NARROW_STAIR', floor: 21 }],
      hovered: 'NARROW_STAIR',
    });
    const parts = v.filter((x) => x.id.startsWith('shortcut-mark-'));
    expect(parts.length).toBeGreaterThan(4);
    for (const x of parts) {
      expect(subjectOfVolume(x.id)).toEqual({
        kind: 'shortcut',
        id: 'NARROW_STAIR',
      });
    }
    const outline = v.filter((x) => x.id.startsWith('outline-NARROW_STAIR'));
    expect(outline.length).toBeGreaterThanOrEqual(12);
    expect(subjectOfVolume(outline[0].id)).toEqual({
      kind: 'shortcut',
      id: 'NARROW_STAIR',
    });
  });

  it('draws no outline for a hovered Shortcut that is locked (not drawn)', () => {
    const v = buildVillage({ ...busy, shortcuts: [], hovered: 'NARROW_STAIR' });
    expect(v.some((x) => x.id.startsWith('outline-NARROW_STAIR'))).toBeFalse();
  });
});

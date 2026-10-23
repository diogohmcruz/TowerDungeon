/**
 * What the Village picture and the panels agree to point at (architecture doc
 * section 5.2, Selection). A subject is a kind and an id; the id is what links a
 * Trade row, an Upgrade row and the scene volumes of that Trade. Pure, no three.js.
 *
 * Extending: the Port footing and the Shortcut marks add their volume id prefix
 * to `VOLUME_SUBJECTS`, their kind to `SubjectKind`, and a builder to
 * `CAPTION_BUILDERS` in `village-captions.ts`.
 */
import type { TradeId } from '../../interfaces/village';
import type { Volume } from './village-layout';

export type SubjectKind = 'annex' | 'villagers' | 'upgrade';

export interface VillageSubject {
  kind: SubjectKind;
  /** The Trade id for Annexes, Villagers and Upgrade rows. Equal ids are one thing. */
  id: string;
}

/** Volume id prefixes, and the subject each belongs to. First match wins. */
export const VOLUME_SUBJECTS: ReadonlyArray<{
  prefix: string;
  kind: SubjectKind;
  id: string;
}> = [
  { prefix: 'field-', kind: 'annex', id: 'TILLER' },
  { prefix: 'furrow-', kind: 'annex', id: 'TILLER' },
  { prefix: 'quarry-cut-', kind: 'annex', id: 'BREAKER' },
  { prefix: 'gantry-', kind: 'annex', id: 'BREAKER' },
  { prefix: 'heap-', kind: 'annex', id: 'BREAKER' },
  { prefix: 'stilt-', kind: 'annex', id: 'SCRIBE' },
  { prefix: 'study-cell', kind: 'annex', id: 'SCRIBE' },
  { prefix: 'slit-', kind: 'annex', id: 'SCRIBE' },
  { prefix: 'footing-', kind: 'annex', id: 'SALTER' },
  { prefix: 'pan-', kind: 'annex', id: 'SALTER' },
];

const TRADES: readonly TradeId[] = [
  'TILLER',
  'BREAKER',
  'SCRIBE',
  'SALTER',
  'SPOREWARDEN',
];

/** Outlines are drawn as volumes; their ids carry the subject they frame. */
const OUTLINE = 'outline-';

export function sameThing(
  a: VillageSubject | null | undefined,
  b: VillageSubject | null | undefined,
): boolean {
  return !!a && !!b && a.id === b.id;
}

/** The subject a scene volume stands for, or null (ground, Tower, Fort, ...). */
export function subjectOfVolume(volumeId: string): VillageSubject | null {
  if (volumeId.startsWith(OUTLINE)) {
    const id = volumeId.slice(OUTLINE.length).split(':')[0];
    return { kind: 'annex', id };
  }
  const villager = /^villager-([A-Z]+)-/.exec(volumeId);
  if (villager && (TRADES as readonly string[]).includes(villager[1])) {
    return { kind: 'villagers', id: villager[1] };
  }
  const hit = VOLUME_SUBJECTS.find((s) => volumeId.startsWith(s.prefix));
  return hit ? { kind: hit.kind, id: hit.id } : null;
}

/** Volumes that make up one kind of a thing (not the outlines). */
function ofSubject(volumes: Volume[], id: string, kind: SubjectKind): Volume[] {
  return volumes.filter((v) => {
    if (v.id.startsWith(OUTLINE)) return false;
    const s = subjectOfVolume(v.id);
    return s?.id === id && s.kind === kind;
  });
}

/** One virtual pixel in world units; a hairline is one, the selected line two. */
const PIXEL = 11 / 220;
const PAD = 0.06;

/**
 * Frames of thin boxes around a thing: its Annex and its Villagers' lane. Only
 * line weight differs: one pixel for the hovered, two for the selected. No
 * scale, no glow, no pulse.
 */
export function outlineVolumes(
  volumes: Volume[],
  id: string,
  weight: 1 | 2,
  tag: 'hover' | 'select',
): Volume[] {
  const out: Volume[] = [];
  const t = PIXEL * weight;
  for (const kind of ['annex', 'villagers'] as const) {
    const parts = ofSubject(volumes, id, kind);
    if (parts.length === 0) continue;
    const x0 = Math.min(...parts.map((v) => v.x - v.w / 2)) - PAD;
    const x1 = Math.max(...parts.map((v) => v.x + v.w / 2)) + PAD;
    const y0 = Math.min(...parts.map((v) => v.y - v.h / 2));
    const y1 = Math.max(...parts.map((v) => v.y + v.h / 2)) + PAD;
    const z = Math.max(...parts.map((v) => v.z + v.d / 2)) + 0.03;
    const name = `${OUTLINE}${id}:${tag}:${kind}`;
    const edge = (suffix: string, x: number, y: number, w: number, h: number) =>
      out.push({
        id: `${name}-${suffix}`,
        group: 'village',
        x,
        y,
        z,
        w,
        h,
        d: 0.01,
        tone: 1,
        alpha: 1,
      });
    const cx = (x0 + x1) / 2;
    const cy = (y0 + y1) / 2;
    edge('n', cx, y1, x1 - x0, t);
    edge('s', cx, y0 + t / 2, x1 - x0, t);
    edge('w', x0, cy, t, y1 - y0);
    edge('e', x1, cy, t, y1 - y0);
  }
  return out;
}

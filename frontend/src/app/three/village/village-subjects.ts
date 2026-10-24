/**
 * What the Village picture and the panels agree to point at (architecture doc
 * section 5.2, Selection). A subject is a kind and an id; the id is what links a
 * Trade row, an Upgrade row and the scene volumes of that Trade. Pure, no three.js.
 *
 * Extending: a new kind of thing adds its volume id prefix to `VOLUME_SUBJECTS`
 * (or a branch in `subjectOfVolume` when the id is in the volume id, as the
 * Shortcut marks do), its kind to `SubjectKind`, and a builder to
 * `CAPTION_BUILDERS` in `village-captions.ts`. A building with a popover needs
 * none of that: see `building-registry.ts`.
 */
import type { TradeId } from '../../interfaces/village';
import type { Volume } from './village-layout';

export type SubjectKind =
  | 'annex'
  | 'villagers'
  | 'upgrade'
  | 'port'
  | 'shortcut'
  | 'bell'
  | 'recall'
  | 'base-door'
  | 'building';

/**
 * The launch controls (architecture doc 5.5). The bell and the recall lamp are
 * one each; the Base door is the Start Floor 0 choice, as a doorway is for a Shortcut.
 */
export const BELL_SUBJECT = 'BELL';
export const RECALL_SUBJECT = 'RECALL';
export const BASE_DOOR_SUBJECT = 'BASE';
/** Volume id prefixes of the launch objects. */
export const BELL_ID = 'bell-';
export const RECALL_ID = 'recall-';
export const BASE_DOOR_ID = 'base-door';

/** The id of the Port subject: the footing, the pier and the Boat are one thing. */
export const PORT_SUBJECT = 'PORT';

/** Id prefix of a Shortcut mark volume; the Shortcut id follows, then `:` and the part. */
export const SHORTCUT_MARK_ID = 'shortcut-mark-';

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
  { prefix: 'port-', kind: 'port', id: PORT_SUBJECT },
  // Registered buildings (`buildings.ts`): the popover framework's subjects.
  { prefix: 'silo', kind: 'building', id: 'SILO' },
  { prefix: 'gate-', kind: 'building', id: 'GATE' },
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
    if (volumeId.includes(':shortcut-')) return { kind: 'shortcut', id };
    if (volumeId.endsWith(':building') || volumeId.includes(':building-')) {
      return { kind: 'building', id };
    }
    if (id === BELL_SUBJECT) return { kind: 'bell', id };
    if (id === RECALL_SUBJECT) return { kind: 'recall', id };
    if (id === BASE_DOOR_SUBJECT) return { kind: 'base-door', id };
    return { kind: id === PORT_SUBJECT ? 'port' : 'annex', id };
  }
  if (volumeId.startsWith(BELL_ID)) return { kind: 'bell', id: BELL_SUBJECT };
  if (volumeId.startsWith(RECALL_ID)) {
    return { kind: 'recall', id: RECALL_SUBJECT };
  }
  if (volumeId.startsWith(BASE_DOOR_ID)) {
    return { kind: 'base-door', id: BASE_DOOR_SUBJECT };
  }
  if (volumeId.startsWith(SHORTCUT_MARK_ID)) {
    return { kind: 'shortcut', id: volumeId.slice(SHORTCUT_MARK_ID.length).split(':')[0] };
  }
  const villager = /^villager-([A-Z]+)-/.exec(volumeId);
  if (villager && (TRADES as readonly string[]).includes(villager[1])) {
    return { kind: 'villagers', id: villager[1] };
  }
  const hit = VOLUME_SUBJECTS.find((s) => volumeId.startsWith(s.prefix));
  return hit ? { kind: hit.kind, id: hit.id } : null;
}

/** Volumes that make up one kind of a thing (not the outlines). */
export function ofSubject(volumes: Volume[], id: string, kind: SubjectKind): Volume[] {
  return volumes.filter((v) => {
    if (v.id.startsWith(OUTLINE)) return false;
    // A building's invisible near-miss pad is for picking only: its frame and anchor hug what is drawn.
    if (kind === 'building' && v.alpha === 0) return false;
    const s = subjectOfVolume(v.id);
    return s?.id === id && s.kind === kind;
  });
}

/** One virtual pixel in world units; a hairline is one, the selected line two. */
const PIXEL = 11 / 220;
const PAD = 0.06;
/** Dash length and gap of a dashed frame, in world units (about three and two pixels). */
const DASH = 0.15;
const GAP = 0.1;

const OUTLINED_KINDS: readonly SubjectKind[] = [
  'annex',
  'villagers',
  'port',
  'shortcut',
  'bell',
  'recall',
  'base-door',
  'building',
];

/**
 * Frames of thin boxes around a thing: its Annex and its Villagers' lane. Only
 * line weight differs: one pixel for the hovered, two for the selected. No
 * scale, no glow, no pulse.
 */
export function outlineVolumes(
  volumes: Volume[],
  id: string,
  weight: 1 | 2,
  tag: 'hover' | 'select' | 'start' | 'unlit',
  style: 'solid' | 'dashed' = 'solid',
): Volume[] {
  const out: Volume[] = [];
  const t = PIXEL * weight;
  for (const kind of OUTLINED_KINDS) {
    const parts = ofSubject(volumes, id, kind);
    if (parts.length === 0) continue;
    const x0 = Math.min(...parts.map((v) => v.x - v.w / 2)) - PAD;
    const x1 = Math.max(...parts.map((v) => v.x + v.w / 2)) + PAD;
    const y0 = Math.min(...parts.map((v) => v.y - v.h / 2));
    const y1 = Math.max(...parts.map((v) => v.y + v.h / 2)) + PAD;
    const z = Math.max(...parts.map((v) => v.z + v.d / 2)) + 0.03;
    const name = `${OUTLINE}${id}:${tag}:${kind}`;
    const edge = (
      suffix: string,
      x: number,
      y: number,
      w: number,
      h: number,
    ) => {
      const base = { group: 'village' as const, z, d: 0.01, tone: 1, alpha: 1 };
      if (style === 'solid') {
        out.push({ id: `${name}-${suffix}`, x, y, w, h, ...base });
        return;
      }
      // A dashed edge: runs of dash separated by gaps along its long side.
      const horizontal = w >= h;
      const length = horizontal ? w : h;
      const runs = Math.max(1, Math.round((length + GAP) / (DASH + GAP)));
      const dash = (length + GAP) / runs - GAP;
      for (let i = 0; i < runs; i++) {
        const along = -length / 2 + dash / 2 + i * (dash + GAP);
        out.push({
          id: `${name}-${suffix}${i}`,
          x: horizontal ? x + along : x,
          y: horizontal ? y : y + along,
          w: horizontal ? dash : w,
          h: horizontal ? h : dash,
          ...base,
        });
      }
    };
    const cx = (x0 + x1) / 2;
    const cy = (y0 + y1) / 2;
    edge('n', cx, y1, x1 - x0, t);
    edge('s', cx, y0 + t / 2, x1 - x0, t);
    edge('w', x0, cy, t, y1 - y0);
    edge('e', x1, cy, t, y1 - y0);
  }
  return out;
}

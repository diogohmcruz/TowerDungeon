/**
 * The Expedition HUD as objects (architecture doc 5.6): Supplies is a gauge post
 * beside the recall lamp, Credits, Materials and Relics carried are crates on the
 * back wall behind the Party, and the Party and each enemy group answer a hover with their exact
 * figures. Built from the same box kit and gauge kit as the Village. Pure layout and
 * pure captions, no three.js and no Angular.
 *
 * Subjects are of kind `expedition`: `SUPPLIES`, `CREDITS`, `MATERIALS`, `RELICS`
 * (volume ids `supplies-*` and `carried-<kind>-*`), `PARTY` and `GROUP:<TYPE>` (no
 * volume: the scene picks them by the screen rectangle of the figures).
 */
import type { GameState, Squad } from '../../interfaces/game-state';
import type { Member } from '../../interfaces/member';
import { formatFigure } from '../village/building-registry';
import { slitGauge } from '../village/gauge-kit';
import type { Volume } from '../village/village-layout';
import { outlineVolumes } from '../village/village-subjects';
import type { VillageSubject } from '../village/village-subjects';
import { TOWER_RECALL_X } from '../launch/launch-objects';
import type { ScreenRect } from '../figures/figure-pick';

export const SUPPLIES_SUBJECT = 'SUPPLIES';
export const PARTY_SUBJECT = 'PARTY';
export const GROUP_PREFIX = 'GROUP:';
/** The Finish Floor mark of a Floor on the picture (#123): `FINISH:<floor>`. */
export const FINISH_PREFIX = 'FINISH:';
export type CrateKind = 'CREDITS' | 'MATERIALS' | 'RELICS';
export const CRATE_KINDS: readonly CrateKind[] = ['CREDITS', 'MATERIALS', 'RELICS'];

/** Volume id prefixes of the Supplies post and of each crate. */
export const SUPPLIES_ID = 'supplies-';
export const CRATE_ID = 'carried-';

/** The post stands just outside the recall lamp, on the same Floor. */
export const SUPPLIES_X = TOWER_RECALL_X + 0.7;
export const SUPPLIES_POST = { h: 1.5 } as const;
/** Depth of the crates: the rear of the Floor, in front of the strip's lip and well behind the rear rank of the Party and the garrison (#127). */
export const CRATE_Z = 0.35;
/**
 * How far above the figures' feet line the crates' shelf stands. The crates read
 * as stored goods on the back wall; high enough that no figure's hit area
 * (a figure's drawn rectangle grown to 22 px and a margin) reaches them.
 */
export const CRATE_RISE = 0.6;
/** Gap between crates along x. */
const CRATE_STEP = 0.26;
const CRATE = { w: 0.17, h: 0.1, d: 0.12 } as const;
const TONE = { lit: 1, bone: 0.9, dark: 0.12, timber: 0.55, stone: 0.72 } as const;

/** One crate stands for 1, 2 or 3 stacked: a larger load, a taller stack. Never colour alone. */
export const CRATE_TIERS: Record<CrateKind, readonly [number, number]> = {
  CREDITS: [100, 1000],
  MATERIALS: [20, 200],
  RELICS: [2, 5],
};

/** How many crates stack for an amount: none for nothing, then 1, 2 or 3. */
export function crateCount(kind: CrateKind, amount: number): number {
  if (!(amount > 0)) return 0;
  const [two, three] = CRATE_TIERS[kind];
  return amount >= three ? 3 : amount >= two ? 2 : 1;
}

function box(
  out: Volume[],
  id: string,
  x: number,
  bottom: number,
  z: number,
  w: number,
  h: number,
  d: number,
  tone: number,
  alpha = 1,
): void {
  out.push({ id, group: 'village', x, y: bottom + h / 2, z, w, h, d, tone, alpha });
}

export interface ExpeditionObjectsInput {
  /** World Y of the walkable surface the Party stands on. */
  surfaceY: number;
  supplies: number;
  maxSupplies: number;
  credits: number;
  materials: number;
  relics: number;
  /** Where the Party's formation is, so the crates stand at its feet. */
  partyX: number;
  /** The subject id hovered, if any: its outline is one pixel. */
  hovered?: string | null;
}

/** Fill of the Supplies gauge, 0..1. */
export function suppliesFill(supplies: number, max: number): number {
  return max > 0 ? Math.min(1, Math.max(0, supplies / max)) : 0;
}

/** The x of crate `index` (0..2) when the Party stands at `partyX`. */
export function crateX(partyX: number, index: number): number {
  return partyX + (index - 1) * CRATE_STEP;
}

/**
 * The Supplies gauge post: a stone foot, a timber post with a slit cut in it whose
 * bone fill is the share of Supplies left, and a cap. Ids begin `supplies-`.
 */
export function suppliesPost(
  out: Volume[],
  surfaceY: number,
  supplies: number,
  max: number,
): void {
  const h = SUPPLIES_POST.h;
  const x = SUPPLIES_X;
  const z = 0.5;
  box(out, 'supplies-foot', x, surfaceY, z, 0.3, 0.1, 0.2, TONE.stone);
  box(out, 'supplies-post', x, surfaceY + 0.1, z, 0.22, h - 0.14, 0.12, TONE.timber);
  box(out, 'supplies-cap', x, surfaceY + h - 0.04, z, 0.3, 0.04, 0.16, TONE.timber);
  slitGauge(out, 'supplies-gauge', {
    x,
    bottom: surfaceY + 0.2,
    z: z + 0.07,
    height: h - 0.5,
    fill: suppliesFill(supplies, max),
    width: 0.1,
  });
  box(out, 'supplies-hit', x, surfaceY, z, 0.5, h, 0.3, TONE.dark, 0);
}

/**
 * One crate place: a flat dark slab always (so an empty place is still a place), and
 * above it a stack of one to three crates, each with `index + 1` bone slats on its
 * front so the three kinds differ by shape as well as by caption. Ids begin
 * `carried-<kind>-`.
 */
export function crateStack(
  out: Volume[],
  kind: CrateKind,
  index: number,
  x: number,
  surfaceY: number,
  amount: number,
): void {
  const id = `${CRATE_ID}${kind.toLowerCase()}`;
  surfaceY += CRATE_RISE;
  const n = crateCount(kind, amount);
  box(out, `${id}-base`, x, surfaceY, CRATE_Z, CRATE.w + 0.06, 0.03, CRATE.d + 0.06, TONE.dark);
  for (let i = 0; i < n; i++) {
    const bottom = surfaceY + 0.03 + i * CRATE.h;
    box(out, `${id}-box-${i}`, x, bottom, CRATE_Z, CRATE.w, CRATE.h, CRATE.d, TONE.stone);
    for (let s = 0; s <= index; s++) {
      const dx = (s - index / 2) * 0.04;
      box(out, `${id}-slat-${i}-${s}`, x + dx, bottom + 0.02, CRATE_Z + CRATE.d / 2 + 0.005, 0.02, CRATE.h - 0.04, 0.02, TONE.bone);
    }
  }
  box(
    out,
    `${id}-hit`,
    x,
    surfaceY,
    CRATE_Z,
    CRATE.w + 0.1,
    Math.max(0.2, 0.03 + n * CRATE.h),
    CRATE.d + 0.1,
    TONE.dark,
    0,
  );
}

/** The post, the crates, and a line on whichever is hovered. */
export function expeditionVolumes(input: ExpeditionObjectsInput): Volume[] {
  const out: Volume[] = [];
  suppliesPost(out, input.surfaceY, input.supplies, input.maxSupplies);
  const amounts: Record<CrateKind, number> = {
    CREDITS: input.credits,
    MATERIALS: input.materials,
    RELICS: input.relics,
  };
  CRATE_KINDS.forEach((kind, i) =>
    crateStack(out, kind, i, crateX(input.partyX, i), input.surfaceY, amounts[kind]),
  );
  const marks: Volume[] = [];
  for (const id of [SUPPLIES_SUBJECT, ...CRATE_KINDS]) {
    if (input.hovered === id) marks.push(...outlineVolumes(out, id, 1, 'hover'));
  }
  return [...out, ...marks];
}

// ---- Groups: the Party and each enemy group, picked by the rectangle of their figures.

/** The group an enemy belongs to: its type, as the card groups them. */
export function groupKey(member: Member): string {
  return member.stats?.type ?? member.name;
}

export function groupSubject(key: string): VillageSubject {
  return { kind: 'expedition', id: `${GROUP_PREFIX}${key}` };
}

export function finishSubject(floor: number): VillageSubject {
  return { kind: 'expedition', id: `${FINISH_PREFIX}${floor}` };
}

export const PARTY: VillageSubject = { kind: 'expedition', id: PARTY_SUBJECT };

/** Whether the subject is the Party or an enemy group (hovered by rectangle, not by volume). */
export function isGroupSubject(subject: VillageSubject | null): boolean {
  return (
    subject?.kind === 'expedition' &&
    (subject.id === PARTY_SUBJECT ||
      subject.id.startsWith(GROUP_PREFIX) ||
      subject.id.startsWith(FINISH_PREFIX))
  );
}

export interface GroupRect {
  subject: VillageSubject;
  rect: ScreenRect;
}

/** Slack around a formation, in CSS px, so a near miss between figures still hovers it. */
export const GROUP_SLACK_PX = 6;

/** The union of figure rectangles, one per group id, in first-seen order. */
export function unionRects(
  figures: ReadonlyArray<{ subject: VillageSubject; rect: ScreenRect }>,
): GroupRect[] {
  const groups = new Map<string, GroupRect>();
  for (const f of figures) {
    const have = groups.get(f.subject.id);
    if (!have) {
      groups.set(f.subject.id, { subject: f.subject, rect: { ...f.rect } });
      continue;
    }
    have.rect.left = Math.min(have.rect.left, f.rect.left);
    have.rect.top = Math.min(have.rect.top, f.rect.top);
    have.rect.right = Math.max(have.rect.right, f.rect.right);
    have.rect.bottom = Math.max(have.rect.bottom, f.rect.bottom);
  }
  return [...groups.values()];
}

/** The group under a point, the smaller rectangle first when two overlap. */
export function groupAt(
  groups: readonly GroupRect[],
  x: number,
  y: number,
): VillageSubject | null {
  let best: GroupRect | null = null;
  let bestArea = Infinity;
  for (const g of groups) {
    const r = g.rect;
    if (
      x < r.left - GROUP_SLACK_PX ||
      x > r.right + GROUP_SLACK_PX ||
      y < r.top - GROUP_SLACK_PX ||
      y > r.bottom + GROUP_SLACK_PX
    ) {
      continue;
    }
    const area = (r.right - r.left) * (r.bottom - r.top);
    if (area < bestArea) {
      best = g;
      bestArea = area;
    }
  }
  return best?.subject ?? null;
}

// ---- Captions: the exact numbers, in words.

/** The caption shape of `village-captions.ts`, restated so this module does not import it. */
export interface ExpeditionCaption {
  name: string;
  status: string;
  makes: string;
  lock: string | null;
}

function flatten(squad: Squad | undefined): Member[] {
  return squad ? (Object.values(squad).flat().filter(Boolean) as Member[]) : [];
}

function healthOf(members: readonly Member[]): { now: number; max: number } {
  let now = 0;
  let max = 0;
  for (const m of members) {
    now += m.currentHealth;
    max += m.maxHealth ?? m.currentHealth;
  }
  return { now, max };
}

/** "SLIME" and "STONE_GOLEM" as "Slime" and "Stone golem". */
export function typeLabel(key: string): string {
  const t = key.replace(/_/g, ' ').toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

function plural(n: number, one: string, many: string): string {
  return `${formatFigure(n)} ${n === 1 ? one : many}`;
}

/** The Floor's enemies of one type. */
export function enemiesOfGroup(state: GameState, key: string): Member[] {
  const floor = state.tower?.floors?.[state.tower?.currentFloor ?? 0];
  return (floor?.enemies ?? []).filter((e) => groupKey(e) === key);
}

/** The Party as Units, by type, for the hover and for the list. */
export function partyFigures(state: GameState): {
  size: number;
  health: { now: number; max: number };
  byType: string;
} {
  const members = flatten(state.unitsOnTower);
  const counts = new Map<string, number>();
  for (const m of members) {
    const k = groupKey(m);
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  return {
    size: members.length,
    health: healthOf(members),
    byType: [...counts].map(([k, n]) => `${typeLabel(k)} ${n}`).join(', '),
  };
}

const CRATE_WORDS: Record<CrateKind, { name: string; good: string }> = {
  CREDITS: { name: 'The Credits crate', good: 'Credits' },
  MATERIALS: { name: 'The Materials crate', good: 'Materials' },
  RELICS: { name: 'The Relics crate', good: 'Relics' },
};

/** The caption of an Expedition object, or null when nothing is out. */
export function expeditionCaption(
  subject: VillageSubject,
  state: GameState,
): ExpeditionCaption | null {
  if (!state.expeditionActive) return null;
  const id = subject.id;
  if (id === SUPPLIES_SUBJECT) {
    return {
      name: 'The Supplies post',
      status: `Supplies ${formatFigure(state.supplies)} of ${formatFigure(state.maxSupplies)}.`,
      makes: 'Drawn from the Pantry. Extraction returns what is left',
      lock: null,
    };
  }
  if ((CRATE_KINDS as readonly string[]).includes(id)) {
    const kind = id as CrateKind;
    const amount =
      kind === 'CREDITS'
        ? state.carriedCredits
        : (state.carriedLoot?.[kind] ?? 0);
    const words = CRATE_WORDS[kind];
    return {
      name: words.name,
      status: `${words.good} carried ${formatFigure(amount ?? 0)}.`,
      makes: 'Carried by the Party. Extraction banks it',
      lock: null,
    };
  }
  if (id === PARTY_SUBJECT) {
    const p = partyFigures(state);
    return {
      name: 'The Party',
      status: `${plural(p.size, 'Unit', 'Units')}. Health ${formatFigure(p.health.now)} of ${formatFigure(p.health.max)}.`,
      makes: p.byType,
      lock: null,
    };
  }
  if (id.startsWith(FINISH_PREFIX)) {
    const floor = Number(id.slice(FINISH_PREFIX.length));
    // Only a Floor the server lists wears the mark, so a stale subject says nothing.
    if (!(state.pillarMarks ?? []).includes(floor)) return null;
    return {
      name: `Finish Floor ${formatFigure(floor)}`,
      status: 'The Finish Floor of a Race you have run.',
      makes: 'First contact is marked on the pillar',
      lock: null,
    };
  }
  if (id.startsWith(GROUP_PREFIX)) {
    const key = id.slice(GROUP_PREFIX.length);
    const group = enemiesOfGroup(state, key);
    if (group.length === 0) return null;
    const h = healthOf(group);
    return {
      name: `${typeLabel(key)} ×${group.length}`,
      status: `${plural(group.length, 'enemy', 'enemies')}. Health ${formatFigure(h.now)} of ${formatFigure(h.max)}.`,
      makes: group.some((e) => e.boss) ? 'A Guardian stands among them' : '',
      lock: null,
    };
  }
  return null;
}

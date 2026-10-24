/**
 * The Expedition HUD as objects (architecture doc 5.6): Supplies is a gauge post
 * beside the recall lamp, Credits, Materials and Relics carried are three treasures on the
 * back wall behind the Party (coin stacks, a heap of quarried blocks, a reliquary with a lit slit),
 * and the Party and each enemy group answer a hover with their exact
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
import { VILLAGE_TONE } from '../village/village-layout';
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
 * How far above the figures' feet line the treasures' shelf stands. They read as stored goods on
 * the back wall; high enough that no figure's hit area (a figure's drawn rectangle grown to 22 px
 * and a margin) reaches them, and the tallest pile (0.17) still stops under the Floor's ceiling.
 */
export const CRATE_RISE = 0.66;
/** Gap between the three treasures along x: each has room for its widest pile and a margin. */
const CRATE_STEP = 0.36;
/** Width of a treasure's shelf and the most its pile ever spans. */
const SHELF_W = 0.34;
const SHELF_H = 0.03;
const SHELF_D = 0.1;
/**
 * The ink densities of the treasures, all from the Village's own tokens. The shader turns a density
 * into one of four ramp slots, dithering the levels in between, so a tone that lands between two
 * slots (the old crates' 0.72 on a front face) is drawn as a muddy checker. Each part here is
 * chosen to land on a slot: a front face at `salt` (0.95) is flat slot 2, at `timber` (0.55) is
 * slot 1 with a little grain, and a top face at either is a step brighter. By day the same densities
 * draw dark ink on the pale ground, like every other part of the scene.
 *  - Credits are bone coins (`salt`) with a duller coin (`timber`) between each two.
 *  - Materials are flat stone blocks (`salt`) under a lit top course (`lit`).
 *  - A Relic is a dull casket (`timber`) with a bone lid (`salt`) and one lit slit (`lit`).
 */
const TONE = {
  coin: VILLAGE_TONE.salt,
  coinEdge: VILLAGE_TONE.timber,
  block: VILLAGE_TONE.salt,
  cap: VILLAGE_TONE.lit,
  shelf: VILLAGE_TONE.timber,
  casket: VILLAGE_TONE.timber,
  lid: VILLAGE_TONE.salt,
  slit: VILLAGE_TONE.lit,
  dark: VILLAGE_TONE.dark,
} as const;

/** A treasure grows through 1, 2 and 3 tiers at these amounts: a larger load, a larger pile. Never colour alone. */
export const CRATE_TIERS: Record<CrateKind, readonly [number, number]> = {
  CREDITS: [100, 1000],
  MATERIALS: [20, 200],
  RELICS: [2, 5],
};

/** The tier of a treasure for an amount: none for nothing, then 1, 2 or 3. */
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
  box(out, 'supplies-foot', x, surfaceY, z, 0.3, 0.1, 0.2, VILLAGE_TONE.stone);
  box(out, 'supplies-post', x, surfaceY + 0.1, z, 0.22, h - 0.14, 0.12, VILLAGE_TONE.timber);
  box(out, 'supplies-cap', x, surfaceY + h - 0.04, z, 0.3, 0.04, 0.16, VILLAGE_TONE.timber);
  slitGauge(out, 'supplies-gauge', {
    x,
    bottom: surfaceY + 0.2,
    z: z + 0.07,
    height: h - 0.5,
    fill: suppliesFill(supplies, max),
    width: 0.1,
  });
  box(out, 'supplies-hit', x, surfaceY, z, 0.5, h, 0.3, VILLAGE_TONE.dark, 0);
}

/** Coins in each column of the Credits pile, left to right, by tier: 3; 4, 3; 4, 4, 3. */
export function coinColumns(tier: number): number[] {
  return [[], [3], [4, 3], [4, 4, 3]][Math.min(3, Math.max(0, tier))];
}

/** Blocks in each course of the Materials heap, from the bottom, by tier: 2, 1; 3, 2; 3, 2, 1. */
export function heapCourses(tier: number): number[] {
  return [[], [2, 1], [3, 2], [3, 2, 1]][Math.min(3, Math.max(0, tier))];
}

/** Caskets in the Relics group: the reliquary alone, then one small casket beside it, then two. */
export function casketCount(tier: number): number {
  return Math.min(3, Math.max(0, tier));
}

const COIN = { w: 0.1, h: 0.04, d: 0.07, step: 0.11 } as const;
const BLOCK = { w: 0.09, h: 0.055, d: 0.07, gap: 0.01 } as const;

/** Coin stacks: flat bone discs piled in columns, a duller coin between every two so each shows as a coin. */
function coinPile(out: Volume[], id: string, x: number, bottom: number, tier: number): void {
  const columns = coinColumns(tier);
  columns.forEach((coins, c) => {
    const cx = x + (c - (columns.length - 1) / 2) * COIN.step;
    for (let i = 0; i < coins; i++) {
      // A coin sits a hair off its neighbour, so a column leans like a hand-stacked one.
      const lean = ((i * 5 + c * 3) % 3 - 1) * 0.008;
      box(
        out,
        `${id}-coin-${c}-${i}`,
        cx + lean,
        bottom + i * COIN.h,
        CRATE_Z,
        COIN.w,
        COIN.h,
        COIN.d,
        i % 2 === 0 ? TONE.coin : TONE.coinEdge,
      );
    }
  });
}

/** A heap of dressed blocks in running bond, narrowing upward, the top course in lighter stone. */
function blockHeap(out: Volume[], id: string, x: number, bottom: number, tier: number): void {
  const courses = heapCourses(tier);
  courses.forEach((blocks, r) => {
    for (let k = 0; k < blocks; k++) {
      const bx = x + (k - (blocks - 1) / 2) * (BLOCK.w + BLOCK.gap);
      box(
        out,
        `${id}-block-${r}-${k}`,
        bx,
        bottom + r * BLOCK.h,
        CRATE_Z,
        BLOCK.w,
        BLOCK.h,
        BLOCK.d,
        r === courses.length - 1 ? TONE.cap : TONE.block,
      );
    }
  });
}

/**
 * A reliquary: a dark casket with a stone lid and ridge and one lit slit in its front, and for a
 * larger load a smaller casket on each side of it.
 */
function reliquaries(out: Volume[], id: string, x: number, bottom: number, tier: number): void {
  const casket = (name: string, cx: number, w: number, h: number, slitW: number, slitH: number) => {
    box(out, `${id}-${name}`, cx, bottom, CRATE_Z, w, h, 0.07, TONE.casket);
    box(out, `${id}-${name}-lid`, cx, bottom + h, CRATE_Z, w + 0.01, 0.025, 0.08, TONE.lid);
    box(out, `${id}-${name}-ridge`, cx, bottom + h + 0.025, CRATE_Z, w - 0.04, 0.025, 0.05, TONE.lid);
    // The slit is cut in the front: a decal, thin, drawn just proud of the casket's face.
    box(out, `${id}-${name}-slit`, cx, bottom + (h - slitH) / 2, CRATE_Z + 0.036, slitW, slitH, 0.02, TONE.slit);
  };
  const n = casketCount(tier);
  if (n === 0) return;
  casket('casket-0', x, 0.12, 0.09, 0.035, 0.06);
  if (n >= 2) casket('casket-1', x + 0.115, 0.075, 0.06, 0.025, 0.035);
  if (n >= 3) casket('casket-2', x - 0.115, 0.075, 0.06, 0.025, 0.035);
}

/**
 * One treasure place: a shelf always (so an empty place is still a place), and on it, by kind,
 * a pile of coins (Credits), a heap of blocks (Materials) or reliquaries (Relics), growing
 * through three tiers with the load. Ids begin `carried-<kind>-`.
 */
export function crateStack(
  out: Volume[],
  kind: CrateKind,
  _index: number,
  x: number,
  surfaceY: number,
  amount: number,
): void {
  const id = `${CRATE_ID}${kind.toLowerCase()}`;
  surfaceY += CRATE_RISE;
  const tier = crateCount(kind, amount);
  box(out, `${id}-base`, x, surfaceY, CRATE_Z, SHELF_W, SHELF_H, SHELF_D, TONE.shelf);
  const top = surfaceY + SHELF_H;
  if (kind === 'CREDITS') coinPile(out, id, x, top, tier);
  else if (kind === 'MATERIALS') blockHeap(out, id, x, top, tier);
  else reliquaries(out, id, x, top, tier);
  box(out, `${id}-hit`, x, surfaceY, CRATE_Z, SHELF_W + 0.04, 0.3, SHELF_D + 0.1, TONE.dark, 0);
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

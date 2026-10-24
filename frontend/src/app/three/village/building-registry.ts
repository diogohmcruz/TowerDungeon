/**
 * The registry of in-world buildings (architecture doc 5.6): the framework every
 * step of 3.0.0 plugs into. A building is a Village subject that opens a popover
 * on a click, shows a caption with its exact figures on hover, and appears in the
 * list view. Pure: no Angular, no three.js.
 *
 * To register a building:
 *  1. Draw it in `village-layout.ts` from the box kit, with volume ids that share
 *     one prefix, and an invisible `<prefix>-hit` box a little larger than it (the
 *     near-miss pad the bell uses). Draw its gauges with `gauge-kit.ts`.
 *  2. Map that prefix to `{ kind: 'building', id: '<ID>' }` in `VOLUME_SUBJECTS`
 *     (`village-subjects.ts`).
 *  3. Add a `defineBuilding({ ... })` to `BUILDINGS` in `buildings.ts`.
 * Nothing else changes: the popover, the hover caption, the outline, the click
 * limits, the closing rules and the list view all follow the registry.
 */
import type { GameAction } from '../../interfaces/game-action.enum';
import type { GameState } from '../../interfaces/game-state';
import type { Availability, LaunchCommand } from '../launch/launch-controls';

/** What an action may do: send the same commands the panel's buttons send. */
export interface BuildingCommands {
  send(action: GameAction, payload?: unknown): void;
  /** The launch controls (the bell, the lamp): Set out with the Loadout chosen, a Wave, Extract. */
  launch(command: LaunchCommand): void;
}

/** One number a building shows: the exact figure, its rate and what spending leaves. */
export interface GaugeSpec {
  id: string;
  /** False hides this gauge (a good that has moved to another building). Absent means shown. */
  visible?(state: GameState): boolean;
  /** The name of the good, as the game calls it ("Food"). */
  label: string;
  /** The exact figure. */
  value(state: GameState): number;
  /** How the figure is written; defaults to `formatFigure`. */
  format?(value: number): string;
  /** 0..1, how full the drawn gauge is. */
  fill(state: GameState): number;
  /** Change per second, signed; null or absent when there is no rate to state. */
  rate?(state: GameState): number | null;
  /** One sentence on what spending leaves behind; null when nothing is asked. */
  leaves?(state: GameState): string | null;
  /** The figure as text when a bare number is not enough ("3 of 8"); replaces `value` and `format`. */
  figure?(state: GameState): string;
  /** False keeps it out of the hover caption (the popover and the list still show it). Default true. */
  caption?: boolean;
}

/** One row of words a building shows besides its gauges: a Milestone, a line of the Toll. */
export interface LineSpec {
  id: string;
  /** A heading the row sits under; consecutive rows with the same group share one. */
  group?: string;
  label: string;
  value: string;
  /** A second, quieter sentence under the row. */
  detail?: string;
}

/** The question an action asks before it runs, and the two answers. */
export interface ConfirmSpec {
  prompt: string;
  confirmLabel: string;
  cancelLabel: string;
}

/** One written line of a section: a loss is written as plainly as a gain. */
export interface WrittenLine {
  text: string;
  loss: boolean;
}

/**
 * Written content a building shows besides its gauges and rows: the Ledger at
 * the Study, the Expedition Summary at the Gate. Plain lines, no actions.
 */
export interface SectionSpec {
  id: string;
  /** The heading above the lines; absent or null for none. */
  heading?(state: GameState): string | null;
  /** The lines in the order they are read (the building decides: newest first). */
  lines(state: GameState): readonly WrittenLine[];
  /** What to say when there are no lines. */
  empty?: string;
  /** A long list scrolls inside the card rather than stretching it. */
  scroll?: boolean;
}

/** A click on a building's popover or a row of the list view. */
export interface ActionSpec {
  id: string;
  /** What the control says; a function when it carries a figure (a cost, a count). */
  label: string | ((state: GameState) => string);
  /** False leaves the action out of the popover and the list (a Type with no gate to name). Absent means shown. */
  visible?(state: GameState): boolean;
  /** Whether it can be used now; when not, the reason in words (never colour alone). */
  availability(state: GameState): Availability;
  /** What the action would leave, in words, shown before the click (hover or focus). */
  preview?(state: GameState): string | null;
  /** When present, the action asks once more, in words, before it runs. */
  confirm?(state: GameState): ConfirmSpec;
  /** Sends the command. Only called when `availability` says usable. */
  run(commands: BuildingCommands, state: GameState): void;
}

export interface BuildingDefinition {
  /** The subject id (upper case, as `SILO`); equal to the id `subjectOfVolume` gives. */
  id: string;
  /** The name on the popover, the caption and the list view. */
  title: string;
  /** False hides the building (a gate not yet open). Absent means always shown. */
  visible?(state: GameState): boolean;
  /**
   * When the building is not yet open, what opens it, in words (a Village Tier
   * not reached). The picture draws a footing and its hover caption says this.
   */
  lock?(state: GameState): string | null;
  gauges: readonly GaugeSpec[];
  /** Rows of words besides the gauges; absent when there are none. */
  lines?(state: GameState): readonly LineSpec[];
  actions: readonly ActionSpec[];
  /**
   * Actions that depend on the state (one per kind of Unit, a label that says what
   * a click would do now). Shown after `actions`; looked up by id like them.
   */
  moreActions?(state: GameState): readonly ActionSpec[];
  /** The card asks for more width than the usual one (a tree of choices to read). */
  wide?: boolean;
  /** Written lines under the rows (a Ledger, a Summary); absent means none. */
  sections?: readonly SectionSpec[];
  /**
   * True when the popover should open by itself as an Expedition ends (an
   * Extraction or a Wipe). It opens once per Expedition and may be dismissed.
   */
  opensOnReturn?(state: GameState): boolean;
}

/** Every action of a building now: the fixed ones, then the state's own. */
export function actionSpecs(
  def: BuildingDefinition,
  state: GameState,
): readonly ActionSpec[] {
  return def.moreActions ? [...def.actions, ...def.moreActions(state)] : def.actions;
}

/** Identity helper that keeps a definition typed where it is written. */
export function defineBuilding(definition: BuildingDefinition): BuildingDefinition {
  return definition;
}

export interface GaugeView {
  id: string;
  label: string;
  /** The exact figure as text. */
  figure: string;
  /** "+1.20/s" and so on, or null. */
  rate: string | null;
  leaves: string | null;
  fill: number;
  /** Whether the hover caption states it. */
  caption: boolean;
}

export interface LineView {
  id: string;
  /** The heading to show above this row, or null when it continues the group above. */
  heading: string | null;
  label: string;
  value: string;
  detail: string | null;
}

export interface ActionView {
  id: string;
  label: string;
  usable: boolean;
  /** Why it cannot be used; null when it can. */
  reason: string | null;
  /** What it would leave, in words; null when the action states none. */
  preview: string | null;
  /** The question asked before it runs; null when it runs at once. */
  confirm: ConfirmSpec | null;
}

export interface WrittenLineView {
  /** "-" for a loss, "." otherwise, so a loss never rests on colour alone. */
  mark: string;
  text: string;
  loss: boolean;
}

export interface SectionView {
  id: string;
  heading: string | null;
  lines: WrittenLineView[];
  empty: string;
  scroll: boolean;
}

export interface BuildingView {
  id: string;
  title: string;
  gauges: GaugeView[];
  lines: LineView[];
  actions: ActionView[];
  /** The card is drawn wider than the usual one. */
  wide: boolean;
  sections: SectionView[];
}

/** A whole figure with separators, or two decimals when it has a fraction. */
export function formatFigure(value: number): string {
  const v = Number.isFinite(value) ? value : 0;
  const whole = Math.abs(v - Math.round(v)) < 0.005;
  return v.toLocaleString('en-US', {
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  });
}

/** A rate in words: "+1.20/s", "-0.40/s", "0.00/s". */
export function formatRate(perSecond: number): string {
  const r = Math.round(perSecond * 100) / 100;
  const text = Math.abs(r).toFixed(2);
  return `${r > 0 ? '+' : r < 0 ? '-' : ''}${text}/s`;
}

export function isVisible(def: BuildingDefinition, state: GameState): boolean {
  return def.visible ? def.visible(state) : true;
}

export function gaugeView(spec: GaugeSpec, state: GameState): GaugeView {
  const value = spec.value(state);
  const rate = spec.rate?.(state) ?? null;
  return {
    id: spec.id,
    label: spec.label,
    figure: spec.figure ? spec.figure(state) : (spec.format ?? formatFigure)(value),
    rate: rate === null ? null : formatRate(rate),
    leaves: spec.leaves?.(state) ?? null,
    fill: Math.min(1, Math.max(0, spec.fill(state))),
    caption: spec.caption !== false,
  };
}

export function lineViews(lines: readonly LineSpec[]): LineView[] {
  let group: string | undefined;
  return lines.map((l) => {
    const heading = l.group !== undefined && l.group !== group ? l.group : null;
    group = l.group;
    return {
      id: l.id,
      heading,
      label: l.label,
      value: l.value,
      detail: l.detail ?? null,
    };
  });
}

export function actionView(spec: ActionSpec, state: GameState): ActionView {
  const can = spec.availability(state);
  const label = typeof spec.label === 'function' ? spec.label(state) : spec.label;
  return {
    id: spec.id,
    label,
    usable: can.usable,
    reason: can.reason,
    preview: spec.preview?.(state) ?? null,
    confirm: can.usable ? (spec.confirm?.(state) ?? null) : null,
  };
}

export function sectionView(spec: SectionSpec, state: GameState): SectionView {
  return {
    id: spec.id,
    heading: spec.heading?.(state) ?? null,
    lines: spec.lines(state).map((l) => ({
      mark: l.loss ? '-' : '.',
      text: l.text,
      loss: l.loss,
    })),
    empty: spec.empty ?? '',
    scroll: spec.scroll ?? false,
  };
}

export function buildingView(def: BuildingDefinition, state: GameState): BuildingView {
  return {
    id: def.id,
    title: def.title,
    wide: def.wide === true,
    gauges: def.gauges
      .filter((g) => (g.visible ? g.visible(state) : true))
      .map((g) => gaugeView(g, state)),
    lines: lineViews(def.lines?.(state) ?? []),
    actions: actionSpecs(def, state)
      .filter((a) => (a.visible ? a.visible(state) : true))
      .map((a) => actionView(a, state)),
    sections: (def.sections ?? []).map((x) => sectionView(x, state)),
  };
}

/** Every shown building of a registry, as views, in registry order. */
export function buildingViews(
  registry: readonly BuildingDefinition[],
  state: GameState,
): BuildingView[] {
  return registry.filter((d) => isVisible(d, state)).map((d) => buildingView(d, state));
}

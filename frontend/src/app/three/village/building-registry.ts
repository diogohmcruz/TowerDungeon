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
import type { Availability } from '../launch/launch-controls';

/** What an action may do: send the same commands the panel's buttons send. */
export interface BuildingCommands {
  send(action: GameAction, payload?: unknown): void;
}

/** One number a building shows: the exact figure, its rate and what spending leaves. */
export interface GaugeSpec {
  id: string;
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
}

/** A click on a building's popover or a row of the list view. */
export interface ActionSpec {
  id: string;
  label: string;
  /** Whether it can be used now; when not, the reason in words (never colour alone). */
  availability(state: GameState): Availability;
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
  gauges: readonly GaugeSpec[];
  actions: readonly ActionSpec[];
  /**
   * Actions that depend on the state (one per kind of Unit, a label that says what
   * a click would do now). Shown after `actions`; looked up by id like them.
   */
  moreActions?(state: GameState): readonly ActionSpec[];
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
}

export interface ActionView {
  id: string;
  label: string;
  usable: boolean;
  /** Why it cannot be used; null when it can. */
  reason: string | null;
}

export interface BuildingView {
  id: string;
  title: string;
  gauges: GaugeView[];
  actions: ActionView[];
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
    figure: (spec.format ?? formatFigure)(value),
    rate: rate === null ? null : formatRate(rate),
    leaves: spec.leaves?.(state) ?? null,
    fill: Math.min(1, Math.max(0, spec.fill(state))),
  };
}

export function actionView(spec: ActionSpec, state: GameState): ActionView {
  const can = spec.availability(state);
  return { id: spec.id, label: spec.label, usable: can.usable, reason: can.reason };
}

export function buildingView(def: BuildingDefinition, state: GameState): BuildingView {
  return {
    id: def.id,
    title: def.title,
    gauges: def.gauges.map((g) => gaugeView(g, state)),
    actions: actionSpecs(def, state).map((a) => actionView(a, state)),
  };
}

/** Every shown building of a registry, as views, in registry order. */
export function buildingViews(
  registry: readonly BuildingDefinition[],
  state: GameState,
): BuildingView[] {
  return registry.filter((d) => isVisible(d, state)).map((d) => buildingView(d, state));
}

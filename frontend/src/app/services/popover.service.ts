import {
  computed,
  effect,
  inject,
  Injectable,
  signal,
  untracked,
} from '@angular/core';

import { ScreenRect } from '../three/figures/figure-pick';
import {
  actionSpecs,
  BuildingCommands,
  BuildingDefinition,
  buildingViews,
} from '../three/village/building-registry';
import { BUILDINGS } from '../three/village/buildings';
import { SUCCESSION_ID } from '../three/village/succession-building';
import type { VillageSubject } from '../three/village/village-subjects';
import { GameAction } from '../interfaces/game-action.enum';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import { VillageSelectionService } from './village-selection.service';
import { ZoomService } from './zoom.service';

/** A press that barely moves and soon lifts is a click; the Tower scene's limits (architecture doc 5.6). */
export const CLICK_SLOP_PX = 5;
export const CLICK_MAX_MS = 500;

export interface Press {
  x: number;
  y: number;
  at: number;
}

/** Whether a press and the click that followed are a click and not a drag or a hold. */
export function isClick(
  press: Press | null,
  click: { clientX: number; clientY: number; timeStamp: number },
): boolean {
  return (
    !!press &&
    Math.hypot(click.clientX - press.x, click.clientY - press.y) <=
      CLICK_SLOP_PX &&
    click.timeStamp - press.at <= CLICK_MAX_MS
  );
}

/**
 * The kinds of subject a building answers to: a registered building, an Annex
 * (its id is the Trade id) and its Villagers' lane, which is the Annex's too.
 */
const BUILDING_KINDS: readonly VillageSubject['kind'][] = [
  'building',
  'annex',
  'villagers',
];

/**
 * The popover and the list view's one model (architecture doc 5.6). A building
 * is a registered definition (`three/village/buildings.ts`); this service holds
 * which one is open (only one ever is), where it is on screen, and performs a
 * building's actions. The popover and the list view both call `perform`, so an
 * action behaves the same in either.
 *
 * It closes by itself while time is held or a zoomed view is up, and when the
 * Village scene gives way to the Tower.
 */
@Injectable({ providedIn: 'root' })
export class PopoverService {
  private readonly stateService = inject(GameStateService);
  private readonly ws = inject(GameWebSocketService);
  private readonly selection = inject(VillageSelectionService);
  private readonly zoom = inject(ZoomService);

  /** The registry the views are built from; a test may swap it. */
  registry: readonly BuildingDefinition[] = BUILDINGS;

  private readonly commands: BuildingCommands = {
    send: (action: GameAction, payload?: unknown) =>
      this.ws.sendAction(action, payload),
  };

  /** Every shown building, with its gauges and actions: the list view's rows. */
  readonly buildings = computed(() =>
    buildingViews(this.registry, this.stateService.registryState()),
  );

  private readonly _openId = signal<string | null>(null);
  /** The id of the building whose popover is open, or null. */
  readonly openId = this._openId.asReadonly();
  /** The open building's view, or null (a building that went away closes it). */
  readonly open = computed(
    () => this.buildings().find((b) => b.id === this._openId()) ?? null,
  );

  private readonly _openSubject = signal<VillageSubject | null>(null);
  /** The subject (kind and id) of the open building, for finding it in the scene. */
  readonly openSubject = this._openSubject.asReadonly();

  private readonly _anchor = signal<ScreenRect | null>(null);
  /** Where the open building is on the canvas now, for the popover that follows it. */
  readonly anchor = this._anchor.asReadonly();

  private press: Press | null = null;
  private meshPressAt = -1;

  /** Whether the Succession was shown the last time we looked: the popover opens when it first is. */
  private succession = false;

  constructor() {
    // Time held, a zoomed view up, or the Village gone (an Expedition is out): no popover.
    effect(() => {
      const state = this.stateService.state();
      const held = this.stateService.session()?.held === true;
      const zoomed = this.zoom.level() !== 'battle';
      const gone = this.open() === null;
      if (held || zoomed || state.expeditionActive || gone) {
        untracked(() => this.close());
      }
    });
    // The Campaign ends and waits in OFFER: the Succession opens at the Tower's foot by itself.
    effect(() => {
      const shown = this.buildings().some((b) => b.id === SUCCESSION_ID);
      untracked(() => {
        if (shown && !this.succession) {
          this.openFor({ kind: 'building', id: SUCCESSION_ID });
        }
        this.succession = shown;
      });
    });
    // An Expedition that ends (Extraction or Wipe) opens the building that reports it,
    // once: the flag is spent when it opens, so a dismissed card stays dismissed.
    effect(() => {
      const state = this.stateService.state();
      const held = this.stateService.session()?.held === true;
      const zoomed = this.zoom.level() !== 'battle';
      untracked(() => {
        if (state.expeditionActive) {
          this.returning = true;
          return;
        }
        if (!this.returning || held || zoomed) return;
        const def = this.registry.find((d) => d.opensOnReturn?.(state));
        if (!def) return;
        this.returning = false;
        this.openFor({ kind: 'building', id: def.id });
      });
    });
  }

  /** An Expedition was seen out, and its report has not been opened yet. */
  private returning = false;

  /** Whether a subject is a registered, shown building. */
  isBuilding(subject: VillageSubject | null): boolean {
    return (
      !!subject &&
      BUILDING_KINDS.includes(subject.kind) &&
      this.buildings().some((b) => b.id === subject.id)
    );
  }

  /** The press that began a click, from the canvas. */
  notePress(press: Press | null): void {
    this.press = press;
  }

  /** The press landed on a scene volume: the scene's own click handler takes the click. */
  noteMeshPress(at: number): void {
    this.meshPressAt = at;
  }

  /** Whether the click that just came is the one a scene volume handles. */
  takeMeshPress(): boolean {
    const hit = !!this.press && this.press.at === this.meshPressAt;
    this.meshPressAt = -1;
    return hit;
  }

  /**
   * A click on a scene volume. A building opens its popover, or closes it when
   * it is the open one; a drag or a long hold does nothing; anything else closes it.
   * Returns whether it took the click (a building).
   */
  sceneClick(
    subject: VillageSubject | null,
    click: { clientX: number; clientY: number; timeStamp: number },
  ): boolean {
    if (!isClick(this.press, click)) {
      return this.isBuilding(subject);
    }
    if (subject && this.isBuilding(subject)) {
      this.toggle(subject);
      return true;
    }
    this.close();
    return false;
  }

  /** A click on empty space (the sky, the dark): the popover closes. */
  emptyClick(click: { clientX: number; clientY: number; timeStamp: number }): void {
    if (isClick(this.press, click)) {
      this.close();
    }
  }

  toggle(subject: VillageSubject): void {
    if (this._openId() === subject.id) {
      this.close();
    } else {
      this.openFor(subject);
    }
  }

  /** Opens a building's popover, closing any other; it is the selected subject too. */
  openFor(subject: VillageSubject): void {
    if (!this.isBuilding(subject)) return;
    this._anchor.set(null);
    const shown: VillageSubject =
      subject.kind === 'villagers' ? { kind: 'annex', id: subject.id } : subject;
    this._openId.set(shown.id);
    this._openSubject.set(shown);
    this.selection.selected.set(shown);
  }

  close(): void {
    const id = this._openId();
    if (id === null) return;
    this._openId.set(null);
    this._openSubject.set(null);
    this._anchor.set(null);
    if (this.selection.selected()?.id === id) {
      this.selection.selected.set(null);
    }
  }

  /** The scene reports where the open building is; moves under half a pixel do not count. */
  setAnchor(rect: ScreenRect | null): void {
    const now = this._anchor();
    const moved = (a: number, b: number) => Math.abs(a - b) >= 0.5;
    if (
      rect &&
      now &&
      !moved(rect.left, now.left) &&
      !moved(rect.top, now.top) &&
      !moved(rect.right, now.right) &&
      !moved(rect.bottom, now.bottom)
    ) {
      return;
    }
    if (rect || now) this._anchor.set(rect);
  }

  /**
   * Performs a building's action, the one route for the popover and the list
   * view. Refused (nothing sent) when the action is unknown or cannot be used.
   */
  perform(buildingId: string, actionId: string): boolean {
    const def = this.registry.find((d) => d.id === buildingId);
    const state = this.stateService.registryState();
    const action = def && actionSpecs(def, state).find((a) => a.id === actionId);
    if (!def || !action || !action.availability(state).usable) {
      return false;
    }
    action.run(this.commands, state);
    return true;
  }
}

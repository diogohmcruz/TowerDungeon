import {
  computed,
  effect,
  inject,
  Injectable,
  signal,
  untracked,
} from '@angular/core';

import { GameAction } from '../interfaces/game-action.enum';
import { ShotName } from '../three/camera-shots';
import { figureId, ScreenRect, UnitFocus } from '../three/figures/figure-pick';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import { StageService } from './stage.service';
import { UnitViewService } from './unit-view.service';

/**
 * The zoom ladder of the Tower scene (architecture doc 5.5): battle, the
 * closeup (#94), and the unit view (#95), which frames one figure and shows its
 * card. A click on a figure goes straight to its unit view; stepping back goes
 * unit, closeup, battle. A zoomed view fills
 * the screen, hiding the menus as the hide key does, and stops time: it sends
 * `HOLD` on the way in and `RESUME` on the way out, the same accepted pause as
 * a Held Party. `overview` is its own toggle and is not part of this ladder.
 */
@Injectable({ providedIn: 'root' })
export class ZoomService {
  private readonly ws = inject(GameWebSocketService);
  private readonly stage = inject(StageService);
  private readonly stateService = inject(GameStateService);
  private readonly unitView = inject(UnitViewService);

  private readonly _level = signal<ShotName>('battle');
  /** The Shot the camera is heading for. */
  readonly level = this._level.asReadonly();
  readonly closeup = computed(() => this._level() === 'closeup');

  private readonly _focus = signal<UnitFocus | null>(null);
  /** The figure the unit view is on; null at the other rungs. */
  readonly focus = this._focus.asReadonly();

  private readonly _anchor = signal<ScreenRect | null>(null);
  /** Where that figure is on the canvas now, for the card that follows it. */
  readonly anchor = this._anchor.asReadonly();

  /** Whether this service sent the `HOLD`, so only it sends the matching `RESUME`. */
  private ownsHold = false;
  private menusWereHidden = false;

  constructor() {
    // An Expedition ending releases the hold: the Tower scene is gone, and so is the Party.
    effect(() => {
      if (
        !this.stateService.state().expeditionActive &&
        this._level() !== 'battle'
      ) {
        this.release();
      }
    });
    // Choosing a Party member in an army list (the keyboard route) opens the same view. A Reserve
    // at home is not drawn as a figure, so choosing one does nothing here and stays in the panel.
    effect(() => {
      const chosen = this.unitView.chosen();
      if (chosen === null) {
        return;
      }
      untracked(() => {
        this.unitView.release();
        const party = Object.values(
          this.stateService.state().unitsOnTower ?? {},
        );
        if (party.some((members) => members?.some((m) => m.id === chosen))) {
          this.focusUnit({ kind: 'unit', id: chosen });
        }
      });
    });
    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', () => this.release());
    }
  }

  /** A click on empty space, or the HUD button: into the closeup, or one rung back. */
  toggleCloseup(): void {
    if (this._level() === 'battle') {
      this.enter();
    } else {
      this.back();
    }
  }

  /**
   * A click on a figure, or choosing a unit in a list: its view, from any
   * rung. Choosing the unit already shown steps back to the closeup. Time is
   * held from the first rung and stays held until battle.
   */
  focusUnit(target: UnitFocus): void {
    const now = this._focus();
    if (now && figureId(now) === figureId(target)) {
      this.back();
      return;
    }
    if (this._level() === 'battle' && !this.enter()) {
      return;
    }
    this._focus.set({ ...target });
    this._level.set('unit');
  }

  /** Escape, or a click on empty space: one rung back toward the normal view. */
  back(): void {
    if (this._level() === 'unit') {
      this._focus.set(null);
      this._anchor.set(null);
      this._level.set('closeup');
    } else {
      this.release();
    }
  }

  /** The scene reports where the focused figure is; small moves do not count. */
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
    if (rect || now) {
      this._anchor.set(rect);
    }
  }

  /** Back to battle, `RESUME` if this service held, menus as they were. Safe to call anywhere. */
  release(): void {
    if (this._level() === 'battle') {
      return;
    }
    this._level.set('battle');
    this._focus.set(null);
    this._anchor.set(null);
    this.stage.setMenusHidden(this.menusWereHidden);
    if (this.ownsHold) {
      this.ownsHold = false;
      // The server clears its own hold when the Expedition ends; only ask while it still holds.
      if (this.stateService.session()?.held !== false) {
        this.ws.sendAction(GameAction.RESUME, null);
      }
    }
  }

  /** Holds, hides the menus and takes the closeup. False without an Expedition. */
  private enter(): boolean {
    if (!this.stateService.state().expeditionActive) {
      return false;
    }
    this.menusWereHidden = this.stage.menusHidden();
    this.stage.setMenusHidden(true);
    // Already Held (returned to a held Party): time is stopped, and it stays the player's to resume.
    if (!this.stateService.session()?.held) {
      this.ws.sendAction(GameAction.HOLD, null);
      this.ownsHold = true;
    }
    this._level.set('closeup');
    return true;
  }
}

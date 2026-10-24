import { computed, effect, inject, Injectable, signal } from '@angular/core';

import { GameAction } from '../interfaces/game-action.enum';
import { ShotName } from '../three/camera-shots';
import { GameStateService } from './game-state.service';
import { GameWebSocketService } from './game-web-socket.service';
import { StageService } from './stage.service';

/**
 * The zoom ladder of the Tower scene (architecture doc 5.5): battle, then the
 * closeup (#94). The unit view (#95) adds the third rung. A zoomed view fills
 * the screen, hiding the menus as the hide key does, and stops time: it sends
 * `HOLD` on the way in and `RESUME` on the way out, the same accepted pause as
 * a Held Party. `overview` is its own toggle and is not part of this ladder.
 */
@Injectable({ providedIn: 'root' })
export class ZoomService {
  private readonly ws = inject(GameWebSocketService);
  private readonly stage = inject(StageService);
  private readonly stateService = inject(GameStateService);

  private readonly _level = signal<ShotName>('battle');
  /** The Shot the camera is heading for. */
  readonly level = this._level.asReadonly();
  readonly closeup = computed(() => this._level() === 'closeup');

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
    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', () => this.release());
    }
  }

  /** A click on empty space, or the HUD button: into the closeup, or back to battle. */
  toggleCloseup(): void {
    if (this.closeup()) {
      this.release();
    } else {
      this.enter();
    }
  }

  /** Escape: one rung back toward the normal view. */
  back(): void {
    this.release();
  }

  /** Back to battle, `RESUME` if this service held, menus as they were. Safe to call anywhere. */
  release(): void {
    if (this._level() === 'battle') {
      return;
    }
    this._level.set('battle');
    this.stage.setMenusHidden(this.menusWereHidden);
    if (this.ownsHold) {
      this.ownsHold = false;
      // The server clears its own hold when the Expedition ends; only ask while it still holds.
      if (this.stateService.session()?.held !== false) {
        this.ws.sendAction(GameAction.RESUME, null);
      }
    }
  }

  private enter(): void {
    if (!this.stateService.state().expeditionActive) {
      return;
    }
    this.menusWereHidden = this.stage.menusHidden();
    this.stage.setMenusHidden(true);
    // Already Held (returned to a held Party): time is stopped, and it stays the player's to resume.
    if (!this.stateService.session()?.held) {
      this.ws.sendAction(GameAction.HOLD, null);
      this.ownsHold = true;
    }
    this._level.set('closeup');
  }
}

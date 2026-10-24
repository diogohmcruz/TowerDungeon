import { effect, inject, Injectable, signal, untracked } from '@angular/core';

import { prefersReducedMotion } from '../three/camera-shots';
import {
  frameAt,
  quantiseCover,
  RISE_DROP,
  TransitionKind,
} from '../three/transition';
import { GameStateService } from './game-state.service';

/**
 * Which scene is drawn when an Expedition starts or ends, and how the two are
 * joined (architecture doc 5.5): a dither dissolve and a 1.6 s camera rise on
 * Set out, the reverse drop on Extraction, and on a Wipe a hold on the empty
 * stair first. Under reduced motion it is a cut. It never blocks input: the
 * veil takes no pointer events and the game is not held by it.
 */
@Injectable({ providedIn: 'root' })
export class TransitionService {
  private readonly state = inject(GameStateService).state;

  private readonly _towerShown = signal(this.state().expeditionActive);
  /** Whether the Tower scene (rather than the Village) is the one drawn. */
  readonly towerShown = this._towerShown.asReadonly();
  private readonly _cover = signal(0);
  /** How much of the picture the void covers, in 16 dither steps. */
  readonly cover = this._cover.asReadonly();
  /** 1 at rest; below 1 the Tower camera is low. Read each frame, so not a signal. */
  lift = 1;
  /** Whether a transition is playing. */
  running = false;

  private kind: TransitionKind | null = null;
  private target = this._towerShown();
  private startedAt = 0;
  private raf = 0;
  private previous = this._towerShown();
  /** Replaced in tests, which drive `advance` themselves. */
  protected schedule: (cb: (now: number) => void) => number = (cb) =>
    typeof requestAnimationFrame === 'function' ? requestAnimationFrame(cb) : 0;

  constructor() {
    effect(() => {
      const state = this.state();
      const active = state.expeditionActive;
      const wiped = state.lastRunSummary?.outcome === 'WIPED';
      untracked(() => {
        if (active === this.previous) {
          return;
        }
        this.previous = active;
        this.begin(active, wiped);
      });
    });
  }

  /** The Tower camera's height below its resting pose, in world units (0 at rest). */
  get drop(): number {
    return (1 - this.lift) * RISE_DROP;
  }

  /** The Expedition began (`toTower`) or ended: play the transition, or cut. */
  begin(toTower: boolean, wiped = false): void {
    this.finish();
    this.target = toTower;
    if (prefersReducedMotion()) {
      this._towerShown.set(toTower);
      return;
    }
    this.kind = toTower ? 'set-out' : wiped ? 'wipe' : 'extract';
    this.running = true;
    this.startedAt = -1;
    this.advance(0);
    this.raf = this.schedule((now) => this.frame(now));
  }

  /** Plays the frame `ms` after the start. */
  advance(ms: number): void {
    if (!this.kind) {
      return;
    }
    const f = frameAt(this.kind, ms);
    if (f.done) {
      this.finish();
      return;
    }
    this._towerShown.set(f.tower);
    this._cover.set(quantiseCover(f.cover));
    this.lift = f.lift;
  }

  /** Ends at once, in the state the transition was heading for. */
  finish(): void {
    if (this.raf && typeof cancelAnimationFrame === 'function') {
      cancelAnimationFrame(this.raf);
    }
    this.raf = 0;
    this.kind = null;
    this.running = false;
    this.lift = 1;
    this._cover.set(0);
    this._towerShown.set(this.target);
  }

  private frame(now: number): void {
    if (!this.kind) {
      return;
    }
    if (this.startedAt < 0) {
      this.startedAt = now;
    }
    this.advance(now - this.startedAt);
    if (this.kind) {
      this.raf = this.schedule((t) => this.frame(t));
    }
  }
}

import { computed, Injectable, signal } from '@angular/core';

import {
  autoPixelScale,
  parseQuality,
  PixelScale,
  Quality,
  readPixelOverride,
} from '../three/ink/pixel-scale';
import { NARROW_BELOW } from '../three/stage-view';

export const QUALITY_STORAGE = 'td-quality';

/**
 * The full-screen stage: the viewport, whether the menus are shown, and the
 * pixel scale the scenes draw at (automatic, or the player's quality setting).
 */
@Injectable({ providedIn: 'root' })
export class StageService {
  /** Dev tooling (`?pixelScale=`) beats the stored setting. */
  private readonly override: PixelScale | null = readPixelOverride(
    typeof location === 'undefined' ? '' : location.search,
  );
  private readonly _quality = signal<Quality>(this.readQuality());
  private readonly _viewport = signal(this.measure());
  private readonly _menusHidden = signal(false);

  readonly quality = this._quality.asReadonly();
  readonly viewport = this._viewport.asReadonly();
  /** True while the menus are hidden (the key or the button). */
  readonly menusHidden = this._menusHidden.asReadonly();
  /** Below about 1100 px the menus take the full width. */
  readonly narrow = computed(() => this._viewport().width < NARROW_BELOW);
  /** Screen pixels per virtual pixel. */
  readonly scale = computed<PixelScale>(() => {
    if (this.override !== null) {
      return this.override;
    }
    const quality = this._quality();
    if (quality !== 'auto') {
      return quality;
    }
    const { width, height } = this._viewport();
    return autoPixelScale(width, height);
  });
  /** The `dpr` handed to `ngt-canvas`: a plain number goes straight to `setPixelRatio`. */
  readonly dpr = computed(() => 1 / this.scale());

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () => this.remeasure());
    }
  }

  setQuality(quality: Quality): void {
    this._quality.set(quality);
    try {
      localStorage.setItem(QUALITY_STORAGE, String(quality));
    } catch {
      /* the setting just does not persist */
    }
  }

  toggleMenus(): void {
    this._menusHidden.update((hidden) => !hidden);
  }

  showMenus(): void {
    this._menusHidden.set(false);
  }

  remeasure(): void {
    this.setViewport(this.measure());
  }

  /** Takes a viewport size (CSS px); the window's own size arrives through `remeasure`. */
  setViewport(next: { width: number; height: number }): void {
    const now = this._viewport();
    if (next.width !== now.width || next.height !== now.height) {
      this._viewport.set(next);
    }
  }

  private measure(): { width: number; height: number } {
    return typeof window === 'undefined'
      ? { width: 1280, height: 720 }
      : { width: window.innerWidth, height: window.innerHeight };
  }

  private readQuality(): Quality {
    try {
      return parseQuality(localStorage.getItem(QUALITY_STORAGE));
    } catch {
      return 'auto';
    }
  }
}

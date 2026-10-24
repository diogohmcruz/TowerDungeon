import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';

import { StageService } from '../../services/stage.service';
import { TransitionService } from '../../services/transition.service';
import { covered } from '../../three/transition';

const VOID_FALLBACK = '#07090f';

/**
 * The dither of the Set out transition: the void, laid over the scene one virtual
 * pixel at a time by the same Bayer threshold the ink shaders use. No blur and no
 * colour but the void; it takes no pointer events, so input is never blocked.
 */
@Component({
  selector: 'app-transition-veil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #veil aria-hidden="true"></canvas>`,
  styles: `
    :host {
      position: absolute;
      inset: 0;
      z-index: 2;
      pointer-events: none;
    }
    canvas {
      display: block;
      width: 100%;
      height: 100%;
      image-rendering: pixelated;
    }
  `,
})
export class TransitionVeil {
  private readonly transition = inject(TransitionService);
  private readonly stage = inject(StageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('veil');

  constructor() {
    effect(() => {
      const cover = this.transition.cover();
      const scale = this.stage.scale();
      const { width, height } = this.stage.viewport();
      this.paint(cover, scale, width, height);
    });
  }

  private paint(cover: number, scale: number, width: number, height: number): void {
    const canvas = this.canvas().nativeElement;
    const w = Math.max(1, Math.ceil(width / scale));
    const h = Math.max(1, Math.ceil(height / scale));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    ctx.clearRect(0, 0, w, h);
    if (cover <= 0) {
      return;
    }
    const ink =
      getComputedStyle(this.host.nativeElement).getPropertyValue('--void').trim() ||
      VOID_FALLBACK;
    ctx.fillStyle = ink;
    if (cover >= 1) {
      ctx.fillRect(0, 0, w, h);
      return;
    }
    // The 4x4 Bayer tile at this cover, laid over the canvas as a repeating pattern.
    const tile = document.createElement('canvas');
    tile.width = 4;
    tile.height = 4;
    const tctx = tile.getContext('2d');
    if (!tctx) {
      return;
    }
    tctx.fillStyle = ink;
    for (let y = 0; y < 4; y++) {
      for (let x = 0; x < 4; x++) {
        if (covered(x, y, cover)) {
          tctx.fillRect(x, y, 1, 1);
        }
      }
    }
    const pattern = ctx.createPattern(tile, 'repeat');
    if (!pattern) {
      return;
    }
    ctx.fillStyle = pattern;
    ctx.fillRect(0, 0, w, h);
  }
}

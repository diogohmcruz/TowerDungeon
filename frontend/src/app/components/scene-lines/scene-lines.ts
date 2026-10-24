import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The words laid over the picture, inside the Tower's region and clear of the
 * menus: the caption of what is pointed at, and above it the one line a refused
 * launch leaves (architecture doc 5.5). Plain text, never a pointer target.
 */
@Component({
  selector: 'app-scene-lines',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (refusal(); as line) {
      <p class="line refusal" role="status">{{ line }}</p>
    }
    @if (caption(); as text) {
      <p class="line" [class.locked]="locked()">{{ text }}</p>
    }
  `,
  host: {
    '[style.left.px]': 'left() + 8',
    '[style.width.px]': 'width() - 16',
  },
  styles: `
    :host {
      position: absolute;
      bottom: 8px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      pointer-events: none;
    }
    .line {
      margin: 0;
      padding: 4px 8px;
      background: var(--panel);
      border: 1px solid var(--rule);
      color: var(--text);
      font-size: 12px;
    }
    .line.locked {
      color: var(--bone);
    }
    .line.refusal {
      border-color: var(--bone);
      color: var(--bone);
    }
  `,
})
export class SceneLines {
  readonly caption = input<string | null>(null);
  readonly locked = input(false);
  readonly refusal = input<string | null>(null);
  /** The region the words sit in, as the canvas gives it (CSS px from its left edge). */
  readonly left = input(0);
  readonly width = input(0);
}

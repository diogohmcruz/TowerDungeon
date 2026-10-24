import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
} from '@angular/core';

import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import type { ActionView } from '../../three/village/building-registry';
import { placeCard } from '../unit-focus/card-placement';

/**
 * The popover of a building (architecture doc 5.6): one translucent card beside
 * the picked building, offering that building's gauges and actions only. It
 * follows the building, stays wholly on screen, and closes on Escape or a click
 * elsewhere. Only one is open at a time: the service holds one id.
 */
@Component({
  selector: 'app-building-popover',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (view(); as v) {
      <aside
        class="popover"
        role="dialog"
        [attr.aria-label]="v.title"
        [style.left.px]="place().left"
        [style.top.px]="place().top"
        [style.width.px]="place().width"
        [style.max-height.px]="place().maxHeight"
      >
        <h3>{{ v.title }}</h3>
        @for (g of v.gauges; track g.id) {
          <section class="gauge" [attr.aria-label]="g.label">
            <p class="figure">
              <span class="label">{{ g.label }}</span>
              <span class="value">{{ g.figure }}</span>
              @if (g.rate) {
                <span class="rate">{{ g.rate }}</span>
              }
            </p>
            <div class="bar" aria-hidden="true">
              <div class="fill" [style.width.%]="g.fill * 100"></div>
            </div>
            @if (g.leaves) {
              <p class="leaves">{{ g.leaves }}</p>
            }
          </section>
        }
        @for (a of v.actions; track a.id) {
          <button
            type="button"
            class="btn"
            [attr.aria-disabled]="a.usable ? null : 'true'"
            [attr.aria-describedby]="a.usable ? null : reasonId(a)"
            (click)="run(v.id, a)"
          >
            {{ a.label }}
          </button>
          @if (a.reason) {
            <p class="reason" [id]="reasonId(a)">{{ a.reason }}</p>
          }
        }
      </aside>
    }
  `,
  host: {
    '(document:keydown.escape)': 'onEscape($event)',
    '(document:pointerdown)': 'onPointerDown($event)',
  },
  styles: `
    .popover {
      position: absolute;
      z-index: 2;
      box-sizing: border-box;
      overflow-y: auto;
      padding: 8px 10px;
      color: var(--text);
      font-size: 12px;
      /* Translucent over the scene: the same hard-edged ink panel, no blur. */
      background: color-mix(in srgb, var(--panel) 88%, transparent);
      border: 1px solid var(--bone);
    }
    h3 {
      margin: 0 0 6px;
      font-size: 13px;
      color: var(--bone);
    }
    .gauge {
      margin-bottom: 8px;
    }
    .figure {
      display: flex;
      gap: 8px;
      align-items: baseline;
      margin: 0;
    }
    .label {
      color: var(--muted);
    }
    .value {
      color: var(--bone);
    }
    .bar {
      height: 4px;
      margin-top: 4px;
      border: 1px solid var(--rule);
    }
    .fill {
      height: 100%;
      background: var(--bone);
    }
    .leaves,
    .reason {
      margin: 4px 0 0;
      color: var(--muted);
    }
    button.btn {
      display: block;
      width: 100%;
      margin-top: 6px;
      text-align: left;
    }
    button[aria-disabled='true'] {
      opacity: 0.45;
      cursor: not-allowed;
    }
  `,
})
export class BuildingPopover {
  private readonly popover = inject(PopoverService);
  private readonly stage = inject(StageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly view = this.popover.open;

  protected readonly place = computed(() => {
    const { width, height } = this.stage.viewport();
    return placeCard(this.popover.anchor(), width, height, this.wanted());
  });

  /** About how tall the card is, so it slides up to show its actions rather than scroll them away. */
  private readonly wanted = computed(() => {
    const v = this.view();
    if (!v) return 0;
    const gauges = v.gauges.reduce((n, g) => n + 54 + (g.leaves ? 48 : 0), 0);
    const actions = v.actions.reduce((n, a) => n + 38 + (a.reason ? 32 : 0), 0);
    return 36 + gauges + actions;
  });

  protected reasonId(action: ActionView): string {
    return `popover-reason-${action.id}`;
  }

  /** A refused action does nothing; its reason is already on the card in words. */
  protected run(buildingId: string, action: ActionView): void {
    if (action.usable) this.popover.perform(buildingId, action.id);
  }

  protected onEscape(event: Event): void {
    if (this.popover.openId() === null) return;
    event.preventDefault();
    this.popover.close();
  }

  /**
   * A press outside the card and outside the canvas (a menu, the top bar) closes
   * it. A press on the canvas is the scene's own click, which knows buildings.
   */
  protected onPointerDown(event: PointerEvent): void {
    if (this.popover.openId() === null) return;
    const target = event.target as Node | null;
    if (!target || this.host.nativeElement.contains(target)) return;
    if ((target as HTMLElement).tagName === 'CANVAS') return;
    this.popover.close();
  }
}

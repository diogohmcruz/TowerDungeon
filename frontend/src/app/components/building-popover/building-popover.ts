import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
} from '@angular/core';

import { PopoverService } from '../../services/popover.service';
import { StageService } from '../../services/stage.service';
import { ActionRow } from '../action-row/action-row';
import { CARD_WIDTH, placeCard } from '../unit-focus/card-placement';

/** The width of a card that asks for more room: a tree of choices to read. */
const WIDE_CARD_WIDTH = 420;

/**
 * The popover of a building (architecture doc 5.6): one translucent card beside
 * the picked building, offering that building's gauges and actions only. It
 * follows the building, stays wholly on screen, and closes on Escape or a click
 * elsewhere. Only one is open at a time: the service holds one id.
 */
@Component({
  selector: 'app-building-popover',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionRow],
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
        <!-- What is read comes first: the Summary of the Expedition that just came back, the Ledger. -->
        @for (s of v.sections; track s.id) {
          <section class="written" [attr.aria-label]="s.heading ?? v.title">
            @if (s.heading) {
              <h4>{{ s.heading }}</h4>
            }
            @if (s.lines.length > 0) {
              <ul
                class="written-lines"
                [class.scroll]="s.scroll"
                [attr.tabindex]="s.scroll ? 0 : null"
              >
                @for (l of s.lines; track $index) {
                  <li [class.loss]="l.loss">{{ l.mark }} {{ l.text }}</li>
                }
              </ul>
            } @else {
              <p class="leaves">{{ s.empty }}</p>
            }
          </section>
        }
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
        @for (l of v.lines; track l.id) {
          @if (l.heading) {
            <h4>{{ l.heading }}</h4>
          }
          <p class="line">
            <span class="label">{{ l.label }}</span>
            <span class="value">{{ l.value }}</span>
          </p>
          @if (l.detail) {
            <p class="leaves">{{ l.detail }}</p>
          }
        }
        @for (a of v.actions; track a.id) {
          <app-action-row
            idPrefix="popover-reason"
            [action]="a"
            (perform)="run(v.id, $event)"
          />
        }
        <button type="button" class="btn dismiss" (click)="dismiss()">
          Close
        </button>
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
    h4 {
      margin: 8px 0 2px;
      font-size: 12px;
      color: var(--bone);
    }
    .line {
      display: flex;
      gap: 8px;
      justify-content: space-between;
      margin: 0;
    }
    .written {
      margin-bottom: 8px;
    }
    .written-lines {
      margin: 0;
      padding: 0;
      list-style: none;
      line-height: 1.5;
    }
    .written-lines.scroll {
      max-height: 132px;
      overflow-y: auto;
    }
    .written-lines li.loss {
      color: var(--ember-text);
    }
    button.dismiss {
      display: block;
      width: 100%;
      margin-top: 6px;
      text-align: left;
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
    return placeCard(
      this.popover.anchor(),
      width,
      height,
      this.wanted(),
      this.view()?.wide ? WIDE_CARD_WIDTH : CARD_WIDTH,
    );
  });

  /** About how tall the card is, so it slides up to show its actions rather than scroll them away. */
  private readonly wanted = computed(() => {
    const v = this.view();
    if (!v) return 0;
    const lines = (text: string) => Math.ceil(text.length / 38) * 18 + 4;
    const gauges = v.gauges.reduce((n, g) => n + 36 + (g.leaves ? lines(g.leaves) : 0), 0);
    const rows = v.lines.reduce(
      (n, l) => n + 18 + (l.heading ? 24 : 0) + (l.detail ? lines(l.detail) : 0),
      0,
    );
    const actions = v.actions.reduce(
      (n, a) =>
        n + 38 + (a.reason ? lines(a.reason) : 0) + (a.preview ? lines(a.preview) : 0),
      0,
    );
    const written = v.sections.reduce(
      (n, s) =>
        n +
        24 +
        (s.lines.length > 0
          ? Math.min(s.scroll ? 132 : Infinity, s.lines.length * 19)
          : 20),
      0,
    );
    return 36 + gauges + rows + written + actions + 38;
  });

  protected dismiss(): void {
    this.popover.close();
  }

  /** A refused action does nothing; its reason is already on the card in words. */
  protected run(buildingId: string, actionId: string): void {
    this.popover.perform(buildingId, actionId);
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

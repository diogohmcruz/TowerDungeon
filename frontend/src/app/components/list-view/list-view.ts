import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  untracked,
  viewChild,
} from '@angular/core';

import { ListViewService } from '../../services/list-view.service';
import { PopoverService } from '../../services/popover.service';
import type { ActionView } from '../../three/village/building-registry';

/** Keys that open the list and close it. Not while typing, and never with a modifier. */
export function listKeyIntent(event: KeyboardEvent): 'toggle' | 'close' | null {
  const key = event.key.toLowerCase();
  if (
    (key !== 'l' && key !== 'escape') ||
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    event.defaultPrevented
  ) {
    return null;
  }
  const target = event.target as HTMLElement | null;
  if (
    target &&
    (target.isContentEditable ||
      ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  ) {
    return null;
  }
  return key === 'l' ? 'toggle' : 'close';
}

/**
 * The list view (architecture doc 5.6): every registered building with its
 * gauges and actions, as plain DOM for the keyboard and screen readers. Hidden
 * until `L` (or the skip button, or a stored choice). Its actions are the
 * popover's: both call `PopoverService.perform`, so the command, the refusal
 * and the reason in words are the same.
 */
@Component({
  selector: 'app-list-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="skip"
      aria-keyshortcuts="L"
      (click)="list.show()"
    >
      Open the list view of buildings and actions (L)
    </button>
    @if (list.open()) {
      <section class="list" role="region" aria-label="List view of buildings">
        <header>
          <h2>Buildings</h2>
          <button
            #close
            type="button"
            class="btn"
            aria-keyshortcuts="L Escape"
            (click)="list.hide()"
          >
            Close the list (L)
          </button>
        </header>
        <label class="remember">
          <input
            type="checkbox"
            [checked]="remembered"
            (change)="remember($event)"
          />
          Open this list when the game starts
        </label>
        @for (b of popover.buildings(); track b.id) {
          <section class="building" [attr.aria-labelledby]="'list-' + b.id">
            <h3 [id]="'list-' + b.id">{{ b.title }}</h3>
            <dl>
              @for (g of b.gauges; track g.id) {
                <dt>{{ g.label }}</dt>
                <dd>
                  {{ g.figure }}
                  @if (g.rate) {
                    <span class="rate">({{ g.rate }})</span>
                  }
                  @if (g.leaves) {
                    <span class="leaves">{{ g.leaves }}</span>
                  }
                </dd>
              }
            </dl>
            @for (a of b.actions; track a.id) {
              <button
                type="button"
                class="btn"
                [attr.aria-disabled]="a.usable ? null : 'true'"
                [attr.aria-describedby]="a.usable ? null : reasonId(b.id, a)"
                (click)="run(b.id, a)"
              >
                {{ a.label }}
              </button>
              @if (a.reason) {
                <span class="reason" [id]="reasonId(b.id, a)">
                  {{ a.reason }}
                </span>
              }
            }
          </section>
        } @empty {
          <p>No building has anything to show yet.</p>
        }
      </section>
    }
  `,
  host: { '(document:keydown)': 'onKey($event)' },
  styles: `
    .skip {
      position: fixed;
      top: 8px;
      left: -10000px;
      z-index: 1100;
      padding: 6px 12px;
      color: var(--bone);
      background: var(--panel);
      border: 1px solid var(--bone);
    }
    .skip:focus {
      left: 8px;
    }
    .list {
      position: fixed;
      inset: 0;
      z-index: 1050;
      overflow-y: auto;
      box-sizing: border-box;
      padding: 16px 24px;
      color: var(--text);
      background: var(--panel);
    }
    header {
      display: flex;
      gap: 16px;
      align-items: baseline;
    }
    h2,
    h3 {
      margin: 0 0 6px;
      color: var(--bone);
    }
    .remember {
      display: block;
      margin: 8px 0 16px;
      color: var(--muted);
    }
    .building {
      max-width: 640px;
      margin-bottom: 16px;
      padding: 8px 12px;
      border: 1px solid var(--rule);
    }
    dl {
      margin: 0 0 8px;
    }
    dt {
      display: inline;
      color: var(--muted);
    }
    dd {
      display: inline;
      margin: 0 0 0 6px;
      color: var(--bone);
    }
    dd::after {
      content: '';
      display: block;
    }
    .rate,
    .leaves,
    .reason {
      color: var(--muted);
    }
    .leaves {
      display: block;
      margin-left: 6px;
    }
    .reason {
      margin-left: 8px;
    }
    button[aria-disabled='true'] {
      opacity: 0.45;
      cursor: not-allowed;
    }
  `,
})
export class ListView {
  protected readonly list = inject(ListViewService);
  protected readonly popover = inject(PopoverService);
  private readonly closeButton = viewChild<ElementRef<HTMLElement>>('close');
  private opener: HTMLElement | null = null;

  protected remembered = false;

  constructor() {
    try {
      this.remembered = localStorage.getItem('td-list-view') === 'on';
    } catch {
      /* no stored choice */
    }
    // Opening moves focus into the list; closing gives it back to where it came from.
    effect(() => {
      const open = this.list.open();
      untracked(() => {
        if (open) {
          this.opener = document.activeElement as HTMLElement | null;
          queueMicrotask(() => this.closeButton()?.nativeElement.focus());
        } else {
          this.opener?.focus?.();
          this.opener = null;
        }
      });
    });
  }

  protected reasonId(buildingId: string, action: ActionView): string {
    return `list-reason-${buildingId}-${action.id}`;
  }

  /** The same route as the popover; a refused action does nothing and its reason is in the row. */
  protected run(buildingId: string, action: ActionView): void {
    if (action.usable) this.popover.perform(buildingId, action.id);
  }

  protected remember(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    this.remembered = on;
    this.list.remember(on);
  }

  protected onKey(event: KeyboardEvent): void {
    const intent = listKeyIntent(event);
    if (intent === 'toggle') {
      event.preventDefault();
      this.list.toggle();
    } else if (intent === 'close' && this.list.open()) {
      event.preventDefault();
      this.list.hide();
    }
  }
}

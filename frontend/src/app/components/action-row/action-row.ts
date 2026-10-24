import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';

import type { ActionView } from '../../three/village/building-registry';

/**
 * One action of a building, as the popover and the list view both show it: a
 * button, the reason in words when it cannot be used, and, for an action that
 * asks first (Exodus), the question with its two answers. A refused action
 * does nothing; the reason is on the page, never colour alone.
 */
@Component({
  selector: 'app-action-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (asking()) {
      <div class="confirm" role="alertdialog" [attr.aria-label]="'Confirm ' + action().label">
        <p>{{ action().confirm?.prompt }}</p>
        <button type="button" class="btn answer" (click)="answer(true)">
          {{ action().confirm?.confirmLabel }}
        </button>
        <button type="button" class="btn answer" (click)="answer(false)">
          {{ action().confirm?.cancelLabel }}
        </button>
      </div>
    } @else {
      <button
        type="button"
        class="btn"
        [attr.aria-disabled]="action().usable ? null : 'true'"
        [attr.aria-describedby]="action().usable ? null : reasonId()"
        (click)="press()"
        (mouseenter)="pointed.set(true)"
        (mouseleave)="pointed.set(false)"
        (focus)="pointed.set(true)"
        (blur)="pointed.set(false)"
      >
        {{ action().label }}
      </button>
      @if (action().reason) {
        <p class="reason" [id]="reasonId()">{{ action().reason }}</p>
      } @else if (action().preview && (alwaysPreview() || pointed())) {
        <p class="reason preview" role="status">{{ action().preview }}</p>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
    button.btn {
      display: block;
      width: 100%;
      margin-top: 6px;
      text-align: left;
    }
    button.answer {
      display: inline-block;
      width: auto;
      margin-right: 6px;
    }
    button[aria-disabled='true'] {
      opacity: 0.45;
      cursor: not-allowed;
    }
    .reason,
    .confirm p {
      margin: 4px 0 0;
      color: var(--muted);
    }
    .confirm {
      margin-top: 6px;
      padding: 6px 8px;
      border: 1px solid var(--rule);
    }
  `,
})
export class ActionRow {
  /** The prefix of the reason's id, so two rows never share one. */
  idPrefix = input.required<string>();
  action = input.required<ActionView>();
  /** The list says what an action would leave at all times; the popover only while it is pointed at. */
  alwaysPreview = input(false);
  /** Raised with the action's id once it is to run: at once, or after the answer "yes". */
  perform = output<string>();

  protected readonly pointed = signal(false);
  private readonly confirming = signal(false);
  /** The question shows only while the action can still be used. */
  protected readonly asking = computed(
    () => this.confirming() && this.action().usable && !!this.action().confirm,
  );

  protected reasonId(): string {
    return `${this.idPrefix()}-${this.action().id}`;
  }

  protected press(): void {
    const a = this.action();
    if (!a.usable) return;
    if (a.confirm) {
      this.confirming.set(true);
    } else {
      this.perform.emit(a.id);
    }
  }

  protected answer(yes: boolean): void {
    this.confirming.set(false);
    if (yes && this.action().usable) this.perform.emit(this.action().id);
  }
}

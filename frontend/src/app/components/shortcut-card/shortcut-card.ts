import { Component, effect, ElementRef, inject, input, untracked } from '@angular/core';
import { ShortcutOffer } from '../../interfaces/game-state';
import { VillageSelectionService } from '../../services/village-selection.service';
import { Card } from '../card/card';

/**
 * One Shortcut in the list. An open one is linked to its doorway in the Village:
 * pointer or focus here outlines the mark, and a click on the mark brings the card
 * into view and focuses it. Pointing and picking only; nothing starts from here.
 */
@Component({
  selector: 'app-shortcut-card',
  standalone: true,
  templateUrl: './shortcut-card.html',
  styleUrl: './shortcut-card.scss',
  imports: [Card],
  host: {
    '[class.linked]': 'linked()',
    '[class.chosen]': 'chosen()',
    '[attr.tabindex]': 'shortcut().unlocked ? 0 : null',
    '[attr.data-row]': '"shortcut:" + shortcut().id',
    '[attr.aria-current]': 'chosen() ? "true" : null',
    '(mouseenter)': 'hover(true)',
    '(mouseleave)': 'hover(false)',
    '(focusin)': 'hover(true)',
    '(focusout)': 'hover(false)',
    '(click)': 'pick()',
    '(keydown)': 'onKey($event)',
  },
})
export class ShortcutCard {
  shortcut = input.required<ShortcutOffer>();

  private readonly selection = inject(VillageSelectionService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** Requests older than this card are not for it to answer. */
  private seen = this.selection.focusRequest()?.seq ?? 0;

  protected linked(): boolean {
    return this.shortcut().unlocked && this.selection.isHovered(this.shortcut().id);
  }

  protected chosen(): boolean {
    return this.shortcut().unlocked && this.selection.isSelected(this.shortcut().id);
  }

  constructor() {
    // A click on the doorway brings this card into view and takes focus here.
    effect(() => {
      const request = this.selection.focusRequest();
      if (!request || request.seq <= this.seen) {
        return;
      }
      this.seen = request.seq;
      untracked(() => {
        const sc = this.shortcut();
        if (request.subject.kind !== 'shortcut' || request.subject.id !== sc.id || !sc.unlocked) {
          return;
        }
        const still =
          typeof matchMedia === 'function' &&
          matchMedia('(prefers-reduced-motion: reduce)').matches;
        const el = this.host.nativeElement;
        el.scrollIntoView({ block: 'nearest', behavior: still ? 'auto' : 'smooth' });
        el.focus({ preventScroll: true });
      });
    });
  }

  protected hover(on: boolean): void {
    const sc = this.shortcut();
    if (!sc.unlocked) {
      return;
    }
    this.selection.hover(on ? { kind: 'shortcut', id: sc.id } : null);
  }

  protected pick(): void {
    const sc = this.shortcut();
    if (sc.unlocked) {
      this.selection.select({ kind: 'shortcut', id: sc.id });
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.selection.select(null);
    } else if (
      event.target === event.currentTarget &&
      (event.key === 'Enter' || event.key === ' ')
    ) {
      event.preventDefault();
      this.pick();
    }
  }
}

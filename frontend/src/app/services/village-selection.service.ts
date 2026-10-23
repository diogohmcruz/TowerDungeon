import { Injectable, signal } from '@angular/core';

import { sameThing, VillageSubject } from '../three/village/village-subjects';

/** A request to bring a panel row into view and focus it. `seq` makes repeats distinct. */
export interface RowFocusRequest {
  subject: VillageSubject;
  seq: number;
}

/**
 * What the Village is pointing at. DOM rows and the 3D scene read and write
 * the same two signals, so hovering a row outlines its Annex and the reverse.
 * Read-only: nothing here buys, assigns or sells (architecture doc 5.2).
 */
@Injectable({ providedIn: 'root' })
export class VillageSelectionService {
  readonly hovered = signal<VillageSubject | null>(null);
  readonly selected = signal<VillageSubject | null>(null);
  /** Set when the scene is clicked: the panel focuses and scrolls to the row. */
  readonly focusRequest = signal<RowFocusRequest | null>(null);
  private seq = 0;

  hover(subject: VillageSubject | null): void {
    if (!sameSubject(this.hovered(), subject)) {
      this.hovered.set(subject);
    }
  }

  /** Picks a thing; a second pick of the same thing lets it go. */
  select(subject: VillageSubject | null): void {
    this.selected.set(
      sameThing(this.selected(), subject) ? null : subject,
    );
  }

  /** The scene was clicked: pick the thing and ask its row to take focus. */
  selectFromScene(subject: VillageSubject): void {
    this.selected.set(subject);
    this.focusRequest.set({ subject, seq: ++this.seq });
  }

  clear(): void {
    this.hovered.set(null);
    this.selected.set(null);
  }

  /** Whether a thing is hovered or selected, for a row or a mark. */
  isHovered(id: string): boolean {
    return this.hovered()?.id === id;
  }

  isSelected(id: string): boolean {
    return this.selected()?.id === id;
  }
}

function sameSubject(
  a: VillageSubject | null,
  b: VillageSubject | null,
): boolean {
  return a === b || (!!a && !!b && a.kind === b.kind && a.id === b.id);
}

import { inject, Injectable, signal } from '@angular/core';

import { PopoverService } from './popover.service';
import { StageService } from './stage.service';

/** The player's choice, kept between visits: `on` always opens it, `off` never opens it by itself. */
export const LIST_VIEW_STORAGE = 'td-list-view';

/**
 * Whether the list view should open by itself. Browsers do not say whether a
 * screen reader is running, so this reads what the player has told the game: a
 * stored choice (the list view's own checkbox) or `?list=1` in the address. The
 * skip button at the top of the page, first in the tab order and announced by a
 * screen reader, is the route to it for everyone else. Pure.
 */
export function listViewWanted(search: string, stored: string | null): boolean {
  const asked = new URLSearchParams(search).get('list');
  if (asked === '0') return false;
  if (asked !== null) return true;
  return stored === 'on';
}

/**
 * The list view's open state (architecture doc 5.6): a DOM list of every
 * registered building with its gauges and actions, hidden by default, opened by
 * `L`, and shown at once when the player has said a screen reader is in use.
 * It holds no copy of the data: the rows are the popover service's buildings.
 */
@Injectable({ providedIn: 'root' })
export class ListViewService {
  private readonly popover = inject(PopoverService);
  private readonly stage = inject(StageService);

  private readonly _open = signal(this.wanted());
  readonly open = this._open.asReadonly();

  show(): void {
    // One surface at a time: the popover gives way to the list.
    this.popover.close();
    // The list is a menu: opening it shows the menus the hide key put away.
    this.stage.showMenus();
    this._open.set(true);
  }

  hide(): void {
    this._open.set(false);
  }

  toggle(): void {
    if (this._open()) this.hide();
    else this.show();
  }

  /** Remembers the choice so a screen reader user finds the list open next visit. */
  remember(on: boolean): void {
    try {
      localStorage.setItem(LIST_VIEW_STORAGE, on ? 'on' : 'off');
    } catch {
      /* the choice just does not persist */
    }
  }

  private wanted(): boolean {
    if (typeof location === 'undefined') return false;
    let stored: string | null = null;
    try {
      stored = localStorage.getItem(LIST_VIEW_STORAGE);
    } catch {
      /* unreadable storage means no stored choice */
    }
    return listViewWanted(location.search, stored);
  }
}

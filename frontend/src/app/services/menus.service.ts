import { computed, inject, Injectable } from '@angular/core';

import { ListViewService } from './list-view.service';
import { PopoverService } from './popover.service';
import { StageService } from './stage.service';
import { ZoomService } from './zoom.service';

/**
 * Whether any menu is on screen, and the one command (H, the top bar's button)
 * that puts them all away or brings the list back. It holds no state of its own:
 * the list view, a popover and the narrow launch bar each keep theirs, so the
 * button follows Escape, `L`, a click elsewhere and held time without a second flag.
 * A zoomed view (closeup, unit view) draws none of them.
 */
@Injectable({ providedIn: 'root' })
export class MenusService {
  private readonly list = inject(ListViewService);
  private readonly popover = inject(PopoverService);
  private readonly stage = inject(StageService);
  private readonly zoom = inject(ZoomService);

  /** The list view is open, a popover is open, or (narrow screens) the launch bar is up. */
  readonly showing = computed(
    () =>
      this.zoom.level() === 'battle' &&
      (this.list.open() ||
        this.popover.openId() !== null ||
        (this.stage.narrow() && !this.stage.launchBarHidden())),
  );

  /** Closes everything that is showing; with nothing showing, opens the list view. */
  toggle(): void {
    if (this.zoom.level() !== 'battle') return;
    if (this.showing()) {
      this.list.hide();
      this.popover.close();
      this.stage.setLaunchBarHidden(true);
    } else {
      this.list.show();
    }
  }
}

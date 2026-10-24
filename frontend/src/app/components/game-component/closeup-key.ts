import type { ListViewService } from '../../services/list-view.service';
import type { PopoverService } from '../../services/popover.service';
import type { ZoomService } from '../../services/zoom.service';

/** A key press the page may take: no modifier, nobody has handled it, and the player is not typing. */
export function keyIsFree(event: KeyboardEvent): boolean {
  if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) {
    return false;
  }
  const target = event.target as HTMLElement | null;
  return !(
    target &&
    (target.isContentEditable ||
      ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  );
}

/**
 * The zoom ladder's keys (#130 joined the Village to it): C is the closeup of whichever scene is
 * up, Escape is one rung back. An open building card and the list view take their own Escape
 * first; whichever handler runs first, the other finds it spent (the card closed, or the key
 * already handled), so one press is one step. Returns whether the key was the ladder's.
 */
export function closeupKey(
  event: KeyboardEvent,
  zoom: ZoomService,
  popover: Pick<PopoverService, 'openId'>,
  list: Pick<ListViewService, 'open'>,
): boolean {
  const key = event.key.toLowerCase();
  if ((key !== 'c' && key !== 'escape') || !keyIsFree(event)) {
    return false;
  }
  if (key === 'c') {
    event.preventDefault();
    zoom.toggleCloseup();
    return true;
  }
  if (zoom.level() !== 'battle') {
    event.preventDefault();
    zoom.back();
  } else if (zoom.villageCloseup() && popover.openId() === null && !list.open()) {
    event.preventDefault();
    zoom.back();
  }
  return true;
}

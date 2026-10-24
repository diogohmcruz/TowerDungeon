import {
  afterEveryRender,
  Directive,
  ElementRef,
  inject,
  signal,
} from '@angular/core';

/**
 * Lets a floating card (the unit card, a building's popover) know how tall its
 * content really is, so `placeCard` can slide it up to show all of it, and says
 * out loud when it still has to scroll: a bone-coloured edge at the bottom while
 * there is more below. The card is capped to the screen and scrolls inside.
 */
@Directive({
  selector: '[appCardFit]',
  host: {
    '[class.more-below]': 'moreBelow()',
    '(scroll)': 'measure()',
  },
})
export class CardFit {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);

  /** The height the whole content takes, border included; 0 until it is measured. */
  readonly height = signal(0);
  /** Whether the card scrolls and has not been scrolled to its end. */
  readonly moreBelow = signal(false);

  constructor() {
    afterEveryRender({ read: () => this.measure() });
  }

  measure(): void {
    const el = this.el.nativeElement;
    const border = el.offsetHeight - el.clientHeight;
    const height = el.scrollHeight + Math.max(0, border);
    if (height !== this.height()) this.height.set(height);
    const more = el.scrollHeight - el.clientHeight - el.scrollTop > 1;
    if (more !== this.moreBelow()) this.moreBelow.set(more);
  }
}

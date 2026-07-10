import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  standalone: true,
  templateUrl: './card.html',
  styleUrl: './card.scss',
})
export class Card {
  /** When false, the header row (title/badge slots) is not rendered. */
  showHead = input<boolean>(true);
  /** Dims the card (e.g. a maxed-out upgrade). */
  muted = input<boolean>(false);
}

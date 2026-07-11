import { Component, input } from '@angular/core';
import { ShortcutOffer } from '../../interfaces/game-state';
import { Card } from '../card/card';

@Component({
  selector: 'app-shortcut-card',
  standalone: true,
  templateUrl: './shortcut-card.html',
  styleUrl: './shortcut-card.scss',
  imports: [Card],
})
export class ShortcutCard {
  shortcut = input.required<ShortcutOffer>();
}

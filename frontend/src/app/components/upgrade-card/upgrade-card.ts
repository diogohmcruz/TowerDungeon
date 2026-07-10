import { Component, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { UpgradeOffer } from '../../interfaces/game-state';
import { Card } from '../card/card';

@Component({
  selector: 'app-upgrade-card',
  standalone: true,
  templateUrl: './upgrade-card.html',
  styleUrl: './upgrade-card.scss',
  imports: [DecimalPipe, Card],
})
export class UpgradeCard {
  offer = input.required<UpgradeOffer>();
  affordable = input<boolean>(false);
  buy = output<string>();
}

import { Component, input } from '@angular/core';
import { MilestoneOffer } from '../../interfaces/game-state';
import { Card } from '../card/card';

@Component({
  selector: 'app-milestone-card',
  standalone: true,
  templateUrl: './milestone-card.html',
  styleUrl: './milestone-card.scss',
  imports: [Card],
})
export class MilestoneCard {
  milestone = input.required<MilestoneOffer>();
}

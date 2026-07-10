import { Component, input, output } from '@angular/core';
import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { UnitStats } from '../../interfaces/unit.enum';
import { Card } from '../card/card';

@Component({
  selector: 'app-unit-card',
  standalone: true,
  templateUrl: './unit-card.html',
  styleUrl: './unit-card.scss',
  imports: [DecimalPipe, LowerCasePipe, Card],
})
export class UnitCard {
  type = input.required<string>();
  stats = input<UnitStats | undefined>();
  buy = output<string>();
}

import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-expedition-loot',
  standalone: true,
  templateUrl: './expedition-loot.html',
  styleUrl: './expedition-loot.scss',
  imports: [DecimalPipe],
})
export class ExpeditionLoot {
  credits = input<number>(0);
  materials = input<number>(0);
  relics = input<number>(0);
}

import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Member } from '../../interfaces/member';

@Component({
  selector: 'app-enemy-card',
  standalone: true,
  templateUrl: './enemy-card.html',
  styleUrl: './enemy-card.scss',
  imports: [DecimalPipe],
})
export class EnemyCard {
  enemy = input.required<Member>();
  healthPct = computed(() => {
    const e = this.enemy();
    return e.stats.health ? (e.currentHealth / e.stats.health) * 100 : 0;
  });
}

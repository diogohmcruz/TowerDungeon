import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Member } from '../../interfaces/member';
import { Card } from '../card/card';

@Component({
  selector: 'app-enemy-card',
  standalone: true,
  templateUrl: './enemy-card.html',
  styleUrl: './enemy-card.scss',
  imports: [DecimalPipe, Card],
})
export class EnemyCard {
  enemy = input.required<Member>();
  maxHealth = computed(
    () => this.enemy().maxHealth ?? this.enemy().stats.health,
  );
  healthPct = computed(() => {
    const max = this.maxHealth();
    return max ? (this.enemy().currentHealth / max) * 100 : 0;
  });
  isBoss = computed(() => this.enemy().boss === true);
}

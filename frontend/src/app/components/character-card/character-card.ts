import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Member } from '../../interfaces/member';
import { Card } from '../card/card';

@Component({
  selector: 'app-character-card',
  standalone: true,
  templateUrl: './character-card.html',
  styleUrl: './character-card.scss',
  imports: [DecimalPipe, Card],
})
export class CharacterCard {
  member = input.required<Member>();
  maxHealth = computed(
    () => this.member().maxHealth ?? this.member().stats.health,
  );
  healthPct = computed(() => {
    const max = this.maxHealth();
    return max ? (this.member().currentHealth / max) * 100 : 0;
  });
  maxMana = computed(() => this.member().maxMana ?? 0);
  manaPct = computed(() => {
    const max = this.maxMana();
    return max ? ((this.member().currentMana ?? 0) / max) * 100 : 0;
  });
}

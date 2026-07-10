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
  healthPct = computed(() => {
    const m = this.member();
    return m.stats.health ? (m.currentHealth / m.stats.health) * 100 : 0;
  });
}

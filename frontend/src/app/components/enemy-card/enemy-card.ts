import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { BodyPartKind, Member } from '../../interfaces/member';
import { Card } from '../card/card';

const PART_LABELS: Record<BodyPartKind, string> = {
  TORSO: 'Torso',
  HEAD: 'Head',
  LEFT_ARM: 'Left arm',
  RIGHT_ARM: 'Right arm',
  LEFT_LEG: 'Left leg',
  RIGHT_LEG: 'Right leg',
};

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
  /** A bar per body part; a creature with a single body shows only the main bar. */
  partBars = computed(() => {
    const parts = this.enemy().parts ?? [];
    if (parts.length < 2) {
      return [];
    }
    return parts.map((part) => ({
      kind: part.kind,
      lost: part.lost,
      pct: part.maxHealth ? (part.currentHealth / part.maxHealth) * 100 : 0,
      title: part.lost
        ? `${PART_LABELS[part.kind]}: lost`
        : `${PART_LABELS[part.kind]}: ${Math.round(part.currentHealth)} / ${Math.round(part.maxHealth)}`,
    }));
  });
  /** The unit view's form: every body part in words, and the Salted and Guardian states. */
  detailed = input(false);
  /** Every part with its health and a state in words; one born without a part says none. */
  partRows = computed(() =>
    (this.enemy().parts ?? []).map((part) => {
      const state = part.lost
        ? part.maxHealth > 0
          ? 'lost'
          : 'none'
        : part.currentHealth < part.maxHealth - 1e-9
          ? 'wounded'
          : 'whole';
      return {
        kind: part.kind,
        label: PART_LABELS[part.kind],
        state,
        health: part.lost
          ? ''
          : `${Math.round(part.currentHealth)} / ${Math.round(part.maxHealth)}`,
      };
    }),
  );
  isSalted = computed(() => this.enemy().salted === true);
  isBoss = computed(() => this.enemy().boss === true);
}

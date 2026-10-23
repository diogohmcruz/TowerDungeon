import { Component, computed, input, model, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { LoadoutPreview } from '../../interfaces/village';

/**
 * What the Party takes when it sets out. Collapsed by default: setting out stays one click with the
 * defaults, and the Loadout only adds options. Each line is its own block in the template, so a new
 * one (Panacea, Trust) is one more block and one more field on {@link LoadoutPreview}.
 */
@Component({
  selector: 'app-loadout',
  standalone: true,
  templateUrl: './loadout.html',
  styleUrl: './loadout.scss',
  imports: [DecimalPipe],
})
export class Loadout {
  readonly preview = input.required<LoadoutPreview>();
  readonly disabled = input(false);
  /** Salt chosen to salt the Supplies with. */
  readonly salt = model(0);

  readonly open = signal(false);

  /** The Salt that can be used: no more than held, and no more than would salt anything. */
  readonly saltMax = computed(() =>
    Math.max(0, Math.min(this.preview().saltHeld, this.preview().saltUseful)),
  );
  readonly saltChosen = computed(() =>
    Math.min(Math.max(0, this.salt()), this.saltMax()),
  );

  /** Share of the Supplies taken that the chosen Salt salts. */
  private readonly saltedShare = computed(() => {
    const p = this.preview();
    return p.suppliesTaken > 0
      ? Math.min(1, (this.saltChosen() * p.suppliesPerSalt) / p.suppliesTaken)
      : 0;
  });

  /** Whole seconds the Supplies last with {@code share} of them salted; null with no one to feed. */
  private lasts(share: number): number | null {
    const p = this.preview();
    const perUnit =
      p.drainPerUnit - share * (p.drainPerUnit - p.saltedDrainPerUnit);
    const drain = p.partySize * perUnit;
    return drain > 0 ? Math.floor(p.suppliesTaken / drain) : null;
  }

  readonly lastsPlain = computed(() => this.lasts(0));
  readonly lastsFullySalted = computed(() => this.lasts(1));
  readonly lastsChosen = computed(() => this.lasts(this.saltedShare()));

  readonly pantryHoldsShort = computed(() => {
    const seconds = this.preview().pantryHoldSeconds;
    return seconds !== null && seconds < 60;
  });

  toggle() {
    this.open.update((open) => !open);
  }

  more() {
    this.salt.set(Math.min(this.saltMax(), this.saltChosen() + 1));
  }

  fewer() {
    this.salt.set(Math.max(0, this.saltChosen() - 1));
  }
}

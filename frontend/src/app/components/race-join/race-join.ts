import { Component, computed, inject, output, signal } from '@angular/core';
import { RaceTerms } from '../../interfaces/race';
import { clock, refusalText } from '../../interfaces/race-copy';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { RaceService } from '../../services/race.service';

export interface TermsCard {
  terms: RaceTerms;
  /** The reason entry is refused, in words; null when it is open. */
  refusal: string | null;
  /** What the entry button does, or null when entry is refused. */
  action: { label: string; next: boolean } | null;
  limit: string;
  closes: string;
  attempts: string;
}

/** The Terms summary for one race, and the words for a refusal. Pure, so it is tested alone. */
export function termsCard(terms: RaceTerms, now: Date = new Date()): TermsCard {
  let refusal: string | null = null;
  if (terms.refusal === 'NOT_OPEN') {
    const closed = terms.closesAt !== null && new Date(terms.closesAt) <= now;
    refusal = refusalText(closed ? 'CLOSED' : 'NOT_YET_OPEN');
  } else if (terms.refusal !== null) {
    refusal = refusalText(terms.refusal);
  }
  let action: TermsCard['action'] = null;
  if (refusal === null) {
    if (terms.attemptOpen) {
      action = { label: `Return to attempt ${terms.attemptsUsed}`, next: false };
    } else if (terms.attemptsUsed === 0) {
      action = { label: 'Enter the Trial', next: false };
    } else {
      action = { label: `Begin attempt ${terms.attemptsUsed + 1}`, next: true };
    }
  }
  return {
    terms,
    refusal,
    action,
    limit: clock(terms.limitSeconds),
    closes:
      terms.closesAt === null
        ? 'No closing time is set.'
        : `Closes at ${new Date(terms.closesAt).toISOString().slice(0, 16).replace('T', ' ')} UTC.`,
    attempts: `${terms.attemptsLeft} of ${terms.maxAttempts} left`,
  };
}

/**
 * The join screen: each Season's Terms in plain numbers, and the way in. Entry that the server
 * would refuse is refused here in words, with the reason, and the button is not offered. Entering
 * sets the real Campaign aside, unchanged, until the Trial is left.
 */
@Component({
  selector: 'app-race-join',
  standalone: true,
  templateUrl: './race-join.html',
  styleUrl: './race-join.scss',
})
export class RaceJoin {
  private readonly races = inject(RaceService);
  private readonly ws = inject(GameWebSocketService);

  /** The panel asks to be closed. */
  readonly closed = output<void>();
  /** The player asks for a race's finished table. */
  readonly showTable = output<string>();

  protected readonly cards = signal<TermsCard[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly alias = signal('');
  protected readonly empty = computed(
    () => !this.loading() && !this.failed() && this.cards().length === 0,
  );

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.failed.set(false);
    try {
      const all = await this.races.races();
      this.cards.set(all.map((terms) => termsCard(terms)));
    } catch {
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  protected onAlias(event: Event): void {
    this.alias.set((event.target as HTMLInputElement).value);
  }

  protected enter(card: TermsCard): void {
    if (!card.action) {
      return;
    }
    this.ws.connectTrial({
      raceId: card.terms.raceId,
      next: card.action.next,
      alias: this.alias(),
    });
    this.closed.emit();
  }
}

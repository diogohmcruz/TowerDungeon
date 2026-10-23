import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RaceTerms } from '../../interfaces/race';
import { GameWebSocketService } from '../../services/game-web-socket.service';
import { RaceService } from '../../services/race.service';
import { RaceJoin, termsCard } from './race-join';

const terms = (over: Partial<RaceTerms> = {}): RaceTerms => ({
  raceId: 'season-1',
  kind: 'SEASON',
  finishFloor: 10,
  opensAt: null,
  closesAt: '2026-11-01T12:00:00Z',
  limitSeconds: 5400,
  maxAttempts: 3,
  maxConcurrent: 20,
  attemptsUsed: 0,
  attemptsLeft: 3,
  attemptOpen: false,
  refusal: null,
  ...over,
});

describe('termsCard', () => {
  const now = new Date('2026-10-24T00:00:00Z');

  it('summarises the Terms in plain numbers', () => {
    const card = termsCard(terms(), now);
    expect(card.limit).toBe('90:00');
    expect(card.closes).toBe('Closes at 2026-11-01 12:00 UTC.');
    expect(card.attempts).toBe('3 of 3 left');
    expect(card.action).toEqual({ label: 'Enter the Trial', next: false });
    expect(card.refusal).toBeNull();
  });

  it('says so when no closing time is set', () => {
    expect(termsCard(terms({ closesAt: null }), now).closes).toBe('No closing time is set.');
  });

  it('returns to an open attempt and begins the next one otherwise', () => {
    expect(termsCard(terms({ attemptsUsed: 2, attemptOpen: true }), now).action).toEqual({
      label: 'Return to attempt 2',
      next: false,
    });
    expect(termsCard(terms({ attemptsUsed: 1, attemptsLeft: 2 }), now).action).toEqual({
      label: 'Begin attempt 2',
      next: true,
    });
  });

  it('refuses with the reason when the attempts are used up', () => {
    const card = termsCard(terms({ attemptsUsed: 3, attemptsLeft: 0, refusal: 'NO_ATTEMPTS' }), now);
    expect(card.refusal).toBe('Every attempt has been used.');
    expect(card.action).toBeNull();
  });

  it('tells a closed Season from one not yet open', () => {
    const closed = termsCard(terms({ closesAt: '2026-10-01T00:00:00Z', refusal: 'NOT_OPEN' }), now);
    expect(closed.refusal).toBe('The Season is closed.');
    const early = termsCard(
      terms({ opensAt: '2026-11-01T00:00:00Z', closesAt: null, refusal: 'NOT_OPEN' }),
      now,
    );
    expect(early.refusal).toBe('The Season has not opened.');
    expect(early.action).toBeNull();
  });
});

describe('RaceJoin', () => {
  let fixture: ComponentFixture<RaceJoin>;
  let entered: unknown[];
  let tables: string[];
  let list: Promise<RaceTerms[]>;

  const el = () => fixture.nativeElement as HTMLElement;

  async function open(all: RaceTerms[]) {
    list = Promise.resolve(all);
    TestBed.configureTestingModule({
      providers: [
        { provide: RaceService, useValue: { races: () => list } },
        { provide: GameWebSocketService, useValue: { connectTrial: (s: unknown) => entered.push(s) } },
      ],
    });
    fixture = TestBed.createComponent(RaceJoin);
    fixture.componentInstance.showTable.subscribe((id) => tables.push(id));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    entered = [];
    tables = [];
  });

  it('shows the Terms and enters with the alias', async () => {
    await open([terms()]);
    expect(el().textContent).toContain('Finish Floor');
    expect(el().textContent).toContain('3 of 3 left');
    expect(el().textContent).toContain('20 Contenders');
    expect(el().textContent).toContain('The Tower keeps count.');
    const input = el().querySelector<HTMLInputElement>('.alias-input')!;
    input.value = 'Ada';
    input.dispatchEvent(new Event('input'));
    el().querySelector<HTMLButtonElement>('button.enter')!.click();
    expect(entered).toEqual([{ raceId: 'season-1', next: false, alias: 'Ada' }]);
  });

  it('offers no way in, and says why, when attempts are used up', async () => {
    await open([terms({ attemptsUsed: 3, attemptsLeft: 0, refusal: 'NO_ATTEMPTS' })]);
    expect(el().querySelector('button.enter')).toBeNull();
    expect(el().querySelector('.join-refusal')?.textContent).toContain('Every attempt has been used.');
    el().querySelector<HTMLButtonElement>('button.show-table')!.click();
    expect(tables).toEqual(['season-1']);
  });

  it('refuses a closed Season in words', async () => {
    await open([terms({ closesAt: '2020-01-01T00:00:00Z', refusal: 'NOT_OPEN' })]);
    expect(el().querySelector('button.enter')).toBeNull();
    expect(el().querySelector('.join-refusal')?.textContent).toContain('The Season is closed.');
  });

  it('offers no table before an attempt has been started', async () => {
    await open([terms()]);
    expect(el().querySelector('button.show-table')).toBeNull();
  });
});

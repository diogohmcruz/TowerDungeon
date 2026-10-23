import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RunSummary } from '../../interfaces/game-state';
import { RunSummaryCard } from './run-summary-card';

describe('RunSummaryCard', () => {
  let fixture: ComponentFixture<RunSummaryCard>;

  const summary = (overrides: Partial<RunSummary>): RunSummary => ({
    outcome: 'WIPED',
    startFloor: 0,
    highestFloor: 7,
    floorsCleared: 6,
    enemiesDefeated: 12,
    creditsGained: 40,
    materialsGained: 20,
    relicsGained: 0,
    unitsLost: 2,
    survivors: 0,
    fallen: [],
    ...overrides,
  });

  const render = (value: RunSummary): HTMLElement => {
    fixture.componentRef.setInput('summary', value);
    fixture.detectChanges();
    return fixture.nativeElement;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RunSummaryCard] });
    fixture = TestBed.createComponent(RunSummaryCard);
  });

  it('names each of the fallen with their type and floor, before the loot', () => {
    const el = render(
      summary({
        fallen: [
          { name: 'Tomas', type: 'ARCHER', floor: 7 },
          { name: 'Brann', type: 'TANK', floor: 7 },
        ],
      }),
    );

    const lines = Array.from(el.querySelectorAll('.fallen li')).map((li) =>
      li.textContent?.trim(),
    );
    expect(lines).toEqual(['Tomas, Archer, fell on Floor 7.', 'Brann, Tank, fell on Floor 7.']);
    const fallen = el.querySelector('.fallen')!;
    const stats = el.querySelector('.run-stats')!;
    expect(fallen.compareDocumentPosition(stats) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('shows no list of the fallen when nobody fell', () => {
    const el = render(summary({ outcome: 'EXTRACTED', unitsLost: 0, survivors: 3 }));

    expect(el.querySelector('.fallen')).toBeNull();
  });
});

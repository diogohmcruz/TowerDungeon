import { TestBed } from '@angular/core/testing';

import type { GameState, RunSummary } from '../../interfaces/game-state';
import { GameStateService } from '../../services/game-state.service';
import { buildingView } from './building-registry';
import { ANNEXES } from './annex-buildings';
import { BUILDINGS, buildingDefinition } from './buildings';
import { GATE } from './gate-building';
import {
  ledgerLines,
  outcomeTitle,
  runSummaryLines,
} from './records-buildings';
import { subjectOfVolume } from './village-subjects';

const STUDY = ANNEXES.find((a) => a.id === 'SCRIBE')!;

const summary = (over: Partial<RunSummary> = {}): RunSummary => ({
  outcome: 'EXTRACTED',
  startFloor: 0,
  highestFloor: 7,
  floorsCleared: 6,
  enemiesDefeated: 12,
  creditsGained: 1200,
  materialsGained: 20,
  relicsGained: 1,
  unitsLost: 1,
  survivors: 2,
  fallen: [{ name: 'Tomas', type: 'ARCHER', floor: 7 }],
  ...over,
});

describe('the Ledger at the Study and the Summary at the Gate', () => {
  let state: GameState;
  const load = (over: Partial<GameState>) => {
    const service = TestBed.inject(GameStateService);
    service.update(over);
    state = service.state();
  };

  beforeEach(() => load({}));

  it('are carried by the Study and the Gate, which are registered', () => {
    expect(buildingDefinition('SCRIBE', BUILDINGS)).toBe(STUDY);
    expect(buildingDefinition('GATE', BUILDINGS)).toBe(GATE);
    expect(STUDY.sections?.map((x) => x.id)).toEqual(['ledger']);
    expect(GATE.sections?.map((x) => x.id)).toEqual(['expedition-summary']);
  });

  it('reads the Ledger most recent first, losses marked with a minus', () => {
    load({
      ledger: [
        { text: 'Emergency Tilling. Pantry below 20.', loss: false },
        { text: 'Starvation. 1 Breaker lost.', loss: true },
      ],
    });
    const section = buildingView(STUDY, state).sections[0];
    expect(section.lines.map((l) => `${l.mark} ${l.text}`)).toEqual([
      '- Starvation. 1 Breaker lost.',
      '. Emergency Tilling. Pantry below 20.',
    ]);
    expect(section.scroll).toBeTrue();
  });

  it('says so when nothing is written', () => {
    const section = buildingView(STUDY, state).sections[0];
    expect(section.lines).toEqual([]);
    expect(section.empty).toBe('Nothing is written yet.');
  });

  it('does not reorder the frame\'s own Ledger', () => {
    load({
      ledger: [
        { text: 'a', loss: false },
        { text: 'b', loss: false },
      ],
    });
    ledgerLines(state);
    expect(state.ledger[0].text).toBe('a');
  });

  it('writes the fallen first, each with a Floor, then the figures', () => {
    const lines = runSummaryLines(summary());
    expect(lines[0]).toEqual({
      text: 'Tomas, Archer, fell on Floor 7.',
      loss: true,
    });
    expect(lines.find((l) => l.text === 'Credits 1,200')?.loss).toBeFalse();
    expect(lines.find((l) => l.text === 'Units lost 1')?.loss).toBeTrue();
  });

  it("writes a Wipe's loot as lost, as plainly as a gain", () => {
    const lines = runSummaryLines(
      summary({ outcome: 'WIPED', survivors: 0, sporesGained: 3 }),
    );
    for (const text of [
      'Credits lost 1,200',
      'Materials lost 20',
      'Relics lost 1',
      'Dragon Spores lost 3',
    ]) {
      expect(lines.find((l) => l.text === text)?.loss)
        .withContext(text)
        .toBeTrue();
    }
  });

  it('titles the Outcome and heads the section with the Floors', () => {
    expect(outcomeTitle(summary({ outcome: 'WIPED' }))).toBe('Wipe');
    expect(outcomeTitle(summary({ outcome: 'VICTORY' }))).toBe('Summit Conquered');
    load({ lastRunSummary: summary() });
    expect(buildingView(GATE, state).sections[0].heading).toBe(
      'Extraction. Floor 0 → 7',
    );
  });

  it('keeps an empty Gate quiet before any Expedition came back', () => {
    expect(buildingView(GATE, state).sections[0].lines).toEqual([]);
    expect(GATE.opensOnReturn?.(state)).toBeFalse();
    load({ lastRunSummary: summary() });
    expect(GATE.opensOnReturn?.(state)).toBeTrue();
  });

  it('picks the Study and the Gate as buildings in the scene', () => {
    expect(subjectOfVolume('study-cell')).toEqual({ kind: 'annex', id: 'SCRIBE' });
    expect(subjectOfVolume('gate-lintel')).toEqual({ kind: 'building', id: 'GATE' });
  });
});

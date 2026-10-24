/**
 * The two things that are read, not worked (architecture doc 5.6): the Ledger,
 * read at the Scribe's Study, and the Expedition Summary, read at the Gate. Each
 * is a written section a building carries (`sections`); the popover and the
 * list view show the same lines. Losses are written as plainly as gains.
 */
import type { GameState, RunSummary } from '../../interfaces/game-state';
import type { SectionSpec, WrittenLine } from './building-registry';

/** A whole number with separators ("1,200"). */
function whole(n: number | undefined): string {
  return Math.round(n ?? 0).toLocaleString('en-US');
}

/** "ARCHER" and "DRAGON_SPORES" read as "Archer" and "Dragon spores". */
export function typeName(type: string): string {
  const words = type.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** What the Expedition was called: the Outcome in the game's words. */
export function outcomeTitle(summary: RunSummary): string {
  switch (summary.outcome) {
    case 'WIPED':
      return 'Wipe';
    case 'VICTORY':
      return 'Summit Conquered';
    default:
      return 'Extraction';
  }
}

/**
 * The Expedition Summary as lines: the fallen first, each named with the Floor
 * they fell on, then the figures. On a Wipe the loot is "lost", and the words and
 * the "-" mark say so, never colour alone.
 */
export function runSummaryLines(summary: RunSummary): WrittenLine[] {
  const wiped = summary.outcome === 'WIPED';
  const lines: WrittenLine[] = summary.fallen.map((unit) => ({
    text: `${unit.name}, ${typeName(unit.type)}, fell on Floor ${unit.floor}.`,
    loss: true,
  }));
  lines.push(
    { text: `Floors cleared ${whole(summary.floorsCleared)}`, loss: false },
    { text: `Enemies slain ${whole(summary.enemiesDefeated)}`, loss: false },
    { text: `Survivors ${whole(summary.survivors)}`, loss: false },
    { text: `Units lost ${whole(summary.unitsLost)}`, loss: true },
    {
      text: `${wiped ? 'Credits lost' : 'Credits'} ${whole(summary.creditsGained)}`,
      loss: wiped,
    },
    {
      text: `${wiped ? 'Materials lost' : 'Materials'} ${whole(summary.materialsGained)}`,
      loss: wiped,
    },
    {
      text: `${wiped ? 'Relics lost' : 'Relics'} ${whole(summary.relicsGained)}`,
      loss: wiped,
    },
  );
  if ((summary.sporesGained ?? 0) > 0) {
    lines.push({
      text: `${wiped ? 'Dragon Spores lost' : 'Dragon Spores'} ${whole(summary.sporesGained)}`,
      loss: wiped,
    });
  }
  return lines;
}

/** The Ledger, most recent first. */
export function ledgerLines(state: GameState): WrittenLine[] {
  return [...state.ledger].reverse().map((l) => ({ text: l.text, loss: l.loss }));
}

/** The Ledger section: most recent first, scrolling inside the card. */
export const LEDGER_SECTION: SectionSpec = {
  id: 'ledger',
  heading: () => 'The Ledger',
  lines: ledgerLines,
  empty: 'Nothing is written yet.',
  scroll: true,
};

/** The Expedition Summary section: the last Expedition that came back. */
export const RUN_SUMMARY_SECTION: SectionSpec = {
  id: 'expedition-summary',
  heading: (s: GameState) =>
    s.lastRunSummary
      ? `${outcomeTitle(s.lastRunSummary)}. Floor ${s.lastRunSummary.startFloor} → ${s.lastRunSummary.highestFloor}`
      : null,
  lines: (s: GameState) =>
    s.lastRunSummary ? runSummaryLines(s.lastRunSummary) : [],
  empty: 'No Expedition has come back yet.',
};

/** Whether an Expedition has come back with a Summary to open at the Gate. */
export function hasReturned(s: GameState): boolean {
  return !!s.lastRunSummary;
}
